/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.documents

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import jakarta.persistence.EntityManager
import nl.info.zac.epistola.documents.model.EpistolaDocument
import nl.info.zac.epistola.documents.model.createEpistolaDocument
import java.time.ZonedDateTime
import java.util.UUID

class EpistolaDocumentRepositoryTest : BehaviorSpec({
    val entityManager = mockk<EntityManager>()
    val epistolaDocumentRepository = EpistolaDocumentRepository(entityManager)

    afterEach { checkUnnecessaryStub() }

    context("finding the template of a document") {
        given("a document whose template was stored") {
            val epistolaDocument = createEpistolaDocument()
            every {
                entityManager.find(EpistolaDocument::class.java, epistolaDocument.informatieObjectUUID)
            } returns epistolaDocument

            `when`("it is looked up by the informatieobject's UUID") {
                val foundEpistolaDocument =
                    epistolaDocumentRepository.findEpistolaDocument(epistolaDocument.informatieObjectUUID)

                then("the stored row is returned") {
                    foundEpistolaDocument shouldBeSameInstanceAs epistolaDocument
                }
            }
        }

        given("a document that has no stored template") {
            val informatieObjectUUID = UUID.randomUUID()
            every { entityManager.find(EpistolaDocument::class.java, informatieObjectUUID) } returns null

            `when`("it is looked up") {
                val foundEpistolaDocument = epistolaDocumentRepository.findEpistolaDocument(informatieObjectUUID)

                then("there is none") {
                    foundEpistolaDocument shouldBe null
                }
            }
        }
    }

    context("storing the template of a document") {
        given("an informatieobject, the template that generated it and the kanaal and language of its variant") {
            val informatieObjectUUID = UUID.randomUUID()
            val persistedSlot = slot<EpistolaDocument>()
            every { entityManager.persist(capture(persistedSlot)) } just runs

            `when`("it is stored") {
                val before = ZonedDateTime.now()
                epistolaDocumentRepository.createEpistolaDocument(
                    informatieObjectUUID = informatieObjectUUID,
                    templateId = "fake-template-id",
                    kanaal = "post",
                    locale = "en-GB"
                )

                then("a row with the UUID, the template, the kanaal, the language and the moment of storing is persisted") {
                    with(persistedSlot.captured) {
                        this.informatieObjectUUID shouldBe informatieObjectUUID
                        templateId shouldBe "fake-template-id"
                        kanaal shouldBe "post"
                        locale shouldBe "en-GB"
                        (creationDate >= before) shouldBe true
                    }
                }
            }
        }
    }
})
