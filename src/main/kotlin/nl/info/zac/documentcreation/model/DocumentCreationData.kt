/*
 * SPDX-FileCopyrightText: 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation.model

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
 * The zaak data that ZAC offers to a document template, independent of which document creation
 * provider renders it.
 *
 * The JSON-B names below are the variable names a template author writes, so a template written
 * against one provider addresses the same fields under the other.
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

    /**
     * Only filled for providers whose templates can address it. Leaving it null keeps it out of the
     * serialized payload, so adding it did not change what an existing integration receives.
     */
    val zaakgeometrie: ZaakGeometrieData? = null,

    /** The zaaktype-specific eigenschappen of the zaak, by name. Filled on the same terms as [zaakgeometrie]. */
    val eigenschappen: Map<String, String>? = null
)

/**
 * A zaak location in the terms a letter needs — a readable coordinate pair rather than GeoJSON.
 *
 * ZAC supports POINT geometries only, so anything else carries its type and no coordinates instead
 * of a shape a template has no way to render.
 */
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

/**
 * GeoJSON orders a point's coordinates longitude first, which is the reverse of how they are
 * written in a letter, so they are named here rather than passed on as a pair.
 */
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
