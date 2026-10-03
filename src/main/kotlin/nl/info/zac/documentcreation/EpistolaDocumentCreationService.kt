/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.persistence.PersistenceException
import jakarta.transaction.TransactionalException
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectCreateLockRequest
import nl.info.client.zgw.drc.model.generated.StatusEnum
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
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus.STORING
import nl.info.zac.documentcreation.model.choose
import nl.info.zac.documentcreation.model.toEpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.toEpistolaTemplateData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
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
    private val epistolaDocumentRepository: EpistolaDocumentRepository,
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
    @Suppress("LongParameterList")
    fun createAndStoreDocument(
        zaak: Zaak,
        templateId: String,
        title: String,
        description: String?,
        taskId: String? = null,
        kanaal: String? = null
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
                taskId = taskId,
                kanaal = kanaal
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
            ).also {
                rememberGeneration(
                    informatieObjectUUID = it.informatieobject.extractUuid(),
                    templateId = templateId,
                    kanaal = generatedDocument.kanaal
                )
            }
        } finally {
            epistolaDocumentCreationStatusStore.remove(userId = loggedInUser.id, zaakUuid = zaak.uuid)
        }
    }

    /**
     * Without a [kanaal], or with one the template has no variant for, the zaak's communicatiekanaal decides. The document
     * names the kanaal ZAC asked Epistola for, and none when it asked for none and Epistola rendered the default
     * variant, so that a new version asks for the same.
     */
    @Suppress("LongParameterList")
    fun createDocument(
        zaak: Zaak,
        templateId: String,
        fileName: String,
        taskId: String? = null,
        kanaal: String? = null,
        onJobStatus: (EpistolaJobStatus) -> Unit = {}
    ): EpistolaGeneratedDocument =
        try {
            readGenerationInput(zaak = zaak, templateId = templateId, taskId = taskId, kanaal = kanaal).let { generationInput ->
                LOG.fine { "Generating Epistola document from template '$templateId' for zaak '${zaak.identificatie}'" }
                epistolaClientService.generateDocument(
                    templateId = templateId,
                    data = generationInput.templateData,
                    fileName = fileName,
                    correlationId = zaak.uuid.toString(),
                    kanaal = generationInput.kanaal,
                    onJobStatus = onJobStatus
                )
            }
        } catch (epistolaException: EpistolaException) {
            throw EpistolaDocumentCreationException(
                templateId = templateId,
                zaakIdentificatie = zaak.identificatie,
                epistolaException = epistolaException
            )
        }

    /**
     * Renders the document as [createDocument] would, from the same data and in the same variant, so that a behandelaar
     * sees what they would get, but keeps nothing: not in Epistola, and not in the zaak.
     */
    fun previewDocument(zaak: Zaak, templateId: String, taskId: String? = null, kanaal: String? = null): ByteArray {
        epistolaTemplatesService.assertTemplateIsOffered(zaaktypeUuid = zaak.zaaktype.extractUuid(), templateId = templateId)
        return try {
            readGenerationInput(zaak = zaak, templateId = templateId, taskId = taskId, kanaal = kanaal).let { generationInput ->
                LOG.fine { "Previewing Epistola document from template '$templateId' for zaak '${zaak.identificatie}'" }
                epistolaClientService.previewDocument(
                    templateId = templateId,
                    data = generationInput.templateData,
                    kanaal = generationInput.kanaal
                )
            }
        } catch (epistolaException: EpistolaException) {
            throw EpistolaDocumentCreationException(
                templateId = templateId,
                zaakIdentificatie = zaak.identificatie,
                epistolaException = epistolaException,
                action = "preview a document"
            )
        }
    }

    private fun readGenerationInput(zaak: Zaak, templateId: String, taskId: String?, kanaal: String?): GenerationInput {
        val generationTemplate = epistolaClientService.readGenerationTemplate(templateId)
        return GenerationInput(
            templateData = documentCreationDataService.createEpistolaData(
                loggedInUser = loggedInUserInstance.get(),
                zaak = zaak,
                taskId = taskId
            ).toEpistolaTemplateData(
                templateId = templateId,
                templateSchema = generationTemplate.dataContract
            ),
            kanaal = generationTemplate.kanalen.choose(
                requestedKanaal = kanaal,
                communicatiekanaal = zaak.communicatiekanaalNaam
            )
        )
    }

    fun readKanalen(zaak: Zaak, templateId: String): EpistolaKanalen {
        epistolaTemplatesService.assertTemplateIsOffered(zaaktypeUuid = zaak.zaaktype.extractUuid(), templateId = templateId)
        return epistolaClientService.readGenerationTemplate(templateId).kanalen
    }

    fun readStatus(zaakUuid: UUID): EpistolaDocumentCreationStatus? =
        epistolaDocumentCreationStatusStore.read(userId = loggedInUserInstance.get().id, zaakUuid = zaakUuid)

    private fun reportStatus(loggedInUser: LoggedInUser, zaak: Zaak, status: EpistolaDocumentCreationStatus) =
        epistolaDocumentCreationStatusStore.update(userId = loggedInUser.id, zaakUuid = zaak.uuid, status = status)

    /** A failure after the document is in the zaak, such as linking it to a task, is not a failure to store it. */
    private fun storeDocument(
        zaak: Zaak,
        templateId: String,
        generatedDocument: EpistolaGeneratedDocument,
        createLockRequest: EnkelvoudigInformatieObjectCreateLockRequest,
        taskId: String?
    ): ZaakInformatieObject =
        try {
            storingInOpenZaak(
                notStoredMessage = "Epistola document '${generatedDocument.documentId}' from template '$templateId' " +
                    "could not be stored in zaak '${zaak.identificatie}'"
            ) {
                enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
                    zaak = zaak,
                    enkelvoudigInformatieObjectCreateLockRequest = createLockRequest,
                    taskId = taskId
                ).also { LOG.fine { "Stored Epistola document '${generatedDocument.documentId}' in zaak '${zaak.uuid}'" } }
            }
        } finally {
            epistolaClientService.deleteDocument(generatedDocument.documentId)
        }

    /**
     * Not remembering the template only costs the document its "new version" action, so it does not fail a
     * document that is already in the zaak.
     */
    private fun rememberGeneration(informatieObjectUUID: UUID, templateId: String, kanaal: String?) {
        try {
            epistolaDocumentRepository.createEpistolaDocument(
                informatieObjectUUID = informatieObjectUUID,
                templateId = templateId,
                kanaal = kanaal
            )
        } catch (persistenceException: PersistenceException) {
            LOG.warning { notRememberedMessage(informatieObjectUUID, persistenceException) }
        } catch (transactionalException: TransactionalException) {
            LOG.warning { notRememberedMessage(informatieObjectUUID, transactionalException) }
        }
    }

    private fun notRememberedMessage(informatieObjectUUID: UUID, failure: RuntimeException) =
        "Could not remember the template of Epistola document '$informatieObjectUUID': ${failure.message}"

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

    private class GenerationInput(
        val templateData: Map<String, Any>,
        val kanaal: String?
    )
}
