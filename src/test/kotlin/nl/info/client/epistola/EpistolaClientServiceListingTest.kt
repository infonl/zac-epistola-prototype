/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.api.ApiException
import app.epistola.client.jakarta.api.CatalogsApi
import app.epistola.client.jakarta.api.TemplatesApi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.core.Response
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.createCatalog
import nl.info.client.epistola.model.createCatalogListResponse
import nl.info.client.epistola.model.createTemplateListResponse
import nl.info.client.epistola.model.createTemplateSummary
import nl.info.zac.configuration.createEpistolaSettings
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_ACCESS_DENIED
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import java.net.ConnectException

private const val FAKE_TENANT_ID = "fake-tenant"
private const val FAKE_CATALOG_ID = "fake-catalog"
private const val FAKE_DEFAULT_CATALOG_ID = "fake-default-catalog"

class EpistolaClientServiceListingTest : BehaviorSpec({
    val templatesApi = mockk<TemplatesApi>()
    val catalogsApi = mockk<CatalogsApi>()

    fun createService() = EpistolaClientService(
        generationApi = mockk(),
        templatesApi = templatesApi,
        catalogsApi = catalogsApi,
        epistolaSettings = createEpistolaSettings(tenantId = FAKE_TENANT_ID, catalogId = FAKE_DEFAULT_CATALOG_ID)
    )

    fun createApiException(status: Int) = ApiException(mockk<Response> { every { this@mockk.status } returns status })

    afterEach { checkUnnecessaryStub() }

    context("listing templates") {
        given("a catalog that fits on one page") {
            val templateSummary = createTemplateSummary()
            every {
                templatesApi.listTemplates(FAKE_TENANT_ID, FAKE_CATALOG_ID, null, 0, 100, null, null)
            } returns createTemplateListResponse(items = listOf(templateSummary), totalPages = 1)

            `when`("the templates are listed") {
                val templates = createService().listTemplates(FAKE_CATALOG_ID)

                then("the one page is read, at the largest page size Epistola allows") {
                    templates shouldBe listOf(templateSummary)
                    verify(exactly = 1) { templatesApi.listTemplates(any(), any(), any(), any(), any(), any(), any()) }
                }
            }
        }

        given("a catalog spread over two pages") {
            val firstTemplateSummary = createTemplateSummary(id = "fake-template-1")
            val secondTemplateSummary = createTemplateSummary(id = "fake-template-2")
            every {
                templatesApi.listTemplates(FAKE_TENANT_ID, FAKE_CATALOG_ID, null, 0, 100, null, null)
            } returns createTemplateListResponse(items = listOf(firstTemplateSummary), pageNumber = 0, totalPages = 2)
            every {
                templatesApi.listTemplates(FAKE_TENANT_ID, FAKE_CATALOG_ID, null, 1, 100, null, null)
            } returns createTemplateListResponse(items = listOf(secondTemplateSummary), pageNumber = 1, totalPages = 2)

            `when`("the templates are listed") {
                val templates = createService().listTemplates(FAKE_CATALOG_ID)

                then("the templates of both pages are returned, so none past the first page is left out") {
                    templates shouldBe listOf(firstTemplateSummary, secondTemplateSummary)
                }
            }
        }

        given("an empty catalog whose response carries no page information") {
            every {
                templatesApi.listTemplates(FAKE_TENANT_ID, FAKE_CATALOG_ID, null, 0, 100, null, null)
            } returns createTemplateListResponse(items = emptyList(), totalPages = null)

            `when`("the templates are listed") {
                val templates = createService().listTemplates(FAKE_CATALOG_ID)

                then("no templates are returned and no further page is requested") {
                    templates shouldBe emptyList()
                    verify(exactly = 1) { templatesApi.listTemplates(any(), any(), any(), any(), any(), any(), any()) }
                }
            }
        }
    }

    context("listing catalogs") {
        given("a tenant whose catalogs are spread over two pages") {
            every { catalogsApi.listCatalogs(FAKE_TENANT_ID, 0, 100, null, null) } returns createCatalogListResponse(
                items = listOf(createCatalog(slug = "fake-catalog-1")),
                pageNumber = 0,
                totalPages = 2
            )
            every { catalogsApi.listCatalogs(FAKE_TENANT_ID, 1, 100, null, null) } returns createCatalogListResponse(
                items = listOf(createCatalog(slug = "fake-catalog-2")),
                pageNumber = 1,
                totalPages = 2
            )

            `when`("the catalogs are listed") {
                val catalogs = createService().listCatalogs()

                then("the catalogs of both pages are returned") {
                    catalogs.map { it.slug } shouldBe listOf("fake-catalog-1", "fake-catalog-2")
                }
            }
        }

        given("a tenant whose catalogs Epistola refuses to show") {
            every { catalogsApi.listCatalogs(FAKE_TENANT_ID, 0, 100, null, null) } throws createApiException(403)

            `when`("the catalogs are listed") {
                val epistolaRequestFailedException = shouldThrow<EpistolaRequestFailedException> {
                    createService().listCatalogs()
                }

                then("ZAC counts as having no access, since its API key lacks the permission to view catalogs") {
                    epistolaRequestFailedException.errorCode shouldBe ERROR_CODE_EPISTOLA_ACCESS_DENIED
                    epistolaRequestFailedException.message shouldBe "Epistola answered HTTP 403 to listing the catalogs"
                }
            }
        }
    }

    context("the catalog of a zaaktype that has none chosen") {
        given("Epistola's settings") {
            `when`("the default catalog is read") {
                val defaultCatalogId = createService().defaultCatalogId

                then("it is the catalog of the settings") {
                    defaultCatalogId shouldBe FAKE_DEFAULT_CATALOG_ID
                }
            }
        }
    }

    context("listing templates while Epistola cannot be reached") {
        given("a connection that Epistola's host refuses") {
            every {
                templatesApi.listTemplates(FAKE_TENANT_ID, FAKE_CATALOG_ID, null, 0, 100, null, null)
            } throws ProcessingException(ConnectException("fakeConnectionRefused"))

            `when`("the templates are listed") {
                val exception = shouldThrow<EpistolaRequestFailedException> {
                    createService().listTemplates(FAKE_CATALOG_ID)
                }

                then("Epistola counts as unavailable, instead of the catalog as empty") {
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_UNAVAILABLE
                    exception.message shouldContain FAKE_CATALOG_ID
                }
            }
        }
    }
})
