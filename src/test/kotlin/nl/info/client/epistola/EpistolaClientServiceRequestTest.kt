/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.checkUnnecessaryStub
import nl.info.client.epistola.exception.EpistolaTemplateDataRejectedException
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.zac.configuration.createEpistolaSettings
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_TEMPLATE_ID = "fake-template"
private const val FAKE_TEMPLATE_ID_WITH_INVALID_DATA = "fake-template-with-invalid-data"
private const val FAKE_FILE_NAME = "fakeFileName.pdf"
private const val FAKE_CORRELATION_ID = "fakeCorrelationId"
private const val FAKE_DOCUMENT_ID = "3d9f8e7a-6b5c-4d3e-8f1a-0b9c8d7e6f5a"
private const val FAKE_REQUEST_ID = "2f1c9d64-5d8e-4a1b-9c0f-1a2b3c4d5e6f"
private const val FAKE_PDF_CONTENT = "fakePdfContent"
private const val FAKE_ZAC_VERSION = "fakeZacVersion"

class EpistolaClientServiceRequestTest : BehaviorSpec({
    val epistolaServer = FakeEpistolaServer()
    val epistolaSettings = createEpistolaSettings(
        restUrl = epistolaServer.baseUri,
        tenantId = FAKE_TENANT_ID,
        catalogId = FAKE_CATALOG_ID,
        generationTimeout = Duration.ZERO
    )
    val epistolaClientProducer = EpistolaClientProducer(
        epistolaSettings = epistolaSettings,
        zacVersion = FAKE_ZAC_VERSION
    )
    val templatesApi = epistolaClientProducer.templatesApi()

    fun createService() = EpistolaClientService(
        generationApi = epistolaClientProducer.generationApi(),
        templatesApi = templatesApi,
        epistolaSettings = epistolaSettings
    )

    afterEach { checkUnnecessaryStub() }
    afterSpec { epistolaServer.stop() }

    context("reading a template to generate from") {
        given("a configured tenant and catalog") {
            `when`("the template is read") {
                epistolaServer.clearRecordedRequests()
                val generationTemplate = createService().readGenerationTemplate(FAKE_TEMPLATE_ID)

                then("Epistola is asked for the template inside that tenant's catalog") {
                    epistolaServer.requestPaths.single() shouldBe
                        "/tenants/$FAKE_TENANT_ID/catalogs/$FAKE_CATALOG_ID/templates/$FAKE_TEMPLATE_ID"
                }

                and("the data model the template declares is returned") {
                    generationTemplate.dataContract shouldBe mapOf("properties" to mapOf("zaak" to emptyMap<String, Any>()))
                }

                and("the kanalen are read from the variants' attributes in that catalog, with that of the default variant") {
                    generationTemplate.kanalen shouldBe EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")
                }

                and("the languages are read from the variants' system locale, each with its kanalen, with that of the default variant") {
                    generationTemplate.locales shouldBe EpistolaLocales(
                        kanalenByLocale = mapOf(
                            "nl-NL" to EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post"),
                            "en-GB" to EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")
                        ),
                        defaultLocale = "nl-NL"
                    )
                }

                and("the request names ZAC and its version as the client that sent it") {
                    epistolaServer.requestUserAgents.single() shouldContain "ZAC/$FAKE_ZAC_VERSION"
                }
            }
        }
    }

    context("generating a document") {
        given("a configured tenant and catalog") {
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

                and("it asks for no variant, so Epistola renders the default one, and names no kanaal") {
                    generatedDocument.kanaal shouldBe null
                    JSONObject(epistolaServer.requestBodies.single()).has("attributes") shouldBe false
                }

                and("the rendered document is returned") {
                    generatedDocument.content.toString(StandardCharsets.UTF_8) shouldBe FAKE_PDF_CONTENT
                }
            }

            `when`("a document is generated for a kanaal") {
                epistolaServer.clearRecordedRequests()
                val generatedDocument = createService().generateDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID,
                    kanaal = "digitaal"
                )

                then("the document names the kanaal it was asked for") {
                    generatedDocument.kanaal shouldBe "digitaal"
                }

                and("the variant for that kanaal is required, and no language is asked for") {
                    val attributes = JSONObject(epistolaServer.requestBodies.single()).getJSONArray("attributes")
                    attributes.length() shouldBe 1
                    with(attributes.getJSONObject(0)) {
                        getString("catalog") shouldBe FAKE_CATALOG_ID
                        getString("key") shouldBe "kanaal"
                        getString("value") shouldBe "digitaal"
                        getBoolean("required") shouldBe true
                    }
                }
            }

            `when`("a document is generated for a kanaal in a language") {
                epistolaServer.clearRecordedRequests()
                val generatedDocument = createService().generateDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID,
                    kanaal = "post",
                    locale = "en-GB"
                )

                then("the document names the kanaal and the language it was asked for") {
                    generatedDocument.kanaal shouldBe "post"
                    generatedDocument.locale shouldBe "en-GB"
                }

                and("both the kanaal and the language are required, the language in Epistola's own catalog") {
                    val attributes = JSONObject(epistolaServer.requestBodies.single()).getJSONArray("attributes")
                    attributes.length() shouldBe 2
                    with(attributes.getJSONObject(0)) {
                        getString("catalog") shouldBe FAKE_CATALOG_ID
                        getString("key") shouldBe "kanaal"
                        getString("value") shouldBe "post"
                        getBoolean("required") shouldBe true
                    }
                    with(attributes.getJSONObject(1)) {
                        getString("catalog") shouldBe "system"
                        getString("key") shouldBe "locale"
                        getString("value") shouldBe "en-GB"
                        getBoolean("required") shouldBe true
                    }
                }
            }

            `when`("a document is generated in a language, for a template whose variants are made for no kanaal") {
                epistolaServer.clearRecordedRequests()
                createService().generateDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    fileName = FAKE_FILE_NAME,
                    correlationId = FAKE_CORRELATION_ID,
                    locale = "en-GB"
                )

                then("only the language is required") {
                    val attributes = JSONObject(epistolaServer.requestBodies.single()).getJSONArray("attributes")
                    attributes.length() shouldBe 1
                    with(attributes.getJSONObject(0)) {
                        getString("catalog") shouldBe "system"
                        getString("key") shouldBe "locale"
                        getString("value") shouldBe "en-GB"
                        getBoolean("required") shouldBe true
                    }
                }
            }
        }
    }

    context("previewing a document") {
        given("a configured tenant and catalog") {
            `when`("a preview is made for a kanaal in a language") {
                epistolaServer.clearRecordedRequests()
                val preview = createService().previewDocument(
                    templateId = FAKE_TEMPLATE_ID,
                    data = mapOf("zaak" to mapOf("identificatie" to "fakeZaakIdentificatie")),
                    kanaal = "digitaal",
                    locale = "nl-NL"
                )

                then("Epistola is asked to preview inside that tenant, without submitting a generation job") {
                    epistolaServer.requestPaths.single() shouldBe "/tenants/$FAKE_TENANT_ID/documents/preview"
                }

                and("the request names the catalog and the template, and requires the variant of the kanaal and the language") {
                    val request = JSONObject(epistolaServer.requestBodies.single())
                    request.getString("catalogId") shouldBe FAKE_CATALOG_ID
                    request.getString("templateId") shouldBe FAKE_TEMPLATE_ID
                    val attributes = request.getJSONArray("attributes")
                    attributes.getJSONObject(0).getString("value") shouldBe "digitaal"
                    attributes.getJSONObject(1).getString("value") shouldBe "nl-NL"
                    attributes.getJSONObject(1).getBoolean("required") shouldBe true
                }

                and("the PDF Epistola answers with is returned") {
                    preview.toString(StandardCharsets.UTF_8) shouldBe FAKE_PDF_CONTENT
                }
            }
        }

        given("a template whose data contract the zaak's data breaks") {
            `when`("a preview is made") {
                epistolaServer.clearRecordedRequests()
                val epistolaTemplateDataRejectedException = shouldThrow<EpistolaTemplateDataRejectedException> {
                    createService().previewDocument(templateId = FAKE_TEMPLATE_ID_WITH_INVALID_DATA, data = emptyMap())
                }

                then("the field Epistola names is read from its problem response, so the behandelaar can see it") {
                    epistolaTemplateDataRejectedException.detail shouldBe "/aanvrager: is required"
                }

                and("it is nowhere in what is logged, neither in the message nor in a cause of the client's own") {
                    generateSequence<Throwable>(epistolaTemplateDataRejectedException) { it.cause }
                        .mapNotNull { it.message }
                        .none { it.contains("aanvrager") } shouldBe true
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
    val requestUserAgents = mutableListOf<String>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requestPaths += path
            exchange.requestHeaders.getFirst("User-Agent")?.let { requestUserAgents += it }
            val requestBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            requestBody.takeIf { it.isNotEmpty() }?.let { requestBodies += it }
            respond(exchange, path, requestBody)
        }
        start()
    }

    val baseUri: String = "http://127.0.0.1:${server.address.port}"

    fun clearRecordedRequests() {
        requestPaths.clear()
        requestBodies.clear()
        requestUserAgents.clear()
    }

    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange, path: String, requestBody: String) =
        when {
            path.endsWith("/documents/preview") && requestBody.contains(FAKE_TEMPLATE_ID_WITH_INVALID_DATA) ->
                exchange.send(PROBLEM_MEDIA_TYPE, TEMPLATE_DATA_INVALID_RESPONSE, status = 400)
            path.contains("/catalogs/") -> exchange.send(EPISTOLA_MEDIA_TYPE, TEMPLATE_RESPONSE)
            path.endsWith("/documents/generate") -> exchange.send(EPISTOLA_MEDIA_TYPE, GENERATION_JOB_RESPONSE)
            path.contains("/documents/jobs/") -> exchange.send(EPISTOLA_MEDIA_TYPE, COMPLETED_JOB_RESPONSE)
            else -> exchange.send("application/pdf", FAKE_PDF_CONTENT)
        }

    private fun HttpExchange.send(contentType: String, body: String, status: Int = 200) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val EPISTOLA_MEDIA_TYPE = "application/vnd.epistola.v1+json"
        const val PROBLEM_MEDIA_TYPE = "application/problem+json"

        val TEMPLATE_DATA_INVALID_RESPONSE = """
            {
              "type": "https://epistola.app/errors/template-data-invalid",
              "title": "Template Data Invalid",
              "status": 400,
              "detail": "Data validation failed: /aanvrager: is required",
              "errors": [{ "field": "/data/aanvrager", "message": "is required", "rejectedValue": null }],
              "missingFields": [{ "path": "/aanvrager", "required": true, "schema": { "type": "object" } }],
              "invalidFields": []
            }
        """.trimIndent()

        val TEMPLATE_RESPONSE = """
            {
              "id": "$FAKE_TEMPLATE_ID",
              "tenantId": "$FAKE_TENANT_ID",
              "name": "fakeTemplateName",
              "dataModel": { "properties": { "zaak": {} } },
              "variants": [
                { "id": "initial", "title": "Per post", "isDefault": true,
                  "attributes": { "$FAKE_CATALOG_ID.kanaal": "post", "system.locale": "nl-NL" } },
                { "id": "english", "title": "English", "isDefault": false,
                  "attributes": { "$FAKE_CATALOG_ID.kanaal": "post", "system.locale": "en-GB" } },
                { "id": "digitaal", "title": "Digitaal", "isDefault": false,
                  "attributes": { "$FAKE_CATALOG_ID.kanaal": "digitaal" } },
                { "id": "other-catalog", "title": "Other catalog", "isDefault": false,
                  "attributes": { "other-catalog.kanaal": "sms" } }
              ]
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
