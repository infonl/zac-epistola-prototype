/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.documents

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import jakarta.transaction.Transactional.TxType.REQUIRED
import jakarta.transaction.Transactional.TxType.SUPPORTS
import nl.info.zac.epistola.documents.model.EpistolaDocument
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Transactional(SUPPORTS)
@NoArgConstructor
@AllOpen
class EpistolaDocumentRepository @Inject constructor(
    private val entityManager: EntityManager
) {
    fun findEpistolaDocument(informatieObjectUUID: UUID): EpistolaDocument? =
        entityManager.find(EpistolaDocument::class.java, informatieObjectUUID)

    @Transactional(REQUIRED)
    fun createEpistolaDocument(
        informatieObjectUUID: UUID,
        catalogId: String,
        templateId: String,
        kanaal: String?,
        locale: String?
    ) =
        EpistolaDocument().apply {
            this.informatieObjectUUID = informatieObjectUUID
            this.catalogId = catalogId
            this.templateId = templateId
            this.kanaal = kanaal
            this.locale = locale
            creationDate = ZonedDateTime.now()
        }.also(entityManager::persist)
}
