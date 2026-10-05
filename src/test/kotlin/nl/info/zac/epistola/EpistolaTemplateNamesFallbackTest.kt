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
import nl.info.client.epistola.model.createCatalog
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.admin.ZaaktypeCmmnConfigurationBeheerService
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.rest.RestOfferedEpistolaTemplate
import nl.info.zac.epistola.rest.createRestEpistolaCatalogMapping
import nl.info.zac.exception.ErrorCode
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.util.UUID

class EpistolaTemplateNamesFallbackTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val zaaktypeCmmnConfigurationBeheerService = mockk<ZaaktypeCmmnConfigurationBeheerService>()
    val ztcClientService = mockk<ZtcClientService>()
    val documentCreationProviderConfiguration = mockk<DocumentCreationProviderConfiguration>()
    val zaaktypeUuid = UUID.randomUUID()
    val otherZaaktypeUuid = UUID.randomUUID()
    val informatieObjectTypeUuid = UUID.randomUUID()

    afterEach { checkUnnecessaryStub() }

    fun newEpistolaTemplatesService() = EpistolaTemplatesService(
        epistolaClientService = epistolaClientService,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        zaaktypeCmmnConfigurationBeheerService = zaaktypeCmmnConfigurationBeheerService,
        ztcClientService = ztcClientService,
        documentCreationProviderConfiguration = documentCreationProviderConfiguration
    )

    fun epistolaFailure(errorCode: ErrorCode) = EpistolaRequestFailedException(
        errorCode = errorCode,
        message = "fakeEpistolaFailure",
        cause = RuntimeException("fakeCause")
    )

    fun givenZaaktypeOffering(zaaktypeUuid: UUID, catalogId: String) {
        every { documentCreationProviderConfiguration.activeProvider } returns DocumentCreationProvider.EPISTOLA
        every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
            createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid).apply {
                isEpistolaEnabled = true
                epistolaCatalogId = catalogId
                epistolaInformatieobjecttypeUuid = informatieObjectTypeUuid
            }
    }

    fun offeredTemplate(id: String, name: String) = RestOfferedEpistolaTemplate(
        id = id,
        name = name,
        informatieObjectTypeUUID = informatieObjectTypeUuid
    )

    context("listing a zaaktype's templates while Epistola cannot be reached") {
        given("templates that were listed while Epistola answered, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates are listed again") {
                val offeredTemplates = service.listOfferedTemplates(zaaktypeUuid)

                then("the template is still listed by the name Epistola gave it last") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-1", "Besluit evenementenvergunning"))
                }
            }
        }

        given("the names of two catalogs that were remembered, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog-1")
            givenZaaktypeOffering(otherZaaktypeUuid, catalogId = "fake-catalog-2")
            every { epistolaClientService.listTemplates("fake-catalog-1") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Uit catalog 1")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            every { epistolaClientService.listTemplates("fake-catalog-2") } returns
                listOf(createTemplateSummary(id = "fake-template-2", name = "Uit catalog 2")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listOfferedTemplates(zaaktypeUuid)
            service.listOfferedTemplates(otherZaaktypeUuid)

            `when`("the templates of each zaaktype are listed") {
                val offeredTemplates = service.listOfferedTemplates(zaaktypeUuid)
                val otherOfferedTemplates = service.listOfferedTemplates(otherZaaktypeUuid)

                then("each zaaktype lists only the templates remembered for its own catalog") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-1", "Uit catalog 1"))
                    otherOfferedTemplates shouldBe listOf(offeredTemplate("fake-template-2", "Uit catalog 2"))
                }
            }
        }

        given("one catalog whose names were remembered, and a zaaktype of another catalog while Epistola cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog-1")
            givenZaaktypeOffering(otherZaaktypeUuid, catalogId = "fake-catalog-2")
            every { epistolaClientService.listTemplates("fake-catalog-1") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Uit catalog 1"))
            every { epistolaClientService.listTemplates("fake-catalog-2") } throws
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates of the zaaktype of the other catalog are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.listOfferedTemplates(otherZaaktypeUuid)
                }

                then("the failure is passed on, rather than the templates of a catalog the zaaktype does not use") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }

        given("a template that was renamed in Epistola after its name was remembered") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Old name")) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "New name"))
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates are listed while Epistola answers") {
                val offeredTemplates = service.listOfferedTemplates(zaaktypeUuid)

                then("Epistola's live name replaces the remembered one") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-1", "New name"))
                }
            }
        }

        given("a template that was renamed, read live, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Old name")) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "New name")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listOfferedTemplates(zaaktypeUuid)
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates are listed") {
                val offeredTemplates = service.listOfferedTemplates(zaaktypeUuid)

                then("the newest name is listed, not the first one that was remembered") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-1", "New name"))
                }
            }
        }

        given("a template that Epistola dropped from its catalog after its name was remembered") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(
                    createTemplateSummary(id = "fake-template-1", name = "Kept"),
                    createTemplateSummary(id = "fake-template-2", name = "Dropped")
                ) andThen
                listOf(createTemplateSummary(id = "fake-template-1", name = "Kept")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listOfferedTemplates(zaaktypeUuid)
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates are listed while Epistola cannot be reached") {
                val offeredTemplates = service.listOfferedTemplates(zaaktypeUuid)

                then("the dropped template does not come back from the remembered names") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-1", "Kept"))
                }
            }
        }

        given("Epistola cannot be reached and no name was read since ZAC started") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } throws
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)

            `when`("the templates are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.listOfferedTemplates(zaaktypeUuid)
                }

                then("the failure is passed on, so the caller can tell the user why nothing is listed") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }

        given("names that were remembered, and Epistola then refuses ZAC access") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_ACCESS_DENIED)
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("the templates are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.listOfferedTemplates(zaaktypeUuid)
                }

                then("the refusal is passed on, since only an unreachable Epistola falls back to remembered names") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_ACCESS_DENIED
                }
            }
        }
    }

    context("storing a catalog mapping while Epistola cannot be reached") {
        given("names that were remembered, and Epistola then cannot be reached") {
            val service = newEpistolaTemplatesService()
            givenZaaktypeOffering(zaaktypeUuid, catalogId = "fake-catalog")
            every { epistolaClientService.listTemplates("fake-catalog") } returns
                listOf(createTemplateSummary(id = "fake-template-1", name = "Besluit evenementenvergunning"))
            every { zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid) } returns
                createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid)
            every { epistolaClientService.listCatalogs() } returns listOf(createCatalog(slug = "fake-catalog")) andThenThrows
                epistolaFailure(ERROR_CODE_EPISTOLA_UNAVAILABLE)
            service.listCatalogs()
            service.listOfferedTemplates(zaaktypeUuid)

            `when`("a mapping is stored") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    service.storeCatalogMapping(
                        zaaktypeUuid = zaaktypeUuid,
                        catalogMapping = createRestEpistolaCatalogMapping(catalogId = "fake-catalog")
                    )
                }

                then("it fails, since a save is checked against Epistola's live list of catalogs and never against memory") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                }
            }
        }
    }
})
