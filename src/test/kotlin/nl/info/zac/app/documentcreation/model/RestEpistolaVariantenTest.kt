/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createDutchAndEnglishLocales
import nl.info.client.epistola.model.createGenerationTemplate
import nl.info.zac.documentcreation.model.EpistolaTemplateInLocale
import nl.info.zac.documentcreation.model.resolveLocale

private fun EpistolaGenerationTemplate.inItsResolvedLocale() = EpistolaTemplateInLocale(template = this, locale = resolveLocale())

class RestEpistolaVariantenTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("offering the variants of a template") {
        given("a template with Dutch variants by post and digitally and an English one by post, for a zaak by e-mail") {
            val generationTemplate = createGenerationTemplate(
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal", "sms"), defaultKanaal = "sms"),
                locales = createDutchAndEnglishLocales()
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.inItsResolvedLocale().toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("the variants in Dutch are offered, with the one the communicatiekanaal suggests") {
                    restEpistolaVarianten shouldBe RestEpistolaVarianten(
                        varianten = listOf("post", "digitaal"),
                        voorgesteldeVariant = "digitaal",
                        communicatiekanaal = "E-mail"
                    )
                }
            }
        }

        given("a template with a Dutch variant by post only and an English one digitally, for a zaak by e-mail") {
            val generationTemplate = createGenerationTemplate(
                locales = EpistolaLocales(
                    kanalenByLocale = mapOf(
                        "nl-NL" to EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post"),
                        "en-GB" to EpistolaKanalen(kanalen = listOf("digitaal"), defaultKanaal = "digitaal")
                    ),
                    defaultLocale = "en-GB"
                )
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.inItsResolvedLocale().toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("the digital English variant is not offered, and the e-mail suggests nothing in Dutch") {
                    restEpistolaVarianten shouldBe RestEpistolaVarianten(
                        varianten = listOf("post"),
                        voorgesteldeVariant = "post",
                        communicatiekanaal = null
                    )
                }
            }
        }

        given("a template with Dutch variants by post and digitally and an English one by post, in the language of the zaaktype") {
            val generationTemplate = createGenerationTemplate(
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal", "sms"), defaultKanaal = "sms"),
                locales = createDutchAndEnglishLocales()
            )

            `when`("they are offered in English, as the zaaktype is set to English") {
                val restEpistolaVarianten = EpistolaTemplateInLocale(
                    template = generationTemplate,
                    locale = generationTemplate.resolveLocale(configuredLocale = "en-GB")
                ).toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("only the English variant is offered, and the e-mail suggests nothing in English") {
                    restEpistolaVarianten shouldBe RestEpistolaVarianten(
                        varianten = listOf("post"),
                        voorgesteldeVariant = "post",
                        communicatiekanaal = null
                    )
                }
            }
        }

        given("a template in English and in German whose default variant carries no language") {
            val generationTemplate = createGenerationTemplate(
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post"),
                locales = EpistolaLocales(
                    kanalenByLocale = mapOf(
                        "en-GB" to EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post"),
                        "de-DE" to EpistolaKanalen(kanalen = listOf("digitaal"), defaultKanaal = "digitaal")
                    ),
                    defaultLocale = null
                )
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.inItsResolvedLocale().toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("the variants of the template as a whole are offered, as no language is resolved") {
                    restEpistolaVarianten shouldBe RestEpistolaVarianten(
                        varianten = listOf("post", "digitaal"),
                        voorgesteldeVariant = "digitaal",
                        communicatiekanaal = "E-mail"
                    )
                }
            }
        }

        given("a template whose variants carry no language") {
            val generationTemplate = createGenerationTemplate(
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.inItsResolvedLocale().toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("the variants of the template as a whole are offered") {
                    restEpistolaVarianten shouldBe RestEpistolaVarianten(
                        varianten = listOf("post", "digitaal"),
                        voorgesteldeVariant = "digitaal",
                        communicatiekanaal = "E-mail"
                    )
                }
            }
        }
    }
})
