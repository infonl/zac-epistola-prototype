/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import nl.info.zac.documentcreation.exception.EpistolaTemplateSchemaMissingException
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

private const val FAKE_TEMPLATE_ID = "fakeTemplateId"

private fun objectSchema(vararg properties: Pair<String, Any>) = mapOf("properties" to properties.toMap())

private fun leafSchema(vararg names: String) =
    mapOf("properties" to names.associateWith { mapOf("type" to "string") })

private fun arraySchema(itemSchema: Any) = mapOf("type" to "array", "items" to itemSchema)

private fun referenceSchema(reference: String) = mapOf("\$ref" to reference)

private fun startformulierDataSchema(dataSchema: Any, vararg definitions: Pair<String, Any>) =
    objectSchema("startformulier" to objectSchema("data" to dataSchema)) + mapOf("\$defs" to definitions.toMap())

private fun createDataWithStartformulier(data: Map<String, Any>) =
    createData(startformulier = createStartformulierData(data = data))

private fun DocumentCreationData.toPayload(schema: Any) =
    toEpistolaTemplateData(templateId = FAKE_TEMPLATE_ID, templateSchema = schema)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.startformulierData() =
    (this["startformulier"] as Map<String, Any>?)?.get("data") as Map<String, Any>?

private fun KProperty1<*, *>.declaredSchema() =
    mapOf("type" to if (returnType.classifier in setOf(String::class, LocalDate::class)) "string" else "object")

private fun schemaDeclaringEveryPropertyOf(vararg sections: Pair<String, KClass<*>>) =
    objectSchema(
        *sections.map { (name, modelClass) ->
            name to mapOf("properties" to modelClass.memberProperties.associate { it.name to it.declaredSchema() })
        }.toTypedArray()
    )

class EpistolaTemplateDataTest : BehaviorSpec({
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

        given("a template that declares the startformulier data as a free-form object") {
            val documentCreationData = createData(
                startformulier = createStartformulierData(
                    data = mapOf("voorletters" to "fakeVoorletters", "bsn" to "fakeBsn")
                )
            )
            val schema = objectSchema(
                "startformulier" to objectSchema("data" to mapOf("type" to "object"))
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toEpistolaTemplateData(
                    templateId = FAKE_TEMPLATE_ID,
                    templateSchema = schema
                )

                then("everything the citizen submitted is sent, because the template declared no fields to narrow it to") {
                    payload shouldContainExactly mapOf(
                        "startformulier" to mapOf(
                            "data" to mapOf("voorletters" to "fakeVoorletters", "bsn" to "fakeBsn")
                        )
                    )
                }
            }
        }

        given("a startformulier list whose item schema declares only some of the fields of each item") {
            val documentCreationData = createDataWithStartformulier(
                mapOf(
                    "kinderen" to listOf(
                        mapOf("naam" to "fakeNaam1", "bsn" to "fakeBsn1"),
                        mapOf("naam" to "fakeNaam2", "bsn" to "fakeBsn2")
                    )
                )
            )
            val schema = startformulierDataSchema(objectSchema("kinderen" to arraySchema(leafSchema("naam"))))

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("every item keeps only the declared fields") {
                    payload.startformulierData() shouldBe mapOf(
                        "kinderen" to listOf(mapOf("naam" to "fakeNaam1"), mapOf("naam" to "fakeNaam2"))
                    )
                }
            }
        }

        given("a startformulier list declared as an array without an item schema") {
            val kinderen = listOf(mapOf("naam" to "fakeNaam1", "bsn" to "fakeBsn1"))
            val documentCreationData = createDataWithStartformulier(mapOf("kinderen" to kinderen))
            val schema = startformulierDataSchema(objectSchema("kinderen" to mapOf("type" to "array")))

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the whole list is sent, because the template declared no fields to narrow its items to") {
                    payload.startformulierData() shouldBe mapOf("kinderen" to kinderen)
                }
            }
        }

        given("a template that declares the startformulier data through a reference to a definition") {
            val documentCreationData = createDataWithStartformulier(
                mapOf("voorletters" to "fakeVoorletters", "bsn" to "fakeBsn")
            )
            val schema = startformulierDataSchema(
                referenceSchema("#/\$defs/aanvraag"),
                "aanvraag" to leafSchema("voorletters")
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("only the fields of the referenced definition are sent") {
                    payload.startformulierData() shouldBe mapOf("voorletters" to "fakeVoorletters")
                }
            }
        }

        given("a template that combines a referenced definition with fields of its own through allOf") {
            val documentCreationData = createDataWithStartformulier(
                mapOf("voorletters" to "fakeVoorletters", "email" to "fakeEmail", "bsn" to "fakeBsn")
            )
            val schema = startformulierDataSchema(
                leafSchema("voorletters") + mapOf("allOf" to listOf(referenceSchema("#/\$defs/contactgegevens"))),
                "contactgegevens" to leafSchema("email")
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the fields of both are sent and nothing else") {
                    payload.startformulierData() shouldBe mapOf(
                        "voorletters" to "fakeVoorletters",
                        "email" to "fakeEmail"
                    )
                }
            }
        }

        given("a template that declares alternative shapes for the startformulier data through oneOf") {
            val documentCreationData = createDataWithStartformulier(
                mapOf("bsn" to "fakeBsn", "kvkNummer" to "fakeKvkNummer", "telefoonnummer" to "fakeTelefoonnummer")
            )
            val schema = startformulierDataSchema(
                mapOf("oneOf" to listOf(leafSchema("bsn"), leafSchema("kvkNummer")))
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the fields of every alternative are sent, and only those") {
                    payload.startformulierData() shouldBe mapOf(
                        "bsn" to "fakeBsn",
                        "kvkNummer" to "fakeKvkNummer"
                    )
                }
            }
        }

        given("a template whose schema refers to definitions that ZAC cannot resolve") {
            val documentCreationData = createData()
            val schema = objectSchema(
                "zaak" to objectSchema("toelichting" to referenceSchema("https://example.com/schemas/richtext.json")),
                "startformulier" to objectSchema("data" to referenceSchema("https://example.com/schemas/aanvraag.json")),
                "aanvrager" to referenceSchema("#/\$defs/doesNotExist")
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("a single value is still sent, because there is nothing inside it to narrow") {
                    payload["zaak"] shouldBe mapOf("toelichting" to "fakeToelichting")
                }

                and("an object is left out rather than sent whole, because ZAC cannot tell which of its fields are declared") {
                    payload shouldNotContainKey "startformulier"
                    payload shouldNotContainKey "aanvrager"
                }
            }
        }

        given("a template whose schema contains a reference that leads back to itself") {
            val documentCreationData = createData()
            val schema = objectSchema("zaak" to referenceSchema("#/\$defs/zaak")) + mapOf(
                "\$defs" to mapOf(
                    "zaak" to leafSchema("identificatie") + mapOf("allOf" to listOf(referenceSchema("#/\$defs/zaak")))
                )
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the declared fields are sent") {
                    payload shouldContainExactly mapOf("zaak" to mapOf("identificatie" to "fakeIdentificatie"))
                }
            }
        }

        given("startformulier fields declared as a single value or without a type, that hold an object") {
            val documentCreationData = createDataWithStartformulier(
                mapOf(
                    "voorletters" to "fakeVoorletters",
                    "adres" to mapOf("straat" to "fakeStraat", "bsn" to "fakeBsn"),
                    "bijlage" to mapOf("naam" to "fakeNaam", "bsn" to "fakeBsn")
                )
            )
            val schema = startformulierDataSchema(
                objectSchema(
                    "voorletters" to mapOf("type" to "string"),
                    "adres" to mapOf("type" to "string"),
                    "bijlage" to mapOf("description" to "fakeDescription")
                )
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the objects are left out, because the template did not ask for an object there") {
                    payload.startformulierData() shouldBe mapOf("voorletters" to "fakeVoorletters")
                }
            }
        }

        given("a startformulier list whose item schema is a reference that ZAC cannot resolve") {
            val documentCreationData = createDataWithStartformulier(
                mapOf(
                    "voorletters" to "fakeVoorletters",
                    "kinderen" to listOf(mapOf("naam" to "fakeNaam1", "bsn" to "fakeBsn1"))
                )
            )
            val schema = startformulierDataSchema(
                objectSchema(
                    "voorletters" to mapOf("type" to "string"),
                    "kinderen" to arraySchema(referenceSchema("https://example.com/schemas/kind.json"))
                )
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the list is left out rather than sent empty") {
                    payload.startformulierData() shouldBe mapOf("voorletters" to "fakeVoorletters")
                }
            }
        }

        given("a startformulier list with one item that does not match the declared item schema") {
            val documentCreationData = createDataWithStartformulier(
                mapOf(
                    "voorletters" to "fakeVoorletters",
                    "namen" to listOf("fakeNaam1", mapOf("naam" to "fakeNaam2", "bsn" to "fakeBsn2"))
                )
            )
            val schema = startformulierDataSchema(
                objectSchema(
                    "voorletters" to mapOf("type" to "string"),
                    "namen" to arraySchema(mapOf("type" to "string"))
                )
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the whole list is left out, so a document never shows a list with an item silently missing") {
                    payload.startformulierData() shouldBe mapOf("voorletters" to "fakeVoorletters")
                }
            }
        }

        given("a startformulier list holding an empty item, whose item schema does not allow one") {
            val documentCreationData = createDataWithStartformulier(
                mapOf(
                    "voorletters" to "fakeVoorletters",
                    "kinderen" to listOf(mapOf("naam" to "fakeNaam1"), null)
                )
            )
            val schema = startformulierDataSchema(
                objectSchema(
                    "voorletters" to mapOf("type" to "string"),
                    "kinderen" to arraySchema(leafSchema("naam"))
                )
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the whole list is left out rather than sent without the empty item") {
                    payload.startformulierData() shouldBe mapOf("voorletters" to "fakeVoorletters")
                }
            }
        }

        given("a startformulier list holding an empty item, whose item schema declares type null") {
            val documentCreationData = createDataWithStartformulier(
                mapOf("kinderen" to listOf(mapOf("naam" to "fakeNaam1", "bsn" to "fakeBsn1"), null))
            )
            val schema = startformulierDataSchema(
                objectSchema("kinderen" to arraySchema(leafSchema("naam") + mapOf("type" to listOf("object", "null"))))
            )

            `when`("the payload is built") {
                val payload = documentCreationData.toPayload(schema)

                then("the empty item keeps its place and the other item keeps only its declared fields") {
                    payload.startformulierData() shouldBe mapOf(
                        "kinderen" to listOf(mapOf("naam" to "fakeNaam1"), null)
                    )
                }
            }
        }
    }
})
