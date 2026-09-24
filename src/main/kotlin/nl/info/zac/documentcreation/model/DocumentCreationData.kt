/*
 * SPDX-FileCopyrightText: 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

import jakarta.json.bind.JsonbBuilder
import jakarta.json.bind.annotation.JsonbDateFormat
import jakarta.json.bind.annotation.JsonbProperty
import net.atos.zac.util.StringUtil
import nl.info.client.kvk.zoeken.model.generated.ResultaatItem
import nl.info.client.zgw.zrc.model.generated.GeoJSONGeometry
import nl.info.client.zgw.zrc.model.generated.GeometryTypeEnum
import java.time.LocalDate

private const val DATE_FORMAT = "dd-MM-yyyy"
private const val POINT_COORDINATE_COUNT = 2

/**
 * Serializing and reading back is what keeps the two providers in step: the Epistola payload is produced
 * by the same JSON-B annotations that produce the SmartDocuments deposit.
 */
private val JSONB = JsonbBuilder.create()

/**
 * The JSON-B names below are the variable names a template author writes, for either provider, so
 * renaming one breaks existing templates.
 */
data class DocumentCreationData(
    @field:JsonbProperty("aanvrager")
    val aanvragerData: AanvragerData? = null,

    @field:JsonbProperty("gebruiker")
    val gebruikerData: GebruikerData,

    @field:JsonbProperty("startformulier")
    val startformulierData: StartformulierData? = null,

    @field:JsonbProperty("taak")
    val taskData: TaskData? = null,

    @field:JsonbProperty("zaak")
    val zaakData: ZaakData
)

fun DocumentCreationData.toEpistolaTemplateData(templateId: String, templateSchema: Any?): Map<String, Any> =
    TemplateSchemaAllowList(templateId = templateId, rootSchema = templateSchema).retain(toPayloadMap())

@Suppress("UNCHECKED_CAST")
private fun DocumentCreationData.toPayloadMap(): Map<String, Any> =
    JSONB.fromJson(JSONB.toJson(this), Map::class.java) as Map<String, Any>

data class AanvragerData(
    val naam: String? = null,
    val straat: String? = null,
    val huisnummer: String? = null,
    val postcode: String? = null,
    val woonplaats: String? = null
)

data class GebruikerData(
    val id: String,
    val naam: String
)

data class StartformulierData(
    val productAanvraagtype: String,

    val data: Map<String, Any>
)

data class TaskData(
    val naam: String,
    var behandelaar: String? = null
)

data class ZaakData(
    val behandelaar: String? = null,

    val besluit: String? = null,

    val communicatiekanaal: String? = null,

    @field:JsonbDateFormat(DATE_FORMAT)
    val einddatum: LocalDate? = null,

    @field:JsonbDateFormat(DATE_FORMAT)
    val einddatumGepland: LocalDate? = null,

    val groep: String? = null,

    val identificatie: String? = null,

    val omschrijving: String? = null,

    val opschortingReden: String? = null,

    @field:JsonbDateFormat(DATE_FORMAT)
    val registratiedatum: LocalDate? = null,

    val resultaat: String? = null,

    @field:JsonbDateFormat(DATE_FORMAT)
    val startdatum: LocalDate? = null,

    val status: String? = null,

    val toelichting: String? = null,

    @field:JsonbDateFormat(DATE_FORMAT)
    val uiterlijkeEinddatumAfdoening: LocalDate? = null,

    val vertrouwelijkheidaanduiding: String? = null,

    val verlengingReden: String? = null,

    val zaaktype: String? = null,

    /** Only filled for Epistola. Left null, it stays out of the SmartDocuments payload. */
    val zaakgeometrie: ZaakGeometrieData? = null,

    /** Filled on the same terms as [zaakgeometrie]. */
    val eigenschappen: Map<String, String>? = null
)

/** ZAC supports POINT geometries only, so any other geometry carries just its type. */
data class ZaakGeometrieData(
    val type: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

fun ResultaatItem.toAanvragerDataBedrijf() =
    this.adres.binnenlandsAdres.let {
        AanvragerData(
            naam = this.naam,
            straat = it.straatnaam,
            huisnummer = this.toHuisnummer(),
            postcode = it.postcode,
            woonplaats = it.plaats
        )
    }

fun ResultaatItem.toHuisnummer(): String? =
    StringUtil.joinNonBlank(
        this.adres.binnenlandsAdres.huisnummer?.toString(),
        this.adres.binnenlandsAdres.huisletter
    )

/** GeoJSON puts the longitude first. */
fun GeoJSONGeometry.toZaakGeometrieData() =
    if (type == GeometryTypeEnum.POINT && coordinates.size >= POINT_COORDINATE_COUNT) {
        ZaakGeometrieData(
            type = type.toString(),
            longitude = coordinates[0].toDouble(),
            latitude = coordinates[1].toDouble()
        )
    } else {
        ZaakGeometrieData(type = type.toString())
    }
