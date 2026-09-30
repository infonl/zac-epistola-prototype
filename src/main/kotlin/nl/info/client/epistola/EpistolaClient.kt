/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import jakarta.inject.Qualifier

/**
 * Selects the Epistola client that [EpistolaClientProducer] builds. The interfaces in Epistola's jar carry
 * `@RegisterRestClient`, so WildFly's MicroProfile Rest Client registers an unconfigured bean for each of
 * them too, and without this qualifier the two are ambiguous.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class EpistolaClient
