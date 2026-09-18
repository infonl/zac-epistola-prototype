/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.converter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import nl.info.zac.documentcreation.model.AanvragerData
import nl.info.zac.documentcreation.model.DocumentCreationData
import nl.info.zac.documentcreation.model.GebruikerData
import nl.info.zac.documentcreation.model.StartformulierData
import nl.info.zac.documentcreation.model.TaskData
import nl.info.zac.documentcreation.model.ZaakData
import nl.info.zac.documentcreation.model.createData
import nl.info.zac.documentcreation.model.createStartformulierData
import nl.info.zac.documentcreation.model.createZaakData
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

private const val FAKE_TEMPLATE_ID = "fakeTemplateId"

private fun objectSchema(vararg properties: Pair<String, Any>) = mapOf("properties" to properties.toMap())

private fun leafSchema(vararg names: String) =
    mapOf("properties" to names.associateWith { mapOf("type" to "string") })

/**
 * A schema that declares every property the Kotlin model has, so that the converter is exercised
 * against the whole model rather than against a list kept up to date by hand.
 */
private fun schemaDeclaringEveryPropertyOf(vararg sections: Pair<String, KClass<*>>) =
    objectSchema(
        *sections.map { (name, modelClass) ->
            name to leafSchema(*modelClass.memberProperties.map { it.name }.toTypedArray())
        }.toTypedArray()
    )

class EpistolaTemplateDataConverterTest : BehaviorSpec({
    context("converting zaak data to an Epistola template payload") {
        given("a template whose schema declares every field ZAC maps") {
            val documentCreationData = createData()
            val schema = schemaDeclaringEveryPropertyOf(
                "zaak" to ZaakData::class,
                "aanvrager" to AanvragerData::class,
                "gebruiker" to GebruikerData::class,
                "taak" to TaskData::class,
                "startformulier" to StartformulierData::class
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("every section ZAC maps is present") {
                    payload.keys shouldContainExactlyInAnyOrder listOf(
                        "zaak",
                        "aanvrager",
                        "gebruiker",
                        "taak",
                        "startformulier"
                    )
                }

                and("every zaak field reaches the template under its own name") {
                    @Suppress("UNCHECKED_CAST")
                    val zaak = payload["zaak"] as Map<String, Any>
                    zaak.keys shouldContainExactlyInAnyOrder ZaakData::class.memberProperties.map { it.name }
                }

                and("the aanvrager fields reach the template") {
                    @Suppress("UNCHECKED_CAST")
                    val aanvrager = payload["aanvrager"] as Map<String, Any>
                    aanvrager.keys shouldContainExactlyInAnyOrder AanvragerData::class.memberProperties.map { it.name }
                }

                and("a date is written in the format a template renders") {
                    @Suppress("UNCHECKED_CAST")
                    val zaak = payload["zaak"] as Map<String, Any>
                    zaak["startdatum"] shouldBe "01-01-2026"
                }

                and("the zaak location is named rather than passed on as a coordinate pair") {
                    @Suppress("UNCHECKED_CAST")
                    val zaakgeometrie = (payload["zaak"] as Map<String, Any>)["zaakgeometrie"] as Map<String, Any>
                    zaakgeometrie shouldContainExactly mapOf(
                        "type" to "Point",
                        "latitude" to java.math.BigDecimal("52.0907"),
                        "longitude" to java.math.BigDecimal("5.1214")
                    )
                }
            }
        }

        given("a template that declares only two of the zaak fields") {
            val documentCreationData = createData()
            val schema = objectSchema("zaak" to leafSchema("identificatie", "omschrijving"))

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("only the declared fields are sent") {
                    payload shouldContainExactly mapOf(
                        "zaak" to mapOf(
                            "identificatie" to "fakeIdentificatie",
                            "omschrijving" to "fakeOmschrijving"
                        )
                    )
                }
            }
        }

        given("a startformulier holding data the citizen submitted that no template declares") {
            val documentCreationData = createData(
                startformulier = createStartformulierData(
                    data = mapOf(
                        "voorletters" to "fakeVoorletters",
                        "bsn" to "fakeBsn",
                        "telefoonnummer" to "fakeTelefoonnummer"
                    )
                )
            )
            val schema = objectSchema(
                "startformulier" to objectSchema("data" to leafSchema("voorletters"))
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("only the declared field leaves ZAC") {
                    payload shouldContainExactly mapOf(
                        "startformulier" to mapOf("data" to mapOf("voorletters" to "fakeVoorletters"))
                    )
                }
            }
        }

        given("a zaak whose optional fields have no value") {
            val documentCreationData = DocumentCreationData(
                gebruikerData = GebruikerData(id = "fakeUserId", naam = "fakeUserName"),
                zaakData = ZaakData(identificatie = "fakeIdentificatie")
            )
            val schema = schemaDeclaringEveryPropertyOf(
                "zaak" to ZaakData::class,
                "gebruiker" to GebruikerData::class
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("the fields without a value are left out instead of being sent as null") {
                    payload shouldContainExactly mapOf(
                        "zaak" to mapOf("identificatie" to "fakeIdentificatie"),
                        "gebruiker" to mapOf("id" to "fakeUserId", "naam" to "fakeUserName")
                    )
                }
            }
        }

        given("a template declaring a section that ZAC has no data for") {
            val documentCreationData = DocumentCreationData(
                gebruikerData = GebruikerData(id = "fakeUserId", naam = "fakeUserName"),
                zaakData = createZaakData()
            )
            val schema = objectSchema(
                "zaak" to leafSchema("identificatie"),
                "taak" to leafSchema("naam")
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("the section is absent rather than sent empty") {
                    payload shouldContainKey "zaak"
                    payload shouldNotContainKey "taak"
                }
            }
        }

        given("a template without a schema") {
            val documentCreationData = createData()

            `when`("the payload is built") {
                val exception = shouldThrow<EpistolaTemplateSchemaMissingException> {
                    documentCreationData.toEpistolaTemplateData(
                        templateId = FAKE_TEMPLATE_ID,
                        templateSchema = null
                    )
                }

                then("nothing is sent and the template is named") {
                    exception.message shouldContain FAKE_TEMPLATE_ID
                }
            }
        }
    }
})
