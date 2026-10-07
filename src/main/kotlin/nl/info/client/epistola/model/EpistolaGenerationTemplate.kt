/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import app.epistola.client.jakarta.model.TemplateDto
import app.epistola.client.jakarta.model.VariantSelectionAttribute
import app.epistola.client.jakarta.model.VariantSummaryDto

/** Epistola's own catalog, which every tenant has and which defines the locale attribute. */
const val SYSTEM_CATALOG = "system"
private const val LOCALE_ATTRIBUTE = "locale"

/**
 * Epistola also accepts a bare `locale` key on a variant, but recommends this one. A variant that carries only the bare
 * key counts as having no language, because ZAC would have to ask for it under that other key.
 */
private const val LOCALE_KEY = "$SYSTEM_CATALOG.$LOCALE_ATTRIBUTE"

/** What ZAC needs of a template to generate from it, read in one request. */
data class EpistolaGenerationTemplate(
    val dataContract: Any?,
    val locales: EpistolaLocales = EpistolaLocales()
)

/**
 * The languages a template's variants are written in, by the BCP-47 tag of their `system.locale`. [defaultLocale] is
 * that of the variant Epistola renders when none matches.
 */
data class EpistolaLocales(
    val locales: List<String> = emptyList(),
    val defaultLocale: String? = null
)

fun TemplateDto.toEpistolaLocales() = EpistolaLocales(
    locales = variants.orEmpty().mapNotNull { it.locale }.distinct(),
    defaultLocale = variants.orEmpty().firstOrNull { it.isDefault == true }?.locale
)

private val VariantSummaryDto.locale get() = attributes?.get(LOCALE_KEY)?.takeIf { it.isNotBlank() }

/**
 * The language is required. Epistola falls back to the template's default variant when no variant has it, so ZAC only
 * asks for a language that one of the template's variants has. Asking for none sends no attributes, and renders the
 * default variant.
 */
fun selectVariantFor(locale: String?) = locale?.let {
    listOf(
        VariantSelectionAttribute()
            .catalog(SYSTEM_CATALOG)
            .key(LOCALE_ATTRIBUTE)
            .value(it)
            .required(true)
    )
}
