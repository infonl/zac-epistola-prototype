/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT
import nl.info.zac.exception.ServerErrorException

/** Not an [EpistolaDocumentGenerationException]: the document may still appear in Epistola afterwards. */
class EpistolaDocumentGenerationTimeoutException(message: String) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT,
    message = message
)
