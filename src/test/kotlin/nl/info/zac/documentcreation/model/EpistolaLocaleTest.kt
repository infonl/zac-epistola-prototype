/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import nl.info.client.epistola.model.EpistolaLocales
import nl.info.client.epistola.model.createDutchAndEnglishLocales
import nl.info.client.epistola.model.createGenerationTemplate

class EpistolaLocaleTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("preselecting the language of a document") {
        given("a template in Dutch and in British English, whose default variant is Dutch") {
            `when`("the language is preselected") {
                then("it is Dutch") {
                    createDutchAndEnglishLocales().preselectedLocale() shouldBe "nl-NL"
                }
            }
        }

        given("a template in British English and in Dutch, whose default variant is English") {
            val locales = EpistolaLocales(
                locales = listOf("en-GB", "nl-NL"),
                defaultLocale = "en-GB"
            )

            `when`("the language is preselected") {
                then("it is Dutch, whatever the default variant is written in") {
                    locales.preselectedLocale() shouldBe "nl-NL"
                }
            }
        }

        given("a template in British English and in Belgian Dutch, whose default variant is English") {
            val locales = EpistolaLocales(
                locales = listOf("en-GB", "nl-BE"),
                defaultLocale = "en-GB"
            )

            `when`("the language is preselected") {
                then("it is Dutch for the region the template has it for") {
                    locales.preselectedLocale() shouldBe "nl-BE"
                }
            }
        }

        given("a template in British English and in German, whose default variant is German") {
            val locales = EpistolaLocales(
                locales = listOf("en-GB", "de-DE"),
                defaultLocale = "de-DE"
            )

            `when`("the language is preselected") {
                then("it is the language of the default variant") {
                    locales.preselectedLocale() shouldBe "de-DE"
                }
            }
        }

        given("a template whose variants carry no language") {
            `when`("the language is preselected") {
                then("there is none") {
                    EpistolaLocales().preselectedLocale() shouldBe null
                }
            }
        }
    }

    context("resolving the language to ask Epistola for") {
        given("a template in Dutch and in British English") {
            val generationTemplate = createGenerationTemplate(locales = createDutchAndEnglishLocales())

            `when`("the language is resolved without a requested language") {
                then("Dutch is resolved") {
                    generationTemplate.resolveLocale() shouldBe "nl-NL"
                }
            }

            `when`("the language is resolved with British English requested") {
                then("British English is resolved") {
                    generationTemplate.resolveLocale(requestedLocale = "en-GB") shouldBe "en-GB"
                }
            }

            `when`("the language is resolved with British English configured for the zaaktype") {
                then("British English is resolved") {
                    generationTemplate.resolveLocale(configuredLocale = "en-GB") shouldBe "en-GB"
                }
            }

            `when`("the language is resolved with a language configured for the zaaktype that the template does not have") {
                then("Dutch is resolved") {
                    generationTemplate.resolveLocale(configuredLocale = "fr-FR") shouldBe "nl-NL"
                }
            }

            `when`("the language is resolved with Dutch requested and British English configured for the zaaktype") {
                then("the requested language wins") {
                    generationTemplate.resolveLocale(requestedLocale = "nl-NL", configuredLocale = "en-GB") shouldBe "nl-NL"
                }
            }

            `when`("the language is resolved with one requested that the template does not have and British English configured") {
                then("the configured language is resolved") {
                    generationTemplate.resolveLocale(requestedLocale = "fr-FR", configuredLocale = "en-GB") shouldBe "en-GB"
                }
            }
        }

        given("a template whose variants carry no language") {
            `when`("the language is resolved") {
                then("no language is resolved") {
                    createGenerationTemplate().resolveLocale() shouldBe null
                }
            }

            `when`("the language is resolved with British English configured for the zaaktype") {
                then("no language is resolved") {
                    createGenerationTemplate().resolveLocale(configuredLocale = "en-GB") shouldBe null
                }
            }
        }
    }

    context("choosing the language to ask Epistola for") {
        given("a template in Dutch and in British English") {
            val locales = createDutchAndEnglishLocales()

            `when`("British English is requested") {
                then("British English is asked for") {
                    locales.choose(requestedLocale = "en-GB") shouldBe "en-GB"
                }
            }

            `when`("British English is requested in other letter case than the template spells it") {
                then("the template's own spelling is asked for, so its variants stay addressable") {
                    locales.choose(requestedLocale = "en-gb") shouldBe "en-GB"
                }
            }

            `when`("no language is requested, or one the template does not have") {
                then("Dutch is asked for") {
                    listOf(null, "fr-FR").forEach {
                        locales.choose(requestedLocale = it) shouldBe "nl-NL"
                    }
                }
            }
        }

        given("a template whose variants carry no language") {
            `when`("British English is requested") {
                then("no language is asked for, so Epistola renders the template as before") {
                    EpistolaLocales().choose(requestedLocale = "en-GB") shouldBe null
                }
            }
        }
    }

    context("naming the language of the informatieobject in Open Zaak") {
        given("the languages Epistola names by BCP-47 tags") {
            `when`("they are named for the Documenten API") {
                then("each gets its ISO 639-2/B code, also where the terminology code differs") {
                    mapOf(
                        "nl-NL" to "dut",
                        "nl-BE" to "dut",
                        "en-GB" to "eng",
                        "de-DE" to "ger",
                        "fr" to "fre",
                        "fy-NL" to "fry",
                        "tr-TR" to "tur",
                        "zh-Hans-CN" to "chi"
                    ).forEach { (locale, taal) ->
                        toInformatieobjectTaal(locale) shouldBe taal
                    }
                }
            }
        }

        given("no language, or a tag that names no language ISO 639-2 has a code for") {
            `when`("it is named for the Documenten API") {
                then("the informatieobject is registered as Dutch, as every Epistola document was before") {
                    listOf(null, "", "not a tag", "x-private", "zz-ZZ").forEach {
                        toInformatieobjectTaal(it) shouldBe "dut"
                    }
                }
            }
        }
    }
})
