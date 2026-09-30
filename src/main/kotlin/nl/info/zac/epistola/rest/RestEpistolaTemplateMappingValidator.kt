/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import java.util.UUID

/**
 * Checks a template mapping before it replaces the stored one.
 *
 * @param availableTemplateIds the ids of the templates in ZAC's Epistola catalog
 * @param informatieobjecttypeUuids the informatieobjecttypen of the zaaktype, the only ones Open Zaak accepts
 * for a document linked to a zaak of that type
 * @throws EpistolaTemplateMappingException naming every group or template that fails a check
 */
fun List<RestMappedEpistolaTemplateGroup>.validate(
    availableTemplateIds: Set<String>,
    informatieobjecttypeUuids: Set<UUID>
) {
    validateGroupNames()
    validateTemplateIds(availableTemplateIds)
    validateInformatieobjecttypen(informatieobjecttypeUuids)
}

private fun List<RestMappedEpistolaTemplateGroup>.validateGroupNames() {
    if (any { it.name.isBlank() }) {
        throw EpistolaTemplateMappingException("Every template group needs a name.")
    }
    duplicatesOf { it.name.trim().lowercase() }.takeIf { it.isNotEmpty() }?.let {
        throw EpistolaTemplateMappingException("Template group names must be unique; duplicated: $it")
    }
}

private fun List<RestMappedEpistolaTemplateGroup>.validateTemplateIds(availableTemplateIds: Set<String>) {
    val templates = flatMap { it.templates }
    templates.duplicatesOf { it.id }.takeIf { it.isNotEmpty() }?.let {
        throw EpistolaTemplateMappingException("A template can be in only one template group; duplicated: $it")
    }
    templates.map { it.id }.filterNot(availableTemplateIds::contains).takeIf { it.isNotEmpty() }?.let {
        throw EpistolaTemplateMappingException("Unknown Epistola templates: $it")
    }
}

private fun List<RestMappedEpistolaTemplateGroup>.validateInformatieobjecttypen(informatieobjecttypeUuids: Set<UUID>) {
    flatMap { it.templates }
        .filterNot { it.informatieObjectTypeUUID in informatieobjecttypeUuids }
        .takeIf { it.isNotEmpty() }
        ?.let { templates ->
            throw EpistolaTemplateMappingException(
                "Templates mapped to an informatieobjecttype that is not one of the zaaktype's: " +
                    templates.map { "${it.id} -> ${it.informatieObjectTypeUUID}" }
            )
        }
}

private fun <T> List<T>.duplicatesOf(key: (T) -> String) =
    groupingBy(key).eachCount().filterValues { it > 1 }.keys
