/*
 * SPDX-FileCopyrightText: 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.smartdocuments.model.document

import jakarta.json.bind.annotation.JsonbProperty
import nl.info.zac.documentcreation.model.DocumentCreationData

data class Deposit(
    @field:JsonbProperty("SmartDocument")
    val smartDocument: SmartDocument,

    @field:JsonbProperty("data")
    val data: DocumentCreationData? = null
)

data class OutputFormat(
    @field:JsonbProperty("OutputFormat")
    val outputFormat: String
)

data class Selection(
    @field:JsonbProperty("TemplateGroup")
    val templateGroup: String? = null,

    @field:JsonbProperty("Template")
    val template: String? = null,

    @field:JsonbProperty("FixedValues")
    val fixedValues: String = ""
)

data class SmartDocument(
    @field:JsonbProperty("Selection")
    val selection: Selection,

    @field:JsonbProperty("Variables")
    val variables: Variables? = null
)

data class Variables(
    // specifying the document output format(s) is optional
    // if it is not specified, the document output format configured in SmartDocuments ('document uitvoer') is used instead
    @field:JsonbProperty("OutputFormats")
    val outputFormats: List<OutputFormat>? = null,

    @field:JsonbProperty("RedirectUrl")
    val redirectUrl: String,

    @field:JsonbProperty("RedirectMethod")
    val redirectMethod: String
)
