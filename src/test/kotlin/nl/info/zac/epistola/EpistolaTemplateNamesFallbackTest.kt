/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.rest.RestMappedEpistolaTemplate
import nl.info.zac.epistola.rest.RestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.rest.createRestMappedEpistolaTemplate
import nl.info.zac.epistola.rest.createRestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.templates.EpistolaTemplateGroupRepository
import nl.info.zac.epistola.templates.model.createEpistolaTemplateGroup
import nl.info.zac.exception.ErrorCode
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.util.UUID

class EpistolaTemplateNamesFallbackTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val epistolaTemplateGroupRepository = mockk<EpistolaTemplateGroupRepository>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val ztcClientService = mockk<ZtcClientService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val zaaktypeUuid = UUID.randomUUID()
    val informatieObjectTypeUuid = UUID.randomUUID()

    afterEach { checkUnnecessaryStub() }

    fun newEpistolaTemplatesService() = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        epistolaTemplateGroupRepository = epistolaTemplateGroupRepository,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        ztcClientService = ztcClientService,
        documentCreationProviderConfiguration = documentCreationProviderConfiguration
    )

    fun epistolaFailure(errorCode: ErrorCode) = EpistolaRequestFailedException(
        errorCode = errorCode,
        message = "fakeEpistolaFailure",
        cause = RuntimeException("fakeCause")
    )

    fun givenStoredTemplates(vararg templateIds: String) {
        val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
        every { documentCreationProviderConfiguration.activeProvider } returns DocumentCreationProvider.EPISTOLA
        every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
            zaaktypeCmmnConfiguration
        every { epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration) } returns listOf(
            createEpistolaTemplateGroup(
                name = "Vergunningen",
                zaaktypeConfiguration = zaaktypeCmmnConfiguration,
                templateIdsToInformatieObjectTypeUuids = templateIds.associateWith { informatieObjectTypeUuid }
            )
        )
    }

    fun mappedTemplate(id: String, name: String) = RestMappedEpistolaTemplate(
        id = id,
        name = name,
        informatieObjectTypeUUID = informatieObjectTypeUuid
    )

    context("reading the template mapping while Epistola cannot be reached") {
        given("a mapping that was read while Epistola answered, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.readTemplateMapping(zaaktypeUuid)

            `when`("the mapping is read again") {
                val templateMapping = service.readTemplateMapping(zaaktypeUuid)

                then("the template is still listed by the name Epistola gave it last") {
                    templateMapping shouldBe listOf(
                        RestMappedEpistolaTemplateGroup(
                            name = "Vergunningen",
                            templates = listOf(mappedTemplate("fake-template-1", "Besluit evenementenvergunning"))
                        )
                    )
                }
            }
        }

        given("a template that was renamed in Epistola after its name was remembered") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Old name")) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "New name"))
            service.readTemplateMapping(zaaktypeUuid)

            `when`("the mapping is read while Epistola answers") {
                val templateMapping = service.readTemplateMapping(zaaktypeUuid)

                then("Epistola's live name replaces the remembered one") {
                    templateMapping.single().templates shouldBe listOf(mappedTemplate("fake-template-1", "New name"))
                }
            }
        }

        given("a template that was renamed, read live, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Old name")) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "New name")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.readTemplateMapping(zaaktypeUuid)
            service.readTemplateMapping(zaaktypeUuid)

            `when`("the mapping is read") {
                val templateMapping = service.readTemplateMapping(zaaktypeUuid)

                then("the newest name is listed, not the first one that was remembered") {
                    templateMapping.single().templates shouldBe listOf(mappedTemplate("fake-template-1", "New name"))
                }
            }
        }

        given("a template that Epistola dropped from its catalog after its name was remembered") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1", "fake-template-2")
            every { epistolaClientService.listTemplates() } returns
                listOf(
                    createTemplateSummary(id = "fake-template-1", name = "Kept"),
                    createTemplateSummary(id = "fake-template-2", name = "Dropped")
                ) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "Kept")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.readTemplateMapping(zaaktypeUuid)
            service.readTemplateMapping(zaaktypeUuid)

            `when`("the mapping is read while Epistola cannot be reached") {
                val templateMapping = service.readTemplateMapping(zaaktypeUuid)

                then("the dropped template does not come back from the remembered names") {
                    templateMapping.single().templates shouldBe listOf(mappedTemplate("fake-template-1", "Kept"))
                }
            }
        }

        given("Epistola cannot be reached and no name was read since ZAC started") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } throws epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)

            `when`("the mapping is read") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.readTemplateMapping(zaaktypeUuid)
                }

                then("the failure is passed on, so the caller can tell the user why nothing is listed") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }

        given("names that were remembered, and Epistola then refuses ZAC access") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_ACCESS_DENIED)
            service.readTemplateMapping(zaaktypeUuid)

            `when`("the mapping is read") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.readTemplateMapping(zaaktypeUuid)
                }

                then("the refusal is passed on, since only an unreachable Epistola falls back to remembered names") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_ACCESS_DENIED
                }
            }
        }
    }

    context("storing the template mapping while Epistola cannot be reached") {
        given("names that were remembered, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenStoredTemplates("fake-template-1")
            every { epistolaClientService.listTemplates() } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.readTemplateMapping(zaaktypeUuid)

            `when`("a mapping is stored") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.storeTemplateMapping(
                        zaaktypeUuid,
                        listOf(
                            createRestMappedEpistolaTemplateGroup(
                                templates = listOf(createRestMappedEpistolaTemplate(id = "fake-template-1"))
                            )
                        )
                    )
                }

                then("it fails, since a save is checked against Epistola's live list and never the remembered names") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }
    }
})
