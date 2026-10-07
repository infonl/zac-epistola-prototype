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
import nl.info.client.epistola.exception.EpistolaRequestFailedException
import nl.info.client.epistola.model.EpistolaGenerationTemplate
import nl.info.client.epistola.model.SYSTEM_CATALOG
import nl.info.client.zgw.util.extractUuid
import nl.info.client.zgw.ztc.ZtcClientService
import nl.info.zac.admin.ZaaktypeCmmnConfigurationBeheerService
import nl.info.zac.admin.ZaaktypeConfigurationService
import nl.info.zac.admin.exception.ZaaktypeConfigurationNotFoundException
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.admin.model.ZaaktypeConfiguration.Companion.ZaaktypeConfigurationType.CMMN
import nl.info.zac.configuration.DocumentCreationProviderConfiguration
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import nl.info.zac.epistola.exception.EpistolaCmmnOnlyException
import nl.info.zac.epistola.exception.EpistolaTemplateMappingException
import nl.info.zac.epistola.exception.EpistolaTemplateNotConfiguredException
import nl.info.zac.epistola.exception.EpistolaTemplateNotOfferedException
import nl.info.zac.epistola.model.EpistolaTemplateSetting
import nl.info.zac.epistola.model.OfferedEpistolaCatalog
import nl.info.zac.epistola.rest.RestEpistolaCatalog
import nl.info.zac.epistola.rest.RestEpistolaCatalogMapping
import nl.info.zac.epistola.rest.RestEpistolaTemplate
import nl.info.zac.epistola.rest.RestEpistolaTemplateSetting
import nl.info.zac.epistola.rest.RestOfferedEpistolaTemplate
import nl.info.zac.epistola.rest.toRestEpistolaCatalog
import nl.info.zac.epistola.rest.toRestEpistolaTemplate
import nl.info.zac.epistola.rest.toRestEpistolaVariant
import nl.info.zac.epistola.rest.validate
import nl.info.zac.exception.ErrorCode.ERROR_CODE_EPISTOLA_UNAVAILABLE
import nl.info.zac.util.AllOpen
import nl.info.zac.util.NoArgConstructor
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * The Epistola templates a zaaktype offers: every template of the one catalog the beheerder chose for it. Epistola
 * groups its templates in catalogs, so ZAC keeps no grouping of its own.
 *
 * Only the catalog's id is stored. Its templates and their names are read from Epistola each time, the same way ZAC
 * handles SmartDocuments templates, so a template added to or renamed in the catalog shows without a step in ZAC.
 *
 * The names of the last successful listing of each catalog are kept in memory, and used only to list a zaaktype's
 * templates while Epistola cannot be reached. A restart empties them; the listing then fails as it does without them.
 */
@ApplicationScoped
@Transactional(SUPPORTS)
@NoArgConstructor
@AllOpen
@Suppress("TooManyFunctions")
class EpistolaTemplatesService @Inject constructor(
    private val epistolaClientService: EpistolaClientService,
    private val zaaktypeConfigurationService: ZaaktypeConfigurationService,
    private val zaaktypeCmmnConfigurationBeheerService: ZaaktypeCmmnConfigurationBeheerService,
    private val ztcClientService: ZtcClientService,
    private val documentCreationProviderConfiguration: DocumentCreationProviderConfiguration
) {
    companion object {
        private val LOG = Logger.getLogger(EpistolaTemplatesService::class.java.name)
    }

    private val lastReadTemplateNames = ConcurrentHashMap<String, ReadTemplateNames>()

    /**
     * Empty when Epistola is not the active provider. Epistola's settings are only validated when it is, so
     * reaching the client in any other configuration would fail.
     *
     * Epistola's own catalog is left out: it holds the attributes every tenant shares, and no templates.
     */
    fun listCatalogs(): List<RestEpistolaCatalog> =
        if (isEpistolaActive()) {
            epistolaClientService.listCatalogs()
                .map { it.toRestEpistolaCatalog() }
                .filterNot { it.id == SYSTEM_CATALOG }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            emptyList()
        }

    /**
     * Empty when Epistola is not the active provider, as [listCatalogs] is.
     *
     * Each template carries its languages, kanalen and variants, read from Epistola with one request per template. While
     * Epistola cannot be reached the templates come by the names of the last listing, and without those details.
     */
    fun listTemplates(catalogId: String): List<RestEpistolaTemplate> =
        if (isEpistolaActive()) {
            val templateNames = readTemplateNames(catalogId)
            val generationTemplates = if (templateNames.isRemembered) {
                emptyMap()
            } else {
                readGenerationTemplates(catalogId = catalogId, templateIds = templateNames.namesById.keys)
            }
            templateNames.namesById.map { (id, name) ->
                RestEpistolaTemplate(
                    id = id,
                    name = name,
                    locales = generationTemplates[id]?.locales?.locales?.sorted(),
                    kanalen = generationTemplates[id]?.kanalen?.kanalen,
                    variants = generationTemplates[id]?.variants?.map { it.toRestEpistolaVariant() }
                )
            }
        } else {
            emptyList()
        }

    /** The catalog is the one ZAC uses for the zaaktype, also while the beheerder has not chosen one. */
    fun readCatalogMapping(zaaktypeUuid: UUID): RestEpistolaCatalogMapping {
        assertEpistolaIsActive()
        val zaaktypeConfiguration = zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
        return RestEpistolaCatalogMapping(
            catalogId = zaaktypeConfiguration?.epistolaCatalogId ?: epistolaClientService.defaultCatalogId,
            informatieObjectTypeUUID = zaaktypeConfiguration?.epistolaInformatieobjecttypeUuid,
            locale = zaaktypeConfiguration?.epistolaLocale,
            templateSettings = zaaktypeConfiguration?.readEpistolaTemplateSettings().orEmpty()
                .map { (templateId, templateSetting) ->
                    RestEpistolaTemplateSetting(
                        templateId = templateId,
                        informatieObjectTypeUUID = templateSetting.informatieObjectTypeUuid,
                        isEnabled = templateSetting.isEnabled
                    )
                }
                .sortedBy(RestEpistolaTemplateSetting::templateId)
        )
    }

    @Transactional(REQUIRED)
    fun storeCatalogMapping(zaaktypeUuid: UUID, catalogMapping: RestEpistolaCatalogMapping) {
        assertEpistolaIsActive()
        val zaaktypeCmmnConfiguration = zaaktypeCmmnConfigurationBeheerService.readZaaktypeCmmnConfiguration(zaaktypeUuid)
            ?: throw ZaaktypeConfigurationNotFoundException(
                "No CMMN zaaktype configuration found for zaaktype UUID '$zaaktypeUuid'"
            )
        catalogMapping.validate(
            availableCatalogIds = listCatalogs().mapTo(mutableSetOf()) { it.id },
            informatieobjecttypeUuids = ztcClientService.readZaaktype(zaaktypeUuid).informatieobjecttypen
                .mapTo(mutableSetOf()) { it.extractUuid() }
        )
        LOG.fine { "Storing Epistola catalog '${catalogMapping.catalogId}' for zaaktype '$zaaktypeUuid'" }
        val templateIdsInCatalog = epistolaClientService.listTemplates(catalogMapping.catalogId)
            .mapTo(mutableSetOf()) { it.toRestEpistolaTemplate().id }
        zaaktypeCmmnConfigurationBeheerService.storeZaaktypeCmmnConfiguration(
            zaaktypeCmmnConfiguration.apply {
                epistolaCatalogId = catalogMapping.catalogId
                epistolaInformatieobjecttypeUuid = catalogMapping.informatieObjectTypeUUID
                epistolaLocale = catalogMapping.locale?.takeIf(String::isNotBlank)
                replaceEpistolaTemplateSettings(
                    catalogMapping.templateSettings
                        .filter { it.templateId in templateIdsInCatalog }
                        .associate {
                            it.templateId to EpistolaTemplateSetting(
                                informatieObjectTypeUuid = it.informatieObjectTypeUUID,
                                isEnabled = it.isEnabled ?: true
                            )
                        }
                )
            }
        )
    }

    /**
     * Empty when [readOfferedCatalog] would refuse, so that *Document maken* lists nothing to generate from. A template
     * the beheerder switched off is left out, and each template names the informatieobjecttype its document is stored
     * under: its own, or else the zaaktype's.
     */
    fun listOfferedTemplates(zaaktypeUuid: UUID): List<RestOfferedEpistolaTemplate> =
        findOfferedCatalog(zaaktypeUuid)?.let { offeredCatalog ->
            readTemplateNames(offeredCatalog.catalogId).namesById
                .filterKeys(offeredCatalog::isOffered)
                .map { (id, name) ->
                    RestOfferedEpistolaTemplate(
                        id = id,
                        name = name,
                        informatieObjectTypeUUID = offeredCatalog.informatieObjectTypeUuidOf(id)
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.orEmpty()

    /**
     * A zaaktype offers the templates of its catalog only while Epistola is the active provider, the beheerder has
     * switched Epistola on for it and has chosen the informatieobjecttype its documents are stored under. Whether a
     * template is in the catalog is for Epistola to say, when the template is read from it.
     *
     * @throws EpistolaCmmnOnlyException when Epistola is the active provider and the zaaktype is not a CMMN one
     * @throws EpistolaTemplateNotConfiguredException when the zaaktype offers no Epistola templates
     */
    fun readOfferedCatalog(zaaktypeUuid: UUID): OfferedEpistolaCatalog {
        val zaaktypeConfiguration = zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)
        if (isEpistolaActive() && zaaktypeConfiguration != null && zaaktypeConfiguration.getConfigurationType() != CMMN) {
            throw EpistolaCmmnOnlyException(
                "Creating a document with Epistola is limited to zaken with a CMMN zaaktype; " +
                    "zaaktype '$zaaktypeUuid' is not a CMMN zaaktype."
            )
        }
        return zaaktypeConfiguration?.toOfferedCatalog()
            ?: throw EpistolaTemplateNotConfiguredException("Zaaktype '$zaaktypeUuid' offers no Epistola templates.")
    }

    /**
     * [readOfferedCatalog] for generating a document from [templateId], which the beheerder may have switched off. Only
     * a new version of a document skips this check, as it is made from the template the first version came from.
     *
     * @throws EpistolaTemplateNotOfferedException when the zaaktype does not offer the template
     */
    fun readCatalogOfferingTemplate(zaaktypeUuid: UUID, templateId: String): OfferedEpistolaCatalog =
        readOfferedCatalog(zaaktypeUuid).also {
            if (!it.isOffered(templateId)) {
                throw EpistolaTemplateNotOfferedException(
                    "Zaaktype '$zaaktypeUuid' does not offer Epistola template '$templateId'."
                )
            }
        }

    fun isEpistolaActive() = documentCreationProviderConfiguration.activeProvider == DocumentCreationProvider.EPISTOLA

    private fun assertEpistolaIsActive() {
        if (!isEpistolaActive()) {
            throw EpistolaTemplateMappingException("Epistola is not the active document creation provider.")
        }
    }

    private fun findOfferedCatalog(zaaktypeUuid: UUID) =
        if (isEpistolaActive()) {
            zaaktypeConfigurationService.readZaaktypeConfiguration(zaaktypeUuid)?.toOfferedCatalog()
        } else {
            null
        }

    private fun ZaaktypeConfiguration.toOfferedCatalog() =
        epistolaInformatieobjecttypeUuid
            ?.takeIf { isEpistolaActive() && isEpistolaEnabled && getConfigurationType() == CMMN }
            ?.let {
                OfferedEpistolaCatalog(
                    catalogId = epistolaCatalogId ?: epistolaClientService.defaultCatalogId,
                    informatieObjectTypeUuid = it,
                    locale = epistolaLocale,
                    templateSettings = readEpistolaTemplateSettings()
                )
            }

    /**
     * Every successful listing replaces the names kept for the catalog, so a template Epistola has dropped from it
     * cannot come back from them.
     */
    private fun listTemplateNames(catalogId: String): Map<String, String> =
        epistolaClientService.listTemplates(catalogId)
            .map { it.toRestEpistolaTemplate() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .also { templates ->
                lastReadTemplateNames[catalogId] = ReadTemplateNames(
                    namesById = templates.associate { it.id to it.name },
                    readAt = Instant.now()
                )
            }
            .associate { it.id to it.name }

    /**
     * Only an Epistola that cannot be reached falls back to the names of the catalog's last listing. Refused access, a
     * rate limit or a rejected request say something the user needs to see.
     */
    private fun readTemplateNames(catalogId: String): TemplateNames =
        try {
            TemplateNames(namesById = listTemplateNames(catalogId), isRemembered = false)
        } catch (epistolaRequestFailedException: EpistolaRequestFailedException) {
            lastReadTemplateNames[catalogId]
                ?.takeIf { epistolaRequestFailedException.errorCode == ERROR_CODE_EPISTOLA_UNAVAILABLE }
                ?.also {
                    LOG.warning {
                        "Epistola cannot be reached; listing the templates of catalog '$catalogId' by the names read at ${it.readAt}"
                    }
                }
                ?.let { TemplateNames(namesById = it.namesById, isRemembered = true) }
                ?: throw epistolaRequestFailedException
        }

    /** Stops at the first template Epistola cannot be reached for, so that the others do not each wait for a timeout. */
    private fun readGenerationTemplates(catalogId: String, templateIds: Collection<String>) =
        mutableMapOf<String, EpistolaGenerationTemplate>().also { generationTemplates ->
            try {
                templateIds.forEach {
                    generationTemplates[it] = epistolaClientService.readGenerationTemplate(
                        catalogId = catalogId,
                        templateId = it
                    )
                }
            } catch (epistolaRequestFailedException: EpistolaRequestFailedException) {
                if (epistolaRequestFailedException.errorCode != ERROR_CODE_EPISTOLA_UNAVAILABLE) {
                    throw epistolaRequestFailedException
                }
                LOG.warning {
                    "Epistola cannot be reached; listing the templates of catalog '$catalogId' without their details"
                }
            }
        }
}

private data class ReadTemplateNames(val namesById: Map<String, String>, val readAt: Instant)

private data class TemplateNames(val namesById: Map<String, String>, val isRemembered: Boolean)
