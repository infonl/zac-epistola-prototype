/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import net.atos.zac.flowable.ZaakVariabelenService.Companion.VAR_ZAAK_UUID
import net.atos.zac.flowable.task.FlowableTaskService
import net.atos.zac.flowable.task.exception.TaskNotFoundException
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.test.org.flowable.task.api.createTestTask
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentPreviewData
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.documentcreation.DocumentCreationService
import nl.info.zac.documentcreation.DocumentCreationUserStore
import nl.info.zac.documentcreation.EpistolaDocumentCreationService
import nl.info.zac.policy.PolicyService
import nl.info.zac.policy.exception.PolicyException
import nl.info.zac.policy.output.createZaakRechtenAllDeny
import java.util.UUID

private val FAKE_PREVIEW = "fakePreviewPdfContent".toByteArray()

class DocumentCreationRestServicePreviewTest : BehaviorSpec({
    val epistolaDocumentCreationService = mockk<EpistolaDocumentCreationService>()
    val policyService = mockk<PolicyService>()
    val zrcClientService = mockk<ZrcClientService>()
    val flowableTaskService = mockk<FlowableTaskService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val documentCreationRestService = DocumentCreationRestService(
        policyService = policyService,
        documentCreationService = mockk<DocumentCreationService>(),
        epistolaDocumentCreationService = epistolaDocumentCreationService,
        zrcClientService = zrcClientService,
        zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>(),
        flowableTaskService = flowableTaskService,
        loggedInUserInstance = loggedInUserInstance,
        documentCreationUserStore = mockk<DocumentCreationUserStore>()
    )

    isolationMode = IsolationMode.InstancePerTest

    given("a preview of an Epistola document requested for a zaak, from one of its tasks") {
        val zaak = createZaak()
        val taskId = "fakeTaskId"
        val task = createTestTask(caseVariables = mapOf(VAR_ZAAK_UUID to zaak.uuid))
        val loggedInUser = createLoggedInUser()
        val restEpistolaDocumentPreviewData = RestEpistolaDocumentPreviewData(
            zaakUuid = zaak.uuid,
            taskId = taskId,
            templateId = "fake-template",
            variant = "post"
        )
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser

        `when`("it is requested by a user who may create documents for the zaak and for the task") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns task
            every { policyService.readTaakRechten(task).creerenDocument } returns true
            every {
                epistolaDocumentCreationService.previewDocument(
                    zaak = zaak,
                    templateId = "fake-template",
                    taskId = taskId,
                    variant = "post"
                )
            } returns FAKE_PREVIEW

            val preview = documentCreationRestService.previewEpistolaDocument(restEpistolaDocumentPreviewData)

            then("the document Epistola rendered is returned, in the variant that was asked for") {
                preview shouldBe FAKE_PREVIEW
            }
        }

        `when`("it is requested by a user who may not create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny()

            shouldThrow<PolicyException> {
                documentCreationRestService.previewEpistolaDocument(restEpistolaDocumentPreviewData)
            }

            then("it is refused before any zaak data reaches Epistola") {
                verify(exactly = 0) { epistolaDocumentCreationService.previewDocument(any(), any(), any(), any()) }
            }
        }

        `when`("it is requested by a user who may create documents for the zaak but not for the task") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns task
            every { policyService.readTaakRechten(task).creerenDocument } returns false

            shouldThrow<PolicyException> {
                documentCreationRestService.previewEpistolaDocument(restEpistolaDocumentPreviewData)
            }

            then("it is refused before any zaak data reaches Epistola") {
                verify(exactly = 0) { epistolaDocumentCreationService.previewDocument(any(), any(), any(), any()) }
            }
        }

        `when`("it is requested with a task that belongs to another zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns createTestTask(
                caseVariables = mapOf(VAR_ZAAK_UUID to UUID.randomUUID())
            )

            shouldThrow<TaskNotFoundException> {
                documentCreationRestService.previewEpistolaDocument(restEpistolaDocumentPreviewData)
            }

            then("it is refused before any zaak data reaches Epistola") {
                verify(exactly = 0) { epistolaDocumentCreationService.previewDocument(any(), any(), any(), any()) }
            }
        }
    }

    given("a preview of an Epistola document requested for a zaak, and not from a task or for a variant") {
        val zaak = createZaak()
        val loggedInUser = createLoggedInUser()
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser

        `when`("it is requested by a user who may create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every {
                epistolaDocumentCreationService.previewDocument(
                    zaak = zaak,
                    templateId = "fake-template",
                    taskId = null,
                    variant = null
                )
            } returns FAKE_PREVIEW

            val preview = documentCreationRestService.previewEpistolaDocument(
                RestEpistolaDocumentPreviewData(zaakUuid = zaak.uuid, templateId = "fake-template")
            )

            then("the document Epistola rendered is returned, and no task is looked up") {
                preview shouldBe FAKE_PREVIEW
                verify(exactly = 0) { flowableTaskService.findOpenTask(any()) }
            }
        }
    }
})
