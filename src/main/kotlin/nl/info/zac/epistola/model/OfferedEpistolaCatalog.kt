/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.model

import java.util.UUID

/**
 * The Epistola catalog whose templates a zaaktype offers, the informatieobjecttype their documents are stored under,
 * and the BCP-47 tag of the language the beheerder chose for all of them, if any.
 */
data class OfferedEpistolaCatalog(
    val catalogId: String,
    val informatieObjectTypeUuid: UUID,
    val locale: String? = null
)
