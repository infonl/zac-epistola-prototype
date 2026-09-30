/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.app.documentcreation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import nl.info.client.zgw.drc.DrcClientService
import nl.info.client.zgw.drc.model.createEnkelvoudigInformatieObject
import nl.info.client.zgw.model.createZaak
import nl.info.client.zgw.model.createZaakInformatieobjectForReads
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.zrc.ZrcClientService
import nl.info.zac.authentication.LoggedInUser
import nl.info.zac.authentication.createLoggedInUser
import nl.info.zac.documentcreation.EpistolaDocumentVersionService
import nl.info.zac.epistola.exception.EpistolaNewVersionNotPossibleException
import nl.info.zac.policy.PolicyService
import nl.info.zac.policy.exception.PolicyException
import nl.info.zac.policy.output.createDocumentRechtenAllDeny
import nl.info.zac.policy.output.createZaakRechtenAllDeny
import java.net.URI

class EpistolaDocumentRestServiceTest : BehaviorSpec({
    val policyService = mockk<PolicyService>()
    val epistolaDocumentVersionService = mockk<EpistolaDocumentVersionService>()
    val zrcClientService = mockk<ZrcClientService>()
    val drcClientService = mockk<DrcClientService>()
    val loggedInUserInstance = mockk<Instance<LoggedInUser>>()
    val epistolaDocumentRestService = EpistolaDocumentRestService(
        policyService = policyService,
        epistolaDocumentVersionService = epistolaDocumentVersionService,
        zrcClientService = zrcClientService,
        drcClientService = drcClientService,
        loggedInUserInstance = loggedInUserInstance
    )

    isolationMode = IsolationMode.InstancePerTest

    afterEach { checkUnnecessaryStub() }

    context("generating a new version of an Epistola document") {
        given("a document linked to a zaak") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            val loggedInUser = createLoggedInUser()
            every { loggedInUserInstance.get() } returns loggedInUser
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every {
                zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            } returns listOf(createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${zaak.uuid}")))
            every { zrcClientService.readZaak(zaak.uuid) } returns zaak

            `when`("a user who may create documents and add versions asks for a new version") {
                val newVersion = createEnkelvoudigInformatieObject(uuid = informatieobjectUuid, versie = 2)
                every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                    creerenDocument = true
                )
                every {
                    policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak)
                } returns createDocumentRechtenAllDeny(toevoegenNieuweVersie = true)
                every {
                    epistolaDocumentVersionService.createNewVersion(zaak, enkelvoudigInformatieObject)
                } returns newVersion

                val restEpistolaDocumentCreationResponse =
                    epistolaDocumentRestService.createVersion(informatieobjectUuid)

                then("the version is generated for the zaak the document is linked to, and the informatieobject is named") {
                    restEpistolaDocumentCreationResponse.informatieobjectUuid shouldBe informatieobjectUuid
                }
            }

            `when`("a user who may not create documents for the zaak asks for a new version") {
                every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny()

                shouldThrow<PolicyException> {
                    epistolaDocumentRestService.createVersion(informatieobjectUuid)
                }

                then("it is refused before any zaak data reaches Epistola") {
                    verify(exactly = 0) { epistolaDocumentVersionService.createNewVersion(any(), any()) }
                }
            }

            `when`("a user who may not add a version to the document asks for one") {
                every { policyService.readZaakRechten(zaak, loggedInUser) } returns createZaakRechtenAllDeny(
                    creerenDocument = true
                )
                every {
                    policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak)
                } returns createDocumentRechtenAllDeny()

                shouldThrow<PolicyException> {
                    epistolaDocumentRestService.createVersion(informatieobjectUuid)
                }

                then("it is refused before any zaak data reaches Epistola") {
                    verify(exactly = 0) { epistolaDocumentVersionService.createNewVersion(any(), any()) }
                }
            }
        }

        given("a document that is not linked to any zaak") {
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every { zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject) } returns emptyList()

            `when`("a new version is asked for") {
                val epistolaNewVersionNotPossibleException = shouldThrow<EpistolaNewVersionNotPossibleException> {
                    epistolaDocumentRestService.createVersion(informatieobjectUuid)
                }

                then("it is refused, because there is no zaak data to generate it from") {
                    epistolaNewVersionNotPossibleException.message shouldBe "Document '$informatieobjectUuid' is " +
                        "linked to 0 zaken, so there is no single zaak whose data a new version could be generated from."
                    verify(exactly = 0) { epistolaDocumentVersionService.createNewVersion(any(), any()) }
                }
            }
        }

        given("a document linked to two zaken") {
            val zaak = createZaak()
            val otherZaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every {
                zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            } returns listOf(
                createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${zaak.uuid}")),
                createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${otherZaak.uuid}"))
            )

            `when`("a new version is asked for") {
                val epistolaNewVersionNotPossibleException = shouldThrow<EpistolaNewVersionNotPossibleException> {
                    epistolaDocumentRestService.createVersion(informatieobjectUuid)
                }

                then("it is refused, so the data of one zaak never ends up in a document the other zaak also holds") {
                    epistolaNewVersionNotPossibleException.message shouldBe "Document '$informatieobjectUuid' is " +
                        "linked to 2 zaken, so there is no single zaak whose data a new version could be generated from."
                    verify(exactly = 0) { epistolaDocumentVersionService.createNewVersion(any(), any()) }
                }
            }
        }
    }

    context("reading whether a new version of a document can be generated") {
        given("a document the user may read") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every {
                zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            } returns listOf(createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${zaak.uuid}")))
            every { zrcClientService.readZaak(zaak.uuid) } returns zaak
            every {
                policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak)
            } returns createDocumentRechtenAllDeny(lezen = true)
            every { epistolaDocumentVersionService.isNewVersionAvailable(informatieobjectUuid) } returns true

            `when`("it is read") {
                val restEpistolaDocument = epistolaDocumentRestService.readEpistolaDocument(informatieobjectUuid)

                then("the answer of the service is returned") {
                    restEpistolaDocument.isNewVersionAvailable shouldBe true
                }
            }
        }

        given("a document the user may read that is linked to two zaken") {
            val zaak = createZaak()
            val otherZaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every {
                zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            } returns listOf(
                createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${zaak.uuid}")),
                createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${otherZaak.uuid}"))
            )
            every { zrcClientService.readZaak(zaak.uuid) } returns zaak
            every {
                policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak)
            } returns createDocumentRechtenAllDeny(lezen = true)

            `when`("it is read") {
                val restEpistolaDocument = epistolaDocumentRestService.readEpistolaDocument(informatieobjectUuid)

                then("no new version is offered, because a new version would be refused") {
                    restEpistolaDocument.isNewVersionAvailable shouldBe false
                    verify(exactly = 0) { epistolaDocumentVersionService.isNewVersionAvailable(any()) }
                }
            }
        }

        given("a document the user may not read") {
            val zaak = createZaak()
            val enkelvoudigInformatieObject = createEnkelvoudigInformatieObject()
            val informatieobjectUuid = enkelvoudigInformatieObject.url.extractUuid()
            every { drcClientService.readEnkelvoudigInformatieobject(informatieobjectUuid) } returns enkelvoudigInformatieObject
            every {
                zrcClientService.listZaakinformatieobjecten(enkelvoudigInformatieObject)
            } returns listOf(createZaakInformatieobjectForReads(zaak = URI("https://example.com/zaken/${zaak.uuid}")))
            every { zrcClientService.readZaak(zaak.uuid) } returns zaak
            every {
                policyService.readDocumentRechten(enkelvoudigInformatieObject, zaak)
            } returns createDocumentRechtenAllDeny()

            `when`("it is read") {
                shouldThrow<PolicyException> {
                    epistolaDocumentRestService.readEpistolaDocument(informatieobjectUuid)
                }

                then("the service is not asked, so nothing is revealed about the document") {
                    verify(exactly = 0) { epistolaDocumentVersionService.isNewVersionAvailable(any()) }
                }
            }
        }
    }
})
