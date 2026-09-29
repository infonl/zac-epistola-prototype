/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ServerErrorException

/** The document was generated, but nothing was added to the zaak, and Epistola's copy is deleted. */
class EpistolaDocumentNotStoredException(message: String, cause: Throwable, detail: String?) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED,
    message = message,
    cause = cause,
    detail = detail
)
