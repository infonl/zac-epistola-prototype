/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_MAPPING_INVALID
import java.util.UUID

class RestEpistolaTemplateMappingValidatorTest : BehaviorSpec({
    val informatieObjectTypeUuid = UUID.randomUUID()
    val availableTemplateIds = setOf("fake-template-1", "fake-template-2")
    val informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)

    fun templateGroup(name: String, vararg templateIds: String) = createRestMappedEpistolaTemplateGroup(
        name = name,
        templates = templateIds.map {
            createRestMappedEpistolaTemplate(id = it, informatieObjectTypeUUID = informatieObjectTypeUuid)
        }
    )

    context("validating a template mapping") {
        given("two named groups, each template known to Epistola and filed under one of the zaaktype's types") {
            val templateGroups = listOf(
                templateGroup("fakeGroupName1", "fake-template-1"),
                templateGroup("fakeGroupName2", "fake-template-2")
            )

            `when`("the mapping is validated") {
                then("it passes") {
                    shouldNotThrowAny {
                        templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                    }
                }
            }
        }

        given("a group without templates") {
            val templateGroups = listOf(templateGroup("fakeGroupName1"))

            `when`("the mapping is validated") {
                then("it passes, so a beheerder can create a group before filling it") {
                    shouldNotThrowAny {
                        templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                    }
                }
            }
        }

        given("a group whose name is blank") {
            val templateGroups = listOf(templateGroup("  ", "fake-template-1"))

            `when`("the mapping is validated") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                }

                then("it is rejected as a mapping error, which the REST layer answers with 400") {
                    exception.message shouldBe "Every template group needs a name."
                    exception.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_MAPPING_INVALID
                }
            }
        }

        given("two groups whose names differ only in case and surrounding spaces") {
            val templateGroups = listOf(
                templateGroup("Vergunningen", "fake-template-1"),
                templateGroup(" vergunningen ", "fake-template-2")
            )

            `when`("the mapping is validated") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                }

                then("it is rejected, because a behandelaar could not tell the two groups apart") {
                    exception.message shouldBe "Template group names must be unique; duplicated: [vergunningen]"
                }
            }
        }

        given("one template placed in two groups") {
            val templateGroups = listOf(
                templateGroup("fakeGroupName1", "fake-template-1"),
                templateGroup("fakeGroupName2", "fake-template-1")
            )

            `when`("the mapping is validated") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                }

                then("it is rejected, so the group a behandelaar opens never changes how the document is filed") {
                    exception.message shouldBe "A template can be in only one template group; duplicated: [fake-template-1]"
                }
            }
        }

        given("a template that Epistola does not have") {
            val templateGroups = listOf(templateGroup("fakeGroupName1", "fake-template-1", "fake-unknown-template"))

            `when`("the mapping is validated") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                }

                then("it is rejected, naming the unknown template") {
                    exception.message shouldBe "Unknown Epistola templates: [fake-unknown-template]"
                }
            }
        }

        given("a template filed under an informatieobjecttype that does not belong to the zaaktype") {
            val foreignInformatieObjectTypeUuid = UUID.randomUUID()
            val templateGroups = listOf(
                createRestMappedEpistolaTemplateGroup(
                    templates = listOf(
                        createRestMappedEpistolaTemplate(
                            id = "fake-template-1",
                            informatieObjectTypeUUID = foreignInformatieObjectTypeUuid
                        )
                    )
                )
            )

            `when`("the mapping is validated") {
                val exception = shouldThrow<EpistolaTemplateMappingException> {
                    templateGroups.validate(availableTemplateIds, informatieobjecttypeUuids)
                }

                then("it is rejected, because Open Zaak would refuse to link the document to the zaak") {
                    exception.message shouldContain "fake-template-1 -> $foreignInformatieObjectTypeUuid"
                }
            }
        }
    }
})
