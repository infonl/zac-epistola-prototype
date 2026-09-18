/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_FAILED
import nl.info.zac.exception.ServerErrorException

/**
 * Epistola accepted the generation request but the job did not produce a document.
 *
 * Separate from the exceptions the generated client throws for a rejected request: here the call
 * succeeded and the failure was reported inside the job, so there is no HTTP status to act on.
 */
class EpistolaDocumentGenerationException(message: String, cause: Throwable? = null) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_GENERATION_FAILED,
    message = message,
    cause = cause
)
