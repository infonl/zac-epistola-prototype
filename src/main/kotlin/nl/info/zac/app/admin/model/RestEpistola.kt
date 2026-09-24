/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.admin.model

import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.util.NoArgConstructor

@NoArgConstructor
data class RestEpistola(
    var enabledGlobally: Boolean,
    var enabledForZaaktype: Boolean
)

fun ZaaktypeConfiguration.toRestEpistola(enabledGlobally: Boolean) = RestEpistola(
    enabledGlobally = enabledGlobally,
    enabledForZaaktype = epistolaEnabled
)
