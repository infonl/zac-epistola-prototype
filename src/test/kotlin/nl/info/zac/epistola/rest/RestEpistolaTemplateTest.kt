/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import jakarta.json.bind.JsonbBuilder

class RestEpistolaTemplateTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("sending and receiving the languages of a template as JSON") {
        val jsonb = JsonbBuilder.create()

        given("a template with its languages") {
            val restEpistolaTemplate = createRestEpistolaTemplate(
                id = "fake-template-id",
                name = "fakeTemplateName",
                locales = listOf("en-GB", "nl-NL")
            )

            `when`("it is serialized and deserialized again") {
                val roundTripped = jsonb.fromJson(jsonb.toJson(restEpistolaTemplate), RestEpistolaTemplate::class.java)

                then("the languages are read back in the same order") {
                    roundTripped shouldBe restEpistolaTemplate
                }
            }
        }

        given("a template whose details could not be read") {
            val restEpistolaTemplate = createRestEpistolaTemplate(id = "fake-template-id", name = "fakeTemplateName")

            `when`("it is serialized and deserialized again") {
                val roundTripped = jsonb.fromJson(jsonb.toJson(restEpistolaTemplate), RestEpistolaTemplate::class.java)

                then("the languages are still unknown, which is not the same as none") {
                    roundTripped.locales shouldBe null
                }
            }
        }
    }
})
