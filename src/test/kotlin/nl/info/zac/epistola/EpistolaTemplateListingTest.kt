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
import jakarta.ws.rs.ProcessingException
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createEpistolaVariant
import nl.info.client.epistola.model.createGenerationTemplate
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.rest.RestEpistolaTemplate
import nl.info.zac.epistola.rest.RestEpistolaVariantAttribute
import nl.info.zac.epistola.rest.createRestEpistolaVariant
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE

class EpistolaTemplateListingTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val epistolaTemplatesService = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        zaaktypeConfigurationService = mockk(),
        zaaktypeCmmnConfigurationBeheerService = mockk(),
        ztcClientService = mockk(),
        documentCreationProviderConfiguration = documentCreationProviderConfiguration
    )

    afterEach { checkUnnecessaryStub() }

    fun givenActiveProvider(documentCreationProvider: DocumentCreationProvider) {
        every { documentCreationProviderConfiguration.activeProvider } returns documentCreationProvider
    }

    context("listing the templates of a catalog") {
        fun localesOf(vararg locales: String) = EpistolaLocales(
            kanalenByLocale = locales.associateWith { EpistolaKanalen() }
        )

        given("Epistola is the active provider and the catalog holds three templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "verlenging beslistermijn"),
                createTemplateSummary(id = "fake-template-2", name = "Besluit evenementenvergunning"),
                createTemplateSummary(id = "fake-template-3", name = "Ontvangstbevestiging")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-1") } returns
                createGenerationTemplate(
                    locales = localesOf("nl-NL", "en-GB"),
                    kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"))
                )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-2") } returns
                createGenerationTemplate(locales = localesOf("nl-NL"), kanalen = EpistolaKanalen(kanalen = listOf("post")))
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-3") } returns
                createGenerationTemplate()

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("all three are returned, ordered by name ignoring case, with the languages and kanalen Epistola gave them") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-2",
                            name = "Besluit evenementenvergunning",
                            locales = listOf("nl-NL"),
                            kanalen = listOf("post"),
                            variants = emptyList()
                        ),
                        RestEpistolaTemplate(
                            id = "fake-template-3",
                            name = "Ontvangstbevestiging",
                            locales = emptyList(),
                            kanalen = emptyList(),
                            variants = emptyList()
                        ),
                        RestEpistolaTemplate(
                            id = "fake-template-1",
                            name = "verlenging beslistermijn",
                            locales = listOf("en-GB", "nl-NL"),
                            kanalen = listOf("post", "digitaal"),
                            variants = emptyList()
                        )
                    )
                }
            }
        }

        given("a template with two variants, and one without") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "Aanvullende informatie"),
                createTemplateSummary(id = "fake-template-2", name = "Zonder varianten")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-1") } returns
                createGenerationTemplate(
                    variants = listOf(
                        createEpistolaVariant(
                            id = "fake-initial",
                            title = "Initial",
                            isDefault = true,
                            attributes = mapOf("locale" to "nl-NL", "kanaal" to "post")
                        ),
                        createEpistolaVariant(
                            id = "fake-large-print",
                            title = "Groot lettertype",
                            attributes = mapOf("locale" to "nl-NL", "kanaal" to "post", "weergave" to "groot")
                        )
                    )
                )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-2") } returns
                createGenerationTemplate()

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("each variant is listed with its title, default flag and attributes in order, and the other has an empty list") {
                    templates.map { it.id to it.variants } shouldBe listOf(
                        "fake-template-1" to listOf(
                            createRestEpistolaVariant(
                                id = "fake-initial",
                                title = "Initial",
                                isDefault = true,
                                attributes = listOf(
                                    RestEpistolaVariantAttribute(key = "locale", value = "nl-NL"),
                                    RestEpistolaVariantAttribute(key = "kanaal", value = "post")
                                )
                            ),
                            createRestEpistolaVariant(
                                id = "fake-large-print",
                                title = "Groot lettertype",
                                attributes = listOf(
                                    RestEpistolaVariantAttribute(key = "locale", value = "nl-NL"),
                                    RestEpistolaVariantAttribute(key = "kanaal", value = "post"),
                                    RestEpistolaVariantAttribute(key = "weergave", value = "groot")
                                )
                            )
                        ),
                        "fake-template-2" to emptyList()
                    )
                }
            }
        }

        given("an Epistola server that sends a template's slug next to its deprecated id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-deprecated-id", slug = "fake-template-slug", name = "fakeName")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-slug") } returns
                createGenerationTemplate(locales = localesOf("nl-NL"))

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("the template is identified, and read, by its slug") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-slug",
                            name = "fakeName",
                            locales = listOf("nl-NL"),
                            kanalen = emptyList(),
                            variants = emptyList()
                        )
                    )
                }
            }
        }

        given("an Epistola server from before contract 1.3.0, which sends only a template's id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-id", slug = null, name = "fakeName")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-id") } returns
                createGenerationTemplate()

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("the template is identified by its id") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-id",
                            name = "fakeName",
                            locales = emptyList(),
                            kanalen = emptyList(),
                            variants = emptyList()
                        )
                    )
                }
            }
        }

        given("a catalog without templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns emptyList()

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("none are returned") {
                    templates.shouldBeEmpty()
                }
            }
        }

        given("an Epistola that stops answering after the listing") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "Eerste"),
                createTemplateSummary(id = "fake-template-2", name = "Tweede"),
                createTemplateSummary(id = "fake-template-3", name = "Derde")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-3") } returns
                createGenerationTemplate(locales = localesOf("nl-NL"))
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-1") } throws
                EpistolaRequestFailedException(
                    errorCode = ERROR_CODE_EPISTOLA_UNAVAILABLE,
                    message = "fakeMessage",
                    cause = ProcessingException("fakeCause")
                )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("every template is still listed by name, those not read have no details, and the rest are not asked for") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-3",
                            name = "Derde",
                            locales = listOf("nl-NL"),
                            kanalen = emptyList(),
                            variants = emptyList()
                        ),
                        RestEpistolaTemplate(id = "fake-template-1", name = "Eerste"),
                        RestEpistolaTemplate(id = "fake-template-2", name = "Tweede")
                    )
                    verify(exactly = 0) { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-2") }
                }
            }
        }

        given("an Epistola that refuses ZAC access to a template") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1")
            )
            every { epistolaClientService.readGenerationTemplate("fake-catalog", "fake-template-1") } throws
                EpistolaRequestFailedException(
                    errorCode = ERROR_CODE_EPISTOLA_ACCESS_DENIED,
                    message = "fakeMessage",
                    cause = ProcessingException("fakeCause")
                )

            `when`("the templates are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    epistolaTemplatesService.listTemplates("fake-catalog")
                }

                then("the failure reaches the caller, as it says something the beheerder needs to see") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_ACCESS_DENIED
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
})
