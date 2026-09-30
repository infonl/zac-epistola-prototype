/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import jakarta.json.bind.annotation.JsonbProperty
import nl.info.zac.util.NoArgConstructor

/** Whether Epistola generated the document and is still the provider, so a new version of it can be generated. */
@NoArgConstructor
data class RestEpistolaDocument(
    @get:JsonbProperty("isNewVersionAvailable")
    @set:JsonbProperty("isNewVersionAvailable")
    var isNewVersionAvailable: Boolean
)
