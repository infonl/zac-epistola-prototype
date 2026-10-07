/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_OFFERED
import nl.info.zac.exception.InputValidationFailedException

class EpistolaTemplateNotOfferedException(message: String) : InputValidationFailedException(
    errorCode = ERROR_CODE_EPISTOLA_TEMPLATE_NOT_OFFERED,
    message = message
)
