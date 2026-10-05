/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.documents.model

import java.time.ZonedDateTime
import java.util.UUID

fun createEpistolaDocument(
    informatieObjectUUID: UUID = UUID.randomUUID(),
    templateId: String = "fake-template-id",
    kanaal: String? = null,
    locale: String? = null
) = EpistolaDocument().apply {
    this.informatieObjectUUID = informatieObjectUUID
    this.templateId = templateId
    this.kanaal = kanaal
    this.locale = locale
    creationDate = ZonedDateTime.now()
}
