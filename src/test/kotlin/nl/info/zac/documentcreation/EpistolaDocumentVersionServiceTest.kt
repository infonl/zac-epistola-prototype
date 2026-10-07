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
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createDutchAndEnglishLocales
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
import nl.info.zac.epistola.model.EpistolaTemplateSetting
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_NEW_VERSION_NOT_POSSIBLE
import nl.info.zac.util.toBase64String
import java.util.UUID

private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_ZAAKTYPE_CATALOG_ID = "fake-zaaktype-catalog"
private const val FAKE_DEFAULT_CATALOG_ID = "fake-default-catalog"
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
        @Suppress("LongParameterList")
        fun givenANewVersionGenerated(
            zaak: Zaak,
            fileName: String,
            kanalen: EpistolaKanalen = EpistolaKanalen(),
            locales: EpistolaLocales = EpistolaLocales(),
            catalogId: String = FAKE_CATALOG_ID,
            templateSettings: Map<String, EpistolaTemplateSetting> = emptyMap()
        ): EpistolaGeneratedDocument {
            val loggedInUser = createLoggedInUser(displayName = "fakeDisplayName")
            val askedLocales = mutableListOf<String?>()
            val generatedDocument = EpistolaGeneratedDocument(
                documentId = UUID.randomUUID(),
                fileName = fileName,
                content = "fakeNewPdfContent".toByteArray()
            )
            every { loggedInUserInstance.get() } returns loggedInUser
            every {
                epistolaTemplatesService.readOfferedCatalog(zaak.zaaktype.extractUuid())
            } returns OfferedEpistolaCatalog(
                catalogId = FAKE_ZAAKTYPE_CATALOG_ID,
                informatieObjectTypeUuid = UUID.randomUUID(),
                templateSettings = templateSettings
            )
            every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
            every { epistolaClientService.readGenerationTemplate(catalogId, FAKE_TEMPLATE_ID) } returns
                EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA, kanalen = kanalen, locales = locales)
            every {
                epistolaClientService.generateDocument(
                    catalogId = catalogId,
                    templateId = FAKE_TEMPLATE_ID,
                    data = any(),
                    fileName = fileName,
                    correlationId = zaak.uuid.toString(),
                    kanaal = any(),
                    locale = captureNullable(askedLocales),
                    onJobStatus = any()
                )
            } answers { generatedDocument.copy(locale = askedLocales.last()) }
            return generatedDocument
        }

        given("a document generated from a template that the beheerder has switched off since") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                templateSettings = mapOf(FAKE_TEMPLATE_ID to EpistolaTemplateSetting(isEnabled = false))
            )
            val newVersion = createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID
                )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns newVersion
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                val createdVersion = epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("it is made, as switching a template off only hides it from the selector in Document maken") {
                    createdVersion shouldBe newVersion
                }
            }
        }

        given(
            "a document Epistola generated by post from a catalog its zaaktype has since moved away from, for a zaak by e-mail"
        ) {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "digitaal")
            )
            val newVersion = createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            val requestSlot = slot<EnkelvoudigInformatieObjectWithLockRequest>()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = "post"
                )
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

                and("it is generated from the template in the document's catalog, rather than in the zaaktype's") {
                    verify(exactly = 1) { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, FAKE_TEMPLATE_ID) }
                }

                and("it is generated by post like the document, rather than in the kanaal the communicatiekanaal suggests") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = "post",
                            locale = any(),
                            onJobStatus = any()
                        )
                    }
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

        given("a document generated by post from a template that no longer has a variant by post, for a zaak by e-mail") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                kanalen = EpistolaKanalen(kanalen = listOf("digitaal", "sms"), defaultKanaal = "sms")
            )
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = "post"
                )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("it is generated in the kanaal the communicatiekanaal suggests instead") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = "digitaal",
                            onJobStatus = any()
                        )
                    }
                }
            }
        }

        given("a document Epistola generated in English, from a template that still has an English variant") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post"),
                locales = createDutchAndEnglishLocales()
            )
            val requestSlot = slot<EnkelvoudigInformatieObjectWithLockRequest>()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns createEpistolaDocument(
                informatieObjectUUID = informatieObjectUUID,
                catalogId = FAKE_CATALOG_ID,
                templateId = FAKE_TEMPLATE_ID,
                kanaal = "post",
                locale = "en-GB"
            )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = capture(requestSlot),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("it is generated in English by post again, rather than in Dutch") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = "post",
                            locale = "en-GB",
                            onJobStatus = any()
                        )
                    }
                }

                and("the new version is registered in English") {
                    requestSlot.captured.taal shouldBe "eng"
                }
            }
        }

        given("a document Epistola generated in a language its template no longer has") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "Post" }
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post"),
                locales = createDutchAndEnglishLocales()
            )
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns createEpistolaDocument(
                informatieObjectUUID = informatieObjectUUID,
                catalogId = FAKE_CATALOG_ID,
                templateId = FAKE_TEMPLATE_ID,
                kanaal = "post",
                locale = "fr-FR"
            )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("it is generated in Dutch, as the form would preselect") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = "post",
                            locale = "nl-NL",
                            onJobStatus = any()
                        )
                    }
                }
            }
        }

        given("a document Epistola generated before ZAC stored languages, from a template that now has Dutch and English") {
            val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post"),
                locales = createDutchAndEnglishLocales()
            )
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID,
                    kanaal = "post"
                )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("Dutch is asked for, which ZAC preferred before, so the Dutch and English variants by post do not tie") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = "post",
                            locale = "nl-NL",
                            onJobStatus = any()
                        )
                    }
                }
            }
        }

        given("a document Epistola generated without a language, from a template whose variants carry none") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(zaak, FAKE_FILE_NAME)
            val requestSlot = slot<EnkelvoudigInformatieObjectWithLockRequest>()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID
                )
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = capture(requestSlot),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("no language is asked for") {
                    verify(exactly = 1) {
                        epistolaClientService.generateDocument(
                            catalogId = FAKE_CATALOG_ID,
                            templateId = FAKE_TEMPLATE_ID,
                            data = any(),
                            fileName = FAKE_FILE_NAME,
                            correlationId = zaak.uuid.toString(),
                            kanaal = null,
                            locale = null,
                            onJobStatus = any()
                        )
                    }
                }

                and("the new version leaves the informatieobject's language as it was") {
                    requestSlot.captured.taal shouldBe null
                }
            }
        }

        given("a document Epistola generated before ZAC remembered the catalog of a document") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(
                zaak = zaak,
                fileName = FAKE_FILE_NAME,
                catalogId = FAKE_DEFAULT_CATALOG_ID
            )
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(informatieObjectUUID = informatieObjectUUID, catalogId = null, templateId = FAKE_TEMPLATE_ID)
            every { epistolaClientService.defaultCatalogId } returns FAKE_DEFAULT_CATALOG_ID
            every {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = any(),
                    toelichting = "Nieuwe versie gegenereerd met Epistola"
                )
            } returns createEnkelvoudigInformatieObject(uuid = informatieObjectUUID, versie = 2)
            every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs

            `when`("a new version is created") {
                epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)

                then("it is generated from the catalog of ZAC's settings, the only one there was then") {
                    verify(exactly = 1) {
                        epistolaClientService.readGenerationTemplate(FAKE_DEFAULT_CATALOG_ID, FAKE_TEMPLATE_ID)
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
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID
                )
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

        given("a document of a zaaktype that no longer offers Epistola templates") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            every { loggedInUserInstance.get() } returns createLoggedInUser()
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID
                )
            every {
                epistolaTemplatesService.readOfferedCatalog(zaak.zaaktype.extractUuid())
            } throws EpistolaTemplateNotConfiguredException("fakeMessage")

            `when`("a new version is created") {
                shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
                }

                then("no zaak data is sent to Epistola") {
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

        given("Open Zaak refuses to store the new version") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject(bestandsnaam = FAKE_FILE_NAME)
            val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
            val generatedDocument = givenANewVersionGenerated(zaak, FAKE_FILE_NAME)
            every { epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) } returns
                createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    catalogId = FAKE_CATALOG_ID,
                    templateId = FAKE_TEMPLATE_ID
                )
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
