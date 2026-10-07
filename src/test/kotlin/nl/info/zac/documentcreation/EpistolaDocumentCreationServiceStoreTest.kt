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
import io.mockk.verifyOrder
import jakarta.enterprise.inject.Instance
import jakarta.persistence.PersistenceException
import jakarta.ws.rs.ProcessingException
import net.atos.zac.flowable.task.exception.TaskNotFoundException
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.zgw.drc.exception.DrcRuntimeException
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.drc.model.generated.StatusEnum
import nl.info.client.zgw.drc.model.generated.VertrouwelijkheidaanduidingEnum as DrcVertrouwelijkheidaanduidingEnum
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.shared.exception.ZgwErrorException
import nl.info.client.zgw.shared.exception.ZgwValidationErrorException
import nl.info.client.zgw.shared.model.ZgwError
import nl.info.client.zgw.shared.model.createFieldValidationError
import nl.info.client.zgw.shared.model.createValidationZgwError
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createInformatieObjectType
import nl.info.client.zgw.ztc.model.generated.VertrouwelijkheidaanduidingEnum
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.exception.EpistolaDocumentCreationException
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.documents.model.createEpistolaDocument
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
import nl.info.zac.util.toBase64String
import java.net.ConnectException
import java.net.URI
import java.time.LocalDate
import java.util.UUID

private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
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

class EpistolaDocumentCreationServiceStoreTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val epistolaTemplatesService = mockk<EpistolaTemplatesService>()
    val epistolaDocumentRepository = mockk<EpistolaDocumentRepository>(relaxed = true)
    val ztcClientService = mockk<ZtcClientService>()
    val enkelvoudigInformatieObjectUpdateService = mockk<EnkelvoudigInformatieObjectUpdateService>()
    val configurationService = mockk<ConfigurationService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationStatusStore = EpistolaDocumentCreationStatusStore()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        epistolaTemplatesService = epistolaTemplatesService,
        epistolaDocumentRepository = epistolaDocumentRepository,
        ztcClientService = ztcClientService,
        enkelvoudigInformatieObjectUpdateService = enkelvoudigInformatieObjectUpdateService,
        configurationService = configurationService,
        epistolaDocumentCreationStatusStore = epistolaDocumentCreationStatusStore,
        loggedInUserInstance = loggedInUserInstance
    )

    afterEach { checkUnnecessaryStub() }

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
                content = "fakePdfContent".toByteArray(),
                kanaal = "digitaal"
            )
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.readCatalogOfferingTemplate(zaak.zaaktype.extractUuid(), any())
            } returns OfferedEpistolaCatalog(catalogId = FAKE_CATALOG_ID, informatieObjectTypeUuid = informatieObjectTypeUuid)
            every { ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid) } returns createInformatieObjectType(
                uri = informatieObjectTypeUri,
                vertrouwelijkheidaanduiding = vertrouwelijkheidaanduiding
            )
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
            EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = EpistolaKanalen())
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = "$FAKE_TITLE.pdf",
                    correlationId = zaak.uuid.toString(),
                    kanaal = any(),
                    locale = any(),
                    onJobStatus = any()
                )
            } returns generatedDocument
            every { configurationService.readBronOrganisatie() } returns FAKE_BRONORGANISATIE
            return generatedDocument to informatieObjectTypeUri
        }

        given("a template of the catalog the zaak's zaaktype offers, under a zaakvertrouwelijk informatieobjecttype") {
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

                and("the catalog, the template and the kanaal it was generated in are remembered, for a new version to use") {
                    verify(exactly = 1) {
                        epistolaDocumentRepository.createEpistolaDocument(
                            informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid(),
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            kanaal = "digitaal",
                            locale = null
                        )
                    }
                }
            }
        }

        given("a document that is stored in the zaak, but whose template cannot be remembered") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            val zaakInformatieObject = createZaakInformatieobjectForReads()
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } returns zaakInformatieObject
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs
            every {
                epistolaDocumentRepository.createEpistolaDocument(
                    informatieObjectUUID = any(),
                    catalogId = any(),
                    templateId = any(),
                    kanaal = any(),
                    locale = any()
                )
            } throws PersistenceException("fakeDatabaseFailure")

            `when`("the document is created and stored") {
                val storedZaakInformatieObject = epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = null
                )

                then("the document is still linked to the zaak, because it only loses its new version action") {
                    storedZaakInformatieObject shouldBe zaakInformatieObject
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

        given("a document whose job Epistola reports as held up while rendering, before it completes") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            var statusWhileGenerating: EpistolaDocumentCreationStatus? = null
            var statusWhileStoring: EpistolaDocumentCreationStatus? = null
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = "$FAKE_TITLE.pdf",
                    correlationId = zaak.uuid.toString(),
                    kanaal = any(),
                    locale = any(),
                    onJobStatus = any()
                )
            } answers {
                lastArg<(EpistolaJobStatus) -> Unit>()(EpistolaJobStatus.HELD_UP_IN_RENDERING)
                statusWhileGenerating = epistolaDocumentCreationService.readStatus(zaak.uuid)
                generatedDocument
            }
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } answers {
                statusWhileStoring = epistolaDocumentCreationService.readStatus(zaak.uuid)
                createZaakInformatieobjectForReads()
            }
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = null
                )

                then("the status Epistola reports can be read while the job runs") {
                    statusWhileGenerating shouldBe EpistolaDocumentCreationStatus.HELD_UP_IN_RENDERING
                }

                and("storing is reported once the document is out of Epistola") {
                    statusWhileStoring shouldBe EpistolaDocumentCreationStatus.STORING
                }

                and("no status is left once the request ends") {
                    epistolaDocumentCreationService.readStatus(zaak.uuid) shouldBe null
                }
            }
        }

        given("storing the generated document in Open Zaak fails with a server error") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            val drcRuntimeException = DrcRuntimeException("fakeDrcFailure")
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } throws drcRuntimeException
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val epistolaDocumentNotStoredException = shouldThrow<EpistolaDocumentNotStoredException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("the behandelaar is told the document was generated but not stored, with Open Zaak's reason") {
                    epistolaDocumentNotStoredException.errorCode shouldBe ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
                    epistolaDocumentNotStoredException.detail shouldBe "fakeDrcFailure"
                }

                and("the log says what failed, without chaining the exception whose message carries Open Zaak's words") {
                    epistolaDocumentNotStoredException.message shouldContain "DrcRuntimeException (fakeDrcFailure)"
                    epistolaDocumentNotStoredException.cause shouldBe null
                }

                and("the log names the zaak, the template and Epistola's document") {
                    epistolaDocumentNotStoredException.message shouldContain zaak.identificatie
                    epistolaDocumentNotStoredException.message shouldContain FAKE_TEMPLATE_ID
                    epistolaDocumentNotStoredException.message shouldContain generatedDocument.documentId.toString()
                }

                and("Epistola's copy is deleted, so no document is left that ZAC holds no reference to") {
                    verify(exactly = 1) { epistolaClientService.deleteDocument(generatedDocument.documentId) }
                }
            }
        }

        given("storing the generated document in Open Zaak fails validation") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } throws ZgwValidationErrorException(
                createValidationZgwError(
                    detail = "fakeDetailOfOpenZaak",
                    invalidParams = listOf(
                        createFieldValidationError(name = "fakeFieldName1", code = "fakeFieldCode1", reason = "fakeReason1"),
                        createFieldValidationError(name = "fakeFieldName2", code = "fakeFieldCode2", reason = "fakeReason2")
                    )
                )
            )
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val epistolaDocumentNotStoredException = shouldThrow<EpistolaDocumentNotStoredException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("the reason Open Zaak gives for each field is shown") {
                    epistolaDocumentNotStoredException.detail shouldBe "fakeReason1, fakeReason2"
                }

                and("the log names the status, the code and the fields, but neither the reasons nor Open Zaak's detail") {
                    epistolaDocumentNotStoredException.message shouldContain
                        "ZgwValidationErrorException (HTTP 123 fakeCode, invalid: fakeFieldName1 [fakeFieldCode1], fakeFieldName2 [fakeFieldCode2])"
                    epistolaDocumentNotStoredException.message shouldNotContain "fakeReason"
                    epistolaDocumentNotStoredException.message shouldNotContain "fakeDetailOfOpenZaak"
                    epistolaDocumentNotStoredException.cause shouldBe null
                }
            }
        }

        given("storing the generated document in Open Zaak is refused with a client error") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } throws ZgwErrorException(
                ZgwError(
                    type = URI("https://localhost:8080/error"),
                    code = "fakeErrorCode",
                    title = "fakeErrorTitle",
                    status = 403,
                    detail = "fakeDetailOfOpenZaak",
                    instance = URI("https://localhost:8080/error-instance")
                )
            )
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val epistolaDocumentNotStoredException = shouldThrow<EpistolaDocumentNotStoredException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("the user gets Open Zaak's detail, and the log only the status and the code") {
                    epistolaDocumentNotStoredException.detail shouldContain "fakeDetailOfOpenZaak"
                    epistolaDocumentNotStoredException.message shouldContain "ZgwErrorException (HTTP 403 fakeErrorCode)"
                    epistolaDocumentNotStoredException.message shouldNotContain "fakeDetailOfOpenZaak"
                    epistolaDocumentNotStoredException.cause shouldBe null
                }
            }
        }

        given("Open Zaak cannot be reached to store the generated document") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } throws ProcessingException("fakeConnectionRefused", ConnectException("fakeCauseMessage"))
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val epistolaDocumentNotStoredException = shouldThrow<EpistolaDocumentNotStoredException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("it counts as a failure to store, and Epistola's copy is deleted") {
                    epistolaDocumentNotStoredException.detail shouldBe "fakeConnectionRefused"
                    verify(exactly = 1) { epistolaClientService.deleteDocument(generatedDocument.documentId) }
                }

                and("the log names the kind of failure and of its cause, and not their messages") {
                    epistolaDocumentNotStoredException.message shouldContain "ProcessingException (ConnectException)"
                    epistolaDocumentNotStoredException.message shouldNotContain "fakeConnectionRefused"
                    epistolaDocumentNotStoredException.message shouldNotContain "fakeCauseMessage"
                    epistolaDocumentNotStoredException.cause shouldBe null
                }
            }
        }

        given("a document that is stored in the zaak, but whose task is no longer open") {
            val zaak = createZaak()
            val (generatedDocument, _) = givenAGeneratedDocument(zaak, UUID.randomUUID())
            val taskNotFoundException = TaskNotFoundException("fakeTaskNotFound")
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = "fakeTaskId",
                    skipPolicyCheck = false
                )
            } throws taskNotFoundException
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("the document is created and stored") {
                val thrownTaskNotFoundException = shouldThrow<TaskNotFoundException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null,
                        taskId = "fakeTaskId"
                    )
                }

                then("the failure is not reported as a failure to store, because the document is in the zaak") {
                    thrownTaskNotFoundException shouldBe taskNotFoundException
                }
            }
        }

        given("Epistola rejects the zaak data against the template's contract") {
            val zaak = createZaak()
            val loggedInUser = createLoggedInUser()
            val informatieObjectTypeUuid = UUID.randomUUID()
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.readCatalogOfferingTemplate(zaak.zaaktype.extractUuid(), any())
            } returns OfferedEpistolaCatalog(catalogId = FAKE_CATALOG_ID, informatieObjectTypeUuid = informatieObjectTypeUuid)
            every {
                ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid)
            } returns createInformatieObjectType()
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
            EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = EpistolaKanalen())
            val epistolaTemplateDataRejectedException = EpistolaTemplateDataRejectedException(
                message = "fakeRejectedMessage",
                detail = "/aanvrager: is required"
            )
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = "$FAKE_TITLE.pdf",
                    correlationId = zaak.uuid.toString(),
                    kanaal = any(),
                    locale = any(),
                    onJobStatus = any()
                )
            } throws epistolaTemplateDataRejectedException

            `when`("the document is created and stored") {
                val epistolaDocumentCreationException = shouldThrow<EpistolaDocumentCreationException> {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = FAKE_TEMPLATE_ID,
                        title = FAKE_TITLE,
                        description = null
                    )
                }

                then("the behandelaar sees Epistola's reason under the error code of the rejection") {
                    epistolaDocumentCreationException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
                    epistolaDocumentCreationException.detail shouldBe "/aanvrager: is required"
                }

                and("the log names the zaak and the template, next to the request that the cause names") {
                    epistolaDocumentCreationException.message shouldContain zaak.identificatie
                    epistolaDocumentCreationException.message shouldContain FAKE_TEMPLATE_ID
                    epistolaDocumentCreationException.cause shouldBe epistolaTemplateDataRejectedException
                }

                and("Epistola's reason stays out of the log") {
                    epistolaDocumentCreationException.message shouldNotContain "aanvrager"
                }

                and("nothing is stored and no status is left") {
                    verify(exactly = 0) {
                        enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                            zaak = any(),
                            enkelvoudigInformatieObjectCreateLockRequest = any(),
                            taskId = any(),
                            skipPolicyCheck = any(),
                            content = any()
                        )
                    }
                    epistolaDocumentCreationService.readStatus(zaak.uuid) shouldBe null
                }
            }
        }

        given("a zaaktype that offers no Epistola templates") {
            val zaak = createZaak()
            every { loggedInUserInstance.get() } returns createLoggedInUser()
            every {
                epistolaTemplatesService.readCatalogOfferingTemplate(any(), any())
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
                    verify(exactly = 0) {
                        epistolaClientService.generateDocument(
                            catalogId = any(),
                            templateId = any(),
                            data = any(),
                            fileName = any(),
                            correlationId = any(),
                            kanaal = any(),
                            locale = any(),
                            onJobStatus = any()
                        )
                    }
                }
            }
        }
    }
})
