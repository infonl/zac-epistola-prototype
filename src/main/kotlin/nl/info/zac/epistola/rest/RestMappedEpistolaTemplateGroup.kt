/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.epistola.templates.model.EpistolaTemplateGroup
import nl.info.zac.epistola.templates.model.addTemplate
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.ZonedDateTime
import java.util.UUID

@NoArgConstructor
@AllOpen
data class RestMappedEpistolaTemplateGroup(
    var name: String,
    var templates: List<RestMappedEpistolaTemplate>
)

@NoArgConstructor
@AllOpen
data class RestMappedEpistolaTemplate(
    var id: String,
    /** Read from Epistola on every read and ignored on save, so a renamed template never shows a stale name. */
    var name: String,
    var informatieObjectTypeUUID: UUID
)

fun RestMappedEpistolaTemplateGroup.toEpistolaTemplateGroup(zaaktypeConfiguration: ZaaktypeConfiguration) =
    EpistolaTemplateGroup().apply {
        name = this@toEpistolaTemplateGroup.name.trim()
        creationDate = ZonedDateTime.now()
        this.zaaktypeConfiguration = zaaktypeConfiguration
        this@toEpistolaTemplateGroup.templates.forEach {
            addTemplate(epistolaId = it.id, informatieObjectTypeUUID = it.informatieObjectTypeUUID)
        }
    }

/** Leaves out every template that [templateNamesById] does not know, because Epistola no longer has it. */
fun EpistolaTemplateGroup.toRestMappedEpistolaTemplateGroup(templateNamesById: Map<String, String>) =
    RestMappedEpistolaTemplateGroup(
        name = name,
        templates = templates.mapNotNull { template ->
            templateNamesById[template.epistolaId]?.let { templateName ->
                RestMappedEpistolaTemplate(
                    id = template.epistolaId,
                    name = templateName,
                    informatieObjectTypeUUID = template.informatieObjectTypeUUID
                )
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    )
