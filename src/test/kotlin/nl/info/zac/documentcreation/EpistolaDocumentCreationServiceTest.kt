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
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.zgw.model.createZaak
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.createData
import java.util.UUID

private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"

class EpistolaDocumentCreationServiceTest : BehaviorSpec({
    val epistolaClientService = mockk<EpistolaClientService>()
    val documentCreationDataService = mockk<DocumentCreationDataService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentCreationService = EpistolaDocumentCreationService(
        epistolaClientService = epistolaClientService,
        documentCreationDataService = documentCreationDataService,
        loggedInUserInstance = loggedInUserInstance
    )

    afterEach { checkUnnecessaryStub() }

    given("a zaak and a template that declares two zaak fields") {
        val loggedInUser = createLoggedInUser()
        val zaak = createZaak()
        val generatedDocument = EpistolaGeneratedDocument(
            documentId = UUID.randomUUID(),
            fileName = FAKE_FILE_NAME,
            content = "fakePdfContent".toByteArray()
        )
        val templateDataSlot = slot<Map<String, Any>>()
        val correlationIdSlot = slot<String>()

        every { loggedInUserInstance.get() } returns loggedInUser
        every {
            documentCreationDataService.createEpistolaData(loggedInUser, zaak, null)
        } returns createData()
        every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns mapOf(
            "properties" to mapOf(
                "zaak" to mapOf(
                    "properties" to mapOf(
                        "identificatie" to mapOf("type" to "string"),
                        "omschrijving" to mapOf("type" to "string")
                    )
                )
            )
        )
        every {
            epistolaClientService.generateDocument(
                templateId = FAKE_TEMPLATE_ID,
                data = capture(templateDataSlot),
                fileName = FAKE_FILE_NAME,
                correlationId = capture(correlationIdSlot)
            )
        } returns generatedDocument

        `when`("a document is created") {
            val document = epistolaDocumentCreationService.createDocument(
                zaak = zaak,
                templateId = FAKE_TEMPLATE_ID,
                fileName = FAKE_FILE_NAME
            )

            then("the rendered document is returned") {
                document shouldBe generatedDocument
            }

            and("only the fields the template declares are sent") {
                templateDataSlot.captured shouldBe mapOf(
                    "zaak" to mapOf(
                        "identificatie" to "fakeIdentificatie",
                        "omschrijving" to "fakeOmschrijving"
                    )
                )
            }

            and("the zaak is identified to Epistola by its UUID and not by its zaaknummer") {
                correlationIdSlot.captured shouldBe zaak.uuid.toString()
            }
        }
    }

    given("a template that declares no schema") {
        val loggedInUser = createLoggedInUser()
        val zaak = createZaak()

        every { loggedInUserInstance.get() } returns loggedInUser
        every {
            documentCreationDataService.createEpistolaData(loggedInUser, zaak, null)
        } returns createData()
        every { epistolaClientService.readTemplateSchema(FAKE_TEMPLATE_ID) } returns null

        `when`("a document is created") {
            val exception = shouldThrow<EpistolaTemplateSchemaMissingException> {
                epistolaDocumentCreationService.createDocument(
                    zaak = zaak,
                    templateId = FAKE_TEMPLATE_ID,
                    fileName = FAKE_FILE_NAME
                )
            }

            then("no zaak data is sent to Epistola") {
                exception.message shouldContain FAKE_TEMPLATE_ID
                verify(exactly = 0) { epistolaClientService.generateDocument(any(), any(), any(), any()) }
            }
        }
    }
})
