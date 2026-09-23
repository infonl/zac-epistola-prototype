/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.COMPLETED
import app.epistola.client.jakarta.model.DocumentGenerationItemDto.StatusEnum.FAILED
import app.epistola.client.jakarta.model.GenerateDocumentRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import nl.info.client.epistola.exception.EpistolaDocumentGenerationException
import nl.info.client.epistola.exception.EpistolaDocumentGenerationTimeoutException
import nl.info.client.epistola.model.EpistolaGeneratedDocument
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CATALOG_ID
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_GENERATION_TIMEOUT_SECONDS
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_TENANT_ID
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

/**
 * Epistola generates asynchronously. ZAC waits for the job inside the call that started it, so a
 * behandelaar gets a document or an error, and never an unfinished request to come back to.
 */
@ApplicationScoped
@NoArgConstructor
@AllOpen
class EpistolaClientService @Inject constructor(
    private val generationApi: Instance<GenerationApi>,

    private val templatesApi: Instance<TemplatesApi>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_TENANT_ID)
    private val tenantId: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_CATALOG_ID)
    private val catalogId: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_GENERATION_TIMEOUT_SECONDS)
    private val generationTimeoutSeconds: Optional<Long>
) {
    companion object {
        /**
         * Long enough for the letters ZAC generates, short enough that a stuck job does not hold a
         * request thread until the browser gives up on it.
         */
        const val DEFAULT_GENERATION_TIMEOUT_SECONDS = 60L

        private val FIRST_POLL_DELAY: Duration = Duration.ofMillis(500)
        private val MAXIMUM_POLL_DELAY: Duration = Duration.ofSeconds(5)
        private const val POLL_DELAY_FACTOR = 2L

        private val LOG = Logger.getLogger(EpistolaClientService::class.java.name)
    }

    /** Epistola keeps [correlationId] with the job, which traces a document in its audit trail back to the zaak. */
    fun generateDocument(
        templateId: String,
        data: Map<String, Any>,
        fileName: String,
        correlationId: String
    ): EpistolaGeneratedDocument {
        val tenant = readTenantId()
        val requestId = generationApi.get().generateDocument(
            tenant,
            GenerateDocumentRequest()
                .catalogId(readCatalogId())
                .templateId(templateId)
                .data(data)
                .filename(fileName)
                .correlationId(correlationId)
        ).requestId
        LOG.fine { "Epistola accepted generation request '$requestId' for template '$templateId'" }

        return downloadDocument(tenant, awaitCompletedItem(tenant, requestId), fileName)
    }

    fun readTemplateSchema(templateId: String): Any? =
        readTemplate(templateId).let { it.dataModel ?: it.schema }

    /** ZAC uses exactly one catalog, so it comes from configuration rather than from the caller. */
    fun readTemplate(templateId: String) =
        templatesApi.get().getTemplate(readTenantId(), readCatalogId(), templateId)

    @Suppress("ReturnCount")
    private fun awaitCompletedItem(tenant: String, requestId: UUID): DocumentGenerationItemDto {
        val deadline = System.nanoTime() + generationTimeout().toNanos()
        var pollDelay = FIRST_POLL_DELAY
        while (true) {
            generationApi.get().getGenerationJobStatus(tenant, requestId).items.orEmpty().firstOrNull()?.let { item ->
                when (item.status) {
                    COMPLETED -> return item
                    FAILED -> throw EpistolaDocumentGenerationException(
                        "Epistola generation request '$requestId' failed: ${item.errorMessage ?: "no reason given"}"
                    )
                    else -> Unit
                }
            }
            val remainingTime = Duration.ofNanos(deadline - System.nanoTime())
            if (remainingTime.isNegative || remainingTime.isZero) {
                throw EpistolaDocumentGenerationTimeoutException(
                    "Epistola generation request '$requestId' did not complete within " +
                        "${generationTimeout().toSeconds()} seconds."
                )
            }
            sleep(minOf(pollDelay, remainingTime))
            pollDelay = minOf(pollDelay.multipliedBy(POLL_DELAY_FACTOR), MAXIMUM_POLL_DELAY)
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
        val downloadedFile = generationApi.get().downloadDocument(tenant, documentId)
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

    private fun generationTimeout(): Duration =
        Duration.ofSeconds(generationTimeoutSeconds.getOrNull() ?: DEFAULT_GENERATION_TIMEOUT_SECONDS)

    private fun sleep(duration: Duration) =
        try {
            Thread.sleep(duration)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            throw EpistolaDocumentGenerationTimeoutException(
                "Waiting for Epistola was interrupted: ${interruptedException.message}"
            )
        }

    private fun readTenantId(): String =
        tenantId.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$ENV_VAR_EPISTOLA_TENANT_ID is not set.")

    private fun readCatalogId(): String =
        catalogId.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$ENV_VAR_EPISTOLA_CATALOG_ID is not set.")
}
