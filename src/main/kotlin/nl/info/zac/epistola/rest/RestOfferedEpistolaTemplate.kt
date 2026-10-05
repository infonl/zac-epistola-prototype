/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.UUID

/** A template a zaak can generate a document from, and the informatieobjecttype that document is stored under. */
@NoArgConstructor
@AllOpen
data class RestOfferedEpistolaTemplate(
    var id: String,
    var name: String,
    var informatieObjectTypeUUID: UUID
)
