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
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.createInformatieObjectType
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.model.EpistolaTemplateSetting
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import java.net.URI
import java.util.UUID

private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_OTHER_TEMPLATE_ID = "fake-other-template"
private const val FAKE_TITLE = "fakeTitle"

private val TEMPLATE_SCHEMA = mapOf(
    "properties" to mapOf(
        "zaak" to mapOf("properties" to mapOf("identificatie" to mapOf("type" to "string")))
    )
)

class EpistolaDocumentCreationInformatieobjecttypeTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val epistolaTemplatesService = mockk<EpistolaTemplatesService>()
    val epistolaDocumentRepository = mockk<EpistolaDocumentRepository>(relaxed = true)
    val ztcClientService = mockk<ZtcClientService>()
    val enkelvoudigInformatieObjectUpdateService = mockk<EnkelvoudigInformatieObjectUpdateService>()
    val configurationService = mockk<ConfigurationService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        epistolaTemplatesService = epistolaTemplatesService,
        epistolaDocumentRepository = epistolaDocumentRepository,
        ztcClientService = ztcClientService,
        enkelvoudigInformatieObjectUpdateService = enkelvoudigInformatieObjectUpdateService,
        configurationService = configurationService,
        epistolaDocumentCreationStatusStore = EpistolaDocumentCreationStatusStore(),
        loggedInUserInstance = loggedInUserInstance
    )
    val zaaktypeInformatieObjectTypeUuid = UUID.randomUUID()
    val templateInformatieObjectTypeUuid = UUID.randomUUID()

    afterEach { checkUnnecessaryStub() }

    fun givenOfferedCatalog(zaak: Zaak, templateId: String): CapturingSlot<EnkelvoudigInformatieObjectCreateLockRequest> {
        val loggedInUser = createLoggedInUser()
        val generatedDocument = EpistolaGeneratedDocument(
            documentId = UUID.randomUUID(),
            fileName = "$FAKE_TITLE.pdf",
            content = "fakePdfContent".toByteArray()
        )
        val createLockRequestSlot = slot<EnkelvoudigInformatieObjectCreateLockRequest>()
        every { loggedInUserInstance.get() } returns loggedInUser
        every { epistolaTemplatesService.readCatalogOfferingTemplate(zaak.zaaktype.extractUuid(), templateId) } returns
            OfferedEpistolaCatalog(
                catalogId = FAKE_CATALOG_ID,
                informatieObjectTypeUuid = zaaktypeInformatieObjectTypeUuid,
                templateSettings = mapOf(
                    FAKE_TEMPLATE_ID to EpistolaTemplateSetting(informatieObjectTypeUuid = templateInformatieObjectTypeUuid)
                )
            )
        every { ztcClientService.readInformatieobjecttype(any<UUID>()) } answers {
            createInformatieObjectType(uri = URI("https://example.com/informatieobjecttypen/${firstArg<UUID>()}"))
        }
        every { documentCreationDataService.createEpistolaData(loggedInUser, zaak, any()) } returns createData()
        every { epistolaClientService.readGenerationTemplate(FAKE_CATALOG_ID, templateId) } returns
            EpistolaGenerationTemplate(dataContract = TEMPLATE_SCHEMA)
        every {
            epistolaClientService.generateDocument(
                catalogId = FAKE_CATALOG_ID,
                templateId = templateId,
                data = any(),
                fileName = "$FAKE_TITLE.pdf",
                correlationId = zaak.uuid.toString(),
                onJobStatus = any()
            )
        } returns generatedDocument
        every { configurationService.readBronOrganisatie() } returns "123443210"
        every {
            enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                zaak = zaak,
                enkelvoudigInformatieObjectCreateLockRequest = capture(createLockRequestSlot),
                taskId = null
            )
        } returns createZaakInformatieobjectForReads()
        every { epistolaClientService.deleteDocument(generatedDocument.documentId) } just runs
        return createLockRequestSlot
    }

    context("storing a document generated from a template") {
        given("a template with an informatieobjecttype of its own, other than the zaaktype's") {
            val zaak = createZaak()
            val createLockRequestSlot = givenOfferedCatalog(zaak, FAKE_TEMPLATE_ID)

            `when`("the document is created and stored") {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = null
                )

                then("it is stored under the informatieobjecttype of the template") {
                    createLockRequestSlot.captured.informatieobjecttype shouldBe
                        URI("https://example.com/informatieobjecttypen/$templateInformatieObjectTypeUuid")
                }
            }
        }

        given("a template without an informatieobjecttype of its own") {
            val zaak = createZaak()
            val createLockRequestSlot = givenOfferedCatalog(zaak, FAKE_OTHER_TEMPLATE_ID)

            `when`("the document is created and stored") {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = FAKE_OTHER_TEMPLATE_ID,
                    title = FAKE_TITLE,
                    description = null
                )

                then("it is stored under the informatieobjecttype of the zaaktype") {
                    createLockRequestSlot.captured.informatieobjecttype shouldBe
                        URI("https://example.com/informatieobjecttypen/$zaaktypeInformatieObjectTypeUuid")
                }
            }
        }
    }
})
