/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub

private const val FAKE_CATALOG_ID = "fake-catalog"

class EpistolaGenerationTemplateTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("reading the languages of a template's variants") {
        given("two Dutch variants, of which the second is the default, an English one and a German one") {
            val template = createTemplate(
                variants = listOf(
                    createVariantSummary(
                        id = "fake-dutch",
                        attributes = mapOf("system.locale" to "nl-NL")
                    ),
                    createVariantSummary(
                        id = "fake-dutch-large-print",
                        isDefault = true,
                        attributes = mapOf("$FAKE_CATALOG_ID.weergave" to "groot", "system.locale" to "nl-NL")
                    ),
                    createVariantSummary(id = "fake-english", attributes = mapOf("system.locale" to "en-GB")),
                    createVariantSummary(id = "fake-german", attributes = mapOf("system.locale" to "de-DE"))
                )
            )

            `when`("the languages are read") {
                val locales = template.toEpistolaLocales()

                then("each language is listed once, in the order of the variants, and the default is the default variant's") {
                    locales shouldBe EpistolaLocales(locales = listOf("nl-NL", "en-GB", "de-DE"), defaultLocale = "nl-NL")
                }
            }
        }

        given("variants that name a language under the bare key Epistola does not recommend, a blank one, or another catalog's") {
            val template = createTemplate(
                variants = listOf(
                    createVariantSummary(id = "fake-bare-locale", isDefault = true, attributes = mapOf("locale" to "en-GB")),
                    createVariantSummary(id = "fake-blank-locale", attributes = mapOf("system.locale" to " ")),
                    createVariantSummary(id = "fake-other-catalog-locale", attributes = mapOf("fake-other-catalog.locale" to "fr-FR")),
                    createVariantSummary(id = "fake-no-attributes", attributes = null)
                )
            )

            `when`("the languages are read") {
                val locales = template.toEpistolaLocales()

                then("there are none, because ZAC only asks for a language by system.locale") {
                    locales shouldBe EpistolaLocales()
                }
            }
        }

        given("a template without variants") {
            `when`("the languages are read") {
                then("there are none") {
                    createTemplate().toEpistolaLocales() shouldBe EpistolaLocales()
                }
            }
        }
    }

    context("choosing the attributes by which Epistola selects a variant") {
        given("a language") {
            `when`("the attributes are chosen") {
                val attributes = selectVariantFor(locale = "en-GB")

                then("only the language is asked for, required, in Epistola's own catalog") {
                    attributes?.map { listOf(it.catalog, it.key, it.value, it.required) } shouldBe listOf(
                        listOf("system", "locale", "en-GB", true)
                    )
                }
            }
        }

        given("no language") {
            `when`("the attributes are chosen") {
                then("there are none, so Epistola renders the default variant") {
                    selectVariantFor(locale = null) shouldBe null
                }
            }
        }
    }
})
