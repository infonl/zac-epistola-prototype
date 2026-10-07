/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import nl.info.zac.documentcreation.model.EpistolaTemplateInLocale
import nl.info.zac.documentcreation.model.kanaalSuggestedBy
import nl.info.zac.documentcreation.model.kanalenIn
import nl.info.zac.documentcreation.model.suggestFor
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

/**
 * Each variant is named by the kanaal it is made for, in the language ZAC asks Epistola for. No [varianten] for a
 * template whose variants are not made for a kanaal, and a [communicatiekanaal] only when it is what suggests
 * [voorgesteldeVariant].
 */
@AllOpen
@NoArgConstructor
data class RestEpistolaVarianten(
    val varianten: List<String>,
    val voorgesteldeVariant: String?,
    val communicatiekanaal: String?
)

fun EpistolaTemplateInLocale.toRestEpistolaVarianten(communicatiekanaal: String?) =
    template.kanalenIn(locale).let { kanalenInLocale ->
        RestEpistolaVarianten(
            varianten = kanalenInLocale.kanalen,
            voorgesteldeVariant = kanalenInLocale.suggestFor(communicatiekanaal),
            communicatiekanaal = communicatiekanaal.takeIf { kanalenInLocale.kanaalSuggestedBy(it) != null }
        )
    }
