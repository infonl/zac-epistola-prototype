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
 * Generates a document with Epistola and hands back its bytes.
 *
 * Generation is asynchronous: the request is accepted, a job runs, and the document can be
 * downloaded once that job reports it. ZAC waits for the job inside the call that started it, so a
 * behandelaar gets either a document or an error, and never an unfinished request to come back to.
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

    /**
     * Renders [templateId] with [data] and returns the result.
     *
     * [correlationId] is echoed back by Epistola on the job item, which is what lets a document in
     * their audit trail be traced back to the zaak it was generated for.
     */
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

    /**
     * The JSON Schema that declares which variables [templateId] accepts.
     *
     * The contract carries that schema under two names. Epistola fills `dataModel`, which is also the
     * one its own import and update endpoints validate against, and leaves the older `schema` empty;
     * a server that still fills `schema` is read as a fallback rather than being treated as a template
     * without a schema at all.
     *
     * Returned as the raw schema object the contract defines, because ZAC reads only the declared
     * property names from it.
     */
    fun readTemplateSchema(templateId: String): Any? =
        readTemplate(templateId).let { it.dataModel ?: it.schema }

    /**
     * The catalog is not a parameter: Epistola carries it as a path segment on every template call and
     * ZAC has exactly one, so a per-call override could only ever be wrong or absent.
     */
    fun readTemplate(templateId: String) =
        templatesApi.get().getTemplate(readTenantId(), readCatalogId(), templateId)

    /**
     * Polls until the job reports its single item as finished, waiting longer after every
     * unsuccessful poll so that a slow job costs few requests while a quick one is still picked up
     * promptly.
     */
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
            if (System.nanoTime() >= deadline) {
                throw EpistolaDocumentGenerationTimeoutException(
                    "Epistola generation request '$requestId' did not complete within " +
                        "${generationTimeout().toSeconds()} seconds."
                )
            }
            sleep(pollDelay)
            pollDelay = minOf(pollDelay.multipliedBy(POLL_DELAY_FACTOR), MAXIMUM_POLL_DELAY)
        }
    }

    /**
     * The client materialises the download into a temporary file, so ZAC reads it and removes it
     * again; leaving it behind would accumulate documents on the application server's disk.
     */
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
            Thread.sleep(duration.toMillis())
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
