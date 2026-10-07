/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.model

import java.util.UUID

/**
 * What a zaaktype sets for a template of its catalog. A null [informatieObjectTypeUuid] means the template stores its
 * documents under the zaaktype's informatieobjecttype. A template that is not [isEnabled] is not offered in Document maken.
 */
data class EpistolaTemplateSetting(
    val informatieObjectTypeUuid: UUID? = null,
    val isEnabled: Boolean = true
) {
    /** A template that takes everything from the zaaktype needs no stored setting. */
    val isDefault get() = informatieObjectTypeUuid == null && isEnabled
}
