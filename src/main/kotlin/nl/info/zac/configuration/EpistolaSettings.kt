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

/**
 * What ZAC needs in order to talk to Epistola, and what makes that configuration usable.
 *
 * Kept apart from [DocumentCreationProviderConfiguration] so that the rules for one provider do not
 * grow inside the class that decides which provider is active. The checks run on startup, because
 * each of them would otherwise surface as a failed document for the first behandelaar who tries one.
 */
internal class EpistolaSettings(
    private val restUrl: String?,
    private val tenantId: String?,
    private val catalogId: String?,
    private val apiKey: String?
) {
    fun verify() {
        verifyRequiredSettingsArePresent()
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

    private fun String?.isConfigured() = this?.isNotBlank() == true
}
