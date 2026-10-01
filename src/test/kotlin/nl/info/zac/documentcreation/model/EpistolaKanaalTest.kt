/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import nl.info.client.epistola.model.EpistolaKanalen

class EpistolaKanaalTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    context("suggesting a kanaal for a zaak's communicatiekanaal") {
        given("a template with a post and a digital variant, whose default variant is the one by post") {
            val kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")

            `when`("the communicatiekanaal is one a citizen uses online") {
                then("the digital variant is suggested") {
                    listOf("E-mail", "E-formulier", "Internet", "Medewerkersportaal").forEach {
                        kanalen.suggestFor(it) shouldBe "digitaal"
                    }
                }
            }

            `when`("the communicatiekanaal is one that is answered on paper") {
                then("the variant by post is suggested") {
                    listOf("Post", "Balie", "Telefoon").forEach {
                        kanalen.suggestFor(it) shouldBe "post"
                    }
                }
            }

            `when`("the communicatiekanaal is written in another case, with spaces around it") {
                then("it is still recognised") {
                    kanalen.suggestFor(" e-MAIL ") shouldBe "digitaal"
                }
            }

            `when`("the communicatiekanaal is one ZAC does not know, or the zaak has none") {
                then("the kanaal of the default variant is suggested") {
                    listOf("Intern", null).forEach {
                        kanalen.suggestFor(it) shouldBe "post"
                    }
                }
            }
        }

        given("a template with only a variant by post, which is its default") {
            val kanalen = EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")

            `when`("the communicatiekanaal is e-mail") {
                then("the variant by post is suggested, because there is no digital one") {
                    kanalen.suggestFor("E-mail") shouldBe "post"
                }
            }
        }

        given("a template whose variants are made for no kanaal") {
            `when`("the communicatiekanaal is post") {
                then("no kanaal is suggested") {
                    EpistolaKanalen().suggestFor("Post") shouldBe null
                }
            }
        }
    }

    context("telling whether the communicatiekanaal itself suggests a kanaal") {
        given("a template with only a variant by post") {
            val kanalen = EpistolaKanalen(kanalen = listOf("post"), defaultKanaal = "post")

            `when`("the communicatiekanaal is e-mail, which suggests a digital variant the template does not have") {
                then("it suggests none") {
                    kanalen.kanaalSuggestedBy("E-mail") shouldBe null
                }
            }

            `when`("the communicatiekanaal is post") {
                then("it suggests post") {
                    kanalen.kanaalSuggestedBy("Post") shouldBe "post"
                }
            }
        }
    }

    context("choosing the kanaal to ask Epistola for") {
        given("a template with a post and a digital variant, and a zaak whose communicatiekanaal is e-mail") {
            val kanalen = EpistolaKanalen(kanalen = listOf("post", "digitaal"), defaultKanaal = "post")

            `when`("the behandelaar chose post") {
                then("post is asked for") {
                    kanalen.choose(requestedKanaal = "post", communicatiekanaal = "E-mail") shouldBe "post"
                }
            }

            `when`("the behandelaar chose nothing, and the communicatiekanaal suggests no kanaal") {
                then("no kanaal is asked for, so Epistola renders the template's default variant itself") {
                    kanalen.choose(requestedKanaal = null, communicatiekanaal = "Intern") shouldBe null
                }
            }

            `when`("the behandelaar chose nothing, or a kanaal the template has no variant for") {
                then("the kanaal the communicatiekanaal suggests is asked for") {
                    listOf(null, "fakeKanaal").forEach {
                        kanalen.choose(requestedKanaal = it, communicatiekanaal = "E-mail") shouldBe "digitaal"
                    }
                }
            }
        }
    }
})
