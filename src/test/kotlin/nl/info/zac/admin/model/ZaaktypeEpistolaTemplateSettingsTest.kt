/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.admin.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import nl.info.zac.epistola.model.EpistolaTemplateSetting
import java.util.UUID

class ZaaktypeEpistolaTemplateSettingsTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    val informatieObjectTypeUuid = UUID.randomUUID()
    val otherInformatieObjectTypeUuid = UUID.randomUUID()

    context("replacing the Epistola template settings of a zaaktype") {
        given("a zaaktype without settings") {
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration()

            `when`("a template gets an informatieobjecttype of its own and another is switched off") {
                zaaktypeCmmnConfiguration.replaceEpistolaTemplateSettings(
                    mapOf(
                        "fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = informatieObjectTypeUuid),
                        "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                    )
                )

                then("both are kept, linked to the zaaktype") {
                    zaaktypeCmmnConfiguration.readEpistolaTemplateSettings() shouldBe mapOf(
                        "fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = informatieObjectTypeUuid),
                        "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                    )
                    zaaktypeCmmnConfiguration.epistolaTemplateSettings.forEach {
                        it.zaaktypeConfiguration shouldBe zaaktypeCmmnConfiguration
                    }
                }
            }
        }

        given("a zaaktype with settings for two templates") {
            val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration().apply {
                replaceEpistolaTemplateSettings(
                    mapOf(
                        "fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = informatieObjectTypeUuid),
                        "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                    )
                )
            }
            val storedTemplateSettings = zaaktypeCmmnConfiguration.epistolaTemplateSettings
                .first { it.epistolaId == "fake-template-1" }

            `when`("the settings are replaced by one that changes the first template and none for the second") {
                zaaktypeCmmnConfiguration.replaceEpistolaTemplateSettings(
                    mapOf(
                        "fake-template-1" to EpistolaTemplateSetting(informatieObjectTypeUuid = otherInformatieObjectTypeUuid)
                    )
                )

                then("the first is updated in place and the second is removed") {
                    zaaktypeCmmnConfiguration.epistolaTemplateSettings shouldHaveSize 1
                    zaaktypeCmmnConfiguration.epistolaTemplateSettings.first() shouldBe storedTemplateSettings
                    storedTemplateSettings.informatieObjectTypeUUID shouldBe otherInformatieObjectTypeUuid
                }
            }

            `when`("a template is given the setting every template has by default") {
                zaaktypeCmmnConfiguration.replaceEpistolaTemplateSettings(
                    mapOf(
                        "fake-template-1" to EpistolaTemplateSetting(),
                        "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                    )
                )

                then("no row is kept for it, so that it follows the zaaktype") {
                    zaaktypeCmmnConfiguration.readEpistolaTemplateSettings() shouldBe mapOf(
                        "fake-template-2" to EpistolaTemplateSetting(isEnabled = false)
                    )
                }
            }

            `when`("the settings are replaced by none") {
                zaaktypeCmmnConfiguration.replaceEpistolaTemplateSettings(emptyMap())

                then("every template follows the zaaktype again") {
                    zaaktypeCmmnConfiguration.epistolaTemplateSettings shouldHaveSize 0
                }
            }
        }
    }
})
