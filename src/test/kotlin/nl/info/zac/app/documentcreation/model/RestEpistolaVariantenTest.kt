/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import nl.info.client.epistola.model.EpistolaKanalen
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createDutchAndEnglishLocales
import nl.info.client.epistola.model.createGenerationTemplate

class RestEpistolaVariantenTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("offering the variants and languages of a template") {
        given("a template with Dutch variants by post and digitally and an English one by post, for a zaak by e-mail") {
            val generationTemplate = createGenerationTemplate(
                kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post"),
                locales = createDutchAndEnglishLocales()
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("each language is offered with its variants and the one suggested in it, and Dutch is preselected") {
                    restEpistolaVarianten.talen shouldBe listOf(
                        RestEpistolaTaal(taal = "nl-NL", varianten = listOf("post", "digitaal"), voorgesteldeVariant = "digitaal"),
                        RestEpistolaTaal(taal = "en-GB", varianten = listOf("post"), voorgesteldeVariant = "post")
                    )
                    restEpistolaVarianten.voorgesteldeTaal shouldBe "nl-NL"
                }

                and("the variants of the template as a whole are offered as before, with the communicatiekanaal that suggests one") {
                    restEpistolaVarianten.varianten shouldBe listOf("post", "digitaal")
                    restEpistolaVarianten.voorgesteldeVariant shouldBe "digitaal"
                    restEpistolaVarianten.communicatiekanaal shouldBe "E-mail"
                }
            }
        }

        given("a template in English and in German whose default variant carries no language") {
            val generationTemplate = createGenerationTemplate(
                locales = EpistolaLocales(
                    kanalenByLocale = mapOf("en-GB" to EpistolaKanalen(), "de-DE" to EpistolaKanalen()),
                    defaultLocale = null
                )
            )

            `when`("they are offered") {
                val restEpistolaVarianten = generationTemplate.toRestEpistolaVarianten(communicatiekanaal = null)

                then("no language is preselected, so the behandelaar chooses one") {
                    restEpistolaVarianten.voorgesteldeTaal shouldBe null
                }
            }
        }

        given("a template whose variants carry no language") {
            `when`("they are offered") {
                val restEpistolaVarianten = createGenerationTemplate().toRestEpistolaVarianten(communicatiekanaal = "E-mail")

                then("no language is offered or preselected") {
                    restEpistolaVarianten.talen shouldBe emptyList()
                    restEpistolaVarianten.voorgesteldeTaal shouldBe null
                }
            }
        }
    }
})
