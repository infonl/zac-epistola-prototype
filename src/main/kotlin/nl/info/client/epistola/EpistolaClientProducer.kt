/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.EpistolaRestClients
import app.epistola.client.jakarta.api.GenerationApi
import app.epistola.client.jakarta.api.TemplatesApi
import app.epistola.client.jakarta.identity.ClientIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_API_KEY
import nl.info.zac.configuration.DocumentCreationProviderConfiguration.Companion.ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL
import nl.info.zac.util.NoArgConstructor
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Epistola's client interfaces come from a jar, so ZAC cannot annotate them with `@RegisterRestClient`
 * and builds them here instead. The producers run on first injection, not on startup, so an
 * installation that runs SmartDocuments and has none of this configured never reaches them.
 */
@ApplicationScoped
@NoArgConstructor
class EpistolaClientProducer @Inject constructor(
    // No parameter may carry a default value, or Kotlin generates a second constructor carrying
    // this one's annotations and Weld cannot choose between them.
    @ConfigProperty(name = ENV_VAR_EPISTOLA_CLIENT_MP_REST_URL)
    private val baseUri: Optional<String>,

    @ConfigProperty(name = ENV_VAR_EPISTOLA_API_KEY)
    private val apiKey: Optional<String>
) {
    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        private const val PRODUCT_NAME = "ZAC"

        /** Epistola uses the version only to attribute traffic, so an unknown one beats a wrong one. */
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
            .apiKey(requiredConfiguration(apiKey, ENV_VAR_EPISTOLA_API_KEY))
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .build()

    private fun zacVersion() =
        EpistolaClientProducer::class.java.`package`?.implementationVersion ?: UNKNOWN_VERSION

    private fun requiredConfiguration(value: Optional<String>, name: String): String =
        value.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$name is not set.")
}
