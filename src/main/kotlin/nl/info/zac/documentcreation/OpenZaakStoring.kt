/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.ws.rs.ProcessingException
import nl.info.client.zgw.shared.exception.ZgwErrorException
import nl.info.client.zgw.shared.exception.ZgwRuntimeException
import nl.info.client.zgw.shared.exception.ZgwValidationErrorException
import nl.info.zac.documentcreation.exception.EpistolaDocumentNotStoredException

/**
 * Runs [store], which puts a generated document in Open Zaak, and turns every way that can fail into
 * an [EpistolaDocumentNotStoredException]: the log gets [notStoredMessage] with what failed, and the user gets what
 * Open Zaak said.
 *
 * @throws EpistolaDocumentNotStoredException when Open Zaak or the connection to it fails while [store] runs
 */
@Suppress("ThrowsCount")
internal fun <T> storingInOpenZaak(notStoredMessage: String, store: () -> T): T {
    try {
        return store()
    } catch (zgwValidationErrorException: ZgwValidationErrorException) {
        throw notStored(
            notStoredMessage = notStoredMessage,
            failure = zgwValidationErrorException,
            diagnosis = zgwValidationErrorException.validatieFout.let { validatieFout ->
                "HTTP ${validatieFout.status} ${validatieFout.code}, invalid: " +
                    validatieFout.invalidParams.joinToString { "${it.name} [${it.code}]" }
            },
            detail = zgwValidationErrorException.validatieFout.let { validatieFout ->
                validatieFout.invalidParams.joinToString(separator = ", ") { it.reason }
                    .ifEmpty { validatieFout.detail }
            }
        )
    } catch (zgwRuntimeException: ZgwRuntimeException) {
        throw notStored(
            notStoredMessage = notStoredMessage,
            failure = zgwRuntimeException,
            diagnosis = zgwRuntimeException.message,
            detail = zgwRuntimeException.message
        )
    } catch (zgwErrorException: ZgwErrorException) {
        throw notStored(
            notStoredMessage = notStoredMessage,
            failure = zgwErrorException,
            diagnosis = "HTTP ${zgwErrorException.zgwError.status} ${zgwErrorException.zgwError.code}",
            detail = zgwErrorException.zgwError.toString()
        )
    } catch (processingException: ProcessingException) {
        throw notStored(
            notStoredMessage = notStoredMessage,
            failure = processingException,
            diagnosis = processingException.cause?.javaClass?.simpleName,
            detail = processingException.message
        )
    }
}

private fun notStored(notStoredMessage: String, failure: Exception, diagnosis: String?, detail: String?) =
    EpistolaDocumentNotStoredException(
        message = "$notStoredMessage: ${failure.javaClass.simpleName}" + diagnosis?.let { " ($it)" }.orEmpty(),
        detail = detail
    )
