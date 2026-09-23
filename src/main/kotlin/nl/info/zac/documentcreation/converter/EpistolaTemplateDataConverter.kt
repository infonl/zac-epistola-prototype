/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.converter

import jakarta.json.bind.JsonbBuilder
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.DocumentCreationData
import java.util.logging.Logger

private const val SCHEMA_PROPERTIES = "properties"
private const val SCHEMA_ITEMS = "items"
private const val SCHEMA_REFERENCE = "\$ref"
private const val SCHEMA_TYPE = "type"
private const val SCHEMA_TYPE_OBJECT = "object"
private const val SCHEMA_TYPE_ARRAY = "array"
private const val LOCAL_REFERENCE_PREFIX = "#"
private const val JSON_POINTER_SEPARATOR = "/"
private val SCHEMA_COMBINATIONS = listOf("allOf", "anyOf", "oneOf")

/**
 * Serializing and reading back is what keeps the two providers in step: the payload is produced by
 * the same JSON-B annotations that produce the SmartDocuments deposit.
 */
private val JSONB = JsonbBuilder.create()

/**
 * The template's JSON Schema is used as an allow-list, not as a validator: `additionalProperties` is
 * ignored, because the startformulier data is an unfiltered map of everything a citizen submitted.
 */
fun DocumentCreationData.toEpistolaTemplateData(templateId: String, templateSchema: Any?): Map<String, Any> =
    TemplateSchemaAllowList(templateId = templateId, rootSchema = templateSchema).retain(toPayloadMap())

@Suppress("UNCHECKED_CAST")
private fun DocumentCreationData.toPayloadMap(): Map<String, Any> =
    JSONB.fromJson(JSONB.toJson(this), Map::class.java) as Map<String, Any>

private class TemplateSchemaAllowList(private val templateId: String, private val rootSchema: Any?) {
    companion object {
        private val LOG = Logger.getLogger(TemplateSchemaAllowList::class.java.name)
    }

    fun retain(payload: Map<String, Any>): Map<String, Any> {
        val rootShape = shapeOf(listOf(rootSchema))
        if (!rootShape.hasDeclaredProperties) {
            throw EpistolaTemplateSchemaMissingException(
                "Epistola template '$templateId' declares no schema, so ZAC cannot determine which zaak " +
                    "data it may receive."
            )
        }
        return retainDeclaredProperties(data = payload, shape = rootShape, path = "")
    }

    private fun retainDeclaredProperties(
        data: Map<String, Any>,
        shape: SchemaShape,
        path: String
    ): Map<String, Any> =
        data.mapNotNull { (name, value) ->
            shape.propertySchemas[name]
                ?.let { retainDeclared(value = value, schemas = it, path = "$path$name") }
                ?.takeUnless { it is Map<*, *> && it.isEmpty() }
                ?.let { name to it }
        }.toMap()

    private fun retainDeclared(value: Any, schemas: List<Any?>, path: String): Any? {
        val shape = shapeOf(schemas)
        return when (value) {
            is Map<*, *> -> when {
                shape.hasDeclaredProperties ->
                    retainDeclaredProperties(data = value.asStringKeyedMap(), shape = shape, path = "$path.")
                shape.isDeclaredWholeAs(SCHEMA_TYPE_OBJECT) -> value
                else -> leaveOut(path = path, shape = shape, expectedType = SCHEMA_TYPE_OBJECT)
            }
            is List<*> -> when {
                shape.itemSchemas.isNotEmpty() -> retainDeclaredItems(items = value, shape = shape, path = path)
                shape.isDeclaredWholeAs(SCHEMA_TYPE_ARRAY) -> value
                else -> leaveOut(path = path, shape = shape, expectedType = SCHEMA_TYPE_ARRAY)
            }
            else -> value
        }
    }

    /** A list with an item left out would render as complete in the document, so it is left out as a whole. */
    private fun retainDeclaredItems(items: List<*>, shape: SchemaShape, path: String): List<Any>? {
        val retainedItems = mutableListOf<Any>()
        for (item in items.filterNotNull()) {
            retainedItems += retainDeclared(value = item, schemas = shape.itemSchemas, path = "$path[]") ?: return null
        }
        return retainedItems
    }

    private fun leaveOut(path: String, shape: SchemaShape, expectedType: String): Any? {
        LOG.warning {
            "Left '$path' out of the payload for Epistola template '$templateId': " +
                if (shape.unresolvedReferences.isNotEmpty()) {
                    "its schema refers to ${shape.unresolvedReferences}, which ZAC cannot resolve."
                } else {
                    "ZAC holds an $expectedType there, and the template neither declares its fields nor " +
                        "declares it as type '$expectedType'."
                }
        }
        return null
    }

    private fun shapeOf(schemas: List<Any?>): SchemaShape {
        val components = schemas.map { componentsOf(schema = it, visitedReferences = emptySet()) }
            .fold(SchemaComponents(), SchemaComponents::plus)
        val propertyMaps = components.schemas.mapNotNull { it[SCHEMA_PROPERTIES] as? Map<*, *> }
        return SchemaShape(
            hasDeclaredProperties = propertyMaps.isNotEmpty(),
            propertySchemas = propertyMaps.flatMap { it.entries }
                .filter { it.key is String }
                .groupBy(keySelector = { it.key as String }, valueTransform = { it.value }),
            itemSchemas = components.schemas.mapNotNull { it[SCHEMA_ITEMS] as? Map<*, *> },
            declaredTypes = components.schemas.flatMap { it[SCHEMA_TYPE].asTypeNames() }.toSet(),
            unresolvedReferences = components.unresolvedReferences
        )
    }

    /**
     * For `anyOf` and `oneOf` ZAC cannot know which alternative the template renders, so it allows the
     * fields of every alternative.
     */
    private fun componentsOf(schema: Any?, visitedReferences: Set<String>): SchemaComponents {
        val schemaMap = schema as? Map<*, *> ?: return SchemaComponents()
        val referenced = (schemaMap[SCHEMA_REFERENCE] as? String)
            ?.takeUnless { it in visitedReferences }
            ?.let { reference ->
                resolveLocalReference(reference)
                    ?.let { componentsOf(schema = it, visitedReferences = visitedReferences + reference) }
                    ?: SchemaComponents(unresolvedReferences = listOf(reference))
            } ?: SchemaComponents()
        return SCHEMA_COMBINATIONS
            .flatMap { (schemaMap[it] as? List<*>).orEmpty() }
            .map { componentsOf(schema = it, visitedReferences = visitedReferences) }
            .fold(SchemaComponents(schemas = listOf(schemaMap)) + referenced, SchemaComponents::plus)
    }

    private fun resolveLocalReference(reference: String): Any? {
        if (!reference.startsWith(LOCAL_REFERENCE_PREFIX)) return null
        val pointer = reference.removePrefix(LOCAL_REFERENCE_PREFIX)
        return when {
            pointer.isEmpty() -> rootSchema
            !pointer.startsWith(JSON_POINTER_SEPARATOR) -> null
            else -> pointer.removePrefix(JSON_POINTER_SEPARATOR)
                .split(JSON_POINTER_SEPARATOR)
                .map { it.replace("~1", "/").replace("~0", "~") }
                .fold(rootSchema) { node, segment ->
                    when (node) {
                        is Map<*, *> -> node[segment]
                        is List<*> -> segment.toIntOrNull()?.let(node::getOrNull)
                        else -> null
                    }
                }
        }
    }
}

private data class SchemaComponents(
    val schemas: List<Map<*, *>> = emptyList(),
    val unresolvedReferences: List<String> = emptyList()
) {
    operator fun plus(other: SchemaComponents) = SchemaComponents(
        schemas = schemas + other.schemas,
        unresolvedReferences = unresolvedReferences + other.unresolvedReferences
    )
}

private class SchemaShape(
    val hasDeclaredProperties: Boolean,
    val propertySchemas: Map<String, List<Any?>>,
    val itemSchemas: List<Any?>,
    val declaredTypes: Set<String>,
    val unresolvedReferences: List<String>
) {
    fun isDeclaredWholeAs(type: String) = type in declaredTypes && unresolvedReferences.isEmpty()
}

private fun Any?.asTypeNames(): List<String> = when (this) {
    is String -> listOf(this)
    is List<*> -> filterIsInstance<String>()
    else -> emptyList()
}

private fun Map<*, *>.asStringKeyedMap(): Map<String, Any> =
    mapNotNull { (key, value) -> if (key is String && value != null) key to value else null }.toMap()
