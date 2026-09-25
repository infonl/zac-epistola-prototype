/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.transaction.Transactional.TxType.REQUIRED
import jakarta.transaction.Transactional.TxType.SUPPORTS
import nl.info.client.epistola.EpistolaClientService
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.exception.ZaaktypeConfigurationNotFoundException
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.rest.RestEpistolaTemplate
import nl.info.zac.epistola.rest.RestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.rest.toEpistolaTemplateGroup
import nl.info.zac.epistola.rest.toRestEpistolaTemplate
import nl.info.zac.epistola.rest.toRestMappedEpistolaTemplateGroup
import nl.info.zac.epistola.rest.validate
import nl.info.zac.epistola.templates.EpistolaTemplateGroupRepository
import nl.info.zac.epistola.templates.model.copyTo
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.util.UUID
import java.util.logging.Logger

/**
 * The Epistola templates a zaaktype offers, arranged in template groups. Epistola keeps its templates flat,
 * so the groups belong to ZAC: the beheerder names them and puts templates in them.
 *
 * Only the template id is stored. Its name is read from Epistola each time, the same way ZAC handles
 * SmartDocuments templates, so a template renamed in Epistola never shows a stale name.
 */
@ApplicationScoped
@Transactional(SUPPORTS)
@NoArgConstructor
@AllOpen
class EpistolaTemplatesService @Inject constructor(
    private val epistolaClientService: EpistolaClientService,
    private val epistolaTemplateGroupRepository: EpistolaTemplateGroupRepository,
    private val zaaktypeConfigurationService: ZaaktypeConfigurationService,
    private val ztcClientService: ZtcClientService,
    private val documentCreationProviderConfiguration: DocumentCreationProviderConfiguration
) {
    companion object {
        private val LOG = Logger.getLogger(EpistolaTemplatesService::class.java.name)
    }

    /**
     * Empty when Epistola is not the active provider. Epistola's settings are only validated when it is, so
     * reaching the client in any other configuration would fail.
     */
    fun listTemplates(): List<RestEpistolaTemplate> =
        if (isEpistolaActive()) {
            epistolaClientService.listTemplates()
                .map { it.toRestEpistolaTemplate() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            emptyList()
        }

    fun readTemplateMapping(zaaktypeUuid: UUID): List<RestMappedEpistolaTemplateGroup> =
        readStoredTemplateGroups(zaaktypeUuid).takeIf { it.isNotEmpty() }?.let { templateGroups ->
            val templateNamesById = listTemplates().associate { it.id to it.name }
            templateGroups
                .map { it.toRestMappedEpistolaTemplateGroup(templateNamesById) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.orEmpty()

    @Transactional(REQUIRED)
    fun storeTemplateMapping(zaaktypeUuid: UUID, templateGroups: List<RestMappedEpistolaTemplateGroup>) {
        if (!isEpistolaActive()) {
            throw EpistolaTemplateMappingException("Epistola is not the active document creation provider.")
        }
        val zaaktypeConfiguration = readZaaktypeConfiguration(zaaktypeUuid)
        templateGroups.validate(
            availableTemplateIds = listTemplates().mapTo(mutableSetOf()) { it.id },
            informatieobjecttypeUuids = ztcClientService.readZaaktype(zaaktypeUuid).informatieobjecttypen
                .mapTo(mutableSetOf()) { it.extractUuid() }
        )
        LOG.fine { "Storing ${templateGroups.size} Epistola template groups for zaaktype '$zaaktypeUuid'" }
        epistolaTemplateGroupRepository.replaceTemplateGroups(
            zaaktypeConfiguration = zaaktypeConfiguration,
            templateGroups = templateGroups.map { it.toEpistolaTemplateGroup(zaaktypeConfiguration) }
        )
    }

    /**
     * Copies from the stored mapping without asking Epistola, and whichever provider is active, so publishing
     * a new version of a zaaktype does not depend on Epistola being reachable.
     */
    @Transactional(REQUIRED)
    fun copyTemplateMapping(previousZaaktypeUuid: UUID, newZaaktypeUuid: UUID) {
        val previousTemplateGroups = zaaktypeConfigurationService.readZaaktypeConfiguration(previousZaaktypeUuid)
            ?.let(epistolaTemplateGroupRepository::listTemplateGroups)
            .orEmpty()
        if (previousTemplateGroups.isEmpty()) return

        val newZaaktypeConfiguration = readZaaktypeConfiguration(newZaaktypeUuid)
        LOG.fine { "Copying Epistola template groups from zaaktype '$previousZaaktypeUuid' to '$newZaaktypeUuid'" }
        epistolaTemplateGroupRepository.replaceTemplateGroups(
            zaaktypeConfiguration = newZaaktypeConfiguration,
            templateGroups = previousTemplateGroups.map { it.copyTo(newZaaktypeConfiguration) }
        )
    }

    /**
     * A template the zaaktype does not offer is refused, and so is every template while the beheerder has
     * switched Epistola off for the zaaktype, so a behandelaar can only generate what the beheerder offers.
     */
    fun readInformatieobjecttypeUuid(zaaktypeUuid: UUID, templateId: String): UUID =
        zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
            ?.takeIf { isEpistolaActive() && it.epistolaEnabled }
            ?.let(epistolaTemplateGroupRepository::listTemplateGroups)
            .orEmpty()
            .flatMap { it.templates }
            .firstOrNull { it.epistolaId == templateId }
            ?.informatieObjectTypeUUID
            ?: throw EpistolaTemplateNotConfiguredException(
                "Epistola template '$templateId' is not configured for zaaktype '$zaaktypeUuid'."
            )

    fun isEpistolaActive() = documentCreationProviderConfiguration.activeProvider == DocumentCreationProvider.EPISTOLA

    private fun readStoredTemplateGroups(zaaktypeUuid: UUID) =
        if (isEpistolaActive()) {
            zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
                ?.let(epistolaTemplateGroupRepository::listTemplateGroups)
                .orEmpty()
        } else {
            emptyList()
        }

    private fun readZaaktypeConfiguration(zaaktypeUuid: UUID): ZaaktypeConfiguration =
        zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
            ?: throw ZaaktypeConfigurationNotFoundException(
                "No zaaktype configuration found for zaaktype UUID '$zaaktypeUuid'"
            )
}
