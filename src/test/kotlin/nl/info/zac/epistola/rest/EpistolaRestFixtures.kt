/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import java.util.UUID

fun createRestEpistolaTemplate(
    id: String = "fake-template-id",
    name: String = "fakeTemplateName"
) = RestEpistolaTemplate(id = id, name = name)

fun createRestEpistolaTemplateSetting(
    templateId: String = "fake-template-id",
    informatieObjectTypeUUID: UUID? = null,
    isEnabled: Boolean = true
) = RestEpistolaTemplateSetting(
    templateId = templateId,
    informatieObjectTypeUUID = informatieObjectTypeUUID,
    isEnabled = isEnabled
)

fun createRestEpistolaCatalog(
    id: String = "fake-catalog-id",
    name: String = "fakeCatalogName"
) = RestEpistolaCatalog(id = id, name = name)

fun createRestEpistolaCatalogMapping(
    catalogId: String = "fake-catalog-id",
    informatieObjectTypeUUID: UUID? = UUID.randomUUID(),
    templateSettings: List<RestEpistolaTemplateSetting> = emptyList()
) = RestEpistolaCatalogMapping(
    catalogId = catalogId,
    informatieObjectTypeUUID = informatieObjectTypeUUID,
    templateSettings = templateSettings
)

fun createRestOfferedEpistolaTemplate(
    id: String = "fake-template-id",
    name: String = "fakeTemplateName",
    informatieObjectTypeUUID: UUID = UUID.randomUUID()
) = RestOfferedEpistolaTemplate(id = id, name = name, informatieObjectTypeUUID = informatieObjectTypeUUID)
