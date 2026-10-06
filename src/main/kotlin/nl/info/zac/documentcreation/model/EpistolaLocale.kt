/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.zac.configuration.ConfigurationService
import java.util.Locale

private const val DUTCH_LOCALE = "nl-NL"
private const val DUTCH_LANGUAGE = "nl"
private const val ISO_639_2_CODE_LENGTH = 3

private val ISO_639_1_CODES = Locale.getISOLanguages().toSet()

/**
 * The Documenten API asks for the bibliographic code of ISO 639-2, where Java gives the terminology code. The two differ
 * only for these languages.
 */
private val BIBLIOGRAPHIC_CODE_OF_TERMINOLOGY_CODE = mapOf(
    "bod" to "tib",
    "ces" to "cze",
    "cym" to "wel",
    "deu" to "ger",
    "ell" to "gre",
    "eus" to "baq",
    "fas" to "per",
    "fra" to "fre",
    "hye" to "arm",
    "isl" to "ice",
    "kat" to "geo",
    "mkd" to "mac",
    "mri" to "mao",
    "msa" to "may",
    "mya" to "bur",
    "nld" to "dut",
    "ron" to "rum",
    "slk" to "slo",
    "sqi" to "alb",
    "zho" to "chi"
)

/** Dutch, also when the template has it only for another region than the Netherlands, and otherwise the default variant's. */
fun EpistolaLocales.preselectedLocale(): String? =
    locales.firstOrNull { it.equals(DUTCH_LOCALE, ignoreCase = true) }
        ?: locales.firstOrNull { Locale.forLanguageTag(it).language == DUTCH_LANGUAGE }
        ?: defaultLocale

fun EpistolaLocales.choose(requestedLocale: String?): String? =
    requestedLocale?.let { requested -> locales.firstOrNull { it.equals(requested, ignoreCase = true) } }
        ?: preselectedLocale()

/**
 * The language ZAC asks Epistola for, and offers the variants of: the [requestedLocale] when the template has it,
 * otherwise the preselected one. The behandelaar does not choose a language, so nothing requests one yet.
 */
fun EpistolaGenerationTemplate.resolveLocale(requestedLocale: String? = null): String? =
    locales.choose(requestedLocale)

fun EpistolaGenerationTemplate.kanalenIn(locale: String?): EpistolaKanalen =
    locale?.let(locales.kanalenByLocale::get) ?: kanalen

/**
 * Asked for a language, Epistola weighs only the variants in it, and two of them would tie without a kanaal, so then
 * the language's default kanaal is asked for when neither the behandelaar nor the communicatiekanaal chose one.
 */
fun EpistolaGenerationTemplate.chooseKanaal(locale: String?, requestedKanaal: String?, communicatiekanaal: String?) =
    if (locale == null) {
        kanalen.choose(requestedKanaal = requestedKanaal, communicatiekanaal = communicatiekanaal)
    } else {
        kanalenIn(locale).let {
            it.choose(requestedKanaal = requestedKanaal, communicatiekanaal = communicatiekanaal) ?: it.defaultKanaal
        }
    }

/** A document whose language ZAC did not ask for, or cannot name, is registered as Dutch, as every document was before. */
fun toInformatieobjectTaal(locale: String?): String =
    locale
        ?.let(Locale::forLanguageTag)
        ?.toIso639Part2TerminologyCode()
        ?.let { BIBLIOGRAPHIC_CODE_OF_TERMINOLOGY_CODE[it] ?: it }
        ?: ConfigurationService.TAAL_NEDERLANDS

/** Java names the three-letter code only of a two-letter code that ISO 639-1 has, and fails for any other. */
private fun Locale.toIso639Part2TerminologyCode() =
    when {
        language.length == ISO_639_2_CODE_LENGTH -> language
        language in ISO_639_1_CODES -> isO3Language
        else -> null
    }
