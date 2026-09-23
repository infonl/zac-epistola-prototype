/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import io.kotest.matchers.comparables.shouldBeLessThan
import kotlin.time.measureTime
import kotlin.time.Duration.Companion.seconds
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.IN_PROGRESS
import app.epistola.client.jakarta.model.GenerateDocumentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.model.createDocumentGenerationItem
import nl.info.client.epistola.model.createGenerationJobDetail
import nl.info.client.epistola.model.createGenerationJobResponse
import nl.info.client.epistola.model.createTemplate
import java.nio.file.Files
import java.util.Optional
import java.util.UUID

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"
private const val FAKE_CORRELATION_ID = "fakeCorrelationId"

class EpistolaClientServiceTest : BehaviorSpec({
    val generationApi = mockk<GenerationApi>()
    val templatesApi = mockk<TemplatesApi>()
    val generationApiInstance = mockk<Instance<GenerationApi>>()
    val templatesApiInstance = mockk<Instance<TemplatesApi>>()

    fun createService(generationTimeoutSeconds: Long? = 0L) = EpistolaClientService(
        generationApi = generationApiInstance,
        templatesApi = templatesApiInstance,
        tenantId = Optional.of(FAKE_TENANT_ID),
        catalogId = Optional.of(FAKE_CATALOG_ID),
        generationTimeoutSeconds = Optional.ofNullable(generationTimeoutSeconds)
    )

    afterEach { checkUnnecessaryStub() }

    context("generating a document") {
        given("a job that completes on the first poll") {
            every { generationApiInstance.get() } returns generationApi
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

                and("the request carries the catalog, the template, the data and the correlation id") {
                    with(generateRequestSlot.captured) {
                        catalogId shouldBe FAKE_CATALOG_ID
                        templateId shouldBe FAKE_TEMPLATE_ID
                        filename shouldBe FAKE_FILE_NAME
                        correlationId shouldBe FAKE_CORRELATION_ID
                        data shouldBe mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie"))
                    }
                }

                and("the temporary file the client wrote is removed") {
                    downloadedFile.exists() shouldBe false
                }
            }
        }

        given("a job that is still running on the first poll and completes on the second") {
            every { generationApiInstance.get() } returns generationApi
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
                val generatedDocument = createService(generationTimeoutSeconds = 30L).generateDocument(
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
            every { generationApiInstance.get() } returns generationApi
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
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("the reason Epistola gave is part of the failure") {
                    exception.message shouldContain "fakeTemplateRenderingError"
                }

                and("no document is downloaded") {
                    verify(exactly = 0) { generationApi.downloadDocument(any(), any()) }
                }
            }
        }

        given("a job that never finishes") {
            every { generationApiInstance.get() } returns generationApi
            val requestId = UUID.randomUUID()

            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))
            )

            `when`("the configured timeout passes") {
                val exception = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                    createService().generateDocument(
                        templateId = FAKE_TEMPLATE_ID,
                        data = emptyMap(),
                        fileName = FAKE_FILE_NAME,
                        correlationId = FAKE_CORRELATION_ID
                    )
                }

                then("waiting stops and the request is named") {
                    exception.message shouldContain requestId.toString()
                }
            }
        }

        given("a job that never finishes, with a timeout that the back-off does not divide evenly") {
            every { generationApiInstance.get() } returns generationApi
            val requestId = UUID.randomUUID()
            every {
                generationApi.generateDocument(FAKE_TENANT_ID, any())
            } returns createGenerationJobResponse(requestId = requestId)
            every { generationApi.getGenerationJobStatus(FAKE_TENANT_ID, requestId) } returns createGenerationJobDetail(
                items = listOf(createDocumentGenerationItem(status = IN_PROGRESS))
            )

            `when`("the configured timeout of one second passes") {
                lateinit var epistolaDocumentGenerationTimeoutException: EpistolaDocumentGenerationTimeoutException
                val waitingTime = measureTime {
                    epistolaDocumentGenerationTimeoutException = shouldThrow<EpistolaDocumentGenerationTimeoutException> {
                        createService(generationTimeoutSeconds = 1L).generateDocument(
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

        given("a job that reports a completed item without a document id") {
            every { generationApiInstance.get() } returns generationApi
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

    context("reading a template schema") {
        val dataModel = mapOf("properties" to mapOf("zaak" to emptyMap<String, Any>()))
        val schema = mapOf("properties" to mapOf("aanvrager" to emptyMap<String, Any>()))

        given("a template that carries its schema in its data model, as Epistola serves it") {
            every { templatesApiInstance.get() } returns templatesApi
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(dataModel = dataModel)

            `when`("the schema is read") {
                val templateSchema = createService().readTemplateSchema(FAKE_TEMPLATE_ID)

                then("the data model is returned as the contract defines it") {
                    templateSchema shouldBe dataModel
                }
            }
        }

        given("a template that carries its schema under the older name instead") {
            every { templatesApiInstance.get() } returns templatesApi
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(schema = schema)

            `when`("the schema is read") {
                val templateSchema = createService().readTemplateSchema(FAKE_TEMPLATE_ID)

                then("that schema is returned rather than the template counting as having none") {
                    templateSchema shouldBe schema
                }
            }
        }

        given("a template that carries a schema under both names") {
            every { templatesApiInstance.get() } returns templatesApi
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate(schema = schema, dataModel = dataModel)

            `when`("the schema is read") {
                val templateSchema = createService().readTemplateSchema(FAKE_TEMPLATE_ID)

                then("the data model wins, because that is the one Epistola validates against") {
                    templateSchema shouldBe dataModel
                }
            }
        }

        given("a template that declares no schema at all") {
            every { templatesApiInstance.get() } returns templatesApi
            every {
                templatesApi.getTemplate(FAKE_TENANT_ID, FAKE_CATALOG_ID, FAKE_TEMPLATE_ID)
            } returns createTemplate()

            `when`("the schema is read") {
                val templateSchema = createService().readTemplateSchema(FAKE_TEMPLATE_ID)

                then("nothing is returned, so the caller decides what an unrestricted template means") {
                    templateSchema shouldBe null
                }
            }
        }
    }
})
