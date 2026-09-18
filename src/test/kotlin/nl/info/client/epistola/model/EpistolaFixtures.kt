/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import app.epistola.client.jakarta.model.DocumentGenerationItemDto
import app.epistola.client.jakarta.model.GenerationJobDetail
import app.epistola.client.jakarta.model.GenerationJobResponse
import app.epistola.client.jakarta.model.TemplateDto
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
    schema: Any? = null
): TemplateDto = TemplateDto()
    .id(id)
    .name(name)
    .schema(schema)
