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
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.documentcreation.converter.toEpistolaTemplateData
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.logging.Logger

/**
 * Leaves storing the document in the zaakregistratie to the caller, so that a preview or a retry does
 * not add a second document to the dossier.
 */
@ApplicationScoped
@NoArgConstructor
@AllOpen
class EpistolaDocumentCreationService @Inject constructor(
    private val epistolaClientService: EpistolaClientService,
    private val documentCreationDataService: DocumentCreationDataService,
    private val loggedInUserInstance: Instance<LoggedInUser>
) {
    companion object {
        private val LOG = Logger.getLogger(EpistolaDocumentCreationService::class.java.name)
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
}
