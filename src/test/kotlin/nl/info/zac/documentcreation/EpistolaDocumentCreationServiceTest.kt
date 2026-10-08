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
private const val FAKE_FILE_NAME = "fakeFileName.pdf"

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
        every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
            EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA)
        every {
            epistolaClientService.generateDocument(
                catalogId = FAKE_CATALOG_ID,
                templateId = FAKE_TEMPLATE_ID,
                data = capture(templateDataSlot),
                fileName = FAKE_FILE_NAME,
                correlationId = capture(correlationIdSlot),
                onJobStatus = any()
            )
        } returns generatedDocument

        `when`("a document is created") {
            val document = epistolaDocumentCreationService.createDocument(
                zaak = zaak,
                catalogId = FAKE_CATALOG_ID,
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
        every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
            EpistolaGenerationTemplate(dataContract = null)

        `when`("a document is created") {
            val exception = shouldThrow<EpistolaTemplateSchemaMissingException> {
                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME
                )
            }

            then("no zaak data is sent to Epistola") {
                exception.message shouldContain FAKE_TEMPLATE_ID
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

    context("reading the status of a document being generated") {
        given("a document that Epistola is rendering for one user of a zaak") {
            val zaakUuid = UUID.randomUUID()
            epistolaDocumentCreationStatusStore.update(
                userId = "fakeUserId",
                zaakUuid = zaakUuid,
                status = EpistolaDocumentCreationStatus.RENDERING
            )

            `when`("that user reads the status for the zaak") {
                every { loggedInUserInstance.get() } returns createLoggedInUser(id = "fakeUserId")
                val status = epistolaDocumentCreationService.readStatus(zaakUuid)

                then("Epistola's status is returned") {
                    status shouldBe EpistolaDocumentCreationStatus.RENDERING
                }
            }

            `when`("another user reads the status for the same zaak") {
                every { loggedInUserInstance.get() } returns createLoggedInUser(id = "fakeOtherUserId")
                val status = epistolaDocumentCreationService.readStatus(zaakUuid)

                then("there is none, because a user only reads the status of their own request") {
                    status shouldBe null
                }
            }
        }
    }
})
