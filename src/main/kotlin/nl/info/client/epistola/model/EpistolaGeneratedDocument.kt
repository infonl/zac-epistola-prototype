/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import java.util.UUID

data class EpistolaGeneratedDocument(
    val documentId: UUID,
    val fileName: String,
    val content: ByteArray
) {
    override fun equals(other: Any?) =
        this === other ||
            (
                other is EpistolaGeneratedDocument &&
                    documentId == other.documentId &&
                    fileName == other.fileName &&
                    content.contentEquals(other.content)
                )

    override fun hashCode(): Int {
        var result = documentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}
