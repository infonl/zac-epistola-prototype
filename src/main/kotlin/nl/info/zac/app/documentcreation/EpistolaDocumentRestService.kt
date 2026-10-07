/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation

import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import nl.info.client.zgw.drc.DrcClientService
import nl.info.client.zgw.drc.model.generated.EnkelvoudigInformatieObject
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.client.zgw.zrc.model.generated.Zaak
import nl.info.client.zgw.zrc.model.zaakUUID
import nl.info.zac.app.documentcreation.model.RestEpistolaDocument
import nl.info.zac.app.documentcreation.model.RestEpistolaDocumentCreationResponse
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.documentcreation.EpistolaDocumentVersionService
import nl.info.zac.epistola.exception.EpistolaNewVersionNotPossibleException
import nl.info.zac.policy.PolicyService
import nl.info.zac.policy.assertPolicy
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.UUID

@Path("epistola-documents")
@Produces(MediaType.APPLICATION_JSON)
@NoArgConstructor
@AllOpen
class EpistolaDocumentRestService @Inject constructor(
    private val policyService: PolicyService,
    private val epistolaDocumentVersionService: EpistolaDocumentVersionService,
    private val zrcClientService: ZrcClientService,
    private val drcClientService: DrcClientService,
    private val loggedInUserInstance: Instance<LoggedInUser>
) {
    /** Whether a new version of the document can be generated with Epistola, so the screen knows to offer it. */
    @GET
    @Path("{uuid}")
    fun readEpistolaDocument(@PathParam("uuid") uuid: UUID): RestEpistolaDocument =
        drcClientService.readEnkelvoudigInformatieobject(uuid).let { enkelvoudigInformatieObject ->
            val zaakInformatieobjecten = zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            assertPolicy(
                policyService.readDocumentRechten(
                    enkelvoudigInformatieObject,
                    zaakInformatieobjecten.firstOrNull()?.let { zrcClientService.readZaak(it.zaakUUID) }
                ).canLezen
            )
            RestEpistolaDocument(
                isNewVersionAvailable = zaakInformatieobjecten.size == 1 &&
                    epistolaDocumentVersionService.isNewVersionAvailable(uuid)
            )
        }

    /**
     * Generates the document again from the template that produced it, with the zaak's data as it is now, and stores
     * it as the next version of the same informatieobject. It returns once that is done, as creating the document
     * did. The zaak is the one the document is linked to, so the data of one zaak cannot end up in the document of
     * another. A document linked to several zaken gets no new version, because nothing says whose data it should show.
     */
    @POST
    @Path("{uuid}/versions")
    fun createVersion(@PathParam("uuid") uuid: UUID): RestEpistolaDocumentCreationResponse {
        val enkelvoudigInformatieObject = drcClientService.readEnkelvoudigInformatieobject(uuid)
        val zaak = readOnlyZaakOfDocument(enkelvoudigInformatieObject)
        assertPolicy(policyService.readZaakRechten(zaak, loggedInUserInstance.get()).canCreerenDocument)
        assertPolicy(policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak).canToevoegenNieuweVersie)
        return epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
            .let { RestEpistolaDocumentCreationResponse(informatieobjectUuid = it.url.extractUuid()) }
    }

    private fun readOnlyZaakOfDocument(enkelvoudigInformatieObject: EnkelvoudigInformatieObject): Zaak =
        zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject).let { zaakInformatieobjecten ->
            zaakInformatieobjecten.singleOrNull()?.let { zrcClientService.readZaak(it.zaakUUID) }
                ?: throw EpistolaNewVersionNotPossibleException(
                    "Document '${enkelvoudigInformatieObject.url.extractUuid()}' is linked to " +
                        "${zaakInformatieobjecten.size} zaken, so there is no single zaak whose data a new version " +
                        "could be generated from."
                )
        }
}
