/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.templates.model

import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import java.time.ZonedDateTime
import java.util.UUID

fun createEpistolaTemplateGroup(
    id: Long? = 1L,
    name: String = "fakeTemplateGroupName",
    zaaktypeConfiguration: ZaaktypeConfiguration = createZaaktypeCmmnConfiguration(),
    templateIdsToInformatieObjectTypeUuids: Map<String, UUID> = mapOf("fake-template-id" to UUID.randomUUID())
) = EpistolaTemplateGroup().apply {
    this.id = id
    this.name = name
    this.zaaktypeConfiguration = zaaktypeConfiguration
    creationDate = ZonedDateTime.now()
    templateIdsToInformatieObjectTypeUuids.forEach { (epistolaId, informatieObjectTypeUUID) ->
        addTemplate(epistolaId = epistolaId, informatieObjectTypeUUID = informatieObjectTypeUUID)
    }
}
