/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.GenerationJobDetail
import app.epistola.client.jakarta.model.GenerationJobResponse
import app.epistola.client.jakarta.model.PageMeta
import app.epistola.client.jakarta.model.TemplateDto
import app.epistola.client.jakarta.model.TemplateListResponse
import app.epistola.client.jakarta.model.TemplateSummaryDto
import java.util.UUID

fun createGenerationJobResponse(
    requestId: UUID = UUID.randomUUID(),
    status: GenerationJobResponse.StatusEnum = GenerationJobResponse.StatusEnum.PENDING
): GenerationJobResponse = GenerationJobResponse()
    .requestId(requestId)
    .status(status)

fun createDocumentGenerationItem(
    id: UUID = UUID.randomUUID(),
    status: DocumentGenerationItemDto.StatusEnum = DocumentGenerationItemDto.StatusEnum.COMPLETED,
    documentId: UUID? = UUID.randomUUID(),
    correlationId: String = "fakeCorrelationId",
    errorMessage: String? = null
): DocumentGenerationItemDto = DocumentGenerationItemDto()
    .id(id)
    .status(status)
    .documentId(documentId)
    .correlationId(correlationId)
    .errorMessage(errorMessage)

fun createGenerationJobDetail(
    items: List<DocumentGenerationItemDto> = listOf(createDocumentGenerationItem())
): GenerationJobDetail = GenerationJobDetail().items(items)

fun createTemplate(
    id: String = "fakeTemplateId",
    name: String = "fakeTemplateName",
    schema: Any? = null,
    dataModel: Any? = null
): TemplateDto = TemplateDto()
    .id(id)
    .name(name)
    .schema(schema)
    .dataModel(dataModel)

fun createTemplateSummary(
    id: String = "fake-template-id",
    slug: String? = id,
    name: String = "fakeTemplateName"
): TemplateSummaryDto = TemplateSummaryDto()
    .id(id)
    .slug(slug)
    .tenantId("fake-tenant")
    .name(name)

fun createTemplateListResponse(
    items: List<TemplateSummaryDto> = listOf(createTemplateSummary()),
    pageNumber: Int = 0,
    totalPages: Int? = 1
): TemplateListResponse = TemplateListResponse()
    .items(items)
    .page(PageMeta().number(pageNumber).totalPages(totalPages))
