/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.EpistolaJobStatus.HELD_UP_IN_QUEUE
import nl.info.client.epistola.model.EpistolaJobStatus.HELD_UP_IN_RENDERING
import nl.info.client.epistola.model.EpistolaJobStatus.RENDERING
import nl.info.client.epistola.model.EpistolaJobStatus.WAITING_IN_QUEUE
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT

/**
 * ZAC cancels the job before throwing this, so trying again does not leave a second document at Epistola. The job's
 * last status says where Epistola held it up: in its queue, which is shared by every tenant, or while rendering it.
 */
class EpistolaDocumentGenerationTimeoutException(
    message: String,
    lastJobStatus: EpistolaJobStatus?
) : EpistolaException(
    errorCode = when (lastJobStatus) {
        WAITING_IN_QUEUE, HELD_UP_IN_QUEUE -> ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE
        RENDERING, HELD_UP_IN_RENDERING -> ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING
        null -> ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT
    },
    message = message
)
