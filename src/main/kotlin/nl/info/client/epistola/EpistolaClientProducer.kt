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
import nl.info.zac.configuration.EpistolaSettings
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

/**
 * Epistola's interfaces carry `@RegisterRestClient` without a `configKey`, so WildFly would configure them from
 * properties named after each interface. ZAC builds them here from the settings it validates on startup
 * instead, and [EpistolaClient] tells the two apart.
 */
@ApplicationScoped
@NoArgConstructor
@AllOpen
class EpistolaClientProducer @Inject constructor(
    private val epistolaSettings: EpistolaSettings,

    @ConfigProperty(name = ENV_VAR_VERSION_NUMBER, defaultValue = UNKNOWN_VERSION)
    private val zacVersion: String
) {
    companion object {
        private val TEN_SECONDS: Duration = Duration.ofSeconds(10)
        private val THIRTY_SECONDS: Duration = Duration.ofSeconds(30)

        private const val EPISTOLA_ZAC_PRODUCT_NAME = "ZAC"
        private const val ENV_VAR_VERSION_NUMBER = "VERSION_NUMBER"
        private const val UNKNOWN_VERSION = "unknown"
    }

    @Produces
    @ApplicationScoped
    @EpistolaClient
    fun generationApi(): GenerationApi = restClients().api(GenerationApi::class.java)

    @Produces
    @ApplicationScoped
    @EpistolaClient
    fun templatesApi(): TemplatesApi = restClients().api(TemplatesApi::class.java)

    private fun restClients(): EpistolaRestClients =
        EpistolaRestClients.builder()
            .baseUri(epistolaSettings.restUrl)
            // sent as the User-Agent of every request, which is how Epistola tells which client and version called it
            .identity(ClientIdentity.builder().product(EPISTOLA_ZAC_PRODUCT_NAME, zacVersion).build())
            .apiKey(epistolaSettings.apiKey)
            .connectTimeout(TEN_SECONDS)
            .readTimeout(THIRTY_SECONDS)
            .build()
}
