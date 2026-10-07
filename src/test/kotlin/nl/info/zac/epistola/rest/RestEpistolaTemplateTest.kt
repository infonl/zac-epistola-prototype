/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import jakarta.json.bind.JsonbBuilder
import nl.info.client.epistola.model.createEpistolaVariant

class RestEpistolaTemplateTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("converting a variant of a template") {
        given("a default variant with a language, a kanaal and another attribute") {
            val epistolaVariant = createEpistolaVariant(
                id = "fake-variant-id",
                title = "fakeVariantTitle",
                isDefault = true,
                attributes = mapOf("locale" to "nl-NL", "kanaal" to "post", "weergave" to "groot")
            )

            `when`("it is converted") {
                val restEpistolaVariant = epistolaVariant.toRestEpistolaVariant()

                then("the attributes are a list of key and value pairs in the same order") {
                    restEpistolaVariant shouldBe RestEpistolaVariant(
                        id = "fake-variant-id",
                        title = "fakeVariantTitle",
                        isDefault = true,
                        attributes = listOf(
                            RestEpistolaVariantAttribute(key = "locale", value = "nl-NL"),
                            RestEpistolaVariantAttribute(key = "kanaal", value = "post"),
                            RestEpistolaVariantAttribute(key = "weergave", value = "groot")
                        )
                    )
                }
            }
        }
    }

    context("sending and receiving the variants of a template as JSON") {
        val jsonb = JsonbBuilder.create()

        given("a template with a default variant and one that is not") {
            val restEpistolaTemplate = createRestEpistolaTemplate(
                id = "fake-template-id",
                name = "fakeTemplateName",
                variants = listOf(
                    createRestEpistolaVariant(
                        id = "fake-initial",
                        title = "Initial",
                        isDefault = true,
                        attributes = listOf(RestEpistolaVariantAttribute(key = "locale", value = "nl-NL"))
                    ),
                    createRestEpistolaVariant(id = "fake-other", title = "Other")
                )
            )

            `when`("it is serialized via JSON-B") {
                val json = jsonb.toJson(restEpistolaTemplate)

                then("the boolean keeps the name of the property, including its is prefix, and the order is kept") {
                    json shouldBe """{"id":"fake-template-id","name":"fakeTemplateName","variants":[""" +
                        """{"attributes":[{"key":"locale","value":"nl-NL"}],"id":"fake-initial","isDefault":true,"title":"Initial"},""" +
                        """{"attributes":[],"id":"fake-other","isDefault":false,"title":"Other"}]}"""
                }
            }

            `when`("it is serialized and deserialized again") {
                val roundTripped = jsonb.fromJson(jsonb.toJson(restEpistolaTemplate), RestEpistolaTemplate::class.java)

                then("the default flag is read back, not left at its default of false") {
                    roundTripped shouldBe restEpistolaTemplate
                }
            }
        }

        given("a template whose details could not be read") {
            val restEpistolaTemplate = createRestEpistolaTemplate(id = "fake-template-id", name = "fakeTemplateName")

            `when`("it is serialized and deserialized again") {
                val roundTripped = jsonb.fromJson(jsonb.toJson(restEpistolaTemplate), RestEpistolaTemplate::class.java)

                then("the variants are still unknown, which is not the same as none") {
                    roundTripped.variants shouldBe null
                }
            }
        }
    }
})
