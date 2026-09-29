/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.exception

import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_GENERATION_FAILED

/**
 * A failure reported inside a job that Epistola accepted, so there is no HTTP status to act on. Epistola's reason is
 * the [detail], because Epistola may quote the data it was rendering.
 */
class EpistolaDocumentGenerationException(message: String, detail: String? = null) : EpistolaException(
    errorCode = ERROR_CODE_EPISTOLA_GENERATION_FAILED,
    message = message,
    detail = detail
)
