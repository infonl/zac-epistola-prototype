/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.UUID

/**
 * The Epistola catalog whose templates a zaaktype offers, and the informatieobjecttype a document generated from one
 * of them is stored under, which is empty until the beheerder chooses one. The [templateSettings] are the templates
 * that differ from the zaaktype, and replace those stored: a template without one is offered and takes the zaaktype's
 * informatieobjecttype.
 */
@NoArgConstructor
@AllOpen
data class RestEpistolaCatalogMapping(
    @field:NotBlank
    var catalogId: String,
    var informatieObjectTypeUUID: UUID?,
    @field:NotNull
    @field:Valid
    var templateSettings: List<RestEpistolaTemplateSetting>
)

/**
 * The informatieobjecttype of one template of the catalog, empty when it takes the zaaktype's, and whether Document
 * maken offers it. The getter renames what is written and the setter what is read, so both carry the name: on the
 * getter alone JSON-B reads nothing and every save would hide the template.
 */
@NoArgConstructor
@AllOpen
data class RestEpistolaTemplateSetting(
    @field:NotBlank
    var templateId: String,
    var informatieObjectTypeUUID: UUID?,
    @get:JsonbProperty("isEnabled")
    @set:JsonbProperty("isEnabled")
    @field:NotNull
    var isEnabled: Boolean?
)

/**
 * Checks a mapping before it replaces the stored one.
 *
 * @param availableCatalogIds the catalogs ZAC offers of the tenant's
 * @param informatieobjecttypeUuids the informatieobjecttypen of the zaaktype, the only ones Open Zaak accepts for a
 * document linked to a zaak of that type
 * @throws EpistolaTemplateMappingException naming the check that failed
 */
fun RestEpistolaCatalogMapping.validate(availableCatalogIds: Set<String>, informatieobjecttypeUuids: Set<UUID>) {
    if (catalogId !in availableCatalogIds) {
        throw EpistolaTemplateMappingException("Unknown Epistola catalog: '$catalogId'")
    }
    validateInformatieobjecttype(informatieobjecttypeUuids)
    validateTemplateSettings(informatieobjecttypeUuids)
}

private fun RestEpistolaCatalogMapping.validateTemplateSettings(informatieobjecttypeUuids: Set<UUID>) {
    templateSettings.mapNotNull { it.informatieObjectTypeUUID }.firstOrNull { it !in informatieobjecttypeUuids }
        ?.let {
            throw EpistolaTemplateMappingException("Informatieobjecttype '$it' is not one of the zaaktype's.")
        }
}

private fun RestEpistolaCatalogMapping.validateInformatieobjecttype(informatieobjecttypeUuids: Set<UUID>) {
    val informatieObjectTypeUuid = informatieObjectTypeUUID ?: throw EpistolaTemplateMappingException(
        "An informatieobjecttype is needed to store the Epistola documents of the zaaktype under."
    )
    if (informatieObjectTypeUuid !in informatieobjecttypeUuids) {
        throw EpistolaTemplateMappingException("Informatieobjecttype '$informatieObjectTypeUuid' is not one of the zaaktype's.")
    }
}
