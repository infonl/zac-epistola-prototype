/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

/**
 * The document creation integration that ZAC uses, if any.
 *
 * ZAC supports at most one provider at a time. The providers have incompatible template models and
 * authentication schemes, and a zaaktype configuration that offered templates from both would give
 * the behandelaar no way to tell which system a template comes from.
 */
enum class DocumentCreationProvider {
    SMARTDOCUMENTS,
    EPISTOLA,
    NONE;

    companion object {
        /**
         * Matching is case-insensitive because the documented spelling (`SmartDocuments`) differs from
         * the enum constant, and operators should not have to guess which one is meant.
         */
        fun fromConfigurationValue(value: String): DocumentCreationProvider? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

        fun configurationValues(): String = entries.joinToString(", ") { it.name }
    }
}
