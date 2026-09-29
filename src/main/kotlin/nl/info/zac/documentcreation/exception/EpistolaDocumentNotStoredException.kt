/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED
import nl.info.zac.exception.ServerErrorException

/**
 * The document was generated, but nothing was added to the zaak, and Epistola's copy is deleted.
 *
 * It has no cause: what Open Zaak said about the document is in [detail], for the user only, and the exceptions of
 * the Open Zaak clients carry it in their messages, so chaining one would put it in the log.
 */
class EpistolaDocumentNotStoredException(message: String, detail: String?) : ServerErrorException(
    errorCode = ERROR_CODE_EPISTOLA_DOCUMENT_NOT_STORED,
    message = message,
    detail = detail
)
