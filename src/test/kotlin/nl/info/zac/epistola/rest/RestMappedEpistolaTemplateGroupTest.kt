/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.epistola.templates.model.createEpistolaTemplateGroup
import java.util.UUID

class RestMappedEpistolaTemplateGroupTest : BehaviorSpec({
    context("converting a REST template group to its entity") {
        given("a group with a name padded with spaces and one template") {
            val zaaktypeConfiguration = createZaaktypeCmmnConfiguration()
            val informatieObjectTypeUuid = UUID.randomUUID()
            val restMappedEpistolaTemplateGroup = createRestMappedEpistolaTemplateGroup(
                name = "  Vergunningen ",
                templates = listOf(
                    createRestMappedEpistolaTemplate(
                        id = "fake-template-1",
                        informatieObjectTypeUUID = informatieObjectTypeUuid
                    )
                )
            )

            `when`("it is converted") {
                val epistolaTemplateGroup = restMappedEpistolaTemplateGroup.toEpistolaTemplateGroup(zaaktypeConfiguration)

                then("the name is stored trimmed and the group belongs to the zaaktype configuration") {
                    epistolaTemplateGroup.name shouldBe "Vergunningen"
                    epistolaTemplateGroup.zaaktypeConfiguration shouldBeSameInstanceAs zaaktypeConfiguration
                }

                and("the template points at its group and at the same zaaktype configuration") {
                    epistolaTemplateGroup.templates shouldHaveSize 1
                    with(epistolaTemplateGroup.templates.single()) {
                        epistolaId shouldBe "fake-template-1"
                        informatieObjectTypeUUID shouldBe informatieObjectTypeUuid
                        templateGroup shouldBeSameInstanceAs epistolaTemplateGroup
                        this.zaaktypeConfiguration shouldBeSameInstanceAs zaaktypeConfiguration
                    }
                }
            }
        }
    }

    context("converting a stored template group to REST") {
        given("a stored group with one template Epistola still has and one it no longer has") {
            val informatieObjectTypeUuid = UUID.randomUUID()
            val epistolaTemplateGroup = createEpistolaTemplateGroup(
                name = "Vergunningen",
                templateIdsToInformatieObjectTypeUuids = mapOf(
                    "fake-template-1" to informatieObjectTypeUuid,
                    "fake-removed-template" to UUID.randomUUID()
                )
            )

            `when`("it is converted with the template names Epistola currently has") {
                val restMappedEpistolaTemplateGroup = epistolaTemplateGroup.toRestMappedEpistolaTemplateGroup(
                    mapOf("fake-template-1" to "Besluit evenementenvergunning")
                )

                then("the template Epistola no longer has is left out, and the other carries its current name") {
                    restMappedEpistolaTemplateGroup shouldBe RestMappedEpistolaTemplateGroup(
                        name = "Vergunningen",
                        templates = listOf(
                            RestMappedEpistolaTemplate(
                                id = "fake-template-1",
                                name = "Besluit evenementenvergunning",
                                informatieObjectTypeUUID = informatieObjectTypeUuid
                            )
                        )
                    )
                }
            }
        }

        given("a stored group whose templates are all gone from Epistola") {
            val epistolaTemplateGroup = createEpistolaTemplateGroup(
                templateIdsToInformatieObjectTypeUuids = mapOf("fake-removed-template" to UUID.randomUUID())
            )

            `when`("it is converted") {
                val restMappedEpistolaTemplateGroup = epistolaTemplateGroup.toRestMappedEpistolaTemplateGroup(
                    emptyMap()
                )

                then("the group is kept, empty, because the group belongs to ZAC and not to Epistola") {
                    restMappedEpistolaTemplateGroup.name shouldBe epistolaTemplateGroup.name
                    restMappedEpistolaTemplateGroup.templates.shouldBeEmpty()
                }
            }
        }

        given("a stored group with templates whose names sort differently from their ids") {
            val epistolaTemplateGroup = createEpistolaTemplateGroup(
                templateIdsToInformatieObjectTypeUuids = mapOf(
                    "fake-template-a" to UUID.randomUUID(),
                    "fake-template-b" to UUID.randomUUID()
                )
            )

            `when`("it is converted") {
                val restMappedEpistolaTemplateGroup = epistolaTemplateGroup.toRestMappedEpistolaTemplateGroup(
                    mapOf("fake-template-a" to "verlenging", "fake-template-b" to "Besluit")
                )

                then("the templates are ordered by name, ignoring case") {
                    restMappedEpistolaTemplateGroup.templates.map { it.name } shouldBe listOf("Besluit", "verlenging")
                }
            }
        }
    }
})
