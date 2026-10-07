/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import java.util.UUID

/** [locale] is the one ZAC asked Epistola for, and null when it asked for none. */
data class EpistolaGeneratedDocument(
    val documentId: UUID,
    val fileName: String,
    val content: ByteArray,
    val locale: String? = null
) {
    override fun equals(other: Any?) =
        this === other ||
            (
                other is EpistolaGeneratedDocument &&
                    documentId == other.documentId &&
                    fileName == other.fileName &&
                    content.contentEquals(other.content) &&
                    locale == other.locale
                )

    override fun hashCode(): Int {
        var result = documentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + (locale?.hashCode() ?: 0)
        return result
    }
}
