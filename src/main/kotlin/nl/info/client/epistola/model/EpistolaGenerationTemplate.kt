/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import app.epistola.client.jakarta.model.TemplateDto
import app.epistola.client.jakarta.model.VariantSelectionAttribute
import app.epistola.client.jakarta.model.VariantSummaryDto

/**
 * The variant attribute by which ZAC asks Epistola for a template's variant. A template author defines it in the
 * template's own catalog, so a variant carries it as `<catalog>.kanaal`.
 */
const val EPISTOLA_KANAAL_ATTRIBUTE = "kanaal"

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
    val kanalen: EpistolaKanalen,
    val locales: EpistolaLocales = EpistolaLocales()
)

/**
 * The kanalen a template's variants are made for. [defaultKanaal] is that of the variant Epistola renders when none
 * matches, and is null when that variant has no kanaal.
 */
data class EpistolaKanalen(
    val kanalen: List<String> = emptyList(),
    val defaultKanaal: String? = null
)

/**
 * The languages a template's variants are written in, by the BCP-47 tag of their `system.locale`, each with the kanalen
 * of the variants in it. [defaultLocale] is that of the variant Epistola renders when none matches.
 *
 * Within a language, the default kanaal is that of the template's default variant when it is written in that language,
 * and otherwise the language's only kanaal. Asked for a language, Epistola weighs only the variants in it, so that
 * kanaal is what singles out one of them.
 */
data class EpistolaLocales(
    val kanalenByLocale: Map<String, EpistolaKanalen> = emptyMap(),
    val defaultLocale: String? = null
) {
    val locales get() = kanalenByLocale.keys.toList()
}

fun TemplateDto.toEpistolaKanalen(catalogId: String): EpistolaKanalen {
    val kanaalKey = "$catalogId.$EPISTOLA_KANAAL_ATTRIBUTE"
    return EpistolaKanalen(
        kanalen = variants.orEmpty().mapNotNull { it.attributes?.get(kanaalKey) }.distinct(),
        defaultKanaal = variants.orEmpty().firstOrNull { it.isDefault == true }?.attributes?.get(kanaalKey)
    )
}

fun TemplateDto.toEpistolaLocales(catalogId: String): EpistolaLocales {
    val kanaalKey = "$catalogId.$EPISTOLA_KANAAL_ATTRIBUTE"
    val defaultVariant = variants.orEmpty().firstOrNull { it.isDefault == true }
    return EpistolaLocales(
        kanalenByLocale = variants.orEmpty()
            .mapNotNull { variant -> variant.locale?.let { it to variant } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (locale, localeVariants) ->
                val kanalen = localeVariants.mapNotNull { it.attributes?.get(kanaalKey) }.distinct()
                EpistolaKanalen(
                    kanalen = kanalen,
                    defaultKanaal = defaultVariant?.takeIf { it.locale == locale }?.attributes?.get(kanaalKey)
                        ?: kanalen.singleOrNull()
                )
            },
        defaultLocale = defaultVariant?.locale
    )
}

private val VariantSummaryDto.locale get() = attributes?.get(LOCALE_KEY)?.takeIf { it.isNotBlank() }

/**
 * Both attributes are required. Epistola falls back to the template's default variant when no variant has every
 * required attribute, so ZAC only asks for a kanaal and a language that one of the template's variants has together.
 * Asking for neither sends no attributes, and renders the default variant.
 */
fun selectVariantFor(kanaal: String?, locale: String?, catalogId: String) = listOfNotNull(
    kanaal?.let {
        VariantSelectionAttribute()
            .catalog(catalogId)
            .key(EPISTOLA_KANAAL_ATTRIBUTE)
            .value(it)
            .required(true)
    },
    locale?.let {
        VariantSelectionAttribute()
            .catalog(SYSTEM_CATALOG)
            .key(LOCALE_ATTRIBUTE)
            .value(it)
            .required(true)
    }
).ifEmpty { null }
