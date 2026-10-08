/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

/** Epistola's own catalog, which every tenant has. */
const val SYSTEM_CATALOG = "system"

/** What ZAC needs of a template to generate from it, read in one request. */
data class EpistolaGenerationTemplate(
    val dataContract: Any?
)
