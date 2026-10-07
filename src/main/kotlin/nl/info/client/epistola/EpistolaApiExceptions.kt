/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import jakarta.json.Json
import jakarta.json.JsonException
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.exception.toEpistolaRequestFailedException
import java.io.StringReader
import java.util.logging.Logger

private const val PROBLEM_DETAIL = "detail"

private val LOG = Logger.getLogger("nl.info.client.epistola.EpistolaApiExceptions")

/**
 * A request that is answered synchronously, such as a preview, hears at once that the data breaks the template's
 * contract, as a 400 whose `detail` names the offending fields as a failed generation job does. Any other failure, and a
 * problem that cannot be read, is a failed request that names the HTTP status alone.
 *
 * The rejection has no cause: the client's own exception repeats the problem in its message, and the log would then
 * carry the fields the behandelaar is shown.
 */
internal fun ApiException.toEpistolaException(request: String, isTemplateRequest: Boolean): EpistolaException =
    toTemplateDataRejection(request) ?: toEpistolaRequestFailedException(request, isTemplateRequest)

private fun ApiException.toTemplateDataRejection(request: String) =
    response?.takeIf { it.status == Status.BAD_REQUEST.statusCode }
        ?.let(::readProblemDetail)
        ?.toDataRejectionDetailOrNull()
        ?.let {
            EpistolaTemplateDataRejectedException(
                message = "Epistola rejected the data of $request against the template's contract",
                detail = it
            )
        }

private fun readProblemDetail(response: Response): String? =
    try {
        Json.createReader(StringReader(response.readEntity(String::class.java))).use {
            it.readObject().getString(PROBLEM_DETAIL, null)
        }
    } catch (processingException: ProcessingException) {
        problemUnreadable(processingException)
    } catch (illegalStateException: IllegalStateException) {
        problemUnreadable(illegalStateException)
    } catch (jsonException: JsonException) {
        problemUnreadable(jsonException)
    }

private fun problemUnreadable(failure: RuntimeException): String? {
    LOG.fine { "Could not read the problem Epistola answered a request with: ${failure.javaClass.simpleName}" }
    return null
}
