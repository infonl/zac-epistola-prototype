/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.util.NoArgConstructor

/** No [status] until Epistola has reported on the job, and none once the document is stored or has failed. */
@NoArgConstructor
data class RestEpistolaDocumentCreationStatus(
    val status: EpistolaDocumentCreationStatus?
)
