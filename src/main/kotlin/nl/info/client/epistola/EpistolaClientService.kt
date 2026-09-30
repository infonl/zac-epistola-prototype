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
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.zac.configuration.EpistolaSettings
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

/**
 * Epistola generates documents asynchronously. ZAC waits for the job inside the call that started it, so a
 * behandelaar gets a document or an error, and never an unfinished request to come back to.
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

    /** Epistola keeps [correlationId] with the job, which traces a document in its audit trail back to the zaak. */
    fun generateDocument(
        templateId: String,
        data: Map<String, Any>,
        fileName: String,
        correlationId: String
    ): EpistolaGeneratedDocument {
        val tenant = epistolaSettings.tenantId
        val requestId = generationApi.generateDocument(
            tenant,
            GenerateDocumentRequest()
                .catalogId(epistolaSettings.catalogId)
                .templateId(templateId)
                .data(data)
                .filename(fileName)
                .correlationId(correlationId)
        ).requestId
        LOG.fine { "Epistola accepted generation request '$requestId' for template '$templateId'" }

        var isJobFinished = false
        val finishedItem = try {
            awaitFinishedItem(tenant, requestId).also { isJobFinished = true }
        } finally {
            if (!isJobFinished) cancelGenerationJob(tenant, requestId)
        }
        if (finishedItem.status == FAILED) {
            throw EpistolaDocumentGenerationException(
                "Epistola generation request '$requestId' failed: ${finishedItem.errorMessage ?: "no reason given"}"
            )
        }
        return downloadDocument(tenant, finishedItem, fileName)
    }

    /** Epistola returns at most [TEMPLATE_PAGE_SIZE] templates per request, so a larger catalog is read page by page. */
    fun listTemplates(): List<TemplateSummaryDto> {
        val templates = mutableListOf<TemplateSummaryDto>()
        var pageNumber = 0
        do {
            val templateListResponse = templatesApi.listTemplates(
                epistolaSettings.tenantId,
                epistolaSettings.catalogId,
                null,
                pageNumber,
                TEMPLATE_PAGE_SIZE,
                null,
                null
            )
            templates += templateListResponse.items.orEmpty()
            pageNumber++
        } while (pageNumber < (templateListResponse.page?.totalPages ?: 0))
        return templates
    }

    fun readTemplateSchema(templateId: String): Any? =
        readTemplate(templateId).let { it.dataModel ?: it.schema }

    /** ZAC uses exactly one catalog, so it comes from configuration rather than from the caller. */
    fun readTemplate(templateId: String) =
        templatesApi.getTemplate(epistolaSettings.tenantId, epistolaSettings.catalogId, templateId)

    private fun awaitFinishedItem(tenant: String, requestId: UUID): DocumentGenerationItemDto {
        val deadline = System.nanoTime() + epistolaSettings.generationTimeout.toNanos()
        var pollDelay = FIRST_POLL_DELAY
        while (true) {
            generationApi.getGenerationJobStatus(tenant, requestId).items.orEmpty().firstOrNull()
                ?.takeIf { it.status == COMPLETED || it.status == FAILED }
                ?.let { return it }
            val remainingTime = Duration.ofNanos(deadline - System.nanoTime())
            if (remainingTime.isNegative || remainingTime.isZero) {
                throw EpistolaDocumentGenerationTimeoutException(
                    "Epistola generation request '$requestId' did not complete within " +
                        "${epistolaSettings.generationTimeout.toSeconds()} seconds."
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
        fileName: String
    ): EpistolaGeneratedDocument {
        val documentId = item.documentId ?: throw EpistolaDocumentGenerationException(
            "Epistola reported generation item '${item.id}' as completed without a document id."
        )
        val downloadedFile = generationApi.downloadDocument(tenant, documentId)
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
                "Waiting for Epistola was interrupted: ${interruptedException.message}"
            )
        }
}
