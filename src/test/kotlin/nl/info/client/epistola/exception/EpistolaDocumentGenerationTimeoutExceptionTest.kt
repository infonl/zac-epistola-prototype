/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT

class EpistolaDocumentGenerationTimeoutExceptionTest : BehaviorSpec({
    context("a job that ZAC stopped waiting for") {
        listOf(
            EpistolaJobStatus.WAITING_IN_QUEUE to ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE,
            EpistolaJobStatus.HELD_UP_IN_QUEUE to ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE,
            EpistolaJobStatus.RENDERING to ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING,
            EpistolaJobStatus.HELD_UP_IN_RENDERING to ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING,
            null to ERROR_CODE_EPISTOLA_GENERATION_TIMED_OUT
        ).forEach { (lastJobStatus, errorCode) ->
            given("a last status of ${lastJobStatus ?: "none, because Epistola reported none"}") {
                `when`("the timeout is reported") {
                    val epistolaDocumentGenerationTimeoutException = EpistolaDocumentGenerationTimeoutException(
                        message = "fakeMessage",
                        lastJobStatus = lastJobStatus
                    )

                    then("the behandelaar sees ${errorCode.value}") {
                        epistolaDocumentGenerationTimeoutException.errorCode shouldBe errorCode
                    }
                }
            }
        }
    }
})
