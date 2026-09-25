/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_NOT_CONFIGURED
import nl.info.zac.exception.InputValidationFailedException

class EpistolaTemplateNotConfiguredException(message: String) : InputValidationFailedException(
    errorCode = ERROR_CODE_EPISTOLA_TEMPLATE_NOT_CONFIGURED,
    message = message
)
