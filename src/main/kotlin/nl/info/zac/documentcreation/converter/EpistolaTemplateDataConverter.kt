/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.converter

import jakarta.json.bind.JsonbBuilder
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.DocumentCreationData

private const val SCHEMA_PROPERTIES = "properties"

/**
 * Serializing and reading back is what keeps the two providers in step: the payload is produced by
 * the same JSON-B annotations that produce the SmartDocuments deposit, so a field added to
 * [DocumentCreationData] reaches both, under the same name and in the same date format.
 */
private val JSONB = JsonbBuilder.create()

/**
 * Turns the zaak data into the payload for an Epistola template, keeping only what that template's
 * JSON Schema declares.
 *
 * The schema is the allow-list, and it is applied even where a schema would permit additional
 * properties. What a citizen filled in on a startformulier arrives as an unfiltered map of
 * everything they submitted — a BSN, a telephone number, a medical circumstance — and a template
 * that does not declare a field has no use for it, so ZAC does not send it.
 */
fun DocumentCreationData.toEpistolaTemplateData(templateId: String, templateSchema: Any?): Map<String, Any> {
    val declaredProperties = templateSchema.declaredProperties()
        ?: throw EpistolaTemplateSchemaMissingException(
            "Epistola template '$templateId' declares no schema, so ZAC cannot determine which zaak " +
                "data it may receive."
        )
    return retainDeclared(toPayloadMap(), declaredProperties)
}

@Suppress("UNCHECKED_CAST")
private fun DocumentCreationData.toPayloadMap(): Map<String, Any> =
    JSONB.fromJson(JSONB.toJson(this), Map::class.java) as Map<String, Any>

/**
 * A property declared without properties of its own — a string, a number, a free-form object — is
 * taken whole; one that declares them is filtered recursively, so a nested object cannot smuggle a
 * field past the allow-list.
 */
private fun retainDeclared(data: Map<String, Any>, declaredProperties: Map<*, *>): Map<String, Any> =
    data.mapNotNull { (name, value) ->
        if (!declaredProperties.containsKey(name)) return@mapNotNull null
        val nestedProperties = declaredProperties[name].declaredProperties()
        when {
            nestedProperties == null -> name to value
            value !is Map<*, *> -> name to value
            else -> retainDeclared(value.asStringKeyedMap(), nestedProperties)
                .takeIf { it.isNotEmpty() }
                ?.let { name to it }
        }
    }.toMap()

/** Reads a JSON Schema's `properties`, whichever shape JSON-B gave the schema. */
private fun Any?.declaredProperties(): Map<*, *>? = (this as? Map<*, *>)?.get(SCHEMA_PROPERTIES) as? Map<*, *>

private fun Map<*, *>.asStringKeyedMap(): Map<String, Any> =
    mapNotNull { (key, value) -> if (key is String && value != null) key to value else null }.toMap()
