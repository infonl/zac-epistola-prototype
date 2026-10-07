/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import app.epistola.client.jakarta.api.CatalogsApi
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.CatalogDto
import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.COMPLETED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.GenerateDocumentRequest
import app.epistola.client.jakarta.model.PreviewDocumentRequest
import app.epistola.client.jakarta.model.TemplateDto
import app.epistola.client.jakarta.model.TemplateSummaryDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.ProcessingException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.exception.EpistolaException
import nl.info.client.epistola.exception.toEpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaJobStatus
import nl.info.client.epistola.model.selectVariantFor
import nl.info.client.epistola.model.toEpistolaLocales
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

    @EpistolaClient
    private val catalogsApi: CatalogsApi,

    private val epistolaSettings: EpistolaSettings
) {
    companion object {
        private val FIRST_POLL_DELAY: Duration = Duration.ofMillis(500)

        /**
         * The dialog shows the job's status as ZAC last polled it. Epistola renders a document in a second or two, so a
         * longer delay would show a job as waiting in the queue while it renders, and notice a finished one late.
         */
        private val MAXIMUM_POLL_DELAY: Duration = Duration.ofSeconds(1)
        private const val POLL_DELAY_FACTOR = 2L

        private val LOG = Logger.getLogger(EpistolaClientService::class.java.name)
    }

    /** The catalog of a zaaktype that has none chosen, and of a document generated before ZAC remembered its catalog. */
    val defaultCatalogId: String
        get() = epistolaSettings.catalogId

    /**
     * Epistola keeps [correlationId] with the job, which traces a document in its audit trail back to the zaak.
     * Without a [locale], Epistola renders the template's default variant.
     * [onJobStatus] hears the job's status at every poll until it finishes.
     */
    @Suppress("LongParameterList")
    fun generateDocument(
        catalogId: String,
        templateId: String,
        data: Map<String, Any>,
        fileName: String,
        correlationId: String,
        locale: String? = null,
        onJobStatus: (EpistolaJobStatus) -> Unit = {}
    ): EpistolaGeneratedDocument {
        val tenant = epistolaSettings.tenantId
        val requestId = requestEpistola(
            request = "a generation request for template '$templateId' of catalog '$catalogId' " +
                "(correlation id '$correlationId')",
            isTemplateRequest = true
        ) {
            generationApi.generateDocument(
                tenant,
                GenerateDocumentRequest()
                    .catalogId(catalogId)
                    .templateId(templateId)
                    .attributes(selectVariantFor(locale))
                    .data(data)
                    .filename(fileName)
                    .correlationId(correlationId)
            )
        }.requestId
        val generationRequest = "generation request '$requestId' for template '$templateId' of catalog '$catalogId' " +
            "(correlation id '$correlationId'${locale?.let { ", locale '$it'" }.orEmpty()})"
        LOG.fine { "Epistola accepted $generationRequest" }

        var isJobFinished = false
        val finishedItem = try {
            awaitFinishedItem(tenant, requestId, generationRequest, onJobStatus).also { isJobFinished = true }
        } finally {
            if (!isJobFinished) cancelGenerationJob(tenant, requestId)
        }
        if (finishedItem.status == FAILED) throw finishedItem.toGenerationFailure(generationRequest)
        return downloadDocument(
            tenant = tenant,
            item = finishedItem,
            fileName = fileName,
            generationRequest = generationRequest
        ).copy(locale = locale)
    }

    /**
     * Epistola's preview renders at once and keeps nothing, so there is no job to wait for and no document to delete.
     * Epistola promises neither PDF/A nor a latency for it, and rate-limits it, which is why it is only for a
     * behandelaar to look at before the document is generated, never what is stored. It sends the data and chooses
     * the variant exactly as [generateDocument] does, and unlike a generation it tells straight away when the data
     * breaks the template's contract.
     */
    fun previewDocument(
        catalogId: String,
        templateId: String,
        data: Map<String, Any>,
        locale: String? = null
    ): ByteArray {
        val previewRequest = "a preview of template '$templateId' of catalog '$catalogId'" +
            locale?.let { " (locale '$it')" }.orEmpty()
        val previewFile = requestEpistola(request = previewRequest, isTemplateRequest = true) {
            generationApi.previewDocument(
                epistolaSettings.tenantId,
                PreviewDocumentRequest()
                    .catalogId(catalogId)
                    .templateId(templateId)
                    .attributes(selectVariantFor(locale))
                    .data(data)
            )
        }
        try {
            return previewFile.readBytes()
        } finally {
            if (!previewFile.delete()) {
                LOG.warning { "Could not remove the temporary file for a preview of template '$templateId'" }
            }
        }
    }

    /** The catalogs the tenant authored and those it subscribed to, Epistola's own among them. */
    fun listCatalogs(): List<CatalogDto> =
        readEveryPage { pageNumber ->
            requestEpistola(request = "listing the catalogs") {
                catalogsApi.listCatalogs(epistolaSettings.tenantId, pageNumber, EPISTOLA_PAGE_SIZE, null, null)
            }.let { it.items to it.page }
        }

    fun listTemplates(catalogId: String): List<TemplateSummaryDto> =
        readEveryPage { pageNumber ->
            requestEpistola(request = "listing the templates of catalog '$catalogId'") {
                templatesApi.listTemplates(
                    epistolaSettings.tenantId,
                    catalogId,
                    null,
                    pageNumber,
                    EPISTOLA_PAGE_SIZE,
                    null,
                    null
                )
            }.let { it.items to it.page }
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

    fun readGenerationTemplate(catalogId: String, templateId: String) =
        readTemplate(catalogId = catalogId, templateId = templateId).let {
            EpistolaGenerationTemplate(
                dataContract = it.dataModel ?: it.schema,
                locales = it.toEpistolaLocales()
            )
        }

    fun readTemplate(catalogId: String, templateId: String): TemplateDto =
        requestEpistola(request = "reading template '$templateId' of catalog '$catalogId'", isTemplateRequest = true) {
            templatesApi.getTemplate(epistolaSettings.tenantId, catalogId, templateId)
        }

    private fun <T> requestEpistola(request: String, isTemplateRequest: Boolean = false, call: () -> T): T =
        try {
            call()
        } catch (apiException: ApiException) {
            throw apiException.toEpistolaException(request, isTemplateRequest)
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
            sleepBeforeNextPoll(minOf(pollDelay, remainingTime))
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
}
