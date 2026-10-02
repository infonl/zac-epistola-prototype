/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.zac.documentcreation.model.kanaalSuggestedBy
import nl.info.zac.documentcreation.model.suggestFor
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

/**
 * No [kanalen] for a template whose variants are not made for a kanaal, and a [communicatiekanaal] only when it is what
 * suggests [voorgesteldKanaal].
 */
@AllOpen
@NoArgConstructor
data class RestEpistolaKanalen(
    val kanalen: List<String>,
    val voorgesteldKanaal: String?,
    val communicatiekanaal: String?
)

fun EpistolaKanalen.toRestEpistolaKanalen(communicatiekanaal: String?) = RestEpistolaKanalen(
    kanalen = kanalen,
    voorgesteldKanaal = suggestFor(communicatiekanaal),
    communicatiekanaal = communicatiekanaal.takeIf { kanaalSuggestedBy(it) != null }
)
