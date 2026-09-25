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
import nl.info.zac.documentcreation.model.toEpistolaTemplateData
import nl.info.zac.epistola.EpistolaTemplatesService
import nl.info.zac.identity.model.getFullName
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import nl.info.zac.util.toBase64String
import java.time.LocalDate
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
     */
    fun createAndStoreDocument(
        zaak: Zaak,
        templateId: String,
        title: String,
        description: String?,
        taskId: String? = null
    ): ZaakInformatieObject {
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
        )
        val zaakInformatieObject = enkelvoudigInformatieObjectUpdateService.createZaakInformatieobjectForZaak(
            zaak = zaak,
            enkelvoudigInformatieObjectCreateLockRequest = generatedDocument.toCreateLockRequest(
                title = title,
                description = description,
                informatieObjectType = informatieObjectType,
                author = loggedInUserInstance.get().getFullName()
            ),
            taskId = taskId
        )
        LOG.fine { "Stored Epistola document '${generatedDocument.documentId}' in zaak '${zaak.uuid}'" }
        epistolaClientService.deleteDocument(generatedDocument.documentId)
        return zaakInformatieObject
    }

    fun createDocument(
        zaak: Zaak,
        templateId: String,
        fileName: String,
        taskId: String? = null
    ): EpistolaGeneratedDocument {
        val templateData = documentCreationDataService.createEpistolaData(
            loggedInUser = loggedInUserInstance.get(),
            zaak = zaak,
            taskId = taskId
        ).toEpistolaTemplateData(
            templateId = templateId,
            templateSchema = epistolaClientService.readTemplateSchema(templateId)
        )
        LOG.fine { "Generating Epistola document from template '$templateId' for zaak '${zaak.uuid}'" }

        return epistolaClientService.generateDocument(
            templateId = templateId,
            data = templateData,
            fileName = fileName,
            correlationId = zaak.uuid.toString()
        )
    }

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
