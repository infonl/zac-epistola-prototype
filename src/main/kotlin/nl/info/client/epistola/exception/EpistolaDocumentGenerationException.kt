/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_FAILED
import nl.info.zac.exception.ServerErrorException

/** A failure reported inside a job that Epistola accepted, so there is no HTTP status to act on. */
class EpistolaDocumentGenerationException(message: String, cause: Throwable? = null) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_GENERATION_FAILED,
    message = message,
    cause = cause
)
