/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import jakarta.json.bind.JsonbBuilder
import jakarta.validation.Validation
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_TEMPLATE_MAPPING_INVALID
import java.util.UUID

class RestEpistolaCatalogMappingTest : BehaviorSpec({
    val informatieObjectTypeUuid = UUID.randomUUID()

    afterEach { checkUnnecessaryStub() }

    context("validating a catalog mapping") {
        given("a catalog Epistola has and an informatieobjecttype of the zaaktype") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid
            )

            `when`("it is validated") {
                then("it passes") {
                    shouldNotThrowAny {
                        catalogMapping.validate(
                            availableCatalogIds = setOf("fake-catalog"),
                            informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                        )
                    }
                }
            }
        }

        given("a catalog Epistola does not have") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-unknown-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid
            )

            `when`("it is validated") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    catalogMapping.validate(
                        availableCatalogIds = setOf("fake-catalog"),
                        informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                    )
                }

                then("it is rejected, naming the catalog") {
                    epistolaTemplateMappingException.errorCode shouldBe ERROR_CODE_EPISTOLA_TEMPLATE_MAPPING_INVALID
                    epistolaTemplateMappingException.message shouldBe "Unknown Epistola catalog: 'fake-unknown-catalog'"
                }
            }
        }

        given("a catalog Epistola has, an informatieobjecttype of the zaaktype and a language") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                locale = "en-GB"
            )

            `when`("it is validated") {
                then("it passes") {
                    shouldNotThrowAny {
                        catalogMapping.validate(
                            availableCatalogIds = setOf("fake-catalog"),
                            informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                        )
                    }
                }
            }
        }

        given("a blank language, which the beheerder sends for no choice") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                locale = " "
            )

            `when`("it is validated") {
                then("it passes") {
                    shouldNotThrowAny {
                        catalogMapping.validate(
                            availableCatalogIds = setOf("fake-catalog"),
                            informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                        )
                    }
                }
            }
        }

        given("a language that is not a language tag") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                locale = "-"
            )

            `when`("it is validated") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    catalogMapping.validate(
                        availableCatalogIds = setOf("fake-catalog"),
                        informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                    )
                }

                then("it is rejected, naming the language") {
                    epistolaTemplateMappingException.message shouldBe "Invalid Epistola language: '-'"
                }
            }
        }

        given("no informatieobjecttype") {
            val catalogMapping = createRestEpistolaCatalogMapping(catalogId = "fake-catalog", informatieObjectTypeUUID = null)

            `when`("it is validated") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    catalogMapping.validate(
                        availableCatalogIds = setOf("fake-catalog"),
                        informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                    )
                }

                then("it is rejected, because a generated document could not be stored without one") {
                    epistolaTemplateMappingException.message shouldBe
                        "An informatieobjecttype is needed to store the Epistola documents of the zaaktype under."
                }
            }
        }

        given("an informatieobjecttype that is not one of the zaaktype's") {
            val otherInformatieObjectTypeUuid = UUID.randomUUID()
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = otherInformatieObjectTypeUuid
            )

            `when`("it is validated") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    catalogMapping.validate(
                        availableCatalogIds = setOf("fake-catalog"),
                        informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                    )
                }

                then("it is rejected, because Open Zaak would not accept the document under it for a zaak of this type") {
                    epistolaTemplateMappingException.message shouldBe
                        "Informatieobjecttype '$otherInformatieObjectTypeUuid' is not one of the zaaktype's."
                }
            }
        }
    }

    context("validating the template settings of a catalog mapping") {
        given("templates whose informatieobjecttypen are the zaaktype's, or none") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                templateSettings = listOf(
                    createRestEpistolaTemplateSetting(templateId = "fake-template-1", informatieObjectTypeUUID = informatieObjectTypeUuid),
                    createRestEpistolaTemplateSetting(templateId = "fake-template-2", isEnabled = false)
                )
            )

            `when`("it is validated") {
                then("it passes") {
                    shouldNotThrowAny {
                        catalogMapping.validate(
                            availableCatalogIds = setOf("fake-catalog"),
                            informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                        )
                    }
                }
            }
        }

        given("a template with an informatieobjecttype that is not one of the zaaktype's") {
            val otherInformatieObjectTypeUuid = UUID.randomUUID()
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "fake-catalog",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                templateSettings = listOf(
                    createRestEpistolaTemplateSetting(
                        templateId = "fake-template-1",
                        informatieObjectTypeUUID = otherInformatieObjectTypeUuid
                    )
                )
            )

            `when`("it is validated") {
                val epistolaTemplateMappingException = shouldThrow<EpistolaTemplateMappingException> {
                    catalogMapping.validate(
                        availableCatalogIds = setOf("fake-catalog"),
                        informatieobjecttypeUuids = setOf(informatieObjectTypeUuid)
                    )
                }

                then("it is rejected, naming the informatieobjecttype") {
                    epistolaTemplateMappingException.message shouldBe
                        "Informatieobjecttype '$otherInformatieObjectTypeUuid' is not one of the zaaktype's."
                }
            }
        }
    }

    context("sending and receiving a template setting as JSON") {
        val jsonb = JsonbBuilder.create()

        given("a template setting that is switched off and has an informatieobjecttype of its own") {
            val restEpistolaTemplateSetting = createRestEpistolaTemplateSetting(
                templateId = "fake-template-1",
                informatieObjectTypeUUID = informatieObjectTypeUuid,
                isEnabled = false
            )

            `when`("it is serialized via JSON-B") {
                val json = jsonb.toJson(restEpistolaTemplateSetting)

                then("the boolean keeps the name of the property, including its is prefix") {
                    json shouldBe """{"informatieObjectTypeUUID":"$informatieObjectTypeUuid","isEnabled":false,""" +
                        """"templateId":"fake-template-1"}"""
                }
            }

            `when`("it is serialized and deserialized again") {
                val roundTripped = jsonb.fromJson(
                    jsonb.toJson(restEpistolaTemplateSetting),
                    RestEpistolaTemplateSetting::class.java
                )

                then("the flag is read back, not left at its default of enabled") {
                    roundTripped shouldBe restEpistolaTemplateSetting
                }
            }
        }

        given("a catalog mapping with a template that is switched off, as the frontend sends it") {
            val json = """{"catalogId":"fake-catalog","informatieObjectTypeUUID":"$informatieObjectTypeUuid","locale":null,""" +
                """"templateSettings":[{"templateId":"fake-template-1","informatieObjectTypeUUID":null,"isEnabled":false}]}"""

            `when`("it is deserialized via JSON-B") {
                val catalogMapping = jsonb.fromJson(json, RestEpistolaCatalogMapping::class.java)

                then("the template is read as switched off") {
                    catalogMapping.templateSettings shouldBe listOf(
                        createRestEpistolaTemplateSetting(templateId = "fake-template-1", isEnabled = false)
                    )
                }
            }
        }
    }

    context("the constraints of a catalog mapping a client sends") {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val jsonb = JsonbBuilder.create()

        given("a catalog mapping with all required fields") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                templateSettings = listOf(createRestEpistolaTemplateSetting(templateId = "fake-template-1"))
            )

            `when`("it is validated") {
                then("it has no violations") {
                    validator.validate(catalogMapping) shouldHaveSize 0
                }
            }
        }

        given("a request body without the template settings") {
            val catalogMapping = jsonb.fromJson(
                """{"catalogId":"fake-catalog","informatieObjectTypeUUID":null,"locale":null}""",
                RestEpistolaCatalogMapping::class.java
            )

            `when`("it is validated") {
                val violations = validator.validate(catalogMapping)

                then("the missing template settings are the only violation, so the request is rejected with 400") {
                    violations shouldHaveSize 1
                    violations.first().propertyPath.toString() shouldBe "templateSettings"
                }
            }
        }

        given("a request body with a blank catalog and a template setting without a template") {
            val catalogMapping = createRestEpistolaCatalogMapping(
                catalogId = "",
                templateSettings = listOf(createRestEpistolaTemplateSetting(templateId = ""))
            )

            `when`("it is validated") {
                val violations = validator.validate(catalogMapping)

                then("both are violations") {
                    violations.map { it.propertyPath.toString() }.toSet() shouldBe
                        setOf("catalogId", "templateSettings[0].templateId")
                }
            }
        }
    }
})
