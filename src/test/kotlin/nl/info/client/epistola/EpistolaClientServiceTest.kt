/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import app.epistola.client.jakarta.api.CatalogsApi
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.IN_PROGRESS
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.PENDING
import app.epistola.client.jakarta.model.GenerateDocumentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.core.Response
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.createDocumentGenerationItem
import nl.info.client.epistola.model.createGenerationJobDetail
import nl.info.client.epistola.model.createGenerationJobResponse
import nl.info.client.epistola.model.createTemplate
import nl.info.zac.configuration.createEpistolaSettings
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.net.ConnectException
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_DEFAULT_CATALOG_ID = "fake-default-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"
private const val FAKE_CORRELATION_ID = "fakeCorrelationId"

class EpistolaClientServiceTest : BehaviorSpec({
    val generationApi = mockk<GenerationApi>()
    val templatesApi = mockk<TemplatesApi>()
    val catalogsApi = mockk<CatalogsApi>()

    fun createService(generationTimeout: Duration = Duration.ZERO) = EpistolaClientService(
        generationApi = generationApi,
        templatesApi = templatesApi,
        catalogsApi = catalogsApi,
        epistolaSettings = createEpistolaSettings(
            tenantId = FAKE_TENANT_ID,
            catalogId = FAKE_DEFAULT_CATALOG_ID,
            generationTimeout = generationTimeout
        )
    )

    fun createApiException(status: Int) = ApiException(mockk<Response> { every { this@mockk.status } returns status })

    afterEach { checkUnnecessaryStub() }

    context("generating a document") {
        given("a job that completes on the first poll") {
            val requestId = UUID.randomUUID()
            val documentId = UUID.randomUUID()
            val pdfContent = "fakePdfContent".toByteArray()
            val downloadedFile = Files.createTempFile("epistola", ".pdf").toFile().apply {
                writeBytes(pdfContent)
            }
            val generateRequestSlot = slot<GenerateDocumentRequest>()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, capture(generateRequestSlot))
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(documentId = documentId))
            )
            every { generationApi.downloadDocument(FAKE_TENANT_ID, documentId) } returns downloadedFile

            `when`("the document is generated") {
                val generatedDocument = createService().generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID
                )

                then("the rendered document is returned") {
                    generatedDocument.documentId shouldBe documentId
                    generatedDocument.fileName shouldBe FAKE_FILE_NAME
                    generatedDocument.content shouldBe pdfContent
                }

                and("the request carries the catalog it was given, the template, the data and the correlation id") {
                    with(generateRequestSlot.captured) {
                        catalogId shouldBe FAKE_CATALOG_ID
                        templateId shouldBe FAKE_TEMPLATE_ID
                        filename shouldBe FAKE_FILE_NAME
                        correlationId shouldBe FAKE_CORRELATION_ID
                        data shouldBe mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie"))
                    }
                }

                and("it asks for no variant, so Epistola renders the template's default one") {
                    generateRequestSlot.captured.attributes shouldBe null
                }

                and("the temporary file the client wrote is removed") {
                    downloadedFile.exists() shouldBe false
                }
            }
        }

        given("a job that is still running on the first poll and completes on the second") {
            val requestId = UUID.randomUUID()
            val documentId = UUID.randomUUID()
            val downloadedFile = Files.createTempFile("epistola", ".pdf").toFile()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returnsMany listOf(
                createGenerationJobDetail(items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))),
                createGenerationJobDetail(items = listOf(createDocumentGenerationItem(documentId = documentId)))
            )
            every { generationApi.downloadDocument(FAKE_TENANT_ID, documentId) } returns downloadedFile

            `when`("the document is generated") {
                val generatedDocument = createService(generationTimeout = Duration.ofSeconds(30)).generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = emptyMap(),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID
                )

                then("the job is polled until it reports the document") {
                    generatedDocument.documentId shouldBe documentId
                    verify(exactly = 2) { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) }
                }
            }
        }

        given("a job that reports its item as failed") {
            val requestId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(
                    createDocumentGenerationItem(
                        status = FAILED,
                        documentId = null,
                        errorMessage = "fakeTemplateRenderingError"
                    )
                )
            )

            `when`("the document is generated") {
                val exception = shouldThrow<EpistolaDocumentGenerationException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the reason Epistola gave is shown to the behandelaar") {
                    exception.detail shouldBe "fakeTemplateRenderingError"
                }

                and("the reason stays out of the message that is logged, because Epistola may quote the data") {
                    exception.message shouldNotContain "fakeTemplateRenderingError"
                    exception.message shouldContain requestId.toString()
                    exception.message shouldContain FAKE_TEMPLATE_ID
                    exception.message shouldContain FAKE_CORRELATION_ID
                }

                and("no document is downloaded") {
                    verify(exactly = 0) { generationApi.downloadDocument(any(), any()) }
                }

                and("the job is not cancelled, because a failed job has finished and left no document") {
                    verify(exactly = 0) { generationApi.cancelGenerationJob(any(), any()) }
                }
            }
        }

        given("a job whose status Epistola fails to report, for example with a 503 while ZAC polls") {
            val requestId = UUID.randomUUID()
            val apiException = createApiException(503)

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } throws apiException
            every { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) } just runs

            `when`("the document is generated") {
                val exception = shouldThrow<EpistolaRequestFailedException> {
                    createService(generationTimeout = Duration.ofSeconds(30)).generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the behandelaar learns that Epistola is unavailable") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                    exception.cause shouldBe apiException
                }

                and("the log names the request and the HTTP status") {
                    exception.message shouldContain requestId.toString()
                    exception.message shouldContain "HTTP 503"
                }

                and("the job Epistola accepted is cancelled, so trying again does not leave a second document") {
                    verify(exactly = 1) { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) }
                }
            }
        }

        given("a job that never finishes") {
            val requestId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))
            )
            every { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) } just runs

            `when`("the configured timeout passes") {
                val exception = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("waiting stops and the request is named") {
                    exception.message shouldContain requestId.toString()
                }

                and("the job is reported as held up while Epistola rendered it, not as a general failure") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_RENDERING
                }

                and("the job is cancelled, so trying again does not leave a second document at Epistola") {
                    verify(exactly = 1) { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) }
                }
            }
        }

        given("a job that never finishes and that Epistola refuses to cancel") {
            val requestId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))
            )
            every { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) } throws ApiException()

            `when`("the configured timeout passes") {
                val exception = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the behandelaar still learns that it took too long, not that cancelling failed") {
                    exception.message shouldContain requestId.toString()
                }
            }
        }

        given("a job that never finishes, with a timeout that the back-off does not divide evenly") {
            val requestId = UUID.randomUUID()
            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))
            )
            every { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) } just runs

            `when`("the configured timeout of one second passes") {
                lateinit var epistolaDocumentGenerationTimeoutException: EpistolaDocumentGenerationTimeoutException
                val waitingTime = measureTime {
                    epistolaDocumentGenerationTimeoutException = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                        createService(generationTimeout = Duration.ofSeconds(1)).generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = emptyMap(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = FAKE_CORRELATION_ID
                        )
                    }
                }

                then("waiting stops at the timeout instead of sleeping out the next full back-off") {
                    epistolaDocumentGenerationTimeoutException.message shouldContain requestId.toString()
                    waitingTime shouldBeLessThan 1.4.seconds
                }
            }
        }

        given("a job that is still waiting in Epistola's queue when the timeout passes") {
            val requestId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = PENDING))
            )
            every { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) } just runs

            `when`("the configured timeout passes") {
                val exception = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the job is reported as held up in Epistola's queue") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_GENERATION_HELD_UP_IN_QUEUE
                }

                and("it is still cancelled at ZAC's own timeout") {
                    verify(exactly = 1) { generationApi.cancelGenerationJob(FAKE_TENANT_ID, requestId) }
                }
            }
        }

        given("a job that waits in the queue, then renders, then completes") {
            val requestId = UUID.randomUUID()
            val documentId = UUID.randomUUID()
            val reportedJobStatuses = mutableListOf<EpistolaJobStatus>()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returnsMany listOf(
                createGenerationJobDetail(items = listOf(createDocumentGenerationItem(status = PENDING))),
                createGenerationJobDetail(items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))),
                createGenerationJobDetail(items = listOf(createDocumentGenerationItem(documentId = documentId)))
            )
            every {
                generationApi.downloadDocument(FAKE_TENANT_ID, documentId)
            } returns Files.createTempFile("epistola", ".pdf").toFile()

            `when`("the document is generated") {
                createService(generationTimeout = Duration.ofSeconds(30)).generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = emptyMap(),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID,
                    onJobStatus = reportedJobStatuses::add
                )

                then("each status Epistola reported is passed on, and the finished one is not") {
                    reportedJobStatuses shouldBe listOf(EpistolaJobStatus.WAITING_IN_QUEUE, EpistolaJobStatus.RENDERING)
                }
            }
        }

        given("a template that Epistola does not know when the job is submitted") {
            every { generationApi.generateDocument(FAKE_TENANT_ID, any()) } throws createApiException(404)

            `when`("the document is generated") {
                val exception = shouldThrow<EpistolaRequestFailedException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the behandelaar learns that the configured template no longer exists") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
                    exception.message shouldContain FAKE_TEMPLATE_ID
                    exception.message shouldContain FAKE_CORRELATION_ID
                }

                and("no job is polled or cancelled, because Epistola accepted none") {
                    verify(exactly = 0) { generationApi.getGenerationJobStatus(any(), any()) }
                    verify(exactly = 0) { generationApi.cancelGenerationJob(any(), any()) }
                }
            }
        }

        given("a completed job whose document cannot be downloaded") {
            val requestId = UUID.randomUUID()
            val documentId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(documentId = documentId))
            )
            every {
                generationApi.downloadDocument(FAKE_TENANT_ID, documentId)
            } throws ProcessingException(ConnectException("fakeConnectionRefused"))
            every { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) } just runs

            `when`("the document is generated") {
                val exception = shouldThrow<EpistolaRequestFailedException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the behandelaar learns that Epistola is unavailable") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }

                and("Epistola's copy is deleted, because nothing will download it any more") {
                    verify(exactly = 1) { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) }
                }
            }
        }

        given("a job that reports a completed item without a document id") {
            val requestId = UUID.randomUUID()
            val itemId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(id = itemId, documentId = null))
            )

            `when`("the document is generated") {
                val exception = shouldThrow<EpistolaDocumentGenerationException> {
                    createService().generateDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the unusable item is named instead of a null pointer being dereferenced") {
                    exception.message shouldContain itemId.toString()
                }
            }
        }
    }

    context("reading a template to generate from") {
        val dataModel = mapOf("properties" to mapOf("zaak" to emptyMap<String, Any>()))
        val schema = mapOf("properties" to mapOf("aanvrager" to emptyMap<String, Any>()))

        given("a template that carries its schema in its data model, as Epistola serves it") {
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(dataModel = dataModel)

            `when`("the template is read") {
                val generationTemplate = createService().readGenerationTemplate(catalogId = FAKE_CATALOG_ID, templateId = FAKE_TEMPLATE_ID)

                then("the data model is its data contract, as the contract defines it") {
                    generationTemplate.dataContract shouldBe dataModel
                }
            }
        }

        given("a template that carries its schema under the older name instead") {
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(schema = schema)

            `when`("the template is read") {
                val generationTemplate = createService().readGenerationTemplate(catalogId = FAKE_CATALOG_ID, templateId = FAKE_TEMPLATE_ID)

                then("that schema is its data contract rather than the template counting as having none") {
                    generationTemplate.dataContract shouldBe schema
                }
            }
        }

        given("a template that carries a schema under both names") {
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(schema = schema, dataModel = dataModel)

            `when`("the template is read") {
                val generationTemplate = createService().readGenerationTemplate(catalogId = FAKE_CATALOG_ID, templateId = FAKE_TEMPLATE_ID)

                then("the data model wins, because that is the one Epistola validates against") {
                    generationTemplate.dataContract shouldBe dataModel
                }
            }
        }

        given("a template that declares no schema") {
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate()

            `when`("the template is read") {
                val generationTemplate = createService().readGenerationTemplate(catalogId = FAKE_CATALOG_ID, templateId = FAKE_TEMPLATE_ID)

                then("it has no data contract, so the caller decides what an unrestricted template means") {
                    generationTemplate.dataContract shouldBe null
                }
            }
        }
    }

    context("reading a template that Epistola refuses to show") {
        given("an API key that Epistola rejects") {
            every { templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } throws createApiException(401)

            `when`("the template is read") {
                val exception = shouldThrow<EpistolaRequestFailedException> {
                    createService().readGenerationTemplate(catalogId = FAKE_CATALOG_ID, templateId = FAKE_TEMPLATE_ID)
                }

                then("the behandelaar learns that ZAC has no access to Epistola") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_ACCESS_DENIED
                    exception.message shouldContain "HTTP 401"
                }
            }
        }
    }

    context("deleting a document at Epistola") {
        given("a document Epistola still holds") {
            val documentId = UUID.randomUUID()
            every { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) } just runs

            `when`("the document is deleted") {
                createService().deleteDocument(documentId)

                then("Epistola is asked to delete it") {
                    verify(exactly = 1) { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) }
                }
            }
        }

        given("a document Epistola refuses to delete") {
            val documentId = UUID.randomUUID()
            every { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) } throws ApiException()

            `when`("the document is deleted") {
                createService().deleteDocument(documentId)

                then("the failure is only logged, because the document is already in the dossier") {
                    verify(exactly = 1) { generationApi.deleteDocument(FAKE_TENANT_ID, documentId) }
                }
            }
        }
    }
})
