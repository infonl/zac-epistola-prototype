/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.zgw.drc.exception.DrcRuntimeException
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.drc.model.generated.VertrouwelijkheidaanduidingEnum as DrcVertrouwelijkheidaanduidingEnum
import nl.info.client.zgw.drc.model.generated.StatusEnum
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createInformatieObjectType
import nl.info.client.zgw.ztc.model.generated.VertrouwelijkheidaanduidingEnum
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.util.toBase64String
import java.net.URI
import java.time.LocalDate
import java.util.UUID

private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"
private const val FAKE_TITLE = "fakeTitle"
private const val FAKE_DESCRIPTION = "fakeDescription"
private const val FAKE_BRONORGANISATIE = "123443210"

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

class EpistolaDocumentCreationServiceTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val epistolaTemplatesService = mockk<EpistolaTemplatesService>()
    val ztcClientService = mockk<ZtcClientService>()
    val enkelvoudigInformatieObjectUpdateService = mockk<EnkelvoudigInformatieObjectUpdateService>()
    val configurationService = mockk<ConfigurationService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        epistolaTemplatesService = epistolaTemplatesService,
        ztcClientService = ztcClientService,
        enkelvoudigInformatieObjectUpdateService = enkelvoudigInformatieObjectUpdateService,
        configurationService = configurationService,
        loggedInUserInstance = loggedInUserInstance
    )

    afterEach { checkUnnecessaryStub() }

    given("a zaak and a template that declares two zaak fields") {
        val loggedInUser = createLoggedInUser()
        val zaak = createZaak()
        val generatedDocument = EpistolaGeneratedDocument(
            documentId = UUID.randomUUID(),
            fileName = FAKE_FILE_NAME,
            content = "fakePdfContent".toByteArray()
        )
        val templateDataSlot = slot<Map<String, Any>>()
        val correlationIdSlot = slot<String>()

        every { loggedInUserInstance.get() } returns loggedInUser
        every {
            documentCreationDataService.createEpistolaData(loggedInUser, zaak, null)
        } returns createData()
        every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns TEMPLATE_SCHEMA
        every {
            epistolaClientService.generateDocument(
                templateId = FAKE_TEMPLATE_ID,
                data = capture(templateDataSlot),
                fileName = FAKE_FILE_NAME,
                correlationId = capture(correlationIdSlot)
            )
        } returns generatedDocument

        `when`("a document is created") {
            val document = epistolaDocumentCreationService.createDocument(
                zaak = zaak,
                templateId = FAKE_TEMPLATE_ID,
                fileName = FAKE_FILE_NAME
            )

            then("the rendered document is returned") {
                document shouldBe generatedDocument
            }

            and("only the fields the template declares are sent") {
                templateDataSlot.captured shouldBe mapOf(
                    "zaak" to mapOf(
                        "identificatie" to "fakeIdentificatie",
                        "omschrijving" to "fakeOmschrijving"
                    )
                )
            }

            and("the zaak is identified to Epistola by its UUID and not by its zaaknummer") {
                correlationIdSlot.captured shouldBe zaak.uuid.toString()
            }
        }
    }

    given("a template that declares no schema") {
        val loggedInUser = createLoggedInUser()
        val zaak = createZaak()

        every { loggedInUserInstance.get() } returns loggedInUser
        every {
            documentCreationDataService.createEpistolaData(loggedInUser, zaak, null)
        } returns createData()
        every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns null

        `when`("a document is created") {
            val exception = shouldThrow<EpistolaTemplateSchemaMissingException> {
                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME
                )
            }

            then("no zaak data is sent to Epistola") {
                exception.message shouldContain FAKE_TEMPLATE_ID
                verify(exactly = 0) { epistolaClientService.generateDocument(any(), any(), any(), any()) }
            }
        }
    }

    context("creating a document and storing it in the zaak's dossier") {
        fun givenAGeneratedDocument(
            zaak: Zaak,
            informatieObjectTypeUuid: UUID,
            vertrouwelijkheidaanduiding: VertrouwelijkheidaanduidingEnum = VertrouwelijkheidaanduidingEnum.ZAAKVERTROUWELIJK
        ): Pair<EpistolaGeneratedDocument, URI> {
            val loggedInUser = createLoggedInUser(displayName = "fakeDisplayName")
            val informatieObjectTypeUri = URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid")
            val generatedDocument = EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = "$FAKE_TITLE.pdf",
                content = "fakePdfContent".toByteArray()
            )
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.readInformatieobjecttypeUuid(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } returns informatieObjectTypeUuid
            every { ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid) } returns createInformatieObjectType(
                uri = informatieObjectTypeUri,
                vertrouwelijkheidaanduiding = vertrouwelijkheidaanduiding
            )
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
            every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns TEMPLATE_SCHEMA
            every {
                epistolaClientService.generateDocument(FAKE_TEMPLATE_ID, any(), "$FAKE_TITLE.pdf", zaak.uuid.toString())
            } returns generatedDocument
            every { configurationService.readBronOrganisatie() } returns FAKE_BRONORGANISATIE
            return generatedDocument to informatieObjectTypeUri
        }

        given("a template configured for the zaak's zaaktype, under a zaakvertrouwelijk informatieobjecttype") {
            val zaak = createZaak()
            val (generatedDocument, informatieObjectTypeUri) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            val zaakInformatieObject = createZaakInformatieobjectForReads()
            val createLockRequestSlot = slot<EnkelvoudigInformatieObjectCreateLockRequest>()
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = capture(createLockRequestSlot),
                    taskId = null
                )
            } returns zaakInformatieObject
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val storedZaakInformatieObject = epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = FAKE_DESCRIPTION
                )

                then("the document is linked to the zaak") {
                    storedZaakInformatieObject shouldBe zaakInformatieObject
                }

                and("it is stored as a PDF with every field the Documenten API requires") {
                    with(createLockRequestSlot.captured) {
                        bronorganisatie shouldBe FAKE_BRONORGANISATIE
                        creatiedatum shouldBe LocalDate.now()
                        titel shouldBe FAKE_TITLE
                        beschrijving shouldBe FAKE_DESCRIPTION
                        auteur shouldBe "fakeDisplayName"
                        taal shouldBe ConfigurationService.TAAL_NEDERLANDS
                        informatieobjecttype shouldBe informatieObjectTypeUri
                        status shouldBe StatusEnum.IN_BEWERKING
                        formaat shouldBe "application/pdf"
                        bestandsnaam shouldBe "$FAKE_TITLE.pdf"
                        bestandsomvang shouldBe generatedDocument.content.size
                        inhoud shouldBe generatedDocument.content.toBase64String()
                    }
                }

                and("its confidentiality comes from the informatieobjecttype instead of being public") {
                    createLockRequestSlot.captured.vertrouwelijkheidaanduiding shouldBe
                        DrcVertrouwelijkheidaanduidingEnum.ZAAKVERTROUWELIJK
                }

                and("Epistola's copy is deleted, but only once the document is in the dossier") {
                    verifyOrder {
                        enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                            zaak = zaak,
                            enkelvoudigInformatieObjectCreateLockRequest = any(),
                            taskId = null
                        )
                        epistolaClientService.deleteDocument(generatedDocument.documentId)
                    }
                }
            }
        }

        given("a document created from a task") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = "fakeTaskId",
                    skipPolicyCheck = false
                )
            } returns createZaakInformatieobjectForReads()
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = null,
                    taskId = "fakeTaskId"
                )

                then("it is linked to the task under this request's own policy checks, which SmartDocuments has to skip") {
                    verify(exactly = 1) {
                        enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                            zaak = zaak,
                            enkelvoudigInformatieObjectCreateLockRequest = any(),
                            taskId = "fakeTaskId",
                            skipPolicyCheck = false
                        )
                    }
                }
            }
        }

        given("storing the generated document in Open Zaak fails") {
            val zaak = createZaak()
            givenAGeneratedDocument(zaak, UUID.randomUUID())
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } throws DrcRuntimeException("fakeDrcFailure")

            `when`("the document is created and stored") {
                val drcRuntimeException = shouldThrow<DrcRuntimeException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("the failure reaches the caller and Epistola's copy is kept, as the only one there is") {
                    drcRuntimeException.message shouldBe "fakeDrcFailure"
                    verify(exactly = 0) { epistolaClientService.deleteDocument(any()) }
                }
            }
        }

        given("a template that is not configured for the zaak's zaaktype") {
            val zaak = createZaak()
            every {
                epistolaTemplatesService.readInformatieobjecttypeUuid(any(), FAKE_TEMPLATE_ID)
            } throws EpistolaTemplateNotConfiguredException("fakeNotConfigured")

            `when`("the document is created and stored") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("it is refused before any zaak data reaches Epistola") {
                    epistolaTemplateNotConfiguredException.message shouldBe "fakeNotConfigured"
                    verify(exactly = 0) { epistolaClientService.generateDocument(any(), any(), any(), any()) }
                }
            }
        }
    }
})
