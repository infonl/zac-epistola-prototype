/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola

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
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createZaakType
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.exception.ZaaktypeConfigurationNotFoundException
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.rest.RestEpistolaTemplate
import nl.info.zac.epistola.rest.RestMappedEpistolaTemplate
import nl.info.zac.epistola.rest.RestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.rest.createRestMappedEpistolaTemplate
import nl.info.zac.epistola.rest.createRestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.templates.EpistolaTemplateGroupRepository
import nl.info.zac.epistola.templates.model.EpistolaTemplateGroup
import nl.info.zac.epistola.templates.model.createEpistolaTemplateGroup
import java.net.URI
import java.util.UUID

class EpistolaTemplatesServiceTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val epistolaTemplateGroupRepository = mockk<EpistolaTemplateGroupRepository>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val ztcClientService = mockk<ZtcClientService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val epistolaTemplatesService = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        epistolaTemplateGroupRepository = epistolaTemplateGroupRepository,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        ztcClientService = ztcClientService,
        documentCreationProviderConfiguration = documentCreationProviderConfiguration
    )

    afterEach { checkUnnecessaryStub() }

    fun givenActiveProvider(documentCreationProvider: DocumentCreationProvider) {
        every { documentCreationProviderConfiguration.activeProvider } returns documentCreationProvider
    }

    context("listing templates") {
        given("Epistola is the active provider and its catalog holds three templates") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates() } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "verlenging beslistermijn"),
                createTemplateSummary(id = "fake-template-2", name = "Besluit evenementenvergunning"),
                createTemplateSummary(id = "fake-template-3", name = "Ontvangstbevestiging")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates()

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
            every { epistolaClientService.listTemplates() } returns listOf(
                createTemplateSummary(id = "fake-deprecated-id", slug = "fake-template-slug", name = "fakeName")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates()

                then("the template is identified by its slug") {
                    templates shouldBe listOf(RestEpistolaTemplate(id = "fake-template-slug", name = "fakeName"))
                }
            }
        }

        given("an Epistola server from before contract 1.3.0, which sends only a template's id") {
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { epistolaClientService.listTemplates() } returns listOf(
                createTemplateSummary(id = "fake-template-id", slug = null, name = "fakeName")
            )

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates()

                then("the template is identified by its id") {
                    templates shouldBe listOf(RestEpistolaTemplate(id = "fake-template-id", name = "fakeName"))
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the templates are listed") {
                val templates = epistolaTemplatesService.listTemplates()

                then("none are returned and Epistola is not called, because its settings are not validated") {
                    templates.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates() }
                }
            }
        }
    }

    context("reading the template mapping of a zaaktype") {
        given("a zaaktype with a stored group holding one template Epistola has and one it no longer has") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns listOf(
                createEpistolaTemplateGroup(
                    name = "Vergunningen",
                    zaaktypeConfiguration = zaaktypeCmmnConfiguration,
                    templateIdsToInformatieObjectTypeUuids = mapOf(
                        "fake-template-1" to informatieObjectTypeUuid,
                        "fake-removed-template" to UUID.randomUUID()
                    )
                )
            )
            every { epistolaClientService.listTemplates() } returns listOf(
                createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")
            )

            `when`("the mapping is read") {
                val templateMapping = epistolaTemplatesService.readTemplateMapping(zaaktypeUuid)

                then("the group is returned with the template's current Epistola name, and without the removed one") {
                    templateMapping shouldBe listOf(
                        RestMappedEpistolaTemplateGroup(
                            name = "Vergunningen",
                            templates = listOf(
                                RestMappedEpistolaTemplate(
                                    id = "fake-template-1",
                                    name = "Besluit evenementenvergunning",
                                    informatieObjectTypeUUID = informatieObjectTypeUuid
                                )
                            )
                        )
                    )
                }
            }
        }

        given("a zaaktype with two stored groups") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns listOf(
                createEpistolaTemplateGroup(name = "vergunningen", templateIdsToInformatieObjectTypeUuids = emptyMap()),
                createEpistolaTemplateGroup(name = "Handhaving", templateIdsToInformatieObjectTypeUuids = emptyMap())
            )
            every { epistolaClientService.listTemplates() } returns emptyList()

            `when`("the mapping is read") {
                val templateMapping = epistolaTemplatesService.readTemplateMapping(zaaktypeUuid)

                then("the groups are ordered by name ignoring case") {
                    templateMapping.map { it.name } shouldBe listOf("Handhaving", "vergunningen")
                }
            }
        }

        given("a zaaktype configuration without stored groups") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns emptyList()

            `when`("the mapping is read") {
                val templateMapping = epistolaTemplatesService.readTemplateMapping(zaaktypeUuid)

                then("it is empty and Epistola is not asked for template names") {
                    templateMapping.shouldBeEmpty()
                    verify(exactly = 0) { epistolaClientService.listTemplates() }
                }
            }
        }

        given("a zaaktype that has never been configured") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns null

            `when`("the mapping is read") {
                val templateMapping = epistolaTemplatesService.readTemplateMapping(zaaktypeUuid)

                then("it is empty, because a zaaktype configuration is only stored on its first save") {
                    templateMapping.shouldBeEmpty()
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("the mapping is read") {
                val templateMapping = epistolaTemplatesService.readTemplateMapping(UUID.randomUUID())

                then("it is empty, without reading the database or Epistola") {
                    templateMapping.shouldBeEmpty()
                    verify(exactly = 0) {
                        zaaktypeConfigurationService.readZaaktypeConfiguration(any())
                        epistolaClientService.listTemplates()
                    }
                }
            }
        }
    }

    context("storing the template mapping of a zaaktype") {
        given("a valid mapping for a configured zaaktype") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            val templateGroupsSlot = slot<List<EpistolaTemplateGroup>>()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaClientService.listTemplates() } returns listOf(createTemplateSummary(id = "fake-template-1"))
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid"))
            )
            every {
                epistolaTemplateGroupRepository.replaceTemplateGroups(zaaktypeCmmnConfiguration, capture(templateGroupsSlot))
            } returns Unit

            `when`("the mapping is stored") {
                epistolaTemplatesService.storeTemplateMapping(
                    zaaktypeUuid = zaaktypeUuid,
                    templateGroups = listOf(
                        createRestMappedEpistolaTemplateGroup(
                            name = "Vergunningen",
                            templates = listOf(
                                createRestMappedEpistolaTemplate(
                                    id = "fake-template-1",
                                    informatieObjectTypeUUID = informatieObjectTypeUuid
                                )
                            )
                        )
                    )
                )

                then("it replaces the stored groups of that zaaktype configuration") {
                    with(templateGroupsSlot.captured.single()) {
                        name shouldBe "Vergunningen"
                        zaaktypeConfiguration shouldBeSameInstanceAs zaaktypeCmmnConfiguration
                        templates.single().epistolaId shouldBe "fake-template-1"
                        templates.single().informatieObjectTypeUUID shouldBe informatieObjectTypeUuid
                    }
                }
            }
        }

        given("a mapping with a template that Epistola does not have") {
            val zaaktypeUuid = UUID.randomUUID()
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every {
                zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
            } returns createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            every { epistolaClientService.listTemplates() } returns emptyList()
            every { ztcClientService.readZaaktype(zaaktypeUuid) } returns createZaakType(
                informatieObjectTypen = listOf(URI("https://example.com/informatieobjecttypen/$informatieObjectTypeUuid"))
            )

            `when`("the mapping is stored") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeTemplateMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        templateGroups = listOf(
                            createRestMappedEpistolaTemplateGroup(
                                templates = listOf(
                                    createRestMappedEpistolaTemplate(
                                        id = "fake-unknown-template",
                                        informatieObjectTypeUUID = informatieObjectTypeUuid
                                    )
                                )
                            )
                        )
                    )
                }

                then("it is rejected and the stored mapping is left alone") {
                    exception.message shouldBe "Unknown Epistola templates: [fake-unknown-template]"
                    verify(exactly = 0) { epistolaTemplateGroupRepository.replaceTemplateGroups(any(), any()) }
                }
            }
        }

        given("a zaaktype that has never been configured") {
            val zaaktypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns null

            `when`("a mapping is stored") {
                val exception = shouldThrow<ZaaktypeConfigurationNotFoundException> {
                    epistolaTemplatesService.storeTemplateMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        templateGroups = listOf(createRestMappedEpistolaTemplateGroup())
                    )
                }

                then("it is rejected, because the mapping needs the zaaktype configuration to belong to") {
                    exception.message shouldBe "No zaaktype configuration found for zaaktype UUID '$zaaktypeUuid'"
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)

            `when`("a mapping is stored") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    epistolaTemplatesService.storeTemplateMapping(
                        zaaktypeUuid = UUID.randomUUID(),
                        templateGroups = emptyList()
                    )
                }

                then("it is rejected, so an empty list cannot wipe a mapping kept for a later switch to Epistola") {
                    exception.message shouldBe "Epistola is not the active document creation provider."
                    verify(exactly = 0) { epistolaTemplateGroupRepository.replaceTemplateGroups(any(), any()) }
                }
            }
        }
    }

    context("copying the template mapping to a new version of a zaaktype") {
        given("a previous version of the zaaktype with a stored group") {
            val previousZaaktypeUuid = UUID.randomUUID()
            val newZaaktypeUuid = UUID.randomUUID()
            val previousZaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(
                id = 1L,
                zaaktypeUUID = previousZaaktypeUuid
            )
            val newZaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(id = 2L, zaaktypeUUID = newZaaktypeUuid)
            val informatieObjectTypeUuid = UUID.randomUUID()
            val templateGroupsSlot = slot<List<EpistolaTemplateGroup>>()
            every {
                zaaktypeConfigurationService.readZaaktypeConfiguration(previousZaaktypeUuid)
            } returns previousZaaktypeCmmnConfiguration
            every {
                zaaktypeConfigurationService.readZaaktypeConfiguration(newZaaktypeUuid)
            } returns newZaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(previousZaaktypeCmmnConfiguration) } returns listOf(
                createEpistolaTemplateGroup(
                    name = "Vergunningen",
                    zaaktypeConfiguration = previousZaaktypeCmmnConfiguration,
                    templateIdsToInformatieObjectTypeUuids = mapOf("fake-template-1" to informatieObjectTypeUuid)
                )
            )
            every {
                epistolaTemplateGroupRepository.replaceTemplateGroups(newZaaktypeCmmnConfiguration, capture(templateGroupsSlot))
            } returns Unit

            `when`("the mapping is copied") {
                epistolaTemplatesService.copyTemplateMapping(
                    previousZaaktypeUuid = previousZaaktypeUuid,
                    newZaaktypeUuid = newZaaktypeUuid
                )

                then("the new version gets the same group and template, without consulting the active provider or Epistola") {
                    with(templateGroupsSlot.captured.single()) {
                        name shouldBe "Vergunningen"
                        zaaktypeConfiguration shouldBeSameInstanceAs newZaaktypeCmmnConfiguration
                        templates.single().epistolaId shouldBe "fake-template-1"
                        templates.single().informatieObjectTypeUUID shouldBe informatieObjectTypeUuid
                    }
                    verify(exactly = 0) {
                        documentCreationProviderConfiguration.activeProvider
                        epistolaClientService.listTemplates()
                    }
                }
            }
        }

        given("a previous version without stored groups") {
            val previousZaaktypeUuid = UUID.randomUUID()
            val previousZaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = previousZaaktypeUuid)
            every {
                zaaktypeConfigurationService.readZaaktypeConfiguration(previousZaaktypeUuid)
            } returns previousZaaktypeCmmnConfiguration
            every {
                epistolaTemplateGroupRepository.listTemplateGroups(previousZaaktypeCmmnConfiguration)
            } returns emptyList()

            `when`("the mapping is copied") {
                epistolaTemplatesService.copyTemplateMapping(
                    previousZaaktypeUuid = previousZaaktypeUuid,
                    newZaaktypeUuid = UUID.randomUUID()
                )

                then("nothing is stored for the new version") {
                    verify(exactly = 0) { epistolaTemplateGroupRepository.replaceTemplateGroups(any(), any()) }
                }
            }
        }
    }

    context("reading the informatieobjecttype a template is filed under") {
        given("a zaaktype whose group offers the template under an informatieobjecttype") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
                .apply { epistolaEnabled = true }
            val informatieObjectTypeUuid = UUID.randomUUID()
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns listOf(
                createEpistolaTemplateGroup(
                    zaaktypeConfiguration = zaaktypeCmmnConfiguration,
                    templateIdsToInformatieObjectTypeUuids = mapOf("fake-other-template" to UUID.randomUUID())
                ),
                createEpistolaTemplateGroup(
                    zaaktypeConfiguration = zaaktypeCmmnConfiguration,
                    templateIdsToInformatieObjectTypeUuids = mapOf("fake-template-1" to informatieObjectTypeUuid)
                )
            )

            `when`("the informatieobjecttype of that template is read") {
                val readInformatieObjectTypeUuid = epistolaTemplatesService.readInformatieobjecttypeUuid(
                    zaaktypeUuid = zaaktypeUuid,
                    templateId = "fake-template-1"
                )

                then("the one configured with it is returned, whichever group holds it") {
                    readInformatieObjectTypeUuid shouldBe informatieObjectTypeUuid
                }
            }
        }

        given("a zaaktype whose groups do not offer the template") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
                .apply { epistolaEnabled = true }
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration
            every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns listOf(
                createEpistolaTemplateGroup(zaaktypeConfiguration = zaaktypeCmmnConfiguration)
            )

            `when`("the informatieobjecttype of an unoffered template is read") {
                val epistolaTemplateNotConfiguredException = shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readInformatieobjecttypeUuid(
                        zaaktypeUuid = zaaktypeUuid,
                        templateId = "fake-unoffered-template"
                    )
                }

                then("it is refused, so a behandelaar can only generate what the beheerder offers") {
                    epistolaTemplateNotConfiguredException.message shouldBe
                        "Epistola template 'fake-unoffered-template' is not configured for zaaktype '$zaaktypeUuid'."
                }
            }
        }

        given("a zaaktype whose group offers the template, but for which the beheerder switched Epistola off") {
            val zaaktypeUuid = UUID.randomUUID()
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
                .apply { epistolaEnabled = false }
            givenActiveProvider(DocumentCreationProvider.EPISTOLA)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns zaaktypeCmmnConfiguration

            `when`("the informatieobjecttype of that template is read") {
                shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readInformatieobjecttypeUuid(
                        zaaktypeUuid = zaaktypeUuid,
                        templateId = "fake-template-id"
                    )
                }

                then("it is refused, while the stored mapping is kept for when Epistola is switched on again") {
                    verify(exactly = 0) { epistolaTemplateGroupRepository.listTemplateGroups(any()) }
                }
            }
        }

        given("SmartDocuments is the active provider") {
            givenActiveProvider(DocumentCreationProvider.SMARTDOCUMENTS)
            every { zaaktypeConfigurationService.readZaaktypeConfiguration(any()) } returns
                createZaaktypeCmmnConfiguration().apply { epistolaEnabled = true }

            `when`("the informatieobjecttype of a template is read") {
                shouldThrow<EpistolaTemplateNotConfiguredException> {
                    epistolaTemplatesService.readInformatieobjecttypeUuid(
                        zaaktypeUuid = UUID.randomUUID(),
                        templateId = "fake-template-1"
                    )
                }

                then("it is refused without reading any stored mapping") {
                    verify(exactly = 0) { epistolaTemplateGroupRepository.listTemplateGroups(any()) }
                }
            }
        }
    }
})
