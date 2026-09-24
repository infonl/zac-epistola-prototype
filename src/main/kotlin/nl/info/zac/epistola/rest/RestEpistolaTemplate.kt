/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import app.epistola.client.jakarta.model.TemplateSummaryDto
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

@NoArgConstructor
@AllOpen
data class RestEpistolaTemplate(
    var id: String,
    var name: String
)

/** Servers before Epistola's contract 1.3.0 send only the deprecated `id`, which carries the same value as `slug`. */
@Suppress("DEPRECATION")
fun TemplateSummaryDto.toRestEpistolaTemplate() = RestEpistolaTemplate(id = slug ?: id, name = name)
