/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObject
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObjectWithLockRequest
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.zac.app.informatieobjecten.EnkelvoudigInformatieObjectUpdateService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus.STORING
import nl.info.zac.documentcreation.model.toEpistolaDocumentCreationStatus
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.epistola.documents.EpistolaDocumentRepository
import nl.info.zac.epistola.exception.EpistolaNewVersionNotPossibleException
import nl.info.zac.identity.model.getFullName
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import nl.info.zac.util.toBase64String
import java.util.UUID
import java.util.logging.Logger

@ApplicationScoped
@NoArgConstructor
@AllOpen
@Suppress("LongParameterList")
class EpistolaDocumentVersionService @Inject constructor(
    private val epistolaDocumentCreationService: EpistolaDocumentCreationService,
    private val epistolaClientService: EpistolaClientService,
    private val epistolaTemplatesService: EpistolaTemplatesService,
    private val epistolaDocumentRepository: EpistolaDocumentRepository,
    private val enkelvoudigInformatieObjectUpdateService: EnkelvoudigInformatieObjectUpdateService,
    private val epistolaDocumentCreationStatusStore: EpistolaDocumentCreationStatusStore,
    private val loggedInUserInstance: Instance<LoggedInUser>
) {
    companion object {
        private const val PDF_MEDIA_TYPE = "application/pdf"
        private const val NEW_VERSION_EXPLANATION = "Nieuwe versie gegenereerd met Epistola"

        private val LOG = Logger.getLogger(EpistolaDocumentVersionService::class.java.name)
    }

    /**
     * A document Epistola generated can only get a new version while Epistola is still the provider, because the
     * template it would be generated from is one Epistola holds.
     */
    fun isNewVersionAvailable(informatieObjectUUID: UUID) =
        epistolaTemplatesService.isEpistolaActive() &&
            epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID) != null

    /**
     * Generates the document again from the template that produced it, with the zaak's data as it is now, and stores
     * it as the next version of the same informatieobject. The versions before it stay in Open Zaak, and when the
     * new version cannot be stored the current one is left as it was.
     *
     * Like [EpistolaDocumentCreationService.createAndStoreDocument], it returns once the document is stored and
     * Epistola's copy is deleted.
     *
     * @throws EpistolaNewVersionNotPossibleException when the document was not generated with Epistola
     * @throws EpistolaDocumentNotStoredException when Open Zaak does not accept the new version
     */
    fun createNewVersion(
        zaak: Zaak,
        enkelvoudigInformatieObject: EnkelvoudigInformatieObject
    ): EnkelvoudigInformatieObject {
        val loggedInUser = loggedInUserInstance.get()
        val informatieObjectUUID = enkelvoudigInformatieObject.url.extractUuid()
        val templateId = epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID)?.templateId
            ?: throw EpistolaNewVersionNotPossibleException(
                "Document '$informatieObjectUUID' was not generated with Epistola, so it has no template to use."
            )
        epistolaTemplatesService.assertTemplateIsOffered(
            zaaktypeUuid = zaak.zaaktype.extractUuid(),
            templateId = templateId
        )
        try {
            val generatedDocument = epistolaDocumentCreationService.createDocument(
                zaak = zaak,
                templateId = templateId,
                fileName = enkelvoudigInformatieObject.bestandsnaam
            ) { reportStatus(loggedInUser, zaak, it.toEpistolaDocumentCreationStatus()) }
            reportStatus(loggedInUser, zaak, STORING)
            return storeNewVersion(
                zaak = zaak,
                templateId = templateId,
                informatieObjectUUID = informatieObjectUUID,
                generatedDocument = generatedDocument,
                author = loggedInUser.getFullName()
            )
        } finally {
            epistolaDocumentCreationStatusStore.remove(userId = loggedInUser.id, zaakUuid = zaak.uuid)
        }
    }

    private fun reportStatus(loggedInUser: LoggedInUser, zaak: Zaak, status: EpistolaDocumentCreationStatus) =
        epistolaDocumentCreationStatusStore.update(userId = loggedInUser.id, zaakUuid = zaak.uuid, status = status)

    private fun storeNewVersion(
        zaak: Zaak,
        templateId: String,
        informatieObjectUUID: UUID,
        generatedDocument: EpistolaGeneratedDocument,
        author: String
    ): EnkelvoudigInformatieObject =
        try {
            storingInOpenZaak(
                notStoredMessage = "Epistola document '${generatedDocument.documentId}' from template " +
                    "'$templateId' could not be stored as a new version of document '$informatieObjectUUID' " +
                    "in zaak '${zaak.identificatie}'"
            ) {
                enkelvoudigInformatieObjectUpdateService.updateEnkelvoudigInformatieObjectWithLockData(
                    enkelvoudigInformatieObjectUUID = informatieObjectUUID,
                    enkelvoudigInformatieObjectWithLockRequest = generatedDocument.toNewVersionRequest(author),
                    toelichting = NEW_VERSION_EXPLANATION
                ).also {
                    LOG.fine {
                        "Stored Epistola document '${generatedDocument.documentId}' as version ${it.versie} " +
                            "of '$informatieObjectUUID'"
                    }
                }
            }
        } finally {
            epistolaClientService.deleteDocument(generatedDocument.documentId)
        }

    private fun EpistolaGeneratedDocument.toNewVersionRequest(author: String) =
        EnkelvoudigInformatieObjectWithLockRequest().apply {
            auteur = author
            formaat = PDF_MEDIA_TYPE
            bestandsnaam = this@toNewVersionRequest.fileName
            inhoud = this@toNewVersionRequest.content.toBase64String()
            bestandsomvang = this@toNewVersionRequest.content.size
        }
}
