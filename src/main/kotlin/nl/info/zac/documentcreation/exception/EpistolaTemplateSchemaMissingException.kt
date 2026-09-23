/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_WITHOUT_SCHEMA
import nl.info.zac.exception.ServerErrorException

class EpistolaTemplateSchemaMissingException(message: String) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_TEMPLATE_WITHOUT_SCHEMA,
    message = message
)
