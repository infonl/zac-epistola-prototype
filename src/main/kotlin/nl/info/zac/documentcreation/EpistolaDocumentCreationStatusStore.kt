/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.inject.Singleton
import nl.info.zac.documentcreation.model.EpistolaDocumentCreationStatus
import nl.info.zac.util.AllOpen
import org.apache.commons.collections4.map.LRUMap
import java.util.Collections
import java.util.UUID

/**
 * The status of the document a user is having Epistola generate for a zaak, which the dialog reads while the request
 * that generates it is still waiting. Kept in memory, as ZAC runs as a single instance: the request that writes a
 * status and the requests that read it reach the same one.
 */
@Singleton
@AllOpen
class EpistolaDocumentCreationStatusStore {
    companion object {
        /** Each entry is removed when its request ends, so this only bounds the map. */
        const val STATUS_MAP_MAX_SIZE = 1000
    }

    private data class Generation(val userId: String, val zaakUuid: UUID)

    private val statusMap: MutableMap<Generation, EpistolaDocumentCreationStatus> =
        Collections.synchronizedMap(LRUMap(STATUS_MAP_MAX_SIZE))

    fun update(userId: String, zaakUuid: UUID, status: EpistolaDocumentCreationStatus) {
        statusMap[Generation(userId, zaakUuid)] = status
    }

    fun read(userId: String, zaakUuid: UUID) = statusMap[Generation(userId, zaakUuid)]

    fun remove(userId: String, zaakUuid: UUID) {
        statusMap.remove(Generation(userId, zaakUuid))
    }
}
