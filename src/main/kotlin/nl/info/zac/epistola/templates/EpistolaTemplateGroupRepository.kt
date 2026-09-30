/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.templates

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import jakarta.transaction.Transactional.TxType.REQUIRED
import jakarta.transaction.Transactional.TxType.SUPPORTS
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.epistola.templates.model.EpistolaTemplateGroup
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor

@ApplicationScoped
@Transactional(SUPPORTS)
@NoArgConstructor
@AllOpen
class EpistolaTemplateGroupRepository @Inject constructor(
    private val entityManager: EntityManager
) {
    fun listTemplateGroups(zaaktypeConfiguration: ZaaktypeConfiguration): List<EpistolaTemplateGroup> =
        entityManager.criteriaBuilder.let { criteriaBuilder ->
            criteriaBuilder.createQuery(EpistolaTemplateGroup::class.java).let { query ->
                query.from(EpistolaTemplateGroup::class.java).let { root ->
                    query.select(root).where(
                        criteriaBuilder.equal(
                            root.get<ZaaktypeConfiguration>(EpistolaTemplateGroup::zaaktypeConfiguration.name)
                                .get<Long>(ZaaktypeConfiguration::id.name),
                            zaaktypeConfiguration.id
                        )
                    )
                }
                entityManager.createQuery(query).resultList
            }
        }

    /**
     * Deletes the stored groups before inserting [templateGroups], so a group or template the beheerder removed
     * does not survive the save. The database deletes a group's templates along with it.
     */
    @Transactional(REQUIRED)
    fun replaceTemplateGroups(
        zaaktypeConfiguration: ZaaktypeConfiguration,
        templateGroups: List<EpistolaTemplateGroup>
    ) {
        entityManager.criteriaBuilder.let { criteriaBuilder ->
            criteriaBuilder.createCriteriaDelete(EpistolaTemplateGroup::class.java).let { delete ->
                delete.from(EpistolaTemplateGroup::class.java).let { root ->
                    delete.where(
                        criteriaBuilder.equal(
                            root.get<ZaaktypeConfiguration>(EpistolaTemplateGroup::zaaktypeConfiguration.name)
                                .get<Long>(ZaaktypeConfiguration::id.name),
                            zaaktypeConfiguration.id
                        )
                    )
                }
                entityManager.createQuery(delete).executeUpdate()
            }
        }
        templateGroups.forEach(entityManager::persist)
    }
}
