/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.ProcessingException
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.drc.model.generated.StatusEnum
import nl.info.client.zgw.shared.exception.ZgwErrorException
import nl.info.client.zgw.shared.exception.ZgwRuntimeException
import nl.info.client.zgw.shared.exception.ZgwValidationErrorException
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.zrc.model.generated.ZaakInformatieObject
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.client.zgw.ztc.model.generated.InformatieObjectType
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.app.shared.toDrcVertrouwelijkheidaanduidingEnum
import nl.info.zac.app.shared.toRestVertrouwelijkheidaanduiding
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.configuration.ConfigurationService
import nl.info.zac.documentcreation.exception.EpistolaDocumentCreationException
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus.STORING
import nl.info.zac.documentcreation.model.toEpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.toEpistolaTemplateData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.identity.model.getFullName
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import nl.info.zac.util.toBase64String
import java.time.LocalDate
import java.util.UUID
import java.util.logging.Logger

@ApplicationScoped
@NoArgConstructor
@AllOpen
@Suppress("LongParameterList")
class EpistolaDocumentCreationService @Inject constructor(
    private val epistolaClientService: EpistolaClientService,
    private val documentCreationDataService: DocumentCreationDataService,
    private val epistolaTemplatesService: EpistolaTemplatesService,
    private val ztcClientService: ZtcClientService,
    private val enkelvoudigInformatieObjectUpdateService: EnkelvoudigInformatieObjectUpdateService,
    private val configurationService: ConfigurationService,
    private val epistolaDocumentCreationStatusStore: EpistolaDocumentCreationStatusStore,
    private val loggedInUserInstance: Instance<LoggedInUser>
) {
    companion object {
        private const val PDF_MEDIA_TYPE = "application/pdf"
        private const val PDF_EXTENSION = ".pdf"

        private val LOG = Logger.getLogger(EpistolaDocumentCreationService::class.java.name)
    }

    /**
     * Unlike the SmartDocuments flow, generating and storing happen in the one request the behandelaar made,
     * so the policy checks of that request still apply when the document is linked to a task.
     *
     * Epistola's copy is deleted whether or not storing succeeds. A document that could not be stored is not kept:
     * the behandelaar is told, and can generate it again from the zaak.
     */
    fun createAndStoreDocument(
        zaak: Zaak,
        templateId: String,
        title: String,
        description: String?,
        taskId: String? = null
    ): ZaakInformatieObject {
        val loggedInUser = loggedInUserInstance.get()
        try {
            val informatieObjectType = ztcClientService.readInformatieobjecttype(
                epistolaTemplatesService.readInformatieobjecttypeUuid(
                    zaaktypeUuid = zaak.zaaktype.extractUuid(),
                    templateId = templateId
                )
            )
            val generatedDocument = createDocument(
                zaak = zaak,
                templateId = templateId,
                fileName = "$title$PDF_EXTENSION",
                taskId = taskId
            ) { reportStatus(loggedInUser, zaak, it.toEpistolaDocumentCreationStatus()) }
            reportStatus(loggedInUser, zaak, STORING)
            return storeDocument(
                zaak = zaak,
                templateId = templateId,
                generatedDocument = generatedDocument,
                createLockRequest = generatedDocument.toCreateLockRequest(
                    title = title,
                    description = description,
                    informatieObjectType = informatieObjectType,
                    author = loggedInUser.getFullName()
                ),
                taskId = taskId
            )
        } finally {
            epistolaDocumentCreationStatusStore.remove(userId = loggedInUser.id, zaakUuid = zaak.uuid)
        }
    }

    fun createDocument(
        zaak: Zaak,
        templateId: String,
        fileName: String,
        taskId: String? = null,
        onJobStatus: (EpistolaJobStatus) -> Unit = {}
    ): EpistolaGeneratedDocument =
        try {
            val templateData = documentCreationDataService.createEpistolaData(
                loggedInUser = loggedInUserInstance.get(),
                zaak = zaak,
                taskId = taskId
            ).toEpistolaTemplateData(
                templateId = templateId,
                templateSchema = epistolaClientService.readTemplateSchema(templateId)
            )
            LOG.fine { "Generating Epistola document from template '$templateId' for zaak '${zaak.identificatie}'" }

            epistolaClientService.generateDocument(
                templateId = templateId,
                data = templateData,
                fileName = fileName,
                correlationId = zaak.uuid.toString(),
                onJobStatus = onJobStatus
            )
        } catch (epistolaException: EpistolaException) {
            throw EpistolaDocumentCreationException(
                templateId = templateId,
                zaakIdentificatie = zaak.identificatie,
                epistolaException = epistolaException
            )
        }

    fun readStatus(zaakUuid: UUID): EpistolaDocumentCreationStatus? =
        epistolaDocumentCreationStatusStore.read(userId = loggedInUserInstance.get().id, zaakUuid = zaakUuid)

    private fun reportStatus(loggedInUser: LoggedInUser, zaak: Zaak, status: EpistolaDocumentCreationStatus) =
        epistolaDocumentCreationStatusStore.update(userId = loggedInUser.id, zaakUuid = zaak.uuid, status = status)

    /** A failure after the document is in the zaak, such as linking it to a task, is not a failure to store it. */
    @Suppress("ThrowsCount")
    private fun storeDocument(
        zaak: Zaak,
        templateId: String,
        generatedDocument: EpistolaGeneratedDocument,
        createLockRequest: EnkelvoudigInformatieObjectCreateLockRequest,
        taskId: String?
    ): ZaakInformatieObject {
        val notStoredMessage = "Epistola document '${generatedDocument.documentId}' from template '$templateId' " +
            "could not be stored in zaak '${zaak.identificatie}'"
        try {
            return enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                zaak = zaak,
                enkelvoudigInformatieObjectCreateLockRequest = createLockRequest,
                taskId = taskId
            ).also { LOG.fine { "Stored Epistola document '${generatedDocument.documentId}' in zaak '${zaak.uuid}'" } }
        } catch (zgwValidationErrorException: ZgwValidationErrorException) {
            throw notStored(
                notStoredMessage = notStoredMessage,
                failure = zgwValidationErrorException,
                diagnosis = zgwValidationErrorException.validatieFout.let { validatieFout ->
                    "HTTP ${validatieFout.status} ${validatieFout.code}, invalid: " +
                        validatieFout.invalidParams.joinToString { "${it.name} [${it.code}]" }
                },
                detail = zgwValidationErrorException.validatieFout.let { validatieFout ->
                    validatieFout.invalidParams.joinToString(separator = ", ") { it.reason }
                        .ifEmpty { validatieFout.detail }
                }
            )
        } catch (zgwRuntimeException: ZgwRuntimeException) {
            throw notStored(
                notStoredMessage = notStoredMessage,
                failure = zgwRuntimeException,
                diagnosis = zgwRuntimeException.message,
                detail = zgwRuntimeException.message
            )
        } catch (zgwErrorException: ZgwErrorException) {
            throw notStored(
                notStoredMessage = notStoredMessage,
                failure = zgwErrorException,
                diagnosis = "HTTP ${zgwErrorException.zgwError.status} ${zgwErrorException.zgwError.code}",
                detail = zgwErrorException.zgwError.toString()
            )
        } catch (processingException: ProcessingException) {
            throw notStored(
                notStoredMessage = notStoredMessage,
                failure = processingException,
                diagnosis = processingException.cause?.javaClass?.simpleName,
                detail = processingException.message
            )
        } finally {
            epistolaClientService.deleteDocument(generatedDocument.documentId)
        }
    }

    private fun notStored(notStoredMessage: String, failure: Exception, diagnosis: String?, detail: String?) =
        EpistolaDocumentNotStoredException(
            message = "$notStoredMessage: ${failure.javaClass.simpleName}" + diagnosis?.let { " ($it)" }.orEmpty(),
            detail = detail
        )

    /**
     * Stored as work in progress, as a SmartDocuments document is, so a behandelaar can still add a new
     * version. The confidentiality comes from the informatieobjecttype, as it does for a document uploaded in ZAC.
     */
    private fun EpistolaGeneratedDocument.toCreateLockRequest(
        title: String,
        description: String?,
        informatieObjectType: InformatieObjectType,
        author: String
    ) = EnkelvoudigInformatieObjectCreateLockRequest().apply {
        bronorganisatie = configurationService.readBronOrganisatie()
        creatiedatum = LocalDate.now()
        titel = title
        auteur = author
        taal = ConfigurationService.TAAL_NEDERLANDS
        beschrijving = description
        status = StatusEnum.IN_BEWERKING
        vertrouwelijkheidaanduiding = informatieObjectType.vertrouwelijkheidaanduiding
            ?.toRestVertrouwelijkheidaanduiding()
            .toDrcVertrouwelijkheidaanduidingEnum()
        informatieobjecttype = informatieObjectType.url
        bestandsnaam = this@toCreateLockRequest.fileName
        formaat = PDF_MEDIA_TYPE
        inhoud = this@toCreateLockRequest.content.toBase64String()
        bestandsomvang = this@toCreateLockRequest.content.size
    }
}
