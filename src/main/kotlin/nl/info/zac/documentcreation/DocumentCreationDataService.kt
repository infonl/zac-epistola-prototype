/*
 * SPDX-FileCopyrightText: 2022 Atos, 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.documentcreation

import jakarta.inject.Inject
import nl.info.client.zgw.zrc.model.Rol
import nl.info.client.zgw.zrc.model.zaakobjecten.ZaakobjectListParameters
import nl.info.client.zgw.zrc.model.zaakobjecten.ZaakobjectProductaanvraag
import net.atos.zac.flowable.task.FlowableTaskService
import net.atos.zac.util.StringUtil
import nl.info.client.brp.BrpClientService
import nl.info.client.brp.model.generated.Adres
import nl.info.client.brp.model.generated.Persoon
import nl.info.client.brp.model.generated.VerblijfadresBinnenland
import nl.info.client.kvk.KvkClientService
import nl.info.client.or.`object`.ObjectsClientService
import nl.info.zac.documentcreation.model.AanvragerData
import nl.info.zac.documentcreation.model.DocumentCreationData
import nl.info.zac.documentcreation.model.GebruikerData
import nl.info.zac.documentcreation.model.StartformulierData
import nl.info.zac.documentcreation.model.TaskData
import nl.info.zac.documentcreation.model.ZaakData
import nl.info.zac.documentcreation.model.toZaakGeometrieData
import nl.info.zac.documentcreation.model.toAanvragerDataBedrijf
import nl.info.client.zgw.shared.ZgwApiService
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.client.zgw.zrc.model.generated.BetrokkeneTypeEnum.NATUURLIJK_PERSOON
import nl.info.client.zgw.zrc.model.generated.BetrokkeneTypeEnum.NIET_NATUURLIJK_PERSOON
import nl.info.client.zgw.zrc.model.generated.BetrokkeneTypeEnum.VESTIGING
import nl.info.client.zgw.zrc.model.generated.NietNatuurlijkPersoonIdentificatie
import nl.info.client.zgw.zrc.model.generated.ObjectTypeEnum
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.zrc.util.isOpgeschort
import nl.info.client.zgw.zrc.util.isVerlengd
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.identity.IdentityService
import nl.info.zac.identity.model.getFullName
import nl.info.zac.productaanvraag.ProductaanvraagService
import nl.info.zac.util.NoArgConstructor
import java.net.URI
import java.util.Objects
import java.util.UUID
import java.util.logging.Logger

@NoArgConstructor
@Suppress("LongParameterList", "TooManyFunctions")
class DocumentCreationDataService @Inject constructor(
    private val zgwApiService: ZgwApiService,
    private val zrcClientService: ZrcClientService,
    private val ztcClientService: ZtcClientService,
    private val brpClientService: BrpClientService,
    private val kvkClientService: KvkClientService,
    private val objectsClientService: ObjectsClientService,
    private val flowableTaskService: FlowableTaskService,
    private val identityService: IdentityService,
    private val productaanvraagService: ProductaanvraagService
) {
    companion object {
        private val LOG = Logger.getLogger(DocumentCreationDataService::class.java.name)
    }

    fun createData(loggedInUser: LoggedInUser, zaak: Zaak, taskId: String? = null) =
        DocumentCreationData(
            aanvragerData = createAanvragerData(zaak, loggedInUser),
            gebruikerData = createGebruikerData(loggedInUser),
            startformulierData = createStartformulierData(zaak.url),
            taskData = taskId?.let { createTaskData(it) },
            zaakData = createZaakData(zaak)
        )

    /**
     * Kept apart from [createData] so that the SmartDocuments payload stays as it is, and does not pay
     * for the extra call to the zaakregistratie that the eigenschappen need.
     */
    fun createEpistolaData(loggedInUser: LoggedInUser, zaak: Zaak, taskId: String? = null) =
        createData(loggedInUser = loggedInUser, zaak = zaak, taskId = taskId).let {
            it.copy(
                zaakData = it.zaakData.copy(
                    zaakgeometrie = zaak.zaakgeometrie?.toZaakGeometrieData(),
                    eigenschappen = readEigenschappen(zaak)
                )
            )
        }

    private fun readEigenschappen(zaak: Zaak): Map<String, String>? =
        zrcClientService.listZaakeigenschappen(zaak.uuid)
            .mapNotNull { zaakEigenschap ->
                zaakEigenschap.naam?.takeIf { it.isNotBlank() }?.let { it to zaakEigenschap.waarde.orEmpty() }
            }
            .groupBy({ it.first }, { it.second })
            .onEach { (naam, waarden) ->
                if (waarden.size > 1) {
                    LOG.warning { "Zaak '${zaak.identificatie}' has ${waarden.size} eigenschappen named '$naam'" }
                }
            }
            .filterValues { it.size == 1 }
            .mapValues { (_, waarden) -> waarden.first() }
            .takeIf { it.isNotEmpty() }

    private fun createGebruikerData(loggedInUser: LoggedInUser) =
        GebruikerData(
            id = loggedInUser.id,
            naam = loggedInUser.getFullName()
        )

    private fun createZaakData(zaak: Zaak) =
        ZaakData(
            behandelaar = zgwApiService.findBehandelaarMedewerkerRoleForZaak(zaak)?.naam,
            communicatiekanaal = zaak.communicatiekanaalNaam,
            einddatum = zaak.einddatum,
            einddatumGepland = zaak.einddatumGepland,
            groep = zgwApiService.findGroepForZaak(zaak)?.naam,
            identificatie = zaak.identificatie,
            omschrijving = zaak.omschrijving,
            opschortingReden = if (zaak.isOpgeschort()) { zaak.opschorting.reden } else null,
            registratiedatum = zaak.registratiedatum,
            resultaat = zaak.resultaat?.let {
                zrcClientService.readResultaat(it).let { resultaat ->
                    ztcClientService.readResultaattype(resultaat.resultaattype).omschrijving
                }
            },
            startdatum = zaak.startdatum,
            status = zaak.status?.let { statusUri ->
                zrcClientService.readStatus(statusUri).let {
                    ztcClientService.readStatustype(it.statustype).omschrijving
                }
            },
            toelichting = zaak.toelichting,
            uiterlijkeEinddatumAfdoening = zaak.uiterlijkeEinddatumAfdoening,
            vertrouwelijkheidaanduiding = zaak.vertrouwelijkheidaanduiding?.toString(),
            verlengingReden = if (zaak.isVerlengd()) { zaak.verlenging.reden } else null,
            zaaktype = ztcClientService.readZaaktype(zaak.zaaktype).omschrijving
        )

    private fun createAanvragerData(zaak: Zaak, loggedInUser: LoggedInUser): AanvragerData? =
        zgwApiService.findInitiatorRoleForZaak(zaak)?.let { initiator ->
            convertToAanvragerData(initiator, zaak.zaaktype.extractUuid(), loggedInUser)
        }

    private fun convertToAanvragerData(initiator: Rol<*>, zaaktypeUuid: UUID, loggedInUser: LoggedInUser): AanvragerData? =
        when (initiator.betrokkeneType) {
            NATUURLIJK_PERSOON -> initiator.identificatienummer?.run {
                createAanvragerDataNatuurlijkPersoon(
                    bsn = this,
                    zaaktypeUuid = zaaktypeUuid,
                    userName = loggedInUser.id
                )
            }
            VESTIGING -> initiator.identificatienummer?.run {
                createAanvragerDataVestiging(this)
            }
            NIET_NATUURLIJK_PERSOON -> createAanvragerDataNietNatuurlijkPersoon(initiator)
            else -> error("Initiator of type '${initiator.betrokkeneType}' is not supported")
        }

    private fun createAanvragerDataNatuurlijkPersoon(bsn: String, zaaktypeUuid: UUID, userName: String): AanvragerData? =
        brpClientService.retrievePersoon(bsn, zaaktypeUuid, userName)?.let(::convertToAanvragerDataPersoon)

    private fun convertToAanvragerDataPersoon(persoon: Persoon) =
        AanvragerData(
            naam = persoon.naam?.volledigeNaam,
            straat = persoon.verblijfplaats?.let { it as? Adres }?.verblijfadres?.officieleStraatnaam,
            huisnummer = persoon.verblijfplaats?.let { it as? Adres }?.verblijfadres?.let { convertToHuisnummer(it) },
            postcode = persoon.verblijfplaats?.let { it as? Adres }?.verblijfadres?.postcode,
            woonplaats = persoon.verblijfplaats?.let { it as? Adres }?.verblijfadres?.woonplaats
        )

    private fun convertToHuisnummer(verblijfadres: VerblijfadresBinnenland) =
        StringUtil.joinNonBlank(
            Objects.toString(verblijfadres.huisnummer, null),
            verblijfadres.huisnummertoevoeging,
            verblijfadres.huisletter
        )

    private fun createAanvragerDataVestiging(vestigingsnummer: String): AanvragerData? =
        kvkClientService.findVestiging(vestigingsnummer)?.toAanvragerDataBedrijf()

    /**
     * Note that niet-natuurlijke personen can be used both for KVK niet-natuurlijke personen (with an RSIN)
     * and for KVK vestigingen.
     */
    private fun createAanvragerDataNietNatuurlijkPersoon(initiator: Rol<*>): AanvragerData? {
        val nietNatuurlijkPersoonIdentificatie = (initiator.betrokkeneIdentificatie as? NietNatuurlijkPersoonIdentificatie)
        val kvkResultaat = when {
            nietNatuurlijkPersoonIdentificatie?.innNnpId?.isNotBlank() == true ->
                kvkClientService.findRechtspersoonByRsin(nietNatuurlijkPersoonIdentificatie.innNnpId)
            nietNatuurlijkPersoonIdentificatie?.vestigingsNummer?.isNotBlank() == true ->
                kvkClientService.findVestiging(nietNatuurlijkPersoonIdentificatie.vestigingsNummer)
            else -> error(
                "Niet-natuurlijke persoon initiator role '$initiator' with neither INN NNP ID (RSIN) " +
                    "nor vestigingsnummer is not supported"
            )
        }
        return kvkResultaat?.toAanvragerDataBedrijf()
    }

    private fun createStartformulierData(zaakUri: URI): StartformulierData? =
        ZaakobjectListParameters().apply {
            zaak = zaakUri
            objectType = ObjectTypeEnum.OVERIGE
        }.let(zrcClientService::listZaakobjecten)
            .results()
            .filter { ZaakobjectProductaanvraag.OBJECT_TYPE_OVERIGE_PRODUCTAANVRAAG == it.objectTypeOverige }
            .mapNotNull { zaakobject ->
                zaakobject.`object`?.extractUuid()?.let(objectsClientService::readObject)?.let { productAanvraagObject ->
                    StartformulierData(
                        productAanvraagtype = productaanvraagService.getProductaanvraag(productAanvraagObject).type,
                        data = productaanvraagService.getAanvraaggegevens(productAanvraagObject)
                    )
                }
            }
            .singleOrNull()

    private fun createTaskData(taskId: String): TaskData =
        flowableTaskService.readTask(taskId).let { taskInfo ->
            TaskData(
                naam = taskInfo.name,
                behandelaar = taskInfo.assignee?.let { identityService.readUser(it).getFullName() }
            )
        }
}
