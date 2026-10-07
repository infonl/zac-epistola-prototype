/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.model.createZaaktypeBpmnConfiguration
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaCmmnOnlyException
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.epistola.rest.RestOfferedEpistolaTemplate
import nl.info.zac.exception.ErrorCode
import java.util.UUID

private const val FAKE_DEFAULT_CATALOG_ID = "fake-default-catalog"

class EpistolaOfferedTemplatesTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val epistolaTemplatesService = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        zaaktypeCmmnConfigurationBeheerService = mockk(),
        ztcClientService = mockk(),
        documentCreationProviderConfiguration = documentCreationProviderConfiguration
    )

    afterEach { checkUnnecessaryStub() }

    fun givenActiveProvider(documentCreationProvider: DocumentCreationProvider) {
        every { documentCreationProviderConfiguration.activeProvider } returns documentCreationProvider
    }

    fun offeringZaaktypeConfiguration(
        zaaktypeUuid: UUID,
        catalogId: String?,
        informatieObjectTypeUuid: UUID?,
        isEpistolaEnabled: Boolean = true,
        locale: String? = null
    ) = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid).apply {
        this.isEpistolaEnabled = isEpistolaEnabled
        epistolaCatalogId = catalogId
        epistolaLocale = locale
        epistolaInformatieobjecttypeUuid = informatieObjectTypeUuid
    }

    context("listing the templates a zaaktype offers") {
        given("a zaaktype that offers the templates of a catalog it chose") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = informatieObjectTypeUuid
                )
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "Ontvangstbevestiging"),
                createTemplateSummary(id = "fake-template-2", name = "besluit evenementenvergunning")
            )

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then(
                    "every template of that catalog is returned by its name, ordered ignoring case, " +
                        "each with the zaaktype's informatieobjecttype"
                ) {
                    offeredTemplates shouldBe listOf(
                        RestOfferedEpistolaTemplate(
                            id = "fake-template-2",
                            name = "besluit evenementenvergunning",
                            informatieObjectTypeUUID = informatieObjectTypeUuid
                        ),
                        RestOfferedEpistolaTemplate(
                            id = "fake-template-1",
                            name = "Ontvangstbevestiging",
                            informatieObjectTypeUUID = informatieObjectTypeUuid
                        )
                    )
                }
            }
        }

        given("a zaaktype for which the beheerder has not chosen a catalog") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.defaultCatalogId } returns FAKE_DEFAULT_CATALOG_ID
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = null,
                    informatieObjectTypeUuid = informatieObjectTypeUuid
                )
            every { epistolaClientService.listTemplates(FAKE_DEFAULT_CATALOG_ID) } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "Standaardbrief")
            )

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("the templates of the catalog of ZAC's settings are returned, as before ZAC knew catalogs") {
                    offeredTemplates.map { it.id } shouldBe listOf("fake-template-1")
                }
            }
        }

        given("a zaaktype for which the beheerder switched Epistola off") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = UUID.randomUUID(),
                    isEpistolaEnabled = false
                )

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("none are returned and Epistola is not asked for them") {
                    offeredTemplates.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates(any()) }
                }
            }
        }

        given("a zaaktype without an informatieobjecttype for its Epistola documents") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(zaaktypeUuid = zaaktypeUuid, catalogId = "fake-catalog", informatieObjectTypeUuid = null)

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("none are returned, because a document generated from one could not be stored") {
                    offeredTemplates.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates(any()) }
                }
            }
        }

        given("a zaaktype that has never been configured") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns null

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("none are returned") {
                    offeredTemplates.shouldBeEmpty()
                }
            }
        }

        given("a BPMN zaaktype") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                createZaaktypeBpmnConfiguration(zaaktypeUUID = zaaktypeUuid).apply {
                    isEpistolaEnabled = true
                    epistolaInformatieobjecttypeUuid = UUID.randomUUID()
                }

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("none are returned, because Epistola is limited to CMMN zaaktypen") {
                    offeredTemplates.shouldBeEmpty()
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(UUID.randomUUID())

                then("none are returned, without reading the database or Epistola") {
                    offeredTemplates.shouldBeEmpty()
                    verify(exactly = 0) {
                        zaaktypeConfigurationService.readZaaktypeConfiguration(any())
                        epistolaClientService.listTemplates(any())
                    }
                }
            }
        }
    }

    context("reading the catalog a zaaktype offers its templates from") {
        given("a zaaktype that offers the templates of a catalog it chose") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = informatieObjectTypeUuid
                )

            `when`("the offered catalog is read") {
                val offeredCatalog = epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)

                then("it is that catalog, with the informatieobjecttype its documents are stored under") {
                    offeredCatalog shouldBe OfferedEpistolaCatalog(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUuid = informatieObjectTypeUuid
                    )
                }

                and("Epistola is not asked whether the catalog holds a template, since reading the template tells") {
                    verify(exactly = 0) { epistolaClientService.listTemplates(any()) }
                }
            }
        }

        given("a zaaktype for which the beheerder chose a language") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = UUID.randomUUID(),
                    locale = "en-GB"
                )

            `when`("the offered catalog is read") {
                val offeredCatalog = epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)

                then("it names the language, for the documents of the zaaktype to be generated in") {
                    offeredCatalog.locale shouldBe "en-GB"
                }
            }
        }

        given("a zaaktype for which the beheerder has not chosen a catalog") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.defaultCatalogId } returns FAKE_DEFAULT_CATALOG_ID
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = null,
                    informatieObjectTypeUuid = informatieObjectTypeUuid
                )

            `when`("the offered catalog is read") {
                val offeredCatalog = epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)

                then("it is the catalog of ZAC's settings") {
                    offeredCatalog.catalogId shouldBe FAKE_DEFAULT_CATALOG_ID
                }
            }
        }

        given("a zaaktype for which the beheerder switched Epistola off") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = UUID.randomUUID(),
                    isEpistolaEnabled = false
                )

            `when`("the offered catalog is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)
                }

                then("it is refused, so a behandelaar can only generate what the beheerder offers") {
                    epistolaTemplateNotConfiguredException.errorCode shouldBe
                        ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_CONFIGURED
                    epistolaTemplateNotConfiguredException.message shouldBe
                        "Zaaktype '$zaaktypeUuid' offers no Epistola templates."
                }
            }
        }

        given("a zaaktype without an informatieobjecttype for its Epistola documents") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(zaaktypeUuid = zaaktypeUuid, catalogId = "fake-catalog", informatieObjectTypeUuid = null)

            `when`("the offered catalog is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)
                }

                then("it is refused, because the document could not be stored") {
                    epistolaTemplateNotConfiguredException.message shouldBe
                        "Zaaktype '$zaaktypeUuid' offers no Epistola templates."
                }
            }
        }

        given("a zaaktype that has never been configured") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns null

            `when`("the offered catalog is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)
                }

                then("it is refused") {
                    epistolaTemplateNotConfiguredException.message shouldBe
                        "Zaaktype '$zaaktypeUuid' offers no Epistola templates."
                }
            }
        }

        given("a BPMN zaaktype, while Epistola is the active provider") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                createZaaktypeBpmnConfiguration(zaaktypeUUID = zaaktypeUuid).apply { isEpistolaEnabled = true }

            `when`("the offered catalog is read") {
                val epistolaCmmnOnlyException = shouldThrow<EpistolaCmmnOnlyException> {
                    epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)
                }

                then("it is refused with a message that says Epistola is limited to CMMN") {
                    epistolaCmmnOnlyException.errorCode shouldBe ErrorCode.ERROR_CODE_EPISTOLA_CMMN_ONLY
                    epistolaCmmnOnlyException.message shouldBe
                        "Creating a document with Epistola is limited to zaken with a CMMN zaaktype; " +
                        "zaaktype '$zaaktypeUuid' is not a CMMN zaaktype."
                }
            }
        }

        given("a BPMN zaaktype, while SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(any()) } returns createZaaktypeBpmnConfiguration()

            `when`("the offered catalog is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readOfferedCatalog(UUID.randomUUID())
                }

                then("it is refused as not configured, since the CMMN limit is Epistola's and Epistola is not in use") {
                    epistolaTemplateNotConfiguredException.errorCode shouldBe
                        ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_CONFIGURED
                }
            }
        }

        given("SmartDocuments is the active provider") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = UUID.randomUUID()
                )

            `when`("the offered catalog is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)
                }

                then("it is refused, while the stored mapping is kept for when Epistola is the provider again") {
                    epistolaTemplateNotConfiguredException.message shouldBe
                        "Zaaktype '$zaaktypeUuid' offers no Epistola templates."
                }
            }
        }
    }
})
