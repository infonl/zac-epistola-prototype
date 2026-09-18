/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_WITHOUT_SCHEMA
import nl.info.zac.exception.ServerErrorException

/**
 * The template does not declare which variables it accepts.
 *
 * ZAC refuses rather than falling back to sending everything: the schema is what decides which zaak
 * data leaves ZAC, so without it there is no basis for sending any.
 */
class EpistolaTemplateSchemaMissingException(message: String) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_TEMPLATE_WITHOUT_SCHEMA,
    message = message
)
