/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.model

import java.util.UUID

/** The Epistola catalog whose templates a zaaktype offers, and the informatieobjecttype their documents are stored under. */
data class OfferedEpistolaCatalog(
    val catalogId: String,
    val informatieObjectTypeUuid: UUID
)
