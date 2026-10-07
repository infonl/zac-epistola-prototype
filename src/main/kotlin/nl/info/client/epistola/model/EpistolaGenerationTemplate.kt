/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import app.epistola.client.jakarta.model.TemplateDto
import app.epistola.client.jakarta.model.VariantSelectionAttribute

/**
 * The variant attribute by which ZAC asks Epistola for a template's variant. A template author defines it in the
 * catalog ZAC uses, so a variant carries it as `<catalog>.kanaal`.
 */
const val EPISTOLA_KANAAL_ATTRIBUTE = "kanaal"

/** Epistola's own catalog, which every tenant has and which defines the locale attribute. */
private const val SYSTEM_CATALOG = "system"
private const val LOCALE_ATTRIBUTE = "locale"
private const val DUTCH_LOCALE = "nl-NL"

/** What ZAC needs of a template to generate from it, read in one request. */
data class EpistolaGenerationTemplate(
    val dataContract: Any?,
    val kanalen: EpistolaKanalen
)

/**
 * The kanalen a template's variants are made for. [defaultKanaal] is that of the variant Epistola renders when none
 * matches, and is null when that variant has no kanaal.
 */
data class EpistolaKanalen(
    val kanalen: List<String> = emptyList(),
    val defaultKanaal: String? = null
)

fun TemplateDto.toEpistolaKanalen(catalogId: String): EpistolaKanalen {
    val kanaalKey = "$catalogId.$EPISTOLA_KANAAL_ATTRIBUTE"
    return EpistolaKanalen(
        kanalen = variants.orEmpty().mapNotNull { it.attributes?.get(kanaalKey) }.distinct(),
        defaultKanaal = variants.orEmpty().firstOrNull { it.isDefault == true }?.attributes?.get(kanaalKey)
    )
}

/**
 * Dutch is a preference rather than a requirement, so that a Dutch and an English variant for the same kanaal do not
 * tie, and a template without a Dutch variant still renders.
 */
fun selectVariantFor(kanaal: String, catalogId: String) = listOf(
    VariantSelectionAttribute()
        .catalog(catalogId)
        .key(EPISTOLA_KANAAL_ATTRIBUTE)
        .value(kanaal)
        .required(true),
    VariantSelectionAttribute()
        .catalog(SYSTEM_CATALOG)
        .key(LOCALE_ATTRIBUTE)
        .value(DUTCH_LOCALE)
        .required(false)
)
