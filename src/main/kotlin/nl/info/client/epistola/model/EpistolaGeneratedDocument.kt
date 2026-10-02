/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import java.util.UUID

/** [kanaal] is that of the variant the document was rendered in, as far as ZAC knows it. */
data class EpistolaGeneratedDocument(
    val documentId: UUID,
    val fileName: String,
    val content: ByteArray,
    val kanaal: String? = null
) {
    override fun equals(other: Any?) =
        this === other ||
            (
                other is EpistolaGeneratedDocument &&
                    documentId == other.documentId &&
                    fileName == other.fileName &&
                    content.contentEquals(other.content) &&
                    kanaal == other.kanaal
                )

    override fun hashCode(): Int {
        var result = documentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + (kanaal?.hashCode() ?: 0)
        return result
    }
}
