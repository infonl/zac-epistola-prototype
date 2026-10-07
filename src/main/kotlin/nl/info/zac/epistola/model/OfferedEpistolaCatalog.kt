/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.model

import java.util.UUID

/**
 * The Epistola catalog whose templates a zaaktype offers, the informatieobjecttype their documents are stored under
 * unless a template sets its own. A template without a setting is offered and takes the zaaktype's
 * informatieobjecttype.
 */
data class OfferedEpistolaCatalog(
    val catalogId: String,
    val informatieObjectTypeUuid: UUID,
    val templateSettings: Map<String, EpistolaTemplateSetting> = emptyMap()
) {
    fun informatieObjectTypeUuidOf(templateId: String) =
        templateSettings[templateId]?.informatieObjectTypeUuid ?: informatieObjectTypeUuid

    fun isOffered(templateId: String) = templateSettings[templateId]?.isEnabled ?: true
}
