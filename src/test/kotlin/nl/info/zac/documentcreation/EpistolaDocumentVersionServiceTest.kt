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
import nl.info.client.zgw.drc.model.createEnkelvoudigInformatieObject
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectWithLockRequest
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.documents.model.createEpistolaDocument
import nl.info.zac.epistola.exception.EpistolaNewVersionNotPossibleException
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_NEW_VERSION_NOT_POSSIBLE
import nl.info.zac.util.toBase64String
import java.util.UUID

private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"

private val TEMPLATE_SCHEMA = mapOf(
    "properties" to mapOf(
        "zaak" to mapOf(
            "properties" to mapOf(
                "identificatie" to mapOf("type" to "string")
            )
        )
    )
)

class EpistolaDocumentVersionServiceTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val epistolaTemplatesService = mockk<EpistolaTemplatesService>()
    val epistolaDocumentRepository = mockk<EpistolaDocumentRepository>()
    val enkelvoudigInformatieObjectUpdateService = mockk<EnkelvoudigInformatieObjectUpdateService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val epistolaDocumentCreationStatusStore = EpistolaDocumentCreationStatusStore()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        epistolaTemplatesService = epistolaTemplatesService,
        epistolaDocumentRepository = epistolaDocumentRepository,
        ztcClientService = mockk(),
        enkelvoudigInformatieObjectUpdateService = enkelvoudigInformatieObjectUpdateService,
        configurationService = mockk(),
        epistolaDocumentCreationStatusStore = epistolaDocumentCreationStatusStore,
        loggedInUserInstance = loggedInUserInstance
    )
    val epistolaDocumentVersionService = EpistolaDocumentVersionService(
        epistolaDocumentCreationService = epistolaDocumentCreationService,
        epistolaClientService = epistolaClientService,
        epistolaTemplatesService = epistolaTemplatesService,
        epistolaDocumentRepository = epistolaDocumentRepository,
        enkelvoudigInformatieObjectUpdateService = enkelvoudigInformatieObjectUpdateService,
        epistolaDocumentCreationStatusStore = epistolaDocumentCreationStatusStore,
        loggedInUserInstance = loggedInUserInstance
    )

    afterEach { checkUnnecessaryStub() }

    context("generating a new version of a document") {
        fun givenANewVersionGenerated(zaak: Zaak, fileName: String): EpistolaGeneratedDocument {
            val loggedInUser = createLoggedInUser(displayName = "fakeDisplayName")
            val generatedDocument = EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = fileName,
                content = "fakeNewPdfContent".toByteArray()
            )
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.assertTemplateIsOffered(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } just runs
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
            every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns TEMPLATE_SCHEMA
            every {
                epistolaClientService.generateDocument(FAKE_TEMPLATE_ID, any(), fileName, zaak.uuid.toString(), any())
            } returns generatedDocument
            return generatedDocument
        }

        given("a document Epistola generated from a template that the zaaktype still offers") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(zaak, FAKE_FILE_NAME)
            val newVersion = createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            val requestSlot = slot<EnkelvoudigInformatieObjectWithLockRequest>()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, templateId = FAKE_TEMPLATE_ID)
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = capture(requestSlot),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns newVersion
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                val createdVersion = epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("the next version of the same informatieobject is returned") {
                    createdVersion shouldBe newVersion
                }

                and("the new file replaces the content as a PDF, with the behandelaar as its author") {
                    with(requestSlot.captured) {
                        auteur shouldBe "fakeDisplayName"
                        formaat shouldBe "application/pdf"
                        bestandsnaam shouldBe FAKE_FILE_NAME
                        bestandsomvang shouldBe generatedDocument.content.size
                        inhoud shouldBe generatedDocument.content.toBase64String()
                    }
                }

                and("Epistola's copy is deleted, but only once the new version is stored") {
                    verifyOrder {
                        enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                            enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                            enkelvoudigInformatieObjectWithLockRequest = any(),
                            toelichting = "Nieuwe versie gegenereerd met Epistola"
                        )
                        epistolaClientService.deleteDocument(generatedDocument.documentId)
                    }
                }
            }
        }

        given("a document Epistola generated whose current version a user replaced with a file of another type") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = "fakeFileName.docx")
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(zaak, FAKE_FILE_NAME)
            val requestSlot = slot<EnkelvoudigInformatieObjectWithLockRequest>()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, templateId = FAKE_TEMPLATE_ID)
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = capture(requestSlot),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 3)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("the PDF gets the same name with a .pdf extension, so its name matches its format") {
                    requestSlot.captured.bestandsnaam shouldBe FAKE_FILE_NAME
                    requestSlot.captured.formaat shouldBe "application/pdf"
                }
            }
        }

        given("a document that Epistola did not generate") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            every { loggedInUserInstance.get() } returns createLoggedInUser()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns null

            `when`("a new version is created") {
                val epistolaNewVersionNotPossibleException = shouldThrow<EpistolaNewVersionNotPossibleException> {
                    epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
                }

                then("it is refused, because there is no template to generate it from") {
                    epistolaNewVersionNotPossibleException.errorCode shouldBe ERROR_CODE_EPISTOLA_NEW_VERSION_NOT_POSSIBLE
                }

                and("nothing is sent to Epistola") {
                    verify(exactly = 0) { epistolaClientService.generateDocument(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("a document whose template the zaaktype no longer offers") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            every { loggedInUserInstance.get() } returns createLoggedInUser()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, templateId = FAKE_TEMPLATE_ID)
            every {
                epistolaTemplatesService.assertTemplateIsOffered(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } throws EpistolaTemplateNotConfiguredException("fakeMessage")

            `when`("a new version is created") {
                shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
                }

                then("no zaak data is sent to Epistola") {
                    verify(exactly = 0) { epistolaClientService.generateDocument(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("Open Zaak refuses to store the new version") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(zaak, FAKE_FILE_NAME)
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, templateId = FAKE_TEMPLATE_ID)
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = any()
                )
            } throws DrcRuntimeException("fakeDrcFailure")
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                val epistolaDocumentNotStoredException = shouldThrow<EpistolaDocumentNotStoredException> {
                    epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
                }

                then("the behandelaar is told nothing was stored, with Open Zaak's reason") {
                    epistolaDocumentNotStoredException.errorCode shouldBe ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
                    epistolaDocumentNotStoredException.detail shouldBe "fakeDrcFailure"
                }

                and("the log names the document and its zaak, without chaining Open Zaak's exception") {
                    epistolaDocumentNotStoredException.message shouldContain informatieObjectUUID.toString()
                    epistolaDocumentNotStoredException.message shouldContain zaak.identificatie
                    epistolaDocumentNotStoredException.cause shouldBe null
                }

                and("Epistola's copy is deleted anyway") {
                    verify(exactly = 1) { epistolaClientService.deleteDocument(generatedDocument.documentId) }
                }
            }
        }
    }

    context("deciding whether a new version can be generated") {
        given("a document Epistola generated, while Epistola is the provider") {
            val informatieObjectUUID = UUID.randomUUID()
            every { epistolaTemplatesService.isEpistolaActive() } returns true
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns createEpistolaDocument()

            `when`("it is asked whether a new version is available") {
                val isNewVersionAvailable = epistolaDocumentVersionService.isNewVersionAvailable(informatieObjectUUID)

                then("it is") {
                    isNewVersionAvailable shouldBe true
                }
            }
        }

        given("a document Epistola generated, after the installation moved to another provider") {
            every { epistolaTemplatesService.isEpistolaActive() } returns false

            `when`("it is asked whether a new version is available") {
                val isNewVersionAvailable = epistolaDocumentVersionService.isNewVersionAvailable(UUID.randomUUID())

                then("it is not, because the template can no longer be used") {
                    isNewVersionAvailable shouldBe false
                }
            }
        }

        given("a document that Epistola did not generate") {
            val informatieObjectUUID = UUID.randomUUID()
            every { epistolaTemplatesService.isEpistolaActive() } returns true
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns null

            `when`("it is asked whether a new version is available") {
                val isNewVersionAvailable = epistolaDocumentVersionService.isNewVersionAvailable(informatieObjectUUID)

                then("it is not") {
                    isNewVersionAvailable shouldBe false
                }
            }
        }
    }
})
