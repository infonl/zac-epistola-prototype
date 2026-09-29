/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED

/** Trying again sends the same data, so this is told apart from a failure that a second attempt might not meet. */
class EpistolaTemplateDataRejectedException(message: String, detail: String) : EpistolaException(
    errorCode = ERROR_CODE_EPISTOLA_TEMPLATE_DATA_REJECTED,
    message = message,
    detail = detail
)
