/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.configuration

import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_CONSUMER_ID
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_TENANT_ID

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
    private val jwtConsumerId: String?,
    private val jwtPrivateKey: String?,
    private val jwtPrivateKeyPath: String?
) {
    companion object {
        /** Epistola identifies a tenant by a slug and rejects anything else on every call. */
        private val TENANT_ID_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")
        private val TENANT_ID_LENGTH = 3..63
    }

    fun validationFailure(): String? = missingSettings() ?: ambiguousPrivateKey() ?: malformedTenantId()

    private fun missingSettings(): String? {
        val missing = listOf(
            ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL to restUrl,
            ENV_VAR_EPISTOLA_TENANT_ID to tenantId,
            ENV_VAR_EPISTOLA_JWT_CONSUMER_ID to jwtConsumerId
        ).filterNot { (_, value) -> value.isConfigured() }
            .map { (name, _) -> name }
            .toMutableList()
        if (!jwtPrivateKey.isConfigured() && !jwtPrivateKeyPath.isConfigured()) {
            missing.add("$ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY or $ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH")
        }
        return missing.takeIf { it.isNotEmpty() }?.let {
            "${DocumentCreationProviderConfiguration.ENV_VAR_DOCUMENT_CREATION_PROVIDER} selects Epistola but the " +
                "following required environment variables are not set: ${it.joinToString(", ")}."
        }
    }

    /**
     * Configuring both an inline key and a key path is rejected rather than resolved, because the two
     * would disagree silently about which identity signs the token.
     */
    private fun ambiguousPrivateKey(): String? =
        if (jwtPrivateKey.isConfigured() && jwtPrivateKeyPath.isConfigured()) {
            "Both $ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY and $ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH are set. " +
                "Configure exactly one of them."
        } else {
            null
        }

    private fun malformedTenantId(): String? {
        val tenantId = tenantId?.trim().orEmpty()
        return if (tenantId.length in TENANT_ID_LENGTH && TENANT_ID_PATTERN.matches(tenantId)) {
            null
        } else {
            "$ENV_VAR_EPISTOLA_TENANT_ID ('$tenantId') is not a valid Epistola tenant identifier. " +
                "Use a slug of ${TENANT_ID_LENGTH.first} to ${TENANT_ID_LENGTH.last} characters matching " +
                "${TENANT_ID_PATTERN.pattern}, for example 'acme-corp'."
        }
    }

    private fun String?.isConfigured() = this?.isNotBlank() == true
}
