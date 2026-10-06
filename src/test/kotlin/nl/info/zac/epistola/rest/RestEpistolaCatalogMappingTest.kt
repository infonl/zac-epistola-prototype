/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.rest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
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
})
