/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.COMPLETED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.GenerateDocumentRequest
import app.epistola.client.jakarta.model.TemplateSummaryDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.ProcessingException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.exception.toEpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.zac.configuration.EpistolaSettings
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

/**
 * Epistola generates documents asynchronously. ZAC waits for the job inside the call that started it, so a
 * behandelaar gets a document or an error, and never an unfinished request to come back to.
 *
 * Every failure is an [EpistolaException] that names the request and the HTTP status, and never carries Epistola's
 * response body into its message, so it can be logged as it is.
 */
@ApplicationScoped
@NoArgConstructor
@AllOpen
class EpistolaClientService @Inject constructor(
    @EpistolaClient
    private val generationApi: GenerationApi,

    @EpistolaClient
    private val templatesApi: TemplatesApi,

    private val epistolaSettings: EpistolaSettings
) {
    companion object {
        private val FIRST_POLL_DELAY: Duration = Duration.ofMillis(500)
        private val MAXIMUM_POLL_DELAY: Duration = Duration.ofSeconds(5)
        private const val POLL_DELAY_FACTOR = 2L

        /** The largest page size Epistola's contract allows. */
        private const val TEMPLATE_PAGE_SIZE = 100

        private val LOG = Logger.getLogger(EpistolaClientService::class.java.name)
    }

    /**
     * Epistola keeps [correlationId] with the job, which traces a document in its audit trail back to the zaak.
     * [onJobStatus] hears the job's status at every poll until it finishes.
     */
    fun generateDocument(
        templateId: String,
        data: Map<String, Any>,
        fileName: String,
        correlationId: String,
        onJobStatus: (EpistolaJobStatus) -> Unit = {}
    ): EpistolaGeneratedDocument {
        val tenant = epistolaSettings.tenantId
        val requestId = requestEpistola(
            request = "a generation request for template '$templateId' (correlation id '$correlationId')",
            isTemplateRequest = true
        ) {
            generationApi.generateDocument(
                tenant,
                GenerateDocumentRequest()
                    .catalogId(epistolaSettings.catalogId)
                    .templateId(templateId)
                    .data(data)
                    .filename(fileName)
                    .correlationId(correlationId)
            )
        }.requestId
        val generationRequest = "generation request '$requestId' for template '$templateId' " +
            "(correlation id '$correlationId')"
        LOG.fine { "Epistola accepted $generationRequest" }

        var isJobFinished = false
        val finishedItem = try {
            awaitFinishedItem(tenant, requestId, generationRequest, onJobStatus).also { isJobFinished = true }
        } finally {
            if (!isJobFinished) cancelGenerationJob(tenant, requestId)
        }
        if (finishedItem.status == FAILED) throw finishedItem.toGenerationFailure(generationRequest)
        return downloadDocument(tenant, finishedItem, fileName, generationRequest)
    }

    /** Epistola returns at most [TEMPLATE_PAGE_SIZE] templates per request, so a larger catalog is read page by page. */
    fun listTemplates(): List<TemplateSummaryDto> {
        val templates = mutableListOf<TemplateSummaryDto>()
        var pageNumber = 0
        do {
            val templateListResponse = requestEpistola(
                request = "listing the templates of catalog '${epistolaSettings.catalogId}'"
            ) {
                templatesApi.listTemplates(
                    epistolaSettings.tenantId,
                    epistolaSettings.catalogId,
                    null,
                    pageNumber,
                    TEMPLATE_PAGE_SIZE,
                    null,
                    null
                )
            }
            templates += templateListResponse.items.orEmpty()
            pageNumber++
        } while (pageNumber < (templateListResponse.page?.totalPages ?: 0))
        return templates
    }

    /**
     * Epistola otherwise keeps the document until its own retention removes it, months later. A failure is
     * only logged: the caller is done with the document either way, and a second attempt makes a new one.
     */
    fun deleteDocument(documentId: UUID) {
        try {
            generationApi.deleteDocument(epistolaSettings.tenantId, documentId)
        } catch (apiException: ApiException) {
            LOG.warning { "Could not delete Epistola document '$documentId': HTTP ${apiException.response?.status}" }
        } catch (processingException: ProcessingException) {
            LOG.warning { "Could not delete Epistola document '$documentId': ${processingException.message}" }
        }
    }

    fun readTemplateSchema(templateId: String): Any? =
        readTemplate(templateId).let { it.dataModel ?: it.schema }

    /** ZAC uses exactly one catalog, so it comes from configuration rather than from the caller. */
    fun readTemplate(templateId: String) =
        requestEpistola(request = "reading template '$templateId'", isTemplateRequest = true) {
            templatesApi.getTemplate(epistolaSettings.tenantId, epistolaSettings.catalogId, templateId)
        }

    private fun <T> requestEpistola(request: String, isTemplateRequest: Boolean = false, call: () -> T): T =
        try {
            call()
        } catch (apiException: ApiException) {
            throw apiException.toEpistolaRequestFailedException(request, isTemplateRequest)
        } catch (processingException: ProcessingException) {
            throw processingException.toEpistolaRequestFailedException(request)
        }

    private fun awaitFinishedItem(
        tenant: String,
        requestId: UUID,
        generationRequest: String,
        onJobStatus: (EpistolaJobStatus) -> Unit
    ): DocumentGenerationItemDto {
        val waitStart = System.nanoTime()
        val deadline = waitStart + epistolaSettings.generationTimeout.toNanos()
        var pollDelay = FIRST_POLL_DELAY
        var lastJobStatus: EpistolaJobStatus? = null
        while (true) {
            val item = requestEpistola(request = "reading the status of $generationRequest") {
                generationApi.getGenerationJobStatus(tenant, requestId)
            }.items.orEmpty().firstOrNull()
            if (item?.status == COMPLETED || item?.status == FAILED) return item
            item?.toEpistolaJobStatus(
                waited = Duration.ofNanos(System.nanoTime() - waitStart),
                heldUpAfter = epistolaSettings.jobHeldUpAfter
            )?.let {
                lastJobStatus = it
                onJobStatus(it)
            }
            val remainingTime = Duration.ofNanos(deadline - System.nanoTime())
            if (remainingTime.isNegative || remainingTime.isZero) {
                throw EpistolaDocumentGenerationTimeoutException(
                    message = "Epistola did not complete $generationRequest within " +
                        "${epistolaSettings.generationTimeout.toSeconds()} seconds; its last status was $lastJobStatus",
                    lastJobStatus = lastJobStatus
                )
            }
            sleep(minOf(pollDelay, remainingTime))
            pollDelay = minOf(pollDelay.multipliedBy(POLL_DELAY_FACTOR), MAXIMUM_POLL_DELAY)
        }
    }

    /** Left running, the job would still render a document that nobody downloads, one more on every retry. */
    private fun cancelGenerationJob(tenant: String, requestId: UUID) {
        try {
            generationApi.cancelGenerationJob(tenant, requestId)
        } catch (apiException: ApiException) {
            LOG.warning {
                "Could not cancel Epistola generation request '$requestId': HTTP ${apiException.response?.status}"
            }
        } catch (processingException: ProcessingException) {
            LOG.warning { "Could not cancel Epistola generation request '$requestId': ${processingException.message}" }
        }
    }

    private fun downloadDocument(
        tenant: String,
        item: DocumentGenerationItemDto,
        fileName: String,
        generationRequest: String
    ): EpistolaGeneratedDocument {
        val documentId = item.documentId ?: throw EpistolaDocumentGenerationException(
            "Epistola reported item '${item.id}' of $generationRequest as completed without a document id."
        )
        var isDownloaded = false
        val downloadedFile = try {
            requestEpistola(request = "downloading document '$documentId' of $generationRequest") {
                generationApi.downloadDocument(tenant, documentId)
            }.also { isDownloaded = true }
        } finally {
            if (!isDownloaded) deleteDocument(documentId)
        }
        try {
            return EpistolaGeneratedDocument(
                documentId = documentId,
                fileName = fileName,
                content = downloadedFile.readBytes()
            )
        } finally {
            if (!downloadedFile.delete()) {
                LOG.warning { "Could not remove the temporary file for Epistola document '$documentId'" }
            }
        }
    }

    private fun sleep(duration: Duration) =
        try {
            Thread.sleep(duration)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            throw EpistolaDocumentGenerationTimeoutException(
                message = "Waiting for Epistola was interrupted: ${interruptedException.message}",
                lastJobStatus = null
            )
        }
}
