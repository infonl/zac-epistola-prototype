/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.admin.model

import jakarta.json.bind.annotation.JsonbProperty
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.util.NoArgConstructor

/**
 * The getter renames what is written and the setter what is read, so both carry the name: on the getter alone
 * JSON-B reads nothing and every save would switch Epistola off.
 */
@NoArgConstructor
data class RestEpistola(
    @get:JsonbProperty("isEnabledGlobally")
    @set:JsonbProperty("isEnabledGlobally")
    var isEnabledGlobally: Boolean,

    @get:JsonbProperty("isEnabledForZaaktype")
    @set:JsonbProperty("isEnabledForZaaktype")
    var isEnabledForZaaktype: Boolean
)

fun ZaaktypeConfiguration.toRestEpistola(isEnabledGlobally: Boolean) = RestEpistola(
    isEnabledGlobally = isEnabledGlobally,
    isEnabledForZaaktype = isEpistolaEnabled
)
