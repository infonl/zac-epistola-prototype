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
import nl.info.client.zgw.drc.model.createEnkelvoudigInformatieObject
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createInformatieObjectType
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.documents.model.createEpistolaDocument
import java.util.UUID

private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_TITLE = "fakeTitle"
private const val FAKE_FILE_NAME = "$FAKE_TITLE.pdf"
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
            } answers { generatedDocument.copy(kanaal = askedKanalen.last()) }
            return askedKanalen
        }

        fun createDocumentAndThenANewVersion(
            zaak: Zaak,
            kanalen: EpistolaKanalen,
            kanaal: String?
        ): Pair<List<String?>, List<String?>> {
            val askedKanalen = givenATemplate(zaak, kanalen)
            val storedKanalen = mutableListOf<String?>()
            val informatieObjectTypeUuid = UUID.randomUUID()
            val zaakInformatieObject = createZaakInformatieobjectForReads()
            val informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid()
            every {
                epistolaTemplatesService.readInformatieobjecttypeUuid(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } returns informatieObjectTypeUuid
            every { ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid) } returns createInformatieObjectType()
            every { configurationService.readBronOrganisatie() } returns FAKE_BRONORGANISATIE
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } returns zaakInformatieObject
            every {
                epistolaDocumentRepository.createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = captureNullable(storedKanalen)
                )
            } returns createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, templateId = FAKE_TEMPLATE_ID)
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } answers {
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = storedKanalen.single()
                )
            }
            every {
                epistolaTemplatesService.assertTemplateIsOffered(zaak.zaaktype.extractUuid(), FAKE_TEMPLATE_ID)
            } just runs
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = any()
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            epistolaDocumentCreationService.createAndStoreDocument(
                zaak = zaak,
                templateId = FAKE_TEMPLATE_ID,
                title = FAKE_TITLE,
                description = null,
                kanaal = kanaal
            )
            epistolaDocumentVersionService.createNewVersion(
                zaak = zaak,
                enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(
                    uuid = informatieObjectUUID,
                    bestandsnaam = FAKE_FILE_NAME
                )
            )
            return askedKanalen to storedKanalen
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

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME
                )

                then("no kanaal is asked for, so Epistola renders the template's default variant itself") {
                    askedKanalen.single() shouldBe null
                }
            }

            `when`(
                "a document is created by post, the default variant's kanaal that the picker preselects, " +
                    "and later a new version of it"
            ) {
                val (askedKanalen, storedKanalen) = createDocumentAndThenANewVersion(
                    zaak = zaak,
                    kanalen = postAndDigitaal,
                    kanaal = "post"
                )

                then("both ask for post, like any kanaal the behandelaar chose") {
                    askedKanalen shouldBe listOf("post", "post")
                }

                and("the document stores post") {
                    storedKanalen shouldBe listOf("post")
                }
            }
        }

        given(
            "a template whose default variant is an English one by post, next to a Dutch one by post, " +
                "and a zaak whose communicatiekanaal suggests no kanaal"
        ) {
            val zaak = createZaak().apply { communicatiekanaalNaam = "Intern" }
            val onlyPost = EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")

            `when`("a document is created without a kanaal to choose, and later a new version of it") {
                val (askedKanalen, storedKanalen) = createDocumentAndThenANewVersion(
                    zaak = zaak,
                    kanalen = onlyPost,
                    kanaal = null
                )

                then("neither asks for a kanaal, so Epistola renders both in the same default variant") {
                    askedKanalen shouldBe listOf(null, null)
                }

                and("the document stores no kanaal") {
                    storedKanalen shouldBe listOf(null)
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

                val document = epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    kanaal = "post"
                )

                then("no kanaal is asked for, so Epistola renders the template as before") {
                    askedKanalen.single() shouldBe null
                }

                and("the document names no kanaal") {
                    document.kanaal shouldBe null
                }
            }
        }
    }
})
