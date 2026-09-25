/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import nl.info.zac.util.NoArgConstructor
import java.util.UUID

@NoArgConstructor
data class RestEpistolaDocumentCreationResponse(
    val informatieobjectUuid: UUID
)
