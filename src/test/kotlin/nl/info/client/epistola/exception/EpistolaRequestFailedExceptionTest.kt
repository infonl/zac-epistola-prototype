/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import app.epistola.client.jakarta.api.ApiException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.core.Response
import nl.info.zac.exception.ErrorCode
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_RATE_LIMITED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_REQUEST_FAILED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class EpistolaRequestFailedExceptionTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    fun createApiException(status: Int) = ApiException(mockk<Response> { every { this@mockk.status } returns status })

    context("an HTTP error from Epistola") {
        listOf(
            Triple(401, false, ERROR_CODE_EPISTOLA_ACCESS_DENIED),
            Triple(403, false, ERROR_CODE_EPISTOLA_ACCESS_DENIED),
            Triple(404, true, ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND),
            Triple(404, false, ERROR_CODE_EPISTOLA_REQUEST_FAILED),
            Triple(429, false, ERROR_CODE_EPISTOLA_RATE_LIMITED),
            Triple(500, false, ERROR_CODE_EPISTOLA_UNAVAILABLE),
            Triple(503, false, ERROR_CODE_EPISTOLA_UNAVAILABLE),
            Triple(400, true, ERROR_CODE_EPISTOLA_REQUEST_FAILED),
            Triple(409, false, ERROR_CODE_EPISTOLA_REQUEST_FAILED)
        ).forEach { (status: Int, isTemplateRequest: Boolean, errorCode: ErrorCode) ->
            given("HTTP $status to a request that ${if (isTemplateRequest) "names" else "does not name"} a template") {
                val apiException = createApiException(status)

                `when`("it is translated") {
                    val epistolaRequestFailedException = apiException.toEpistolaRequestFailedException(
                        request = "fakeRequest",
                        isTemplateRequest = isTemplateRequest
                    )

                    then("the behandelaar sees ${errorCode.value}") {
                        epistolaRequestFailedException.errorCode shouldBe errorCode
                    }

                    and("the log names the request and the status") {
                        epistolaRequestFailedException.message shouldBe "Epistola answered HTTP $status to fakeRequest"
                    }
                }
            }
        }

        given("an API exception without a response") {
            val apiException = ApiException()

            `when`("it is translated") {
                val epistolaRequestFailedException = apiException.toEpistolaRequestFailedException(
                    request = "fakeRequest",
                    isTemplateRequest = true
                )

                then("it counts as a failed request rather than a null pointer") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_REQUEST_FAILED
                }
            }
        }
    }

    context("a request that does not get an answer from Epistola") {
        listOf(
            ConnectException("fakeConnectionRefused"),
            UnknownHostException("fakeUnknownHost"),
            SocketTimeoutException("fakeReadTimedOut")
        ).forEach { ioException ->
            given("a ${ioException.javaClass.simpleName}") {
                val processingException = ProcessingException(ioException)

                `when`("it is translated") {
                    val epistolaRequestFailedException = processingException.toEpistolaRequestFailedException(
                        request = "fakeRequest"
                    )

                    then("Epistola counts as unavailable, and the log names the kind of failure") {
                        epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                        epistolaRequestFailedException.message shouldContain ioException.javaClass.simpleName
                    }
                }
            }
        }

        given("a response that the client could not read") {
            val processingException = ProcessingException("fakeUnreadableResponse")

            `when`("it is translated") {
                val epistolaRequestFailedException = processingException.toEpistolaRequestFailedException(
                    request = "fakeRequest"
                )

                then("it counts as a failed request, because Epistola did answer") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_REQUEST_FAILED
                }
            }
        }
    }
})
