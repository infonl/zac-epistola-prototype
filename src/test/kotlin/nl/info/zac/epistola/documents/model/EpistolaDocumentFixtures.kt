/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.documents.model

import java.time.ZonedDateTime
import java.util.UUID

fun createEpistolaDocument(
    informatieObjectUUID: UUID = UUID.randomUUID(),
    catalogId: String? = "fake-catalog-id",
    templateId: String = "fake-template-id"
) = EpistolaDocument().apply {
    this.informatieObjectUUID = informatieObjectUUID
    this.catalogId = catalogId
    this.templateId = templateId
    creationDate = ZonedDateTime.now()
}
