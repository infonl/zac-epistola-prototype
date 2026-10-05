/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.zac.documentcreation.model.kanaalSuggestedBy
import nl.info.zac.documentcreation.model.preselectedLocale
import nl.info.zac.documentcreation.model.suggestFor
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

/**
 * Each variant is named by the kanaal it is made for. No [varianten] for a template whose variants are not made for a
 * kanaal, and a [communicatiekanaal] only when it is what suggests [voorgesteldeVariant].
 *
 * Each of the [talen] names the variants written in it. No [talen] for a template whose variants carry no language.
 */
@AllOpen
@NoArgConstructor
data class RestEpistolaVarianten(
    val varianten: List<String>,
    val voorgesteldeVariant: String?,
    val communicatiekanaal: String?,
    val talen: List<RestEpistolaTaal>,
    val voorgesteldeTaal: String?
)

/** A language of a template, by the BCP-47 tag of its `system.locale`, with the variants in that language. */
@AllOpen
@NoArgConstructor
data class RestEpistolaTaal(
    val taal: String,
    val varianten: List<String>,
    val voorgesteldeVariant: String?
)

fun EpistolaGenerationTemplate.toRestEpistolaVarianten(communicatiekanaal: String?) = RestEpistolaVarianten(
    varianten = kanalen.kanalen,
    voorgesteldeVariant = kanalen.suggestFor(communicatiekanaal),
    communicatiekanaal = communicatiekanaal.takeIf { kanalen.kanaalSuggestedBy(it) != null },
    talen = locales.kanalenByLocale.map { (locale, kanalenInLocale) ->
        RestEpistolaTaal(
            taal = locale,
            varianten = kanalenInLocale.kanalen,
            voorgesteldeVariant = kanalenInLocale.suggestFor(communicatiekanaal)
        )
    },
    voorgesteldeTaal = locales.preselectedLocale()
)
