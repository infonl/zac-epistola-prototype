/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import nl.info.client.epistola.model.EpistolaJobStatus

enum class EpistolaDocumentCreationStatus {
    WAITING_IN_QUEUE,
    HELD_UP_IN_QUEUE,
    RENDERING,
    HELD_UP_IN_RENDERING,
    STORING
}

fun EpistolaJobStatus.toEpistolaDocumentCreationStatus() =
    when (this) {
        EpistolaJobStatus.WAITING_IN_QUEUE -> EpistolaDocumentCreationStatus.WAITING_IN_QUEUE
        EpistolaJobStatus.HELD_UP_IN_QUEUE -> EpistolaDocumentCreationStatus.HELD_UP_IN_QUEUE
        EpistolaJobStatus.RENDERING -> EpistolaDocumentCreationStatus.RENDERING
        EpistolaJobStatus.HELD_UP_IN_RENDERING -> EpistolaDocumentCreationStatus.HELD_UP_IN_RENDERING
    }
