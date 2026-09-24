/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.configuration

import java.time.Duration

fun createEpistolaSettings(
    restUrl: String = "https://epistola.example.com",
    tenantId: String = "fake-tenant",
    catalogId: String = "fake-catalog",
    apiKey: String = "fakeEpistolaApiKey",
    generationTimeout: Duration = Duration.ofSeconds(60)
) = EpistolaSettings(
    restUrl = restUrl,
    tenantId = tenantId,
    catalogId = catalogId,
    apiKey = apiKey,
    generationTimeout = generationTimeout
)
