/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.UUID

/**
 * The Epistola catalog whose templates a zaaktype offers, and the informatieobjecttype a document generated from one of
 * them is stored under. The informatieobjecttype is empty until the beheerder chooses one.
 */
@NoArgConstructor
@AllOpen
data class RestEpistolaCatalogMapping(
    var catalogId: String,
    var informatieObjectTypeUUID: UUID?
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
}

private fun RestEpistolaCatalogMapping.validateInformatieobjecttype(informatieobjecttypeUuids: Set<UUID>) {
    val informatieObjectTypeUuid = informatieObjectTypeUUID ?: throw EpistolaTemplateMappingException(
        "An informatieobjecttype is needed to store the Epistola documents of the zaaktype under."
    )
    if (informatieObjectTypeUuid !in informatieobjecttypeUuids) {
        throw EpistolaTemplateMappingException("Informatieobjecttype '$informatieObjectTypeUuid' is not one of the zaaktype's.")
    }
}
