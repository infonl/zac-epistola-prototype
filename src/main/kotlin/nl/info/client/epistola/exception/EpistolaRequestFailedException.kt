/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import app.epistola.client.jakarta.api.ApiException
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.core.Response.Status
import jakarta.ws.rs.core.Response.Status.Family
import nl.info.zac.exception.ErrorCode
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_RATE_LIMITED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_REQUEST_FAILED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.io.IOException

/** Only the HTTP status is kept: Epistola's response body may quote the data ZAC sent. */
class EpistolaRequestFailedException(errorCode: ErrorCode, message: String, cause: Throwable) : EpistolaException(
    errorCode = errorCode,
    message = message,
    cause = cause
)

/** A 404 means a missing template only for a request that names one; for a job or a document it is ZAC's own fault. */
fun ApiException.toEpistolaRequestFailedException(request: String, isTemplateRequest: Boolean) =
    response?.status.let { status ->
        EpistolaRequestFailedException(
            errorCode = when {
                status == Status.UNAUTHORIZED.statusCode || status == Status.FORBIDDEN.statusCode ->
                    ERROR_CODE_EPISTOLA_ACCESS_DENIED
                status == Status.NOT_FOUND.statusCode && isTemplateRequest -> ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
                status == Status.TOO_MANY_REQUESTS.statusCode -> ERROR_CODE_EPISTOLA_RATE_LIMITED
                status != null && Family.familyOf(status) == Family.SERVER_ERROR -> ERROR_CODE_EPISTOLA_UNAVAILABLE
                else -> ERROR_CODE_EPISTOLA_REQUEST_FAILED
            },
            message = "Epistola answered HTTP $status to $request",
            cause = this
        )
    }

/** A refused connection, an unknown host and a read timeout all arrive as an [IOException] under the client's own. */
fun ProcessingException.toEpistolaRequestFailedException(request: String) =
    EpistolaRequestFailedException(
        errorCode = if (cause is IOException) ERROR_CODE_EPISTOLA_UNAVAILABLE else ERROR_CODE_EPISTOLA_REQUEST_FAILED,
        message = "Epistola could not be used for $request: ${cause?.javaClass?.simpleName ?: message}",
        cause = this
    )
