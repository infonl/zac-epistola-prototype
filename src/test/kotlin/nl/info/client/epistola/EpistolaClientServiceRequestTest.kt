/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import jakarta.enterprise.inject.Instance
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CATALOG_ID
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Optional

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"
private const val FAKE_CORRELATION_ID = "fakeCorrelationId"
private const val FAKE_DOCUMENT_ID = "3d9f8e7a-6b5c-4d3e-8f1a-0b9c8d7e6f5a"
private const val FAKE_REQUEST_ID = "2f1c9d64-5d8e-4a1b-9c0f-1a2b3c4d5e6f"
private const val FAKE_PDF_CONTENT = "fakePdfContent"

class EpistolaClientServiceRequestTest : BehaviorSpec({
    val epistolaServer = FakeEpistolaServer()
    val epistolaClientProducer = EpistolaClientProducer(
        baseUri = Optional.of(epistolaServer.baseUri),
        apiKey = Optional.of("fakeEpistolaApiKey")
    )
    val generationApi = epistolaClientProducer.generationApi()
    val templatesApi = epistolaClientProducer.templatesApi()
    val generationApiInstance = mockk<Instance<GenerationApi>>()
    val templatesApiInstance = mockk<Instance<TemplatesApi>>()

    fun createService(catalogId: String? = FAKE_CATALOG_ID) = EpistolaClientService(
        generationApi = generationApiInstance,
        templatesApi = templatesApiInstance,
        tenantId = Optional.of(FAKE_TENANT_ID),
        catalogId = Optional.ofNullable(catalogId),
        generationTimeoutSeconds = Optional.of(0L)
    )

    afterEach { checkUnnecessaryStub() }
    afterSpec { epistolaServer.stop() }

    context("reading a template schema") {
        given("a configured tenant and catalog") {
            every { templatesApiInstance.get() } returns templatesApi

            `when`("the schema is read") {
                epistolaServer.clearRecordedRequests()
                val templateSchema = createService().readTemplateSchema(FAKE_TEMPLATE_ID)

                then("Epistola is asked for the template inside that tenant's catalog") {
                    epistolaServer.requestPaths.single() shouldBe
                        "/tenants/$FAKE_TENANT_ID/catalogs/$FAKE_CATALOG_ID/templates/$FAKE_TEMPLATE_ID"
                }

                and("the data model the template declares is returned") {
                    templateSchema shouldBe mapOf("properties" to mapOf("zaak" to emptyMap<String, Any>()))
                }
            }
        }

        given("no configured catalog") {
            every { templatesApiInstance.get() } returns templatesApi

            `when`("the schema is read") {
                epistolaServer.clearRecordedRequests()
                val illegalStateException = shouldThrow<IllegalStateException> {
                    createService(catalogId = null).readTemplateSchema(FAKE_TEMPLATE_ID)
                }

                then("the missing setting is named and Epistola is never called") {
                    illegalStateException.message shouldBe "$ENV_VAR_EPISTOLA_CATALOG_ID is not set."
                    epistolaServer.requestPaths.shouldBeEmpty()
                }
            }
        }
    }

    context("generating a document") {
        given("a configured tenant and catalog") {
            every { generationApiInstance.get() } returns generationApi

            `when`("a document is generated") {
                epistolaServer.clearRecordedRequests()
                val generatedDocument = createService().generateDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID
                )

                then("the generation request names the catalog that holds the template") {
                    JSONObject(epistolaServer.requestBodies.single()).getString("catalogId") shouldBe FAKE_CATALOG_ID
                }

                and("the rendered document is returned") {
                    generatedDocument.content.toString(StandardCharsets.UTF_8) shouldBe FAKE_PDF_CONTENT
                }
            }
        }
    }

    context("Epistola carrying the catalog as a path segment rather than as a query parameter") {
        given("a template request without a catalog") {
            `when`("the client is asked to send it") {
                epistolaServer.clearRecordedRequests()
                val nullPointerException = shouldThrow<NullPointerException> {
                    templatesApi.getTemplate(FAKE_TENANT_ID, null, FAKE_TEMPLATE_ID)
                }

                then("it fails before any request is sent, so a catalog can never be left unset") {
                    nullPointerException.message shouldContain "PathParam"
                    epistolaServer.requestPaths.shouldBeEmpty()
                }
            }
        }
    }
})

private class FakeEpistolaServer {
    val requestPaths = mutableListOf<String>()
    val requestBodies = mutableListOf<String>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requestPaths += path
            exchange.requestBody.readBytes().takeIf { it.isNotEmpty() }?.let {
                requestBodies += it.toString(StandardCharsets.UTF_8)
            }
            respond(exchange, path)
        }
        start()
    }

    val baseUri: String = "http://127.0.0.1:${server.address.port}"

    fun clearRecordedRequests() {
        requestPaths.clear()
        requestBodies.clear()
    }

    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange, path: String) =
        when {
            path.contains("/catalogs/") -> exchange.send(EPISTOLA_MEDIA_TYPE, TEMPLATE_RESPONSE)
            path.endsWith("/documents/generate") -> exchange.send(EPISTOLA_MEDIA_TYPE, GENERATION_JOB_RESPONSE)
            path.contains("/documents/jobs/") -> exchange.send(EPISTOLA_MEDIA_TYPE, COMPLETED_JOB_RESPONSE)
            else -> exchange.send("application/pdf", FAKE_PDF_CONTENT)
        }

    private fun HttpExchange.send(contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val EPISTOLA_MEDIA_TYPE = "application/vnd.epistola.v1+json"

        val TEMPLATE_RESPONSE = """
            {
              "id": "$FAKE_TEMPLATE_ID",
              "tenantId": "$FAKE_TENANT_ID",
              "name": "fakeTemplateName",
              "dataModel": { "properties": { "zaak": {} } }
            }
        """.trimIndent()

        val GENERATION_JOB_RESPONSE = """
            {
              "requestId": "$FAKE_REQUEST_ID",
              "status": "PENDING",
              "jobType": "SINGLE",
              "totalCount": 1
            }
        """.trimIndent()

        val COMPLETED_JOB_RESPONSE = """
            {
              "items": [
                {
                  "id": "7c6b5a49-3e2d-4c1b-8a09-f8e7d6c5b4a3",
                  "templateId": "$FAKE_TEMPLATE_ID",
                  "status": "COMPLETED",
                  "documentId": "$FAKE_DOCUMENT_ID",
                  "filename": "$FAKE_FILE_NAME"
                }
              ]
            }
        """.trimIndent()
    }
}
