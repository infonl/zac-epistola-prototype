/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.exception

import nl.info.client.epistola.exception.EpistolaException
import nl.info.zac.exception.ServerErrorException

/**
 * Names the zaak in the one log entry a failure of Epistola produces, next to the request and HTTP status that the
 * cause names. It keeps the cause's error code, so the behandelaar sees what went wrong at Epistola.
 */
class EpistolaDocumentCreationException(
    templateId: String,
    zaakIdentificatie: String,
    epistolaException: EpistolaException,
    action: String = "create a document"
) : ServerErrorException(
    errorCode = epistolaException.errorCode,
    message = "Epistola could not $action from template '$templateId' for zaak '$zaakIdentificatie'",
    cause = epistolaException,
    detail = epistolaException.detail
)
