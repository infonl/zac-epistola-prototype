/*
 * SPDX-FileCopyrightText: 2022 Atos, 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

package nl.info.zac.app.documentcreation

import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import net.atos.zac.flowable.task.FlowableTaskService
import net.atos.zac.flowable.task.TaakVariabelenService.readZaakUUID
import net.atos.zac.flowable.task.exception.TaskNotFoundException
import nl.info.client.smartdocuments.exception.SmartDocumentsRuntimeException
import nl.info.client.zgw.shared.exception.ZgwErrorException
import nl.info.client.zgw.shared.exception.ZgwRuntimeException
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.app.documentcreation.model.RestDocumentCreationAttendedData
import nl.info.zac.app.documentcreation.model.RestDocumentCreationAttendedResponse
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentCreationData
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentCreationResponse
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentCreationStatus
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentPreviewData
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.runAsLoggedInUser
import nl.info.zac.authentication.runAsSystemUser
import nl.info.zac.documentcreation.DocumentCreationService
import nl.info.zac.documentcreation.DocumentCreationUserStore
import nl.info.zac.documentcreation.EpistolaDocumentCreationService
import nl.info.zac.documentcreation.model.DocumentCreationAttendedResponse
import nl.info.zac.documentcreation.model.DocumentCreationDataAttended
import nl.info.zac.exception.InputValidationFailedException
import nl.info.zac.exception.ServerErrorException
import nl.info.zac.policy.PolicyService
import nl.info.zac.policy.assertPolicy
import nl.info.zac.smartdocuments.SmartDocumentsService
import nl.info.zac.smartdocuments.exception.SmartDocumentsDisabledException
import nl.info.zac.smartdocuments.exception.SmartDocumentsUnsupportedOutputFormatException
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.ZonedDateTime
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

@Singleton
@Path("document-creation")
@Produces(MediaType.APPLICATION_JSON)
@NoArgConstructor
@AllOpen
@Suppress("LongParameterList", "TooManyFunctions")
class DocumentCreationRestService @Inject constructor(
    private val policyService: PolicyService,
    private val documentCreationService: DocumentCreationService,
    private val epistolaDocumentCreationService: EpistolaDocumentCreationService,
    private val zrcClientService: ZrcClientService,
    private val zaaktypeConfigurationService: ZaaktypeConfigurationService,
    private val flowableTaskService: FlowableTaskService,
    private val loggedInUserInstance: Instance<LoggedInUser>,
    private val documentCreationUserStore: DocumentCreationUserStore,
    private val smartDocumentsService: SmartDocumentsService
) {
    companion object {
        private const val MEDIA_TYPE_PDF = "application/pdf"

        enum class SmartDocumentsWizardResult(val value: String) {
            SUCCESS("success"),
            CANCELLED("cancelled"),
            FAILURE("failure"),
            UNSUPPORTED_OUTPUT_FORMAT("unsupported-output-format")
        }

        private val LOG = Logger.getLogger(DocumentCreationRestService::class.java.name)
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/create-document-attended")
    fun createDocumentAttended(
        @Valid restDocumentCreationAttendedData: RestDocumentCreationAttendedData
    ): RestDocumentCreationAttendedResponse =
        zrcClientService.readZaak(restDocumentCreationAttendedData.zaakUuid).also { zaak ->
            assertDocumentCreationAllowed(zaak = zaak, taskId = restDocumentCreationAttendedData.taskId)
        }.let { zaak ->
            createSmartDocumentsDocumentAttended(zaak, restDocumentCreationAttendedData)
                .let { RestDocumentCreationAttendedResponse(it.redirectUrl, it.message) }
        }

    /**
     * Returns once the document is in the zaak's dossier: Epistola renders it while this request waits, so
     * there is no wizard and no callback. The wait is bounded by `EPISTOLA_GENERATION_TIMEOUT_SECONDS`.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/epistola/create-document")
    fun createEpistolaDocument(
        @Valid restEpistolaDocumentCreationData: RestEpistolaDocumentCreationData
    ): RestEpistolaDocumentCreationResponse =
        zrcClientService.readZaak(restEpistolaDocumentCreationData.zaakUuid).also { zaak ->
            assertDocumentCreationAllowed(zaak = zaak, taskId = restEpistolaDocumentCreationData.taskId)
        }.let { zaak ->
            epistolaDocumentCreationService.createAndStoreDocument(
                zaak = zaak,
                templateId = restEpistolaDocumentCreationData.templateId,
                title = restEpistolaDocumentCreationData.title,
                description = restEpistolaDocumentCreationData.description,
                taskId = restEpistolaDocumentCreationData.taskId
            )
        }.let { RestEpistolaDocumentCreationResponse(informatieobjectUuid = it.informatieobject.extractUuid()) }

    /**
     * Lets a behandelaar look at the document before it is generated: Epistola renders it from the data that creating it
     * would send, and nothing is kept. Epistola promises nothing about a preview, so it is not what ends up in the
     * zaak. It asks as much as creating the document, because it sends the zaak's data to Epistola just the same.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MEDIA_TYPE_PDF)
    @Path("/epistola/preview-document")
    fun previewEpistolaDocument(
        @Valid restEpistolaDocumentPreviewData: RestEpistolaDocumentPreviewData
    ): ByteArray =
        zrcClientService.readZaak(restEpistolaDocumentPreviewData.zaakUuid).also { zaak ->
            assertDocumentCreationAllowed(zaak = zaak, taskId = restEpistolaDocumentPreviewData.taskId)
        }.let { zaak ->
            epistolaDocumentCreationService.previewDocument(
                zaak = zaak,
                templateId = restEpistolaDocumentPreviewData.templateId,
                taskId = restEpistolaDocumentPreviewData.taskId
            )
        }

    /**
     * What Epistola reports on the document the logged-in user is generating for the zaak, while the request above
     * waits. Only the user's own request is ever read, so no rights beyond being logged in are checked.
     */
    @GET
    @Path("/epistola/create-document/{zaakUuid}/status")
    fun readEpistolaDocumentCreationStatus(@PathParam("zaakUuid") zaakUuid: UUID) =
        RestEpistolaDocumentCreationStatus(status = epistolaDocumentCreationService.readStatus(zaakUuid))

    /**
     * SmartDocuments callback for CMMN zaak
     *
     * Called when SmartDocument Wizard "Finish" button is clicked. The URL provided as "redirectUrl" to
     * SmartDocuments contains all the parameters needed to store the document for a zaak:
     * zaak ID, template and template group IDs, username and created document ID
     */
    @POST
    @Path("/smartdocuments/callback/zaak/{zaakUuid}")
    @Produces(MediaType.TEXT_HTML)
    @Suppress("LongParameterList")
    fun createCmmnDocumentForZaakCallback(
        @PathParam("zaakUuid") zaakUuid: UUID,
        @QueryParam("templateGroupId") templateGroupId: String,
        @QueryParam("templateId") templateId: String,
        @QueryParam("title") title: String,
        @QueryParam("description") description: String?,
        @QueryParam("creationDate") creationDate: ZonedDateTime,
        @QueryParam("userName") userName: String,
        @QueryParam("documentCreationToken") documentCreationToken: UUID?,
        @FormParam("sdDocument") @DefaultValue("") fileId: String,
    ): Response =
        storeDocument(
            zaakUuid = zaakUuid,
            title = title,
            description = description,
            creationDate = creationDate,
            userName = userName,
            documentCreationToken = documentCreationToken,
            fileId = fileId
        ) {
            documentCreationService.getInformationObjecttypeUuid(it, templateGroupId, templateId)
        }

    /**
     * SmartDocuments callback for CMMN task
     *
     * Called when SmartDocument Wizard "Finish" button is clicked. The URL provided as "redirectUrl" to
     * SmartDocuments contains all the parameters needed to store the document for a task:
     * zaak and task IDs, template and template group IDs, username and created document ID
     */
    @POST
    @Path("/smartdocuments/callback/zaak/{zaakUuid}/task/{taskId}")
    @Produces(MediaType.TEXT_HTML)
    @Suppress("LongParameterList")
    fun createCmmnDocumentForTaskCallback(
        @PathParam("zaakUuid") zaakUuid: UUID,
        @PathParam("taskId") taskId: String,
        @QueryParam("templateGroupId") templateGroupId: String,
        @QueryParam("templateId") templateId: String,
        @QueryParam("title") title: String,
        @QueryParam("description") description: String?,
        @QueryParam("creationDate") creationDate: ZonedDateTime,
        @QueryParam("userName") userName: String,
        @QueryParam("documentCreationToken") documentCreationToken: UUID?,
        @FormParam("sdDocument") @DefaultValue("") fileId: String,
    ): Response =
        storeDocument(
            zaakUuid = zaakUuid,
            taskId = taskId,
            title = title,
            description = description,
            creationDate = creationDate,
            userName = userName,
            documentCreationToken = documentCreationToken,
            fileId = fileId
        ) {
            documentCreationService.getInformationObjecttypeUuid(it, templateGroupId, templateId)
        }

    private fun assertDocumentCreationAllowed(zaak: Zaak, taskId: String?) {
        assertPolicy(policyService.readZaakRechten(zaak, loggedInUserInstance.get()).canCreerenDocument)
        taskId?.let {
            val task = flowableTaskService.findOpenTask(it)
                ?: throw TaskNotFoundException("No open task found with task id: '$it'")
            if (readZaakUUID(task) != zaak.uuid) {
                throw TaskNotFoundException("No open task found with task id: '$it' for zaak '${zaak.uuid}'")
            }
            assertPolicy(policyService.readTaakRechten(task).canCreerenDocument)
        }
    }

    @Suppress("ThrowsCount")
    private fun createSmartDocumentsDocumentAttended(
        zaak: Zaak,
        restDocumentCreationAttendedData: RestDocumentCreationAttendedData
    ): DocumentCreationAttendedResponse {
        if (!zaaktypeConfigurationService.isSmartDocumentsEnabled(zaak.zaaktype.extractUuid())) {
            throw SmartDocumentsDisabledException()
        }
        return DocumentCreationDataAttended(
            zaak = zaak,
            taskId = restDocumentCreationAttendedData.taskId,
            templateId = restDocumentCreationAttendedData.smartDocumentsTemplateId
                ?: throw IllegalArgumentException("SmartDocuments template ID is required"),
            templateGroupId = restDocumentCreationAttendedData.smartDocumentsTemplateGroupId
                ?: throw IllegalArgumentException("SmartDocuments template group ID is required"),
            title = restDocumentCreationAttendedData.title,
            description = restDocumentCreationAttendedData.description,
            author = restDocumentCreationAttendedData.author,
            creationDate = restDocumentCreationAttendedData.creationDate
        ).let(documentCreationService::createDocumentAttended)
    }

    private fun storeDocument(
        zaakUuid: UUID,
        taskId: String? = null,
        title: String,
        description: String?,
        creationDate: ZonedDateTime,
        userName: String,
        documentCreationToken: UUID?,
        fileId: String,
        fetchInformatieobjecttypeUuidFunction: (zaak: Zaak) -> UUID,
    ): Response {
        // spend the token before the cancellation branch, so a cancelled wizard cannot leave it open to replay
        val documentCreationUser = consumeDocumentCreationUser(documentCreationToken, zaakUuid)
        return runAsDocumentCreationUser(documentCreationUser) {
            if (fileId.isBlank()) {
                val zaak = zrcClientService.readZaak(zaakUuid)
                Response.seeOther(
                    documentCreationService.documentCreationFinishPageUrl(
                        zaakId = zaak.identificatie,
                        taskId = taskId,
                        documentName = title,
                        result = SmartDocumentsWizardResult.CANCELLED.value
                    )
                ).build()
            } else {
                storeSmartDocumentsDocument(
                    zaakUuid = zaakUuid,
                    taskId = taskId,
                    title = title,
                    description = description,
                    creationDate = creationDate,
                    userName = userName,
                    fileId = fileId,
                    fetchInformatieobjecttypeUuidFunction = fetchInformatieobjecttypeUuidFunction
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private fun storeSmartDocumentsDocument(
        zaakUuid: UUID,
        taskId: String?,
        title: String,
        description: String?,
        creationDate: ZonedDateTime,
        userName: String,
        fileId: String,
        fetchInformatieobjecttypeUuidFunction: (zaak: Zaak) -> UUID,
    ): Response {
        val zaak = zrcClientService.readZaak(zaakUuid)
        val result = try {
            val file = smartDocumentsService.downloadDocument(fileId)
            val informatieobjecttypeUuid = fetchInformatieobjecttypeUuidFunction(zaak)
            documentCreationService.storeDownloadedDocument(
                zaak = zaak,
                taskId = taskId,
                file = file,
                title = title,
                description = description,
                informatieobjecttypeUuid = informatieobjecttypeUuid,
                creationDate = creationDate,
                userName = userName
            )
            SmartDocumentsWizardResult.SUCCESS
        } catch (smartDocumentsUnsupportedOutputFormatException: SmartDocumentsUnsupportedOutputFormatException) {
            logDocumentCreationFailure(smartDocumentsUnsupportedOutputFormatException, zaakUuid, taskId)
            SmartDocumentsWizardResult.UNSUPPORTED_OUTPUT_FORMAT
        } catch (serverErrorException: ServerErrorException) {
            logDocumentCreationFailure(serverErrorException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (smartDocumentsRuntimeException: SmartDocumentsRuntimeException) {
            logDocumentCreationFailure(smartDocumentsRuntimeException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (zgwRuntimeException: ZgwRuntimeException) {
            logDocumentCreationFailure(zgwRuntimeException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (zgwErrorException: ZgwErrorException) {
            logDocumentCreationFailure(zgwErrorException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (inputValidationFailedException: InputValidationFailedException) {
            logDocumentCreationFailure(inputValidationFailedException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (webApplicationException: WebApplicationException) {
            logDocumentCreationFailure(webApplicationException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        } catch (processingException: ProcessingException) {
            logDocumentCreationFailure(processingException, zaakUuid, taskId)
            SmartDocumentsWizardResult.FAILURE
        }
        return Response.seeOther(
            documentCreationService.documentCreationFinishPageUrl(
                zaakId = zaak.identificatie,
                taskId = taskId,
                documentName = title,
                result = result.value
            )
        ).build()
    }

    private fun logDocumentCreationFailure(exception: RuntimeException, zaakUuid: UUID, taskId: String?) =
        LOG.log(Level.WARNING, exception) {
            "Failed to create document for zaak $zaakUuid" + if (taskId != null) " and task $taskId" else ""
        }

    // if/else instead of `?.let`: CodeQL's model of `let` makes the HTML response look tainted by the token (java/xss)
    private fun consumeDocumentCreationUser(documentCreationToken: UUID?, zaakUuid: UUID): LoggedInUser? =
        if (documentCreationToken == null) {
            null
        } else {
            documentCreationUserStore.consumeUser(documentCreationToken) ?: run {
                LOG.warning {
                    "Unknown or expired document creation token for zaak '$zaakUuid'; " +
                        "storing the document as the functionele gebruiker"
                }
                null
            }
        }

    /**
     * Runs the callback as the user the token identifies. An unknown token leaves it to the
     * functionele gebruiker rather than failing.
     */
    private fun <T> runAsDocumentCreationUser(documentCreationUser: LoggedInUser?, block: () -> T): T =
        if (documentCreationUser == null) {
            runAsSystemUser(block)
        } else {
            runAsLoggedInUser(documentCreationUser, block)
        }
}
