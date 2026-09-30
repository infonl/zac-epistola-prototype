/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.COMPLETED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.IN_PROGRESS
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.PENDING
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.createDocumentGenerationItem
import java.time.Duration
import java.time.OffsetDateTime

private val HELD_UP_AFTER: Duration = Duration.ofSeconds(15)

class EpistolaGenerationItemsTest : BehaviorSpec({
    context("the status of a job that has not finished") {
        given("a job waiting in Epistola's queue for less time than the held-up threshold") {
            val item = createDocumentGenerationItem(status = PENDING)

            `when`("its status is read after ZAC has waited 5 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(5), heldUpAfter = HELD_UP_AFTER)

                then("it is waiting in the queue") {
                    jobStatus shouldBe EpistolaJobStatus.WAITING_IN_QUEUE
                }
            }
        }

        given("a job waiting in Epistola's queue for longer than the held-up threshold") {
            val item = createDocumentGenerationItem(status = PENDING)

            `when`("its status is read after ZAC has waited 20 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(20), heldUpAfter = HELD_UP_AFTER)

                then("it is held up in the queue") {
                    jobStatus shouldBe EpistolaJobStatus.HELD_UP_IN_QUEUE
                }
            }
        }

        given("a pending job whose creation time Epistola's clock, running an hour behind ZAC's, puts an hour ago") {
            val item = createDocumentGenerationItem(status = PENDING, createdAt = OffsetDateTime.now().minusHours(1))

            `when`("its status is read after ZAC has waited 5 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(5), heldUpAfter = HELD_UP_AFTER)

                then("it is not held up, because how long it waited is measured on ZAC's clock") {
                    jobStatus shouldBe EpistolaJobStatus.WAITING_IN_QUEUE
                }
            }
        }

        given("a job that started rendering as soon as Epistola created it") {
            val createdAt = OffsetDateTime.now()
            val item = createDocumentGenerationItem(status = IN_PROGRESS, createdAt = createdAt, startedAt = createdAt)

            `when`("its status is read after ZAC has waited 20 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(20), heldUpAfter = HELD_UP_AFTER)

                then("it is held up while rendering") {
                    jobStatus shouldBe EpistolaJobStatus.HELD_UP_IN_RENDERING
                }
            }
        }

        given("a job that waited 10 seconds in the queue before it started rendering") {
            val createdAt = OffsetDateTime.now()
            val item = createDocumentGenerationItem(
                status = IN_PROGRESS,
                createdAt = createdAt,
                startedAt = createdAt.plusSeconds(10)
            )

            `when`("its status is read after ZAC has waited 20 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(20), heldUpAfter = HELD_UP_AFTER)

                then("it is rendering, because the time in the queue does not count as rendering") {
                    jobStatus shouldBe EpistolaJobStatus.RENDERING
                }
            }
        }

        given("a rendering job for which Epistola reports no timestamps") {
            val item = createDocumentGenerationItem(status = IN_PROGRESS)

            `when`("its status is read after ZAC has waited 20 seconds") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(20), heldUpAfter = HELD_UP_AFTER)

                then("all of ZAC's wait counts as rendering") {
                    jobStatus shouldBe EpistolaJobStatus.HELD_UP_IN_RENDERING
                }
            }
        }

        given("a job that has finished") {
            val item = createDocumentGenerationItem(status = COMPLETED)

            `when`("its status is read") {
                val jobStatus = item.toEpistolaJobStatus(waited = Duration.ofSeconds(20), heldUpAfter = HELD_UP_AFTER)

                then("there is none to report") {
                    jobStatus shouldBe null
                }
            }
        }
    }

    context("the failure of a job that Epistola reports as failed") {
        given("a job whose data breaks the template's contract") {
            val item = createDocumentGenerationItem(
                status = FAILED,
                errorMessage = "Data validation failed: : required property 'aanvrager' not found"
            )

            `when`("the failure is read") {
                val epistolaException = item.toGenerationFailure(generationRequest = "fakeGenerationRequest")

                then("the data counts as rejected, so the behandelaar is not told to try again") {
                    epistolaException.shouldBeInstanceOf<EpistolaTemplateDataRejectedException>()
                }

                and("the behandelaar learns which field the template misses") {
                    epistolaException.detail shouldBe ": required property 'aanvrager' not found"
                }

                and("the field stays out of the message that is logged") {
                    epistolaException.message shouldContain "fakeGenerationRequest"
                    epistolaException.message shouldNotContain "aanvrager"
                }
            }
        }

        given("a job that fails while rendering") {
            val item = createDocumentGenerationItem(status = FAILED, errorMessage = "fakeRenderingError")

            `when`("the failure is read") {
                val epistolaException = item.toGenerationFailure(generationRequest = "fakeGenerationRequest")

                then("it is a failure to render, with Epistola's reason for the behandelaar only") {
                    epistolaException.shouldBeInstanceOf<EpistolaDocumentGenerationException>()
                    epistolaException.detail shouldBe "fakeRenderingError"
                    epistolaException.message shouldNotContain "fakeRenderingError"
                }
            }
        }
    }
})
