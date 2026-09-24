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

fun createRestMappedEpistolaTemplate(
    id: String = "fake-template-id",
    name: String = "fakeTemplateName",
    informatieObjectTypeUUID: UUID = UUID.randomUUID()
) = RestMappedEpistolaTemplate(id = id, name = name, informatieObjectTypeUUID = informatieObjectTypeUUID)

fun createRestMappedEpistolaTemplateGroup(
    name: String = "fakeTemplateGroupName",
    templates: List<RestMappedEpistolaTemplate> = listOf(createRestMappedEpistolaTemplate())
) = RestMappedEpistolaTemplateGroup(name = name, templates = templates)
