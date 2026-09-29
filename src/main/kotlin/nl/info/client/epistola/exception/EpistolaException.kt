/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode
import nl.info.zac.exception.ServerErrorException

/** Sealed, so a caller that adds its own context to a failure of Epistola can catch exactly these. */
sealed class EpistolaException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
    detail: String? = null
) : ServerErrorException(errorCode = errorCode, message = message, cause = cause, detail = detail)
