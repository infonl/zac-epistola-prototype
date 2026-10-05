/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import nl.info.zac.util.NoArgConstructor
import java.util.UUID

@NoArgConstructor
data class RestEpistolaDocumentPreviewData(
    @field:NotNull
    var zaakUuid: UUID,

    var taskId: String? = null,

    @field:NotBlank
    var templateId: String,

    /**
     * The chosen variant, named by its kanaal. Without one, the zaak's communicatiekanaal decides which of the
     * template's variants is used.
     */
    var variant: String? = null
)
