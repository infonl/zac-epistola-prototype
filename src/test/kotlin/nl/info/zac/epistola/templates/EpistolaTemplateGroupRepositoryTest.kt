/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.templates

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.checkUnnecessaryStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.admin.model.createZaaktypeCmmnConfiguration
import nl.info.zac.epistola.templates.model.EpistolaTemplateGroup
import nl.info.zac.epistola.templates.model.createEpistolaTemplateGroup

class EpistolaTemplateGroupRepositoryTest : BehaviorSpec({
    val entityManager = mockk<EntityManager>()
    val criteriaBuilder = mockk<CriteriaBuilder>()
    val root = mockk<Root<EpistolaTemplateGroup>>()
    val zaaktypeConfigurationPath = mockk<Path<ZaaktypeConfiguration>>()
    val zaaktypeConfigurationIdPath = mockk<Path<Long>>()
    val zaaktypeConfigurationPredicate = mockk<Predicate>()
    val zaaktypeCmmnConfiguration = createZaaktypeCmmnConfiguration(id = 1234L)
    val epistolaTemplateGroupRepository = EpistolaTemplateGroupRepository(entityManager)

    afterEach { checkUnnecessaryStub() }

    fun givenAPredicateOnTheZaaktypeConfigurationId() {
        every { entityManager.criteriaBuilder } returns criteriaBuilder
        every { root.get<ZaaktypeConfiguration>("zaaktypeConfiguration") } returns zaaktypeConfigurationPath
        every { zaaktypeConfigurationPath.get<Long>("id") } returns zaaktypeConfigurationIdPath
        every { criteriaBuilder.equal(zaaktypeConfigurationIdPath, 1234L) } returns zaaktypeConfigurationPredicate
    }

    context("listing the template groups of a zaaktype configuration") {
        given("a zaaktype configuration with one stored group") {
            val criteriaQuery = mockk<CriteriaQuery<EpistolaTemplateGroup>>()
            val typedQuery = mockk<TypedQuery<EpistolaTemplateGroup>>()
            val epistolaTemplateGroup = createEpistolaTemplateGroup(zaaktypeConfiguration = zaaktypeCmmnConfiguration)
            givenAPredicateOnTheZaaktypeConfigurationId()
            every { criteriaBuilder.createQuery(EpistolaTemplateGroup::class.java) } returns criteriaQuery
            every { criteriaQuery.from(EpistolaTemplateGroup::class.java) } returns root
            every { criteriaQuery.select(root) } returns criteriaQuery
            every { criteriaQuery.where(zaaktypeConfigurationPredicate) } returns criteriaQuery
            every { entityManager.createQuery(criteriaQuery) } returns typedQuery
            every { typedQuery.resultList } returns listOf(epistolaTemplateGroup)

            `when`("its groups are listed") {
                val templateGroups = epistolaTemplateGroupRepository.listTemplateGroups(zaaktypeCmmnConfiguration)

                then("the groups selected by the zaaktype configuration's id are returned") {
                    templateGroups shouldBe listOf(epistolaTemplateGroup)
                }
            }
        }
    }

    context("replacing the template groups of a zaaktype configuration") {
        given("a zaaktype configuration and two new groups") {
            val criteriaDelete = mockk<CriteriaDelete<EpistolaTemplateGroup>>()
            val deleteQuery = mockk<Query>()
            val firstEpistolaTemplateGroup = createEpistolaTemplateGroup(id = null, name = "fakeTemplateGroupName1")
            val secondEpistolaTemplateGroup = createEpistolaTemplateGroup(id = null, name = "fakeTemplateGroupName2")
            givenAPredicateOnTheZaaktypeConfigurationId()
            every { criteriaBuilder.createCriteriaDelete(EpistolaTemplateGroup::class.java) } returns criteriaDelete
            every { criteriaDelete.from(EpistolaTemplateGroup::class.java) } returns root
            every { criteriaDelete.where(zaaktypeConfigurationPredicate) } returns criteriaDelete
            every { entityManager.createQuery(criteriaDelete) } returns deleteQuery
            every { deleteQuery.executeUpdate() } returns 3
            every { entityManager.persist(any<EpistolaTemplateGroup>()) } returns Unit

            `when`("the groups are replaced") {
                epistolaTemplateGroupRepository.replaceTemplateGroups(
                    zaaktypeConfiguration = zaaktypeCmmnConfiguration,
                    templateGroups = listOf(firstEpistolaTemplateGroup, secondEpistolaTemplateGroup)
                )

                then("the stored groups are deleted before the new ones are persisted") {
                    verifyOrder {
                        deleteQuery.executeUpdate()
                        entityManager.persist(firstEpistolaTemplateGroup)
                        entityManager.persist(secondEpistolaTemplateGroup)
                    }
                }
            }
        }
    }
})
