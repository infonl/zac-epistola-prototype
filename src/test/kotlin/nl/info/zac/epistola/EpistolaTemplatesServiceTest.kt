/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola

import app.epistola.client.jakarta.model.CatalogDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.ws.rs.ProcessingException
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createCatalog
import nl.info.client.epistola.model.createGenerationTemplate
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createZaakType
import nl.info.zac.admin.ZaaktypeCmmnConfigurationBeheerService
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.exception.ZaaktypeConfigurationNotFoundException
import nl.info.zac.admin.model.ZaaktypeCmmnConfiguration
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.epistola.rest.RestEpistolaCatalog
import nl.info.zac.epistola.rest.RestEpistolaCatalogMapping
import nl.info.zac.epistola.rest.RestEpistolaTemplate
import nl.info.zac.epistola.rest.createRestEpistolaCatalogMapping
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.net.URI
import java.util.UUID

private const val FAKE_DEFAULT_CATALOG_ID = "fake-default-catalog"

class EpistolaTemplatesServiceTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val zaaktypeCmmnConfigurationBeheerService = mockk<ZaaktypeCmmnConfigurationBeheerService>()
    val ztcClientService = mockk<ZtcClientService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val epistolaTemplatesService = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        zaaktypeCmmnConfigurationBeheerService = zaaktypeCmmnConfigurationBeheerService,
        ztcClientService = ztcClientService,
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

    context("listing catalogs") {
        given("Epistola is the active provider and the tenant has two catalogs of its own and Epistola's") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listCatalogs() } returns listOf(
                createCatalog(slug = "vergunningen", name = "vergunningen"),
                createCatalog(slug = "system", name = "System", type = CatalogDto.TypeEnum.SUBSCRIBED),
                createCatalog(slug = "brieven", name = "Brieven", type = CatalogDto.TypeEnum.SUBSCRIBED)
            )

            `when`("the catalogs are listed") {
                val catalogs = epistolaTemplatesService.listCatalogs()

                then(
                    "the tenant's own and subscribed catalogs are returned ordered by name ignoring case, " +
                        "without Epistola's, which holds no templates"
                ) {
                    catalogs shouldBe listOf(
                        RestEpistolaCatalog(id = "brieven", name = "Brieven"),
                        RestEpistolaCatalog(id = "vergunningen", name = "vergunningen")
                    )
                }
            }
        }

        given("an Epistola server from before contract 1.3.0, which sends only a catalog's id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listCatalogs() } returns listOf(
                createCatalog(slug = null, id = "fake-catalog-id", name = "fakeCatalogName")
            )

            `when`("the catalogs are listed") {
                val catalogs = epistolaTemplatesService.listCatalogs()

                then("the catalog is identified by its id") {
                    catalogs shouldBe listOf(RestEpistolaCatalog(id = "fake-catalog-id", name = "fakeCatalogName"))
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the catalogs are listed") {
                val catalogs = epistolaTemplatesService.listCatalogs()

                then("none are returned and Epistola is not called, because its settings are not validated") {
                    catalogs.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listCatalogs() }
                }
            }
        }
    }

    context("listing the templates of a catalog") {
        given("Epistola is the active provider and the catalog holds three templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "verlenging beslistermijn"),
                createTemplateSummary(id = "fake-template-2", name = "Besluit evenementenvergunning"),
                createTemplateSummary(id = "fake-template-3", name = "Ontvangstbevestiging")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("all three are returned, ordered by name ignoring case") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(id = "fake-template-2", name = "Besluit evenementenvergunning"),
                        RestEpistolaTemplate(id = "fake-template-3", name = "Ontvangstbevestiging"),
                        RestEpistolaTemplate(id = "fake-template-1", name = "verlenging beslistermijn")
                    )
                }
            }
        }

        given("an Epistola server that sends a template's slug next to its deprecated id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-deprecated-id", slug = "fake-template-slug", name = "fakeName")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("the template is identified by its slug") {
                    templates shouldBe listOf(RestEpistolaTemplate(id = "fake-template-slug", name = "fakeName"))
                }
            }
        }

        given("an Epistola server from before contract 1.3.0, which sends only a template's id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-id", slug = null, name = "fakeName")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("the template is identified by its id") {
                    templates shouldBe listOf(RestEpistolaTemplate(id = "fake-template-id", name = "fakeName"))
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("none are returned and Epistola is not called, because its settings are not validated") {
                    templates.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates(any()) }
                }
            }
        }
    }

    context("listing the languages of a catalog") {
        fun givenTemplatesWithLocales(catalogId: String, localesByTemplateId: Map<String, EpistolaLocales>) {
            every { epistolaClientService.listTemplates(catalogId) } returns
                localesByTemplateId.keys.map { createTemplateSummary(id = it, slug = it) }
            localesByTemplateId.forEach { (templateId, locales) ->
                every { epistolaClientService.readGenerationTemplate(catalogId, templateId) } returns
                    createGenerationTemplate(locales = locales)
            }
        }

        fun localesOf(vararg locales: String) = EpistolaLocales(
            kanalenByLocale = locales.associateWith { EpistolaKanalen() }
        )

        given("a catalog whose templates all offer Dutch and English") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(
                catalogId = "fake-catalog",
                localesByTemplateId = mapOf(
                    "fake-template-1" to localesOf("nl-NL", "en-GB"),
                    "fake-template-2" to localesOf("en-GB", "nl-NL")
                )
            )

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("both are returned, in alphabetical order") {
                    locales shouldBe listOf("en-GB", "nl-NL")
                }
            }
        }

        given("a catalog with a template that has no English") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(
                catalogId = "fake-catalog",
                localesByTemplateId = mapOf(
                    "fake-template-1" to localesOf("nl-NL", "en-GB"),
                    "fake-template-2" to localesOf("nl-NL"),
                    "fake-template-3" to localesOf("nl-NL", "en-GB", "de-DE")
                )
            )

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("English is not offered, because a zaak would get a document without it from that template") {
                    locales shouldBe listOf("nl-NL")
                }
            }
        }

        given("a catalog whose templates share no language") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(
                catalogId = "fake-catalog",
                localesByTemplateId = mapOf(
                    "fake-template-1" to localesOf("nl-NL"),
                    "fake-template-2" to localesOf("en-GB")
                )
            )

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("none is offered") {
                    locales.shouldBeEmpty()
                }
            }
        }

        given("a catalog with a template whose variants carry no language") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(
                catalogId = "fake-catalog",
                localesByTemplateId = mapOf(
                    "fake-template-1" to localesOf("nl-NL", "en-GB"),
                    "fake-template-2" to EpistolaLocales()
                )
            )

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("that template takes no part, as it is generated without a language") {
                    locales shouldBe listOf("en-GB", "nl-NL")
                }
            }
        }

        given("a catalog whose templates carry no language at all") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(
                catalogId = "fake-catalog",
                localesByTemplateId = mapOf(
                    "fake-template-1" to EpistolaLocales(),
                    "fake-template-2" to EpistolaLocales()
                )
            )

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("none is offered") {
                    locales.shouldBeEmpty()
                }
            }
        }

        given("a catalog without templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            givenTemplatesWithLocales(catalogId = "fake-catalog", localesByTemplateId = emptyMap())

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("none is offered") {
                    locales.shouldBeEmpty()
                }
            }
        }

        given("a catalog whose template has a slug next to its deprecated id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-deprecated-id", slug = "fake-template-slug"))
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-slug") } returns
                createGenerationTemplate(locales = localesOf("nl-NL"))

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("the template is read by its slug, as Epistola identifies it") {
                    locales shouldBe listOf("nl-NL")
                }
            }
        }

        given("an Epistola that cannot be reached") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } throws EpistolaRequestFailedException(
                errorCode = ERROR_CODE_EPISTOLA_UNAVAILABLE,
                message = "fakeMessage",
                cause = ProcessingException("fakeCause")
            )

            `when`("the languages are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaTemplatesService.listCatalogLocales("fake-catalog")
                }

                then("the failure reaches the caller, as it does when the templates are listed") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the languages are listed") {
                val locales = epistolaTemplatesService.listCatalogLocales("fake-catalog")

                then("none are returned and Epistola is not called, because its settings are not validated") {
                    locales.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates(any()) }
                }
            }
        }
    }

    context("reading the catalog mapping of a zaaktype") {
        given("a zaaktype for which the beheerder chose a catalog, a language and an informatieobjecttype") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = informatieObjectTypeUuid,
                    locale = "en-GB"
                )

            `when`("the mapping is read") {
                val catalogMapping = epistolaTemplatesService.readCatalogMapping(zaaktypeUuid)

                then("the language is returned with the catalog and the informatieobjecttype") {
                    catalogMapping shouldBe RestEpistolaCatalogMapping(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUUID = informatieObjectTypeUuid,
                        locale = "en-GB"
                    )
                }
            }
        }

        given("a zaaktype for which the beheerder chose a catalog and an informatieobjecttype") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
                offeringZaaktypeConfiguration(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogId = "fake-catalog",
                    informatieObjectTypeUuid = informatieObjectTypeUuid
                )

            `when`("the mapping is read") {
                val catalogMapping = epistolaTemplatesService.readCatalogMapping(zaaktypeUuid)

                then("both are returned") {
                    catalogMapping shouldBe RestEpistolaCatalogMapping(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUUID = informatieObjectTypeUuid,
                        locale = null
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

            `when`("the mapping is read") {
                val catalogMapping = epistolaTemplatesService.readCatalogMapping(zaaktypeUuid)

                then("the catalog is the one of ZAC's settings, which its templates come from") {
                    catalogMapping shouldBe RestEpistolaCatalogMapping(
                        catalogId = FAKE_DEFAULT_CATALOG_ID,
                        informatieObjectTypeUUID = informatieObjectTypeUuid,
                        locale = null
                    )
                }
            }
        }

        given("a zaaktype that has never been configured") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.defaultCatalogId } returns FAKE_DEFAULT_CATALOG_ID
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns null

            `when`("the mapping is read") {
                val catalogMapping = epistolaTemplatesService.readCatalogMapping(zaaktypeUuid)

                then("it names the catalog of ZAC's settings and no informatieobjecttype") {
                    catalogMapping shouldBe RestEpistolaCatalogMapping(
                        catalogId = FAKE_DEFAULT_CATALOG_ID,
                        informatieObjectTypeUUID = null,
                        locale = null
                    )
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the mapping is read") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.readCatalogMapping(UUID.randomUUID())
                }

                then("it is refused, since there is no catalog of ZAC's settings to name") {
                    epistolaTemplateMappingException.message shouldBe "Epistola is not the active document creation provider."
                }
            }
        }
    }

    context("storing the catalog mapping of a zaaktype") {
        given("a catalog Epistola has and an informatieobjecttype of the zaaktype") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            val storedConfigurationSlot = slot<ZaaktypeCmmnConfiguration>()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every {
                zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid)
            } returns zaaktypeCmmnConfiguration
            every { epistolaClientService.listCatalogs() } returns listOf(createCatalog(slug = "fake-catalog"))
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid"))
            )
            every {
                zaaktypeCmmnConfigurationBeheerService.storeZaaktypeCmmnConfiguration(capture(storedConfigurationSlot))
            } returns zaaktypeCmmnConfiguration

            `when`("the mapping is stored") {
                epistolaTemplatesService.storeCatalogMapping(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogMapping = createRestEpistolaCatalogMapping(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUUID = informatieObjectTypeUuid,
                        locale = "en-GB"
                    )
                )

                then("the zaaktype configuration is stored with the catalog, the language and the informatieobjecttype") {
                    with(storedConfigurationSlot.captured) {
                        this shouldBeSameInstanceAs zaaktypeCmmnConfiguration
                        epistolaCatalogId shouldBe "fake-catalog"
                        epistolaLocale shouldBe "en-GB"
                        epistolaInformatieobjecttypeUuid shouldBe informatieObjectTypeUuid
                    }
                }
            }

            `when`("the mapping is stored without a language") {
                zaaktypeCmmnConfiguration.epistolaLocale = "en-GB"
                epistolaTemplatesService.storeCatalogMapping(
                    zaaktypeUuid = zaaktypeUuid,
                    catalogMapping = createRestEpistolaCatalogMapping(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUUID = informatieObjectTypeUuid,
                        locale = ""
                    )
                )

                then("a language chosen earlier is cleared") {
                    storedConfigurationSlot.captured.epistolaLocale shouldBe null
                }
            }
        }

        given("a catalog Epistola does not have") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every {
                zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid)
            } returns createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            every { epistolaClientService.listCatalogs() } returns listOf(createCatalog(slug = "fake-catalog"))
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid"))
            )

            `when`("the mapping is stored") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeCatalogMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        catalogMapping = createRestEpistolaCatalogMapping(
                            catalogId = "fake-unknown-catalog",
                            informatieObjectTypeUUID = informatieObjectTypeUuid
                        )
                    )
                }

                then("it is rejected and the stored mapping is left alone") {
                    epistolaTemplateMappingException.message shouldBe "Unknown Epistola catalog: 'fake-unknown-catalog'"
                    verify(exactly = 0) { zaaktypeCmmnConfigurationBeheerService.storeZaaktypeCmmnConfiguration(any()) }
                }
            }
        }

        given("Epistola's own catalog") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every {
                zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid)
            } returns createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            every { epistolaClientService.listCatalogs() } returns listOf(
                createCatalog(slug = "system", type = CatalogDto.TypeEnum.SUBSCRIBED)
            )
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid"))
            )

            `when`("it is stored as the catalog of a zaaktype") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeCatalogMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        catalogMapping = createRestEpistolaCatalogMapping(
                            catalogId = "system",
                            informatieObjectTypeUUID = informatieObjectTypeUuid
                        )
                    )
                }

                then("it is rejected, as ZAC does not offer it") {
                    epistolaTemplateMappingException.message shouldBe "Unknown Epistola catalog: 'system'"
                }
            }
        }

        given("an informatieobjecttype that is not one of the zaaktype's") {
            val zaaktypeUuid = UUID.randomUUID()
            val otherInformatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every {
                zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid)
            } returns createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            every { epistolaClientService.listCatalogs() } returns listOf(createCatalog(slug = "fake-catalog"))
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/${UUID.randomUUID()}"))
            )

            `when`("the mapping is stored") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeCatalogMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        catalogMapping = createRestEpistolaCatalogMapping(
                            catalogId = "fake-catalog",
                            informatieObjectTypeUUID = otherInformatieObjectTypeUuid
                        )
                    )
                }

                then("it is rejected and the stored mapping is left alone") {
                    epistolaTemplateMappingException.message shouldBe
                        "Informatieobjecttype '$otherInformatieObjectTypeUuid' is not one of the zaaktype's."
                    verify(exactly = 0) { zaaktypeCmmnConfigurationBeheerService.storeZaaktypeCmmnConfiguration(any()) }
                }
            }
        }

        given("a zaaktype that has no CMMN configuration") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid) } returns null

            `when`("a mapping is stored") {
                val zaaktypeConfigurationNotFoundException = shouldThrow<ZaaktypeConfigurationNotFoundException> {
                    epistolaTemplatesService.storeCatalogMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        catalogMapping = createRestEpistolaCatalogMapping()
                    )
                }

                then("it is rejected, because the mapping needs the zaaktype configuration to belong to") {
                    zaaktypeConfigurationNotFoundException.message shouldBe
                        "No CMMN zaaktype configuration found for zaaktype UUID '$zaaktypeUuid'"
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("a mapping is stored") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeCatalogMapping(
                        zaaktypeUuid = UUID.randomUUID(),
                        catalogMapping = createRestEpistolaCatalogMapping()
                    )
                }

                then("it is rejected, so a mapping kept for a later switch to Epistola is left alone") {
                    epistolaTemplateMappingException.message shouldBe "Epistola is not the active document creation provider."
                    verify(exactly = 0) { zaaktypeCmmnConfigurationBeheerService.storeZaaktypeCmmnConfiguration(any()) }
                }
            }
        }
    }
})
