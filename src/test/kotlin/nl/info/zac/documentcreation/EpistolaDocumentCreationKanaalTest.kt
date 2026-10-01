/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import nl.info.client.zgw.util.extractUuid
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import java.util.UUID

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

                then("no kanaal is asked for, so Epistola renders the template's default variant itself") {
                    askedKanalen.single() shouldBe null
                }
            }
        }

        given("a template the zaak's zaaktype offers, with a post and a digital variant") {
            val zaak = createZaak()
            every {
                epistolaTemplatesService.assertTemplateIsOffered(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } just runs
            every { epistolaClientService.readGenerationTemplate(FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = postAndDigitaal)

            `when`("its kanalen are read") {
                val kanalen = epistolaDocumentCreationService.readKanalen(zaak = zaak, templateId = FAKE_TEMPLATE_ID)

                then("both are returned, with that of the default variant") {
                    kanalen shouldBe postAndDigitaal
                }
            }
        }

        given("a template the zaak's zaaktype does not offer") {
            val zaak = createZaak()
            every {
                epistolaTemplatesService.assertTemplateIsOffered(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } throws EpistolaTemplateNotConfiguredException("fakeMessage")

            `when`("its kanalen are read") {
                shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaDocumentCreationService.readKanalen(zaak = zaak, templateId = FAKE_TEMPLATE_ID)
                }

                then("Epistola is not asked about it") {
                    verify(exactly = 0) { epistolaClientService.readGenerationTemplate(any()) }
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
