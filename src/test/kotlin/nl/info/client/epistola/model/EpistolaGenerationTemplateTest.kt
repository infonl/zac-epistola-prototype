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
        given("variants in Dutch and in English by post and digitally, whose default is Dutch and digital, and one in German") {
            val template = createTemplate(
                variants = listOf(
                    createVariantSummary(
                        id = "fake-dutch-post",
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "post", "system.locale" to "nl-NL")
                    ),
                    createVariantSummary(
                        id = "fake-dutch-digitaal",
                        isDefault = true,
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "digitaal", "system.locale" to "nl-NL")
                    ),
                    createVariantSummary(
                        id = "fake-english-post",
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "post", "system.locale" to "en-GB")
                    ),
                    createVariantSummary(
                        id = "fake-english-digitaal",
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "digitaal", "system.locale" to "en-GB")
                    ),
                    createVariantSummary(id = "fake-german", attributes = mapOf("system.locale" to "de-DE"))
                )
            )

            `when`("the languages are read") {
                val locales = template.toEpistolaLocales(FAKE_CATALOG_ID)

                then(
                    "each language is listed once with its kanalen, and only Dutch has the default variant's kanaal, " +
                        "so that nothing singles out one of the two English variants"
                ) {
                    locales shouldBe EpistolaLocales(
                        kanalenByLocale = mapOf(
                            "nl-NL" to EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "digitaal"),
                            "en-GB" to EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = null),
                            "de-DE" to EpistolaKanalen(kanalen = emptyList(), defaultKanaal = null)
                        ),
                        defaultLocale = "nl-NL"
                    )
                }

                and("they are listed in the order of the variants") {
                    locales.locales shouldBe listOf("nl-NL", "en-GB", "de-DE")
                }
            }
        }

        given("a Dutch default variant by post, and an English variant by post that is the only one in its language") {
            val template = createTemplate(
                variants = listOf(
                    createVariantSummary(
                        id = "fake-dutch-post",
                        isDefault = true,
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "post", "system.locale" to "nl-NL")
                    ),
                    createVariantSummary(
                        id = "fake-english-post",
                        attributes = mapOf("$FAKE_CATALOG_ID.kanaal" to "post", "system.locale" to "en-GB")
                    )
                )
            )

            `when`("the languages are read") {
                val locales = template.toEpistolaLocales(FAKE_CATALOG_ID)

                then("English has its only kanaal as its default kanaal") {
                    locales.kanalenByLocale["en-GB"] shouldBe EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")
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
                val locales = template.toEpistolaLocales(FAKE_CATALOG_ID)

                then("there are none, because ZAC only asks for a language by system.locale") {
                    locales shouldBe EpistolaLocales()
                }
            }
        }

        given("a template without variants") {
            `when`("the languages are read") {
                then("there are none") {
                    createTemplate().toEpistolaLocales(FAKE_CATALOG_ID) shouldBe EpistolaLocales()
                }
            }
        }
    }

    context("choosing the attributes by which Epistola selects a variant") {
        given("a kanaal and a language") {
            `when`("the attributes are chosen") {
                val attributes = selectVariantFor(kanaal = "post", locale = "en-GB", catalogId = FAKE_CATALOG_ID)

                then("both are required, the language in Epistola's own catalog") {
                    attributes?.map { listOf(it.catalog, it.key, it.value, it.required) } shouldBe listOf(
                        listOf(FAKE_CATALOG_ID, "kanaal", "post", true),
                        listOf("system", "locale", "en-GB", true)
                    )
                }
            }
        }

        given("neither a kanaal nor a language") {
            `when`("the attributes are chosen") {
                then("there are none, so Epistola renders the default variant") {
                    selectVariantFor(kanaal = null, locale = null, catalogId = FAKE_CATALOG_ID) shouldBe null
                }
            }
        }
    }
})
