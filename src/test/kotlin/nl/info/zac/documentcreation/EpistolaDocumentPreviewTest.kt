/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
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
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.exception.EpistolaDocumentCreationException
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_RATE_LIMITED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.util.UUID

private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private val FAKE_PREVIEW = "fakePreviewPdfContent".toByteArray()

private val TEMPLATE_SCHEMA = mapOf(
    "properties" to mapOf(
        "zaak" to mapOf(
            "properties" to mapOf(
                "identificatie" to mapOf("type" to "string"),
                "omschrijving" to mapOf("type" to "string")
            )
        )
    )
)

class EpistolaDocumentPreviewTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val epistolaTemplatesService = mockk<EpistolaTemplatesService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        epistolaTemplatesService = epistolaTemplatesService,
        epistolaDocumentRepository = mockk<EpistolaDocumentRepository>(),
        ztcClientService = mockk<ZtcClientService>(),
        enkelvoudigInformatieObjectUpdateService = mockk<EnkelvoudigInformatieObjectUpdateService>(),
        configurationService = mockk<ConfigurationService>(),
        epistolaDocumentCreationStatusStore = EpistolaDocumentCreationStatusStore(),
        loggedInUserInstance = loggedInUserInstance
    )

    fun givenATemplateThatIsOffered(
        zaakUuid: UUID,
        dataContract: Any? = TEMPLATE_SCHEMA
    ) {
        every { epistolaTemplatesService.readCatalogOfferingTemplate(zaakUuid, any()) } returns
            OfferedEpistolaCatalog(
                catalogId = FAKE_CATALOG_ID,
                informatieObjectTypeUuid = UUID.randomUUID()
            )
        every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
            EpistolaGenerationTemplate(dataContract = dataContract)
    }

    afterEach { checkUnnecessaryStub() }

    context("previewing a document before it is generated") {
        given("a zaak and a template that declares two zaak fields") {
            val loggedInUser = createLoggedInUser()
            val zaak = createZaak()
            val templateDataSlot = slot<Map<String, Any>>()
            givenATemplateThatIsOffered(zaak.zaaktype.extractUuid())
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every {
                epistolaClientService.previewDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = capture(templateDataSlot),
                )
            } returns FAKE_PREVIEW

            `when`("a preview is made") {
                val preview = epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)

                then("the document Epistola rendered is returned") {
                    preview shouldBe FAKE_PREVIEW
                }

                and("only the fields the template declares are sent, as when the document is generated") {
                    templateDataSlot.captured shouldBe mapOf(
                        "zaak" to mapOf(
                            "identificatie" to "fakeIdentificatie",
                            "omschrijving" to "fakeOmschrijving"
                        )
                    )
                }

                and("no document is generated, so nothing is created at Epistola or stored in the zaak") {
                    verify(exactly = 0) {
                        epistolaClientService.generateDocument(
                            catalogId = any(),
                            templateId = any(),
                            data = any(),
                            fileName = any(),
                            correlationId = any(),
                            onJobStatus = any()
                        )
                    }
                }
            }
        }

        given("a task of the zaak") {
            val loggedInUser = createLoggedInUser()
            val zaak = createZaak()
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, "fakeTaskId") } returns createData()

            `when`("a preview is made from the task") {
                givenATemplateThatIsOffered(zaak.zaaktype.extractUuid())
                every {
                    epistolaClientService.previewDocument(
                        catalogId = FAKE_CATALOG_ID,
                        templateId = FAKE_TEMPLATE_ID,
                        data = any()
                    )
                } returns FAKE_PREVIEW

                epistolaDocumentCreationService.previewDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    taskId = "fakeTaskId"
                )

                then("the task's data is read and previewed, as it would be generated") {
                    verify(exactly = 1) {
                        epistolaClientService.previewDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any()
                        )
                    }
                }
            }
        }

        given("a zaaktype that offers no Epistola templates") {
            val zaak = createZaak()
            every {
                epistolaTemplatesService.readCatalogOfferingTemplate(zaak.zaaktype.extractUuid(), any())
            } throws EpistolaTemplateNotConfiguredException("fakeNotConfigured")

            `when`("a preview is made") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then("it is refused before any zaak data reaches Epistola") {
                    epistolaTemplateNotConfiguredException.message shouldBe "fakeNotConfigured"
                    verify(exactly = 0) {
                        epistolaClientService.previewDocument(
                            catalogId = any(),
                            templateId = any(),
                            data = any()
                        )
                    }
                }
            }
        }

        given("a template that declares no schema") {
            val zaak = createZaak()
            val loggedInUser = createLoggedInUser()
            givenATemplateThatIsOffered(zaak.zaaktype.extractUuid(), dataContract = null)
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()

            `when`("a preview is made") {
                val epistolaTemplateSchemaMissingException = shouldThrow<EpistolaTemplateSchemaMissingException> {
                    epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then("no zaak data is sent to Epistola") {
                    epistolaTemplateSchemaMissingException.message shouldContain FAKE_TEMPLATE_ID
                    verify(exactly = 0) {
                        epistolaClientService.previewDocument(
                            catalogId = any(),
                            templateId = any(),
                            data = any()
                        )
                    }
                }
            }
        }

        given("Epistola is unavailable when the template is read") {
            val zaak = createZaak()
            every { epistolaTemplatesService.readCatalogOfferingTemplate(zaak.zaaktype.extractUuid(), any()) } returns
                OfferedEpistolaCatalog(
                    catalogId = FAKE_CATALOG_ID,
                    informatieObjectTypeUuid = UUID.randomUUID()
                )
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } throws
                EpistolaRequestFailedException(
                    errorCode = ERROR_CODE_EPISTOLA_UNAVAILABLE,
                    message = "fakeUnavailable",
                    cause = RuntimeException()
                )

            `when`("a preview is made") {
                val epistolaDocumentCreationException = shouldThrow<EpistolaDocumentCreationException> {
                    epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then(
                    "the behandelaar is told that Epistola is unavailable, and no zaak data is collected from the " +
                        "ZGW, BRP and KVK sources"
                ) {
                    epistolaDocumentCreationException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                    verify(exactly = 0) { documentCreationDataService.createEpistolaData(any(), any(), any()) }
                }
            }
        }

        given("Epistola rejects the zaak data against the template's contract") {
            val zaak = createZaak()
            val loggedInUser = createLoggedInUser()
            givenATemplateThatIsOffered(zaak.zaaktype.extractUuid())
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            val epistolaTemplateDataRejectedException = EpistolaTemplateDataRejectedException(
                message = "fakeRejectedMessage",
                detail = "/aanvrager: is required"
            )
            every {
                epistolaClientService.previewDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                )
            } throws epistolaTemplateDataRejectedException

            `when`("a preview is made") {
                val epistolaDocumentCreationException = shouldThrow<EpistolaDocumentCreationException> {
                    epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then("the behandelaar sees Epistola's reason under the error code of the rejection") {
                    epistolaDocumentCreationException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
                    epistolaDocumentCreationException.detail shouldBe "/aanvrager: is required"
                }

                and("the log says it was a preview of that template for that zaak, without Epistola's reason") {
                    epistolaDocumentCreationException.message shouldContain "preview"
                    epistolaDocumentCreationException.message shouldContain FAKE_TEMPLATE_ID
                    epistolaDocumentCreationException.message shouldContain zaak.identificatie
                    epistolaDocumentCreationException.message shouldNotContain "aanvrager"
                    epistolaDocumentCreationException.cause shouldBe epistolaTemplateDataRejectedException
                }
            }
        }

        given("Epistola is rate-limiting previews") {
            val zaak = createZaak()
            val loggedInUser = createLoggedInUser()
            givenATemplateThatIsOffered(zaak.zaaktype.extractUuid())
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every {
                epistolaClientService.previewDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                )
            } throws EpistolaRequestFailedException(
                errorCode = ERROR_CODE_EPISTOLA_RATE_LIMITED,
                message = "fakeRateLimited",
                cause = RuntimeException()
            )

            `when`("a preview is made") {
                val epistolaDocumentCreationException = shouldThrow<EpistolaDocumentCreationException> {
                    epistolaDocumentCreationService.previewDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then("the behandelaar is told that Epistola is receiving too many requests") {
                    epistolaDocumentCreationException.errorCode shouldBe ERROR_CODE_EPISTOLA_RATE_LIMITED
                }
            }
        }
    }
})
