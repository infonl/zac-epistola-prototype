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
import nl.info.zac.documentcreation.model.resolveLocale
import nl.info.zac.documentcreation.model.toEpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.toEpistolaTemplateData
import nl.info.zac.documentcreation.model.toInformatieobjectTaal
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
        taskId: String? = null
    ): ZaakInformatieObject {
        val loggedInUser = loggedInUserInstance.get()
        try {
            val offeredCatalog = epistolaTemplatesService.readCatalogOfferingTemplate(
                zaaktypeUuid = zaak.zaaktype.extractUuid(),
                templateId = templateId
            )
            val informatieObjectType = ztcClientService.readInformatieobjecttype(
                offeredCatalog.informatieObjectTypeUuidOf(templateId)
            )
            val generatedDocument = createDocument(
                zaak = zaak,
                catalogId = offeredCatalog.catalogId,
                templateId = templateId,
                fileName = "$title$PDF_EXTENSION",
                taskId = taskId,
                zaaktypeLocale = offeredCatalog.locale
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
                    catalogId = offeredCatalog.catalogId,
                    templateId = templateId,
                    locale = generatedDocument.locale
                )
            }
        } finally {
            epistolaDocumentCreationStatusStore.remove(userId = loggedInUser.id, zaakUuid = zaak.uuid)
        }
    }

    /**
     * The document names the language ZAC asked Epistola for, and none when it asked for none and Epistola rendered the
     * default variant, so that a new version asks for the same.
     *
     * A [taal] is the BCP-47 tag of a language of the template, such as the one a document was generated in. Without
     * one, or with one the template no longer has, ZAC asks for the [zaaktypeLocale] when the template has it, and
     * otherwise for the language it resolves for the template.
     */
    @Suppress("LongParameterList")
    fun createDocument(
        zaak: Zaak,
        catalogId: String,
        templateId: String,
        fileName: String,
        taskId: String? = null,
        taal: String? = null,
        zaaktypeLocale: String? = null,
        onJobStatus: (EpistolaJobStatus) -> Unit = {}
    ): EpistolaGeneratedDocument =
        try {
            readGenerationInput(
                zaak = zaak,
                catalogId = catalogId,
                templateId = templateId,
                taskId = taskId,
                taal = taal,
                zaaktypeLocale = zaaktypeLocale
            ).let { generationInput ->
                LOG.fine {
                    "Generating Epistola document from template '$templateId' of catalog '$catalogId' " +
                        "for zaak '${zaak.identificatie}'"
                }
                epistolaClientService.generateDocument(
                    catalogId = catalogId,
                    templateId = templateId,
                    data = generationInput.templateData,
                    fileName = fileName,
                    correlationId = zaak.uuid.toString(),
                    locale = generationInput.locale,
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
     * Renders the document as [createDocument] would, from the same data and in the same language, so that a behandelaar
     * sees what they would get, but keeps nothing: not in Epistola, and not in the zaak.
     */
    fun previewDocument(
        zaak: Zaak,
        templateId: String,
        taskId: String? = null
    ): ByteArray {
        val offeredCatalog = epistolaTemplatesService.readCatalogOfferingTemplate(
            zaaktypeUuid = zaak.zaaktype.extractUuid(),
            templateId = templateId
        )
        val catalogId = offeredCatalog.catalogId
        return try {
            readGenerationInput(
                zaak = zaak,
                catalogId = catalogId,
                templateId = templateId,
                taskId = taskId,
                taal = null,
                zaaktypeLocale = offeredCatalog.locale
            ).let { generationInput ->
                LOG.fine {
                    "Previewing Epistola document from template '$templateId' of catalog '$catalogId' " +
                        "for zaak '${zaak.identificatie}'"
                }
                epistolaClientService.previewDocument(
                    catalogId = catalogId,
                    templateId = templateId,
                    data = generationInput.templateData,
                    locale = generationInput.locale
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

    private fun readGenerationInput(
        zaak: Zaak,
        catalogId: String,
        templateId: String,
        taskId: String?,
        taal: String?,
        zaaktypeLocale: String?
    ): GenerationInput {
        val generationTemplate = epistolaClientService.readGenerationTemplate(catalogId = catalogId, templateId = templateId)
        val locale = generationTemplate.resolveLocale(requestedLocale = taal, configuredLocale = zaaktypeLocale)
        return GenerationInput(
            templateData = documentCreationDataService.createEpistolaData(
                loggedInUser = loggedInUserInstance.get(),
                zaak = zaak,
                taskId = taskId
            ).toEpistolaTemplateData(
                templateId = templateId,
                templateSchema = generationTemplate.dataContract
            ),
            locale = locale
        )
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
    private fun rememberGeneration(
        informatieObjectUUID: UUID,
        catalogId: String,
        templateId: String,
        locale: String?
    ) {
        try {
            epistolaDocumentRepository.createEpistolaDocument(
                informatieObjectUUID = informatieObjectUUID,
                catalogId = catalogId,
                templateId = templateId,
                locale = locale
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
        taal = toInformatieobjectTaal(this@toCreateLockRequest.locale)
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
        val locale: String?
    )
}
