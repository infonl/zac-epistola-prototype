/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import app.epistola.client.jakarta.model.TemplateSummaryDto
import jakarta.json.bind.annotation.JsonbProperty
import nl.info.client.epistola.model.EpistolaVariant
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

/**
 * What Epistola says of a template: the BCP-47 tags of the languages its variants are written in, the kanalen
 * they are made for, and the variants themselves. All three are null when Epistola could not be asked, which is not
 * the same as a template without any.
 */
@NoArgConstructor
@AllOpen
data class RestEpistolaTemplate(
    var id: String,
    var name: String,
    var locales: List<String>? = null,
    var kanalen: List<String>? = null,
    var variants: List<RestEpistolaVariant>? = null
)

/**
 * A variant of a template with the attributes that set it apart, in the order they are shown. The getter renames what
 * is written and the setter what is read, so both carry the name of [isDefault].
 */
@NoArgConstructor
@AllOpen
data class RestEpistolaVariant(
    var id: String,
    var title: String,
    @get:JsonbProperty("isDefault")
    @set:JsonbProperty("isDefault")
    var isDefault: Boolean,
    var attributes: List<RestEpistolaVariantAttribute>
)

@NoArgConstructor
@AllOpen
data class RestEpistolaVariantAttribute(
    var key: String,
    var value: String
)

fun EpistolaVariant.toRestEpistolaVariant() = RestEpistolaVariant(
    id = id,
    title = title,
    isDefault = isDefault,
    attributes = attributes.map { (key, value) -> RestEpistolaVariantAttribute(key = key, value = value) }
)

/** Servers before Epistola's contract 1.3.0 send only the deprecated `id`, which carries the same value as `slug`. */
@Suppress("DEPRECATION")
fun TemplateSummaryDto.toRestEpistolaTemplate() = RestEpistolaTemplate(id = slug ?: id, name = name)
