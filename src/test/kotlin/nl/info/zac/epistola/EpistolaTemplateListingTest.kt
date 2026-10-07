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
import nl.info.client.epistola.model.createGenerationTemplate
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.rest.RestEpistolaTemplate
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
        given("Epistola is the active provider and the catalog holds three templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates("fake-catalog") } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "verlenging beslistermijn"),
                createTemplateSummary(id = "fake-template-2", name = "Besluit evenementenvergunning"),
                createTemplateSummary(id = "fake-template-3", name = "Ontvangstbevestiging")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("all three are returned, ordered by name ignoring case, without reading each template") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-2",
                            name = "Besluit evenementenvergunning"
                        ),
                        RestEpistolaTemplate(
                            id = "fake-template-3",
                            name = "Ontvangstbevestiging"
                        ),
                        RestEpistolaTemplate(
                            id = "fake-template-1",
                            name = "verlenging beslistermijn"
                        )
                    )
                    verify(exactly = 0) { epistolaClientService.readGenerationTemplate(any(), any()) }
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
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-slug",
                            name = "fakeName"
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

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates("fake-catalog")

                then("the template is identified by its id") {
                    templates shouldBe listOf(
                        RestEpistolaTemplate(
                            id = "fake-template-id",
                            name = "fakeName"
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
