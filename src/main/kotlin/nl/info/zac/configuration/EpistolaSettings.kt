/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.configuration

import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_DOCUMENT_CREATION_PROVIDER
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_API_KEY
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CATALOG_ID
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_TENANT_ID
import nl.info.zac.configuration.exception.InvalidDocumentCreationProviderConfigurationException
import java.time.Duration

data class EpistolaSettings(
    val restUrl: String,
    val tenantId: String,
    val catalogId: String,
    val apiKey: String,
    val generationTimeout: Duration
) {
    companion object {
        /** Epistola identifies a tenant and a catalog by a slug and rejects anything else on every call. */
        private val SLUG_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")
        private val TENANT_ID_LENGTH = 3..63
        private val CATALOG_ID_LENGTH = 3..50

        fun validated(
            restUrl: String?,
            tenantId: String?,
            catalogId: String?,
            apiKey: String?,
            generationTimeout: Duration
        ): EpistolaSettings {
            verifyRequiredSettingsArePresent(
                ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL to restUrl,
                ENV_VAR_EPISTOLA_TENANT_ID to tenantId,
                ENV_VAR_EPISTOLA_CATALOG_ID to catalogId,
                ENV_VAR_EPISTOLA_API_KEY to apiKey
            )
            return EpistolaSettings(
                restUrl = restUrl.orEmpty(),
                tenantId = verifiedSlug(
                    name = ENV_VAR_EPISTOLA_TENANT_ID,
                    value = tenantId.orEmpty(),
                    length = TENANT_ID_LENGTH,
                    example = "acme-corp"
                ),
                catalogId = verifiedSlug(
                    name = ENV_VAR_EPISTOLA_CATALOG_ID,
                    value = catalogId.orEmpty(),
                    length = CATALOG_ID_LENGTH,
                    example = "default"
                ),
                apiKey = apiKey.orEmpty(),
                generationTimeout = generationTimeout
            )
        }

        private fun verifyRequiredSettingsArePresent(vararg settings: Pair<String, String?>) {
            val missing = settings.filter { (_, value) -> value.isNullOrBlank() }.map { (name, _) -> name }
            if (missing.isNotEmpty()) {
                throw InvalidDocumentCreationProviderConfigurationException(
                    "$ENV_VAR_DOCUMENT_CREATION_PROVIDER selects Epistola but the following required " +
                        "environment variables are not set: ${missing.joinToString(", ")}."
                )
            }
        }

        private fun verifiedSlug(name: String, value: String, length: IntRange, example: String): String {
            if (value.length !in length || !SLUG_PATTERN.matches(value)) {
                throw InvalidDocumentCreationProviderConfigurationException(
                    "$name ('$value') is not a valid Epistola identifier. " +
                        "Use a slug of ${length.first} to ${length.last} characters matching " +
                        "${SLUG_PATTERN.pattern}, for example '$example'."
                )
            }
            return value
        }
    }

    /** Keeps the API key out of logs and exception messages that print these settings. */
    override fun toString() =
        "EpistolaSettings(restUrl=$restUrl, tenantId=$tenantId, catalogId=$catalogId, apiKey=<hidden>, " +
            "generationTimeout=$generationTimeout)"
}
