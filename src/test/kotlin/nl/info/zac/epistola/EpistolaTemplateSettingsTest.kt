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
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaTemplateNotOfferedException
import nl.info.zac.epistola.model.EpistolaTemplateSetting
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.epistola.rest.RestOfferedEpistolaTemplate
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_OFFERED
import java.util.UUID

class EpistolaTemplateSettingsTest : BehaviorSpec({
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
    val zaaktypeUuid = UUID.randomUUID()
    val informatieObjectTypeUuid = UUID.randomUUID()
    val templateInformatieObjectTypeUuid = UUID.randomUUID()

    afterEach { checkUnnecessaryStub() }

    fun givenOfferingZaaktype(templateSettings: Map<String, EpistolaTemplateSetting>) {
        every { documentCreationProviderConfiguration.activeProvider } returns DocumentCreationProvider.EPISTOLA
        every { zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid) } returns
            createZaaktypeCmmnConfiguration(zaaktypeUUID = zaaktypeUuid).apply {
                isEpistolaEnabled = true
                epistolaCatalogId = "fake-catalog"
                epistolaInformatieobjecttypeUuid = informatieObjectTypeUuid
                replaceEpistolaTemplateSettings(templateSettings)
            }
    }

    fun givenCatalogWithTemplates(vararg templateIds: String) {
        every { epistolaClientService.listTemplates("fake-catalog") } returns
            templateIds.map { createTemplateSummary(id = it, name = "Template $it") }
    }

    fun offeredTemplate(templateId: String, informatieObjectTypeUuid: UUID) = RestOfferedEpistolaTemplate(
        id = templateId,
        name = "Template $templateId",
        informatieObjectTypeUUID = informatieObjectTypeUuid
    )

    context("listing the templates a zaaktype offers") {
        given("a template with an informatieobjecttype of its own and a template without") {
            givenOfferingZaaktype(
                mapOf("fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = templateInformatieObjectTypeUuid))
            )
            givenCatalogWithTemplates("fake-template-1", "fake-template-2")

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("the first names its own informatieobjecttype and the second the zaaktype's") {
                    offeredTemplates shouldBe listOf(
                        offeredTemplate("fake-template-1", templateInformatieObjectTypeUuid),
                        offeredTemplate("fake-template-2", informatieObjectTypeUuid)
                    )
                }
            }
        }

        given("a template that is switched off, with an informatieobjecttype of its own, and a template added to the catalog since") {
            givenOfferingZaaktype(
                mapOf(
                    "fake-template-1" to EpistolaTemplateSetting(
                        informatieObjectTypeUuid = templateInformatieObjectTypeUuid,
                        isEnabled = false
                    )
                )
            )
            givenCatalogWithTemplates("fake-template-1", "fake-template-2")

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("only the template that is not switched off is offered, which includes the one the setting does not know") {
                    offeredTemplates shouldBe listOf(offeredTemplate("fake-template-2", informatieObjectTypeUuid))
                }
            }
        }

        given("every template of the catalog switched off") {
            givenOfferingZaaktype(
                mapOf(
                    "fake-template-1" to EpistolaTemplateSetting(isEnabled = false),
                    "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                )
            )
            givenCatalogWithTemplates("fake-template-1", "fake-template-2")

            `when`("the templates are listed") {
                val offeredTemplates = epistolaTemplatesService.listOfferedTemplates(zaaktypeUuid)

                then("none is offered") {
                    offeredTemplates.shouldBeEmpty()
                }
            }
        }
    }

    context("reading the catalog that offers a template to generate a document from") {
        given("a template that is switched off") {
            givenOfferingZaaktype(mapOf("fake-template-1" to EpistolaTemplateSetting(isEnabled = false)))

            `when`("the catalog is read for that template") {
                val epistolaTemplateNotOfferedException = shouldThrow<EpistolaTemplateNotOfferedException> {
                    epistolaTemplatesService.readCatalogOfferingTemplate(zaaktypeUuid, "fake-template-1")
                }

                then("it is refused, so that a request that names the template cannot get past the selector") {
                    epistolaTemplateNotOfferedException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_NOT_OFFERED
                    epistolaTemplateNotOfferedException.message shouldBe
                        "Zaaktype '$zaaktypeUuid' does not offer Epistola template 'fake-template-1'."
                }
            }

            `when`("the offered catalog is read without naming a template, as a new version of a document does") {
                val offeredCatalog = epistolaTemplatesService.readOfferedCatalog(zaaktypeUuid)

                then("it is returned, so that a document made from the template before can still get a new version") {
                    offeredCatalog shouldBe OfferedEpistolaCatalog(
                        catalogId = "fake-catalog",
                        informatieObjectTypeUuid = informatieObjectTypeUuid,
                        templateSettings = mapOf("fake-template-1" to EpistolaTemplateSetting(isEnabled = false))
                    )
                }
            }
        }

        given("a template that is offered, with an informatieobjecttype of its own") {
            givenOfferingZaaktype(
                mapOf("fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = templateInformatieObjectTypeUuid))
            )

            `when`("the catalog is read for that template") {
                val offeredCatalog = epistolaTemplatesService.readCatalogOfferingTemplate(zaaktypeUuid, "fake-template-1")

                then("the informatieobjecttype of the template wins over the zaaktype's") {
                    offeredCatalog.informatieObjectTypeUuidOf("fake-template-1") shouldBe templateInformatieObjectTypeUuid
                }

                and("a template without a setting takes the zaaktype's") {
                    offeredCatalog.informatieObjectTypeUuidOf("fake-template-2") shouldBe informatieObjectTypeUuid
                }
            }
        }

        given("a template the zaaktype has no setting for") {
            givenOfferingZaaktype(emptyMap())

            `when`("the catalog is read for that template") {
                val offeredCatalog = epistolaTemplatesService.readCatalogOfferingTemplate(zaaktypeUuid, "fake-template-1")

                then("it is offered, and takes the zaaktype's informatieobjecttype") {
                    offeredCatalog.isOffered("fake-template-1") shouldBe true
                    offeredCatalog.informatieObjectTypeUuidOf("fake-template-1") shouldBe informatieObjectTypeUuid
                }
            }
        }
    }
})
