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
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.zgw.drc.exception.DrcRuntimeException
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.drc.model.generated.VertrouwelijkheidaanduidingEnum as DrcVertrouwelijkheidaanduidingEnum
import nl.info.client.zgw.drc.model.generated.StatusEnum
import nl.info.client.zgw.shared.exception.ZgwErrorException
import nl.info.client.zgw.shared.exception.ZgwValidationErrorException
import nl.info.client.zgw.shared.model.ZgwError
import nl.info.client.zgw.shared.model.createFieldValidationError
import nl.info.client.zgw.shared.model.createValidationZgwError
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
import nl.info.zac.documentcreation.exception.EpistolaDocumentCreationException
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.documents.model.createEpistolaDocument
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED
import nl.info.zac.util.toBase64String
import java.net.ConnectException
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

class EpistolaDocumentCreationKanaalTest : BehaviorSpec({
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

    context("choosing the template's variant by kanaal") {
        val generatedDocument = EpistolaGeneratedDocument(
            documentId = UUID.randomUUID(),
            fileName = FAKE_FILE_NAME,
            content = "fakePdfContent".toByteArray()
        )
        val postAndDigitaal = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")

        fun givenATemplate(zaak: Zaak, kanalen: EpistolaKanalen): List<String?> {
            val loggedInUser = createLoggedInUser()
            val askedKanalen = mutableListOf<String?>()
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every { epistolaClientService.readGenerationTemplate(FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = kanalen)
            every {
                epistolaClientService.generateDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = FAKE_FILE_NAME,
                    correlationId = any(),
                    kanaal = captureNullable(askedKanalen),
                    onJobStatus = any()
                )
            } returns generatedDocument
            return askedKanalen
        }

        given("a template with a post and a digital variant, and a zaak whose communicatiekanaal is e-mail") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is created without choosing a kanaal") {
                val askedKanalen = givenATemplate(zaak, postAndDigitaal)

                epistolaDocumentCreationService.createDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID, fileName = FAKE_FILE_NAME)

                then("the digital variant is asked for") {
                    askedKanalen.single() shouldBe "digitaal"
                }
            }

            `when`("a document is created by post") {
                val askedKanalen = givenATemplate(zaak, postAndDigitaal)

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    kanaal = "post"
                )

                then("the chosen kanaal wins over the communicatiekanaal") {
                    askedKanalen.single() shouldBe "post"
                }
            }

            `when`("a document is created for a kanaal the template has no variant for") {
                val askedKanalen = givenATemplate(zaak, postAndDigitaal)

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    kanaal = "fakeKanaal"
                )

                then("the communicatiekanaal decides instead") {
                    askedKanalen.single() shouldBe "digitaal"
                }
            }
        }

        given("a template with a post and a digital variant, and a zaak whose communicatiekanaal suggests neither") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "Intern" }

            `when`("a document is created without choosing a kanaal") {
                val askedKanalen = givenATemplate(zaak, postAndDigitaal)

                epistolaDocumentCreationService.createDocument(zaak = zaak, templateId = FAKE_TEMPLATE_ID, fileName = FAKE_FILE_NAME)

                then("the kanaal of the template's default variant is asked for") {
                    askedKanalen.single() shouldBe "post"
                }
            }
        }

        given("a template whose variants are made for no kanaal") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is created by post") {
                val askedKanalen = givenATemplate(zaak, EpistolaKanalen())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    kanaal = "post"
                )

                then("no kanaal is asked for, so Epistola renders the template as before") {
                    askedKanalen.single() shouldBe null
                }
            }
        }
    }
})
