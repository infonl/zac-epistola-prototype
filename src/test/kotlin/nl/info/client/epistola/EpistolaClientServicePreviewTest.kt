/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.PreviewDocumentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.ws.rs.core.Response
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.zac.configuration.createEpistolaSettings
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_RATE_LIMITED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_REQUEST_FAILED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
import java.nio.file.Files

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"

class EpistolaClientServicePreviewTest : BehaviorSpec({
    val generationApi = mockk<GenerationApi>()
    val templatesApi = mockk<TemplatesApi>()
    val epistolaClientService = EpistolaClientService(
        generationApi = generationApi,
        templatesApi = templatesApi,
        epistolaSettings = createEpistolaSettings(tenantId = FAKE_TENANT_ID, catalogId = FAKE_CATALOG_ID)
    )

    fun createApiException(status: Int, body: String? = null) = ApiException(
        mockk<Response> {
            every { this@mockk.status } returns status
            body?.let { every { readEntity(String::class.java) } returns it }
        }
    )

    afterEach { checkUnnecessaryStub() }

    context("previewing a document") {
        given("a template that Epistola can render") {
            val pdfContent = "fakePreviewContent".toByteArray()
            val previewFile = Files.createTempFile("epistola", ".pdf").toFile().apply { writeBytes(pdfContent) }
            val previewRequestSlot = slot<PreviewDocumentRequest>()
            every { generationApi.previewDocument(FAKE_TENANT_ID, capture(previewRequestSlot)) } returns previewFile

            `when`("a preview is made") {
                val preview = epistolaClientService.previewDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie"))
                )

                then("the rendered document is returned") {
                    preview shouldBe pdfContent
                }

                and("the request carries the catalog, the template and the data, as a generation does") {
                    with(previewRequestSlot.captured) {
                        catalogId shouldBe FAKE_CATALOG_ID
                        templateId shouldBe FAKE_TEMPLATE_ID
                        data shouldBe mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie"))
                    }
                }

                and("it asks for no variant, so Epistola renders the template's default one") {
                    previewRequestSlot.captured.attributes shouldBe null
                }

                and("no job is submitted and no document deleted, because a preview is kept nowhere") {
                    verify(exactly = 0) { generationApi.generateDocument(any(), any()) }
                    verify(exactly = 0) { generationApi.deleteDocument(any(), any()) }
                }

                and("the temporary file the client wrote is removed") {
                    previewFile.exists() shouldBe false
                }
            }
        }

        given("a template with a post and a digital variant") {
            val previewFile = Files.createTempFile("epistola", ".pdf").toFile().apply {
                writeBytes("fakePreviewContent".toByteArray())
            }
            val previewRequestSlot = slot<PreviewDocumentRequest>()
            every { generationApi.previewDocument(FAKE_TENANT_ID, capture(previewRequestSlot)) } returns previewFile

            `when`("a preview is made for the digital kanaal") {
                epistolaClientService.previewDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = emptyMap(),
                    kanaal = "digitaal"
                )

                then("the variant for that kanaal is required, and the Dutch one preferred, as when generating") {
                    previewRequestSlot.captured.attributes.map { Triple(it.catalog, it.key, it.value) to it.required } shouldBe
                        listOf(
                            Triple(FAKE_CATALOG_ID, "kanaal", "digitaal") to true,
                            Triple("system", "locale", "nl-NL") to false
                        )
                }
            }
        }

        given("data that breaks the template's data contract in a field at the top of the data") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws createApiException(
                status = 400,
                body = """
                    {"title":"Template Data Invalid","detail":"Data validation failed: : required property 'aanvrager' not found"}
                """.trimIndent()
            )

            `when`("a preview is made") {
                val epistolaTemplateDataRejectedException = shouldThrow<EpistolaTemplateDataRejectedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("the behandelaar learns which field the template misses, without the colon that an empty path leaves in front") {
                    epistolaTemplateDataRejectedException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
                    epistolaTemplateDataRejectedException.detail shouldBe "required property 'aanvrager' not found"
                }
            }
        }

        given("data that breaks the template's data contract in a field that has a path") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws createApiException(
                status = 400,
                body = """{"title":"Template Data Invalid","detail":"Data validation failed: /aanvrager: is required"}"""
            )

            `when`("a preview is made") {
                val epistolaTemplateDataRejectedException = shouldThrow<EpistolaTemplateDataRejectedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("the behandelaar gets the rejection that a failed generation would have given, at once") {
                    epistolaTemplateDataRejectedException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
                }

                and("the path stays in front of Epistola's reason, with its own colon") {
                    epistolaTemplateDataRejectedException.detail shouldBe "/aanvrager: is required"
                }

                and("Epistola's reason stays out of the message that is logged, and out of a cause that would repeat it") {
                    epistolaTemplateDataRejectedException.message shouldContain FAKE_TEMPLATE_ID
                    epistolaTemplateDataRejectedException.message shouldNotContain "aanvrager"
                    epistolaTemplateDataRejectedException.cause shouldBe null
                }
            }
        }

        given("a 400 that is not about the data") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws createApiException(
                status = 400,
                body = """{"title":"Validation Failed","detail":"variantId and attributes are mutually exclusive"}"""
            )

            `when`("a preview is made") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("it is a failed request, which the behandelaar can do nothing about") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_REQUEST_FAILED
                }
            }
        }

        given("a 400 whose body is not JSON") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws
                createApiException(status = 400, body = "fakeNotJson")

            `when`("a preview is made") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("it is a failed request as well") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_REQUEST_FAILED
                }
            }
        }

        given("a 400 whose response can no longer be read") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws ApiException(
                mockk<Response> {
                    every { status } returns 400
                    every { readEntity(String::class.java) } throws IllegalStateException("fakeEntityConsumed")
                }
            )

            `when`("a preview is made") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("it is a failed request as well") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_REQUEST_FAILED
                }
            }
        }

        given("Epistola is rate-limiting previews") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws createApiException(429)

            `when`("a preview is made") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("the behandelaar is told to try again in a few minutes") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_RATE_LIMITED
                }
            }
        }

        given("a template that Epistola does not know") {
            every { generationApi.previewDocument(FAKE_TENANT_ID, any()) } throws createApiException(404)

            `when`("a preview is made") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaClientService.previewDocument(templateId = FAKE_TEMPLATE_ID, data = emptyMap())
                }

                then("the behandelaar learns that the configured template no longer exists") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_NOT_FOUND
                    epistolaRequestFailedException.message shouldContain FAKE_TEMPLATE_ID
                }
            }
        }
    }
})
