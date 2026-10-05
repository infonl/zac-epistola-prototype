/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import app.epistola.client.jakarta.model.CatalogDto
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

@NoArgConstructor
@AllOpen
data class RestEpistolaCatalog(
    var id: String,
    var name: String
)

/** Servers before Epistola's contract 1.3.0 send only the deprecated `id`, which carries the same value as `slug`. */
@Suppress("DEPRECATION")
fun CatalogDto.toRestEpistolaCatalog() = RestEpistolaCatalog(id = slug ?: id, name = name)
