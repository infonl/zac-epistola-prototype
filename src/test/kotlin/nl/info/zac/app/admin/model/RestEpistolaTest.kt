/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.admin.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import jakarta.json.bind.JsonbBuilder

class RestEpistolaTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    val jsonb = JsonbBuilder.create()

    given("Epistola that is enabled globally and switched off for the zaaktype") {
        val restEpistola = RestEpistola(isEnabledGlobally = true, isEnabledForZaaktype = false)

        `when`("it is serialized via JSON-B") {
            val json = jsonb.toJson(restEpistola)

            then("the booleans keep the names of the properties, including their is prefix") {
                json shouldBe """{"isEnabledForZaaktype":false,"isEnabledGlobally":true}"""
            }
        }
    }

    given("JSON with the is-prefixed names the frontend sends") {
        val json = """{"isEnabledGlobally":true,"isEnabledForZaaktype":true}"""

        `when`("it is deserialized via JSON-B") {
            val restEpistola = jsonb.fromJson(json, RestEpistola::class.java)

            then("both booleans are read, not left at their default") {
                restEpistola shouldBe RestEpistola(isEnabledGlobally = true, isEnabledForZaaktype = true)
            }
        }
    }
})
