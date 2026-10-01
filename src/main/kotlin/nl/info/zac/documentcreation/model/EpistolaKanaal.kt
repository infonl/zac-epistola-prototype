/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import nl.info.client.epistola.model.EpistolaKanalen

private const val KANAAL_POST = "post"
private const val KANAAL_DIGITAAL = "digitaal"

/**
 * A citizen who came to the counter or phoned gets the answer on paper. The communicatiekanalen are a reference
 * table that a beheerder can extend, so one that is not listed here gets the template's default variant.
 */
private val KANAAL_OF_COMMUNICATIEKANAAL = mapOf(
    "e-mail" to KANAAL_DIGITAAL,
    "e-formulier" to KANAAL_DIGITAAL,
    "internet" to KANAAL_DIGITAAL,
    "medewerkersportaal" to KANAAL_DIGITAAL,
    "post" to KANAAL_POST,
    "balie" to KANAAL_POST,
    "telefoon" to KANAAL_POST
)

fun EpistolaKanalen.suggestFor(communicatiekanaal: String?): String? =
    communicatiekanaal?.trim()?.lowercase()
        ?.let(KANAAL_OF_COMMUNICATIEKANAAL::get)
        ?.takeIf { it in kanalen }
        ?: defaultKanaal

fun EpistolaKanalen.choose(requestedKanaal: String?, communicatiekanaal: String?): String? =
    requestedKanaal?.takeIf { it in kanalen } ?: suggestFor(communicatiekanaal)
