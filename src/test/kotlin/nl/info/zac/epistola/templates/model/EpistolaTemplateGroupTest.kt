/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.templates.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import java.util.UUID

class EpistolaTemplateGroupTest : BehaviorSpec({
    context("copying a stored template group to another zaaktype configuration") {
        given("a stored group with one template") {
            val informatieObjectTypeUuid = UUID.randomUUID()
            val epistolaTemplateGroup = createEpistolaTemplateGroup(
                name = "Vergunningen",
                templateIdsToInformatieObjectTypeUuids = mapOf("fake-template-1" to informatieObjectTypeUuid)
            )
            val newZaaktypeConfiguration = createZaaktypeCmmnConfiguration(id = 5678L)

            `when`("it is copied") {
                val copiedEpistolaTemplateGroup = epistolaTemplateGroup.copyTo(newZaaktypeConfiguration)

                then("the copy is a new group with the same name and template, owned by the new configuration") {
                    copiedEpistolaTemplateGroup.id shouldBe null
                    copiedEpistolaTemplateGroup.name shouldBe "Vergunningen"
                    copiedEpistolaTemplateGroup.zaaktypeConfiguration shouldBeSameInstanceAs newZaaktypeConfiguration
                    with(copiedEpistolaTemplateGroup.templates.single()) {
                        id shouldBe null
                        epistolaId shouldBe "fake-template-1"
                        informatieObjectTypeUUID shouldBe informatieObjectTypeUuid
                        templateGroup shouldBeSameInstanceAs copiedEpistolaTemplateGroup
                        zaaktypeConfiguration shouldBeSameInstanceAs newZaaktypeConfiguration
                    }
                }
            }
        }
    }
})
