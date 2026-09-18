/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.EpistolaRestClients
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.auth.JwtSigner
import app.epistola.client.jakarta.identity.ClientIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_CONSUMER_ID
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH
import nl.info.zac.util.NoArgConstructor
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.file.Path
import java.security.PrivateKey
import java.time.Duration
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Builds Epistola's API clients.
 *
 * Epistola's client interfaces come from a jar, so ZAC cannot annotate them with
 * `@RegisterRestClient` the way it does for the clients it generates itself. They are built
 * programmatically instead, which also keeps the timeouts and the signing identity in one place.
 *
 * The producer methods run on first injection rather than on startup, so an installation that runs
 * SmartDocuments — and therefore has none of this configured — never reaches them.
 */
@ApplicationScoped
@NoArgConstructor
class EpistolaClientProducer @Inject constructor(
    // No parameter may carry a default value, or Kotlin generates a second constructor carrying
    // this one's annotations and Weld cannot choose between them.
    @ConfigProperty(name = ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL)
    private val baseUri: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_JWT_CONSUMER_ID)
    private val consumerId: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY)
    private val privateKeyPem: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH)
    private val privateKeyPath: Optional<String>
) {
    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        /**
         * Epistola rejects a token older than its own maximum lifetime. Sixty seconds is what the
         * contract documents, and it is short enough that a captured token is worthless.
         */
        private val JWT_TOKEN_LIFETIME: Duration = Duration.ofSeconds(60)

        private const val PRODUCT_NAME = "ZAC"

        /**
         * ZAC does not carry its version at runtime outside a packaged deployment, and Epistola
         * uses this only to attribute traffic, so an unknown version is better than a wrong one.
         */
        private const val UNKNOWN_VERSION = "unknown"
    }

    @Produces
    @ApplicationScoped
    fun generationApi(): GenerationApi = restClients().api(GenerationApi::class.java)

    @Produces
    @ApplicationScoped
    fun templatesApi(): TemplatesApi = restClients().api(TemplatesApi::class.java)

    private fun restClients(): EpistolaRestClients =
        EpistolaRestClients.builder()
            .baseUri(requiredConfiguration(baseUri, ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL))
            .identity(ClientIdentity.builder().product(PRODUCT_NAME, zacVersion()).build())
            .jwtSigner(
                JwtSigner.builder()
                    .consumerId(requiredConfiguration(consumerId, ENV_VAR_EPISTOLA_JWT_CONSUMER_ID))
                    .privateKey(readPrivateKey())
                    .tokenLifetime(JWT_TOKEN_LIFETIME)
                    .build()
            )
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .build()

    /**
     * Which of the two key settings is used is decided here and nowhere else; configuring both is
     * already rejected on startup.
     */
    private fun readPrivateKey(): PrivateKey =
        privateKeyPem.getOrNull()?.takeIf { it.isNotBlank() }?.let(JwtSigner::parsePrivateKeyPem)
            ?: privateKeyPath.getOrNull()?.takeIf { it.isNotBlank() }?.let { JwtSigner.loadPrivateKey(Path.of(it)) }
            ?: throw IllegalStateException(
                "Neither $ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY nor $ENV_VAR_EPISTOLA_JWT_PRIVATE_KEY_PATH is set."
            )

    private fun zacVersion() =
        EpistolaClientProducer::class.java.`package`?.implementationVersion ?: UNKNOWN_VERSION

    private fun requiredConfiguration(value: Optional<String>, name: String): String =
        value.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$name is not set.")
}
