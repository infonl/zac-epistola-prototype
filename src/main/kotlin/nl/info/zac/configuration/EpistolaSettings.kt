/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.configuration

import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_API_KEY
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CATALOG_ID
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_TENANT_ID
import nl.info.zac.configuration.exception.InvalidDocumentCreationProviderConfigurationException

internal class EpistolaSettings(
    private val restUrl: String?,
    private val tenantId: String?,
    private val catalogId: String?,
    private val apiKey: String?
) {
    companion object {
        /** Epistola identifies a tenant and a catalog by a slug and rejects anything else on every call. */
        private val SLUG_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")
        private val TENANT_ID_LENGTH = 3..63
        private val CATALOG_ID_LENGTH = 3..50
    }

    fun verify() {
        verifyRequiredSettingsArePresent()
        verifyIsSlug(
            name = ENV_VAR_EPISTOLA_TENANT_ID,
            value = tenantId,
            length = TENANT_ID_LENGTH,
            example = "acme-corp"
        )
        verifyIsSlug(
            name = ENV_VAR_EPISTOLA_CATALOG_ID,
            value = catalogId,
            length = CATALOG_ID_LENGTH,
            example = "default"
        )
    }

    private fun verifyRequiredSettingsArePresent() {
        val missing = listOf(
            ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL to restUrl,
            ENV_VAR_EPISTOLA_TENANT_ID to tenantId,
            ENV_VAR_EPISTOLA_CATALOG_ID to catalogId,
            ENV_VAR_EPISTOLA_API_KEY to apiKey
        ).filterNot { (_, value) -> value.isConfigured() }
            .map { (name, _) -> name }
        if (missing.isNotEmpty()) {
            throw InvalidDocumentCreationProviderConfigurationException(
                "${DocumentCreationProviderConfiguration.ENV_VAR_DOCUMENT_CREATION_PROVIDER} selects Epistola " +
                    "but the following required environment variables are not set: ${missing.joinToString(", ")}."
            )
        }
    }

    private fun verifyIsSlug(name: String, value: String?, length: IntRange, example: String) {
        val slug = value.orEmpty()
        if (slug.length !in length || !SLUG_PATTERN.matches(slug)) {
            throw InvalidDocumentCreationProviderConfigurationException(
                "$name ('$slug') is not a valid Epistola identifier. " +
                    "Use a slug of ${length.first} to ${length.last} characters matching " +
                    "${SLUG_PATTERN.pattern}, for example '$example'."
            )
        }
    }

    private fun String?.isConfigured() = this?.isNotBlank() == true
}
