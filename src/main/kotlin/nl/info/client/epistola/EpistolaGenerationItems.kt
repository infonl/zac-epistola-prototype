/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.IN_PROGRESS
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.PENDING
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.EpistolaJobStatus.HELD_UP_IN_QUEUE
import nl.info.client.epistola.model.EpistolaJobStatus.HELD_UP_IN_RENDERING
import nl.info.client.epistola.model.EpistolaJobStatus.RENDERING
import nl.info.client.epistola.model.EpistolaJobStatus.WAITING_IN_QUEUE
import java.time.Duration

/** Epistola's own prefix for data that breaks the template's contract, followed by the offending fields. */
private const val DATA_VALIDATION_FAILED_PREFIX = "Data validation failed:"

/**
 * The fields Epistola names when data breaks the template's contract, in the same words for a failed job and for a
 * rejected preview, and null for any other failure. Epistola names a field as `<JSON Pointer>: <reason>`. A field at
 * the top of the data has an empty pointer, which leaves a colon in front of the reason.
 */
internal fun String.toDataRejectionDetailOrNull() =
    takeIf { it.startsWith(DATA_VALIDATION_FAILED_PREFIX) }
        ?.removePrefix(DATA_VALIDATION_FAILED_PREFIX)
        ?.trim()
        ?.removePrefix(":")
        ?.trim()

/**
 * Epistola's timestamps come from its own clock, so only the time between two of them is used: how long the job
 * waited for a render slot. How long ZAC has [waited] in all comes from ZAC's clock, and the difference is how long
 * the job has been rendering. Null for a job that has finished.
 */
internal fun DocumentGenerationItemDto.toEpistolaJobStatus(waited: Duration, heldUpAfter: Duration): EpistolaJobStatus? =
    when (status) {
        PENDING -> if (waited > heldUpAfter) HELD_UP_IN_QUEUE else WAITING_IN_QUEUE
        IN_PROGRESS -> {
            val waitedInQueue = createdAt?.let { created ->
                startedAt?.let { started -> Duration.between(created, started) }
            } ?: Duration.ZERO
            if (waited.minus(waitedInQueue) > heldUpAfter) HELD_UP_IN_RENDERING else RENDERING
        }
        else -> null
    }

internal fun sleepBeforeNextPoll(duration: Duration) =
    try {
        Thread.sleep(duration)
    } catch (interruptedException: InterruptedException) {
        Thread.currentThread().interrupt()
        throw EpistolaDocumentGenerationTimeoutException(
            message = "Waiting for Epistola was interrupted: ${interruptedException.message}",
            lastJobStatus = null
        )
    }

internal fun DocumentGenerationItemDto.toGenerationFailure(generationRequest: String): EpistolaException =
    errorMessage?.toDataRejectionDetailOrNull()?.let {
        EpistolaTemplateDataRejectedException(
            message = "Epistola rejected the data of $generationRequest against the template's contract",
            detail = it
        )
    } ?: EpistolaDocumentGenerationException(
        message = "Epistola failed to render $generationRequest",
        detail = errorMessage
    )
