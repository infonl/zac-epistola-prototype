/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createDutchAndEnglishLocales
import nl.info.client.zgw.drc.model.createEnkelvoudigInformatieObject
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.zrc.model.generated.ZaakInformatieObject
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
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import java.util.UUID

private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"

private val TEMPLATE_SCHEMA = mapOf(
    "properties" to mapOf(
        "zaak" to mapOf(
            "properties" to mapOf("identificatie" to mapOf("type" to "string"))
        )
    )
)

private class AskedVariant(
    val kanalen: MutableList<String?> = mutableListOf(),
    val locales: MutableList<String?> = mutableListOf()
)

class EpistolaDocumentCreationLocaleTest : BehaviorSpec({
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
    val postAndDigitaal = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")

    afterEach { checkUnnecessaryStub() }

    context("the language ZAC asks Epistola for") {
        fun givenATemplate(zaak: Zaak, locales: EpistolaLocales): AskedVariant {
            val loggedInUser = createLoggedInUser()
            val askedVariant = AskedVariant()
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = postAndDigitaal, locales = locales)
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = FAKE_FILE_NAME,
                    correlationId = any(),
                    kanaal = captureNullable(askedVariant.kanalen),
                    locale = captureNullable(askedVariant.locales),
                    onJobStatus = any()
                )
            } returns EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = FAKE_FILE_NAME,
                content = "fakePdfContent".toByteArray()
            )
            return askedVariant
        }

        given("a template with Dutch variants by post and digitally and an English one by post, for a zaak by e-mail") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is created") {
                val askedVariant = givenATemplate(zaak, createDutchAndEnglishLocales())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME
                )

                then("Dutch is asked for, in the variant the communicatiekanaal suggests, as the behandelaar chooses no language") {
                    askedVariant.locales.single() shouldBe "nl-NL"
                    askedVariant.kanalen.single() shouldBe "digitaal"
                }
            }

            `when`("a document is generated again in English, as a new version of an English document is") {
                val askedVariant = givenATemplate(zaak, createDutchAndEnglishLocales())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    taal = "en-GB"
                )

                then("English is asked for by post, its only variant, rather than digitally in Dutch") {
                    askedVariant.locales.single() shouldBe "en-GB"
                    askedVariant.kanalen.single() shouldBe "post"
                }
            }

            `when`("a document is generated again digitally in English, a combination no variant has") {
                val askedVariant = givenATemplate(zaak, createDutchAndEnglishLocales())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    variant = "digitaal",
                    taal = "en-GB"
                )

                then("the chosen language wins, in a kanaal it has, so Epistola never falls back to its default variant") {
                    askedVariant.locales.single() shouldBe "en-GB"
                    askedVariant.kanalen.single() shouldBe "post"
                }
            }

            `when`("a document is generated again in a language the template no longer has") {
                val askedVariant = givenATemplate(zaak, createDutchAndEnglishLocales())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    taal = "fr-FR"
                )

                then("Dutch is asked for instead") {
                    askedVariant.locales.single() shouldBe "nl-NL"
                }
            }
        }

        given("a template whose variants carry no language") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is generated again in English") {
                val askedVariant = givenATemplate(zaak, EpistolaLocales())

                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME,
                    taal = "en-GB"
                )

                then("no language is asked for, and the kanaal is chosen as before") {
                    askedVariant.locales.single() shouldBe null
                    askedVariant.kanalen.single() shouldBe "digitaal"
                }
            }
        }
    }

    context("storing an Epistola document in the zaak's dossier") {
        fun givenADocumentGeneratedIn(
            zaak: Zaak,
            locales: EpistolaLocales
        ): Pair<ZaakInformatieObject, CapturingSlot<EnkelvoudigInformatieObjectCreateLockRequest>> {
            val loggedInUser = createLoggedInUser()
            val informatieObjectTypeUuid = UUID.randomUUID()
            val askedLocale = mutableListOf<String?>()
            val zaakInformatieObject = createZaakInformatieobjectForReads()
            val createLockRequestSlot = slot<EnkelvoudigInformatieObjectCreateLockRequest>()
            val generatedDocument = EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = "fakeTitle.pdf",
                content = "fakePdfContent".toByteArray()
            )
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.readOfferedCatalog(zaak.zaaktype.extractUuid())
            } returns OfferedEpistolaCatalog(catalogId = FAKE_CATALOG_ID, informatieObjectTypeUuid = informatieObjectTypeUuid)
            every { ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid) } returns createInformatieObjectType()
            every { configurationService.readBronOrganisatie() } returns "123443210"
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = EpistolaKanalen(), locales = locales)
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = "fakeTitle.pdf",
                    correlationId = any(),
                    kanaal = null,
                    locale = captureNullable(askedLocale),
                    onJobStatus = any()
                )
            } answers { generatedDocument.copy(locale = askedLocale.last()) }
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = capture(createLockRequestSlot),
                    taskId = null
                )
            } returns zaakInformatieObject
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs
            return zaakInformatieObject to createLockRequestSlot
        }

        given("a template whose only language is British English") {
            val zaak = createZaak()

            `when`("a document is created and stored") {
                val (zaakInformatieObject, createLockRequestSlot) = givenADocumentGeneratedIn(
                    zaak = zaak,
                    locales = EpistolaLocales(kanalenByLocale = mapOf("en-GB" to EpistolaKanalen()), defaultLocale = "en-GB")
                )

                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = "fakeTitle",
                    description = null
                )

                then("the informatieobject is registered in English, by its ISO 639-2/B code") {
                    createLockRequestSlot.captured.taal shouldBe "eng"
                }

                and("the language ZAC asked Epistola for is remembered with the template, for a new version to use") {
                    verify(exactly = 1) {
                        epistolaDocumentRepository.createEpistolaDocument(
                            informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid(),
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            kanaal = null,
                            locale = "en-GB"
                        )
                    }
                }
            }
        }

        given("a template whose variants carry no language") {
            val zaak = createZaak()

            `when`("a document is created and stored") {
                val (zaakInformatieObject, createLockRequestSlot) = givenADocumentGeneratedIn(zaak = zaak, locales = EpistolaLocales())

                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = "fakeTitle",
                    description = null
                )

                then("the informatieobject is registered in Dutch, as before") {
                    createLockRequestSlot.captured.taal shouldBe "dut"
                }

                and("no language is remembered, because ZAC asked for none") {
                    verify(exactly = 1) {
                        epistolaDocumentRepository.createEpistolaDocument(
                            informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid(),
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            kanaal = null,
                            locale = null
                        )
                    }
                }
            }
        }
    }

    context("creating a document, and later a new version of it") {
        fun givenTheZaaksDossier(zaak: Zaak, zaakInformatieObject: ZaakInformatieObject) {
            val informatieObjectTypeUuid = UUID.randomUUID()
            val informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid()
            every {
                epistolaTemplatesService.readOfferedCatalog(zaak.zaaktype.extractUuid())
            } returns OfferedEpistolaCatalog(catalogId = FAKE_CATALOG_ID, informatieObjectTypeUuid = informatieObjectTypeUuid)
            every { ztcClientService.readInformatieobjecttype(informatieObjectTypeUuid) } returns createInformatieObjectType()
            every { configurationService.readBronOrganisatie() } returns "123443210"
            val loggedInUser = createLoggedInUser()
            every { loggedInUserInstance.get() } returns loggedInUser
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, null) } returns createData()
            every {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = any(),
                    taskId = null
                )
            } returns zaakInformatieObject
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = any()
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
        }

        fun createDocumentAndThenANewVersion(zaak: Zaak, locales: EpistolaLocales): Pair<AskedVariant, List<String?>> {
            val askedVariant = AskedVariant()
            val storedKanalen = mutableListOf<String?>()
            val storedLocales = mutableListOf<String?>()
            val zaakInformatieObject = createZaakInformatieobjectForReads()
            val informatieObjectUUID = zaakInformatieObject.informatieobject.extractUuid()
            val generatedDocument = EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = "fakeTitle.pdf",
                content = "fakePdfContent".toByteArray()
            )
            givenTheZaaksDossier(zaak, zaakInformatieObject)
            every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = postAndDigitaal, locales = locales)
            every {
                epistolaClientService.generateDocument(
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = "fakeTitle.pdf",
                    correlationId = any(),
                    kanaal = captureNullable(askedVariant.kanalen),
                    locale = captureNullable(askedVariant.locales),
                    onJobStatus = any()
                )
            } answers { generatedDocument.copy(kanaal = askedVariant.kanalen.last(), locale = askedVariant.locales.last()) }
            every {
                epistolaDocumentRepository.createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = captureNullable(storedKanalen),
                    locale = captureNullable(storedLocales)
                )
            } returns createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, catalogId = FAKE_CATALOG_ID)
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } answers {
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = storedKanalen.single(),
                    locale = storedLocales.single()
                )
            }
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            epistolaDocumentCreationService.createAndStoreDocument(
                zaak = zaak,
                templateId = FAKE_TEMPLATE_ID,
                title = "fakeTitle",
                description = null
            )
            epistolaDocumentVersionService.createNewVersion(
                zaak = zaak,
                enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, bestandsnaam = "fakeTitle.pdf")
            )
            return askedVariant to storedLocales
        }

        given("a template with Dutch variants by post and digitally and an English one by post, for a zaak by e-mail") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is created, and later a new version of it") {
                val (askedVariant, storedLocales) = createDocumentAndThenANewVersion(
                    zaak = zaak,
                    locales = createDutchAndEnglishLocales()
                )

                then("both ask for the digital Dutch variant the zaak suggests") {
                    askedVariant.locales shouldBe listOf("nl-NL", "nl-NL")
                    askedVariant.kanalen shouldBe listOf("digitaal", "digitaal")
                }

                and("the document stores Dutch") {
                    storedLocales shouldBe listOf("nl-NL")
                }
            }
        }

        given("a template whose only language is British English, for a zaak by e-mail") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }

            `when`("a document is created, and later a new version of it") {
                val (askedVariant, storedLocales) = createDocumentAndThenANewVersion(
                    zaak = zaak,
                    locales = EpistolaLocales(
                        kanalenByLocale = mapOf("en-GB" to EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")),
                        defaultLocale = "en-GB"
                    )
                )

                then("both ask for English by post, the only variant there is") {
                    askedVariant.locales shouldBe listOf("en-GB", "en-GB")
                    askedVariant.kanalen shouldBe listOf("post", "post")
                }

                and("the document stores English") {
                    storedLocales shouldBe listOf("en-GB")
                }
            }
        }

        given("a template whose variants carry no language, for a zaak whose communicatiekanaal suggests no kanaal") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "Intern" }

            `when`("a document is created, and later a new version of it") {
                val (askedVariant, storedLocales) = createDocumentAndThenANewVersion(
                    zaak = zaak,
                    locales = EpistolaLocales()
                )

                then("neither asks for a language, so Epistola renders the default variant both times") {
                    askedVariant.locales shouldBe listOf(null, null)
                    askedVariant.kanalen shouldBe listOf(null, null)
                }

                and("the document stores no language") {
                    storedLocales shouldBe listOf(null)
                }
            }
        }
    }
})
