/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT
import nl.info.zac.exception.ServerErrorException

/**
 * The generation job was still running when ZAC stopped waiting for it.
 *
 * Distinct from [EpistolaDocumentGenerationException] because the document may still appear in
 * Epistola afterwards, so this is not evidence that generation failed.
 */
class EpistolaDocumentGenerationTimeoutException(message: String) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT,
    message = message
)
