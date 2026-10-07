/*
 * SPDX-FileCopyrightText: 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

package nl.info.zac.app.documentcreation

import nl.info.client.epistola.model.createGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentCreationData
import nl.info.zac.app.documentcreation.model.RestEpistolaVarianten
import nl.info.zac.documentcreation.EpistolaDocumentCreationService
import nl.info.zac.documentcreation.model.EpistolaTemplateInLocale
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import jakarta.servlet.http.HttpSession
import net.atos.zac.flowable.ZaakVariabelenService.Companion.VAR_ZAAK_UUID
import net.atos.zac.flowable.task.FlowableTaskService
import net.atos.zac.flowable.task.exception.TaskNotFoundException
import nl.info.client.smartdocuments.model.createFile
import nl.info.client.zgw.drc.exception.DrcRuntimeException
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.client.zgw.zrc.model.generated.ZaakInformatieObject
import nl.info.test.org.flowable.task.api.createTestTask
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.app.documentcreation.model.createRestDocumentCreationAttendedData
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.LoggedInUserProvider
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.documentcreation.DocumentCreationService
import nl.info.zac.documentcreation.DocumentCreationUserStore
import nl.info.zac.documentcreation.model.DocumentCreationDataAttended
import nl.info.zac.documentcreation.model.createDocumentCreationAttendedResponse
import nl.info.zac.exception.ErrorCode.ERROR_CODE_SMARTDOCUMENTS_DISABLED
import nl.info.zac.policy.PolicyService
import nl.info.zac.policy.exception.PolicyException
import nl.info.zac.policy.output.createZaakRechtenAllDeny
import nl.info.zac.smartdocuments.SmartDocumentsService
import nl.info.zac.smartdocuments.exception.SmartDocumentsDisabledException
import org.flowable.task.api.TaskInfo
import nl.info.zac.smartdocuments.exception.SmartDocumentsUnsupportedOutputFormatException
import java.net.URI
import java.time.ZonedDateTime
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class DocumentCreationRestServiceEpistolaTest : BehaviorSpec({
    val documentCreationService = mockk<DocumentCreationService>()
    val epistolaDocumentCreationService = mockk<EpistolaDocumentCreationService>()
    val policyService = mockk<PolicyService>()
    val zrcClientService = mockk<ZrcClientService>()
    val zaaktypeConfigurationService = mockk<ZaaktypeConfigurationService>()
    val flowableTaskService = mockk<FlowableTaskService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val documentCreationUserStore = mockk<DocumentCreationUserStore>()
    val smartDocumentsService = mockk<SmartDocumentsService>()
    val documentCreationRestService = DocumentCreationRestService(
        policyService = policyService,
        documentCreationService = documentCreationService,
        epistolaDocumentCreationService = epistolaDocumentCreationService,
        zrcClientService = zrcClientService,
        zaaktypeConfigurationService = zaaktypeConfigurationService,
        flowableTaskService = flowableTaskService,
        loggedInUserInstance = loggedInUserInstance,
        documentCreationUserStore = documentCreationUserStore,
        smartDocumentsService = smartDocumentsService
    )

    isolationMode = IsolationMode.InstancePerTest

    given("an Epistola document requested for a zaak, from one of its tasks") {
        val zaak = createZaak()
        val taskId = "fakeTaskId"
        val task = createTestTask(caseVariables = mapOf(VAR_ZAAK_UUID to zaak.uuid))
        val informatieobjectUuid = UUID.randomUUID()
        val loggedInUser = createLoggedInUser()
        val restEpistolaDocumentCreationData = RestEpistolaDocumentCreationData(
            zaakUuid = zaak.uuid,
            taskId = taskId,
            templateId = "fake-template",
            title = "fakeTitle",
            description = "fakeDescription"
        )
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser

        `when`("it is requested by a user who may create documents for the zaak and for the task") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns task
            every { policyService.readTaakRechten(task).canCreerenDocument } returns true
            every {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = "fake-template",
                    title = "fakeTitle",
                    description = "fakeDescription",
                    taskId = taskId
                )
            } returns createZaakInformatieobjectForReads(
                informatieobject = URI("https://example.com/enkelvoudiginformatieobjecten/$informatieobjectUuid")
            )

            val restEpistolaDocumentCreationResponse = documentCreationRestService.createEpistolaDocument(
                restEpistolaDocumentCreationData
            )

            then("the document is generated and stored, and the stored informatieobject is named") {
                restEpistolaDocumentCreationResponse.informatieobjectUuid shouldBe informatieobjectUuid
            }
        }

        `when`("it is requested by a user who may not create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny()

            shouldThrow<PolicyException> {
                documentCreationRestService.createEpistolaDocument(restEpistolaDocumentCreationData)
            }

            then("it is refused before any zaak data reaches Epistola") {
                verify(exactly = 0) {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = any(),
                        templateId = any(),
                        title = any(),
                        description = any(),
                        taskId = any(),
                        variant = any()
                    )
                }
            }
        }

        `when`("it is requested by a user who may create documents for the zaak but not for the task") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns task
            every { policyService.readTaakRechten(task).canCreerenDocument } returns false

            shouldThrow<PolicyException> {
                documentCreationRestService.createEpistolaDocument(restEpistolaDocumentCreationData)
            }

            then("it is refused before any zaak data reaches Epistola") {
                verify(exactly = 0) {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = any(),
                        templateId = any(),
                        title = any(),
                        description = any(),
                        taskId = any(),
                        variant = any()
                    )
                }
            }
        }

        `when`("it is requested with a task that belongs to another zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns createTestTask(
                caseVariables = mapOf(VAR_ZAAK_UUID to UUID.randomUUID())
            )

            val taskNotFoundException = shouldThrow<TaskNotFoundException> {
                documentCreationRestService.createEpistolaDocument(restEpistolaDocumentCreationData)
            }

            then("it is refused, naming the task and the zaak, before any zaak data reaches Epistola") {
                taskNotFoundException.message shouldBe
                    "No open task found with task id: 'fakeTaskId' for zaak '${zaak.uuid}'"
                verify(exactly = 0) {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = any(),
                        templateId = any(),
                        title = any(),
                        description = any(),
                        taskId = any(),
                        variant = any()
                    )
                }
            }
        }

        `when`("it is requested for a task that is no longer open") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { flowableTaskService.findOpenTask(taskId) } returns null

            val taskNotFoundException = shouldThrow<TaskNotFoundException> {
                documentCreationRestService.createEpistolaDocument(restEpistolaDocumentCreationData)
            }

            then("it is refused, naming the task") {
                taskNotFoundException.message shouldBe "No open task found with task id: 'fakeTaskId'"
            }
        }
    }

    given("an Epistola document requested for a zaak, and not from a task") {
        val zaak = createZaak()
        val informatieobjectUuid = UUID.randomUUID()
        val loggedInUser = createLoggedInUser()
        val restEpistolaDocumentCreationData = RestEpistolaDocumentCreationData(
            zaakUuid = zaak.uuid,
            templateId = "fake-template",
            title = "fakeTitle",
            description = "fakeDescription"
        )
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser

        `when`("it is requested by a user who may create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every {
                epistolaDocumentCreationService.createAndStoreDocument(
                    zaak = zaak,
                    templateId = "fake-template",
                    title = "fakeTitle",
                    description = "fakeDescription",
                    taskId = null
                )
            } returns createZaakInformatieobjectForReads(
                informatieobject = URI("https://example.com/enkelvoudiginformatieobjecten/$informatieobjectUuid")
            )

            val restEpistolaDocumentCreationResponse = documentCreationRestService.createEpistolaDocument(
                restEpistolaDocumentCreationData
            )

            then("the document is generated and stored for the zaak, and no task is looked up or checked") {
                restEpistolaDocumentCreationResponse.informatieobjectUuid shouldBe informatieobjectUuid
                verify(exactly = 0) { flowableTaskService.findOpenTask(any()) }
                verify(exactly = 0) { policyService.readTaakRechten(any<TaskInfo>()) }
            }
        }
    }

    given("an Epistola document requested for a zaak by post") {
        val zaak = createZaak()
        val loggedInUser = createLoggedInUser()
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser
        every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(creerenDocument = true)
        every {
            epistolaDocumentCreationService.createAndStoreDocument(
                zaak = zaak,
                templateId = "fake-template",
                title = "fakeTitle",
                description = null,
                taskId = null,
                variant = "post"
            )
        } returns createZaakInformatieobjectForReads()

        `when`("it is requested") {
            documentCreationRestService.createEpistolaDocument(
                RestEpistolaDocumentCreationData(
                    zaakUuid = zaak.uuid,
                    templateId = "fake-template",
                    title = "fakeTitle",
                    variant = "post"
                )
            )

            then("the document is generated in that variant") {
                verify(exactly = 1) {
                    epistolaDocumentCreationService.createAndStoreDocument(
                        zaak = zaak,
                        templateId = "fake-template",
                        title = "fakeTitle",
                        description = null,
                        taskId = null,
                        variant = "post"
                    )
                }
            }
        }
    }

    given("a template with a post and a digital variant, and a zaak whose communicatiekanaal is e-mail") {
        val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
        val loggedInUser = createLoggedInUser()
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser

        `when`("its variants are read by a user who may create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                creerenDocument = true
            )
            every { epistolaDocumentCreationService.readVarianten(zaak = zaak, templateId = "fake-template") } returns
                EpistolaTemplateInLocale(
                template = createGenerationTemplate(
                    kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")
                ),
                locale = null
            )

            val restEpistolaVarianten = documentCreationRestService.readEpistolaVarianten(zaak.uuid, "fake-template")

            then("both variants are offered, with the digital one suggested by the communicatiekanaal it names") {
                restEpistolaVarianten shouldBe RestEpistolaVarianten(
                    varianten = listOf("post", "digitaal"),
                    voorgesteldeVariant = "digitaal",
                    communicatiekanaal = "E-mail"
                )
            }
        }

        `when`("its variants are read by a user who may not create documents for the zaak") {
            every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny()

            shouldThrow<PolicyException> {
                documentCreationRestService.readEpistolaVarianten(zaak.uuid, "fake-template")
            }

            then("it is refused before Epistola is asked") {
                verify(exactly = 0) { epistolaDocumentCreationService.readVarianten(any(), any()) }
            }
        }
    }

    given("a template with only a variant by post, and a zaak whose communicatiekanaal is e-mail") {
        val zaak = createZaak().apply { communicatiekanaalNaam = "E-mail" }
        val loggedInUser = createLoggedInUser()
        every { zrcClientService.readZaak(zaak.uuid) } returns zaak
        every { loggedInUserInstance.get() } returns loggedInUser
        every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(creerenDocument = true)
        every { epistolaDocumentCreationService.readVarianten(zaak = zaak, templateId = "fake-template") } returns
            EpistolaTemplateInLocale(
                template = createGenerationTemplate(kanalen = EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")),
                locale = null
            )

        `when`("its variants are read") {
            val restEpistolaVarianten = documentCreationRestService.readEpistolaVarianten(zaak.uuid, "fake-template")

            then("post is suggested as the default, without naming the communicatiekanaal that did not suggest it") {
                restEpistolaVarianten shouldBe RestEpistolaVarianten(
                    varianten = listOf("post"),
                    voorgesteldeVariant = "post",
                    communicatiekanaal = null
                )
            }
        }
    }

    given("an Epistola document that the logged-in user is having rendered for a zaak") {
        val zaakUuid = UUID.randomUUID()
        every {
            epistolaDocumentCreationService.readStatus(zaakUuid)
        } returns EpistolaDocumentCreationStatus.HELD_UP_IN_RENDERING

        `when`("the status is read") {
            val restEpistolaDocumentCreationStatus = documentCreationRestService.readEpistolaDocumentCreationStatus(zaakUuid)

            then("the status Epistola reports is returned, without reading the zaak") {
                restEpistolaDocumentCreationStatus.status shouldBe EpistolaDocumentCreationStatus.HELD_UP_IN_RENDERING
                verify(exactly = 0) { zrcClientService.readZaak(any<UUID>()) }
            }
        }
    }
})
