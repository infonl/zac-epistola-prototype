/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.admin.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import nl.info.zac.database.flyway.FlywayIntegrator.Companion.SCHEMA
import nl.info.zac.util.AllOpen
import java.time.ZonedDateTime
import java.util.UUID

/**
 * What a zaaktype sets for one template of its Epistola catalog, when that differs from the zaaktype: an
 * informatieobjecttype of its own, or that Document maken does not offer it.
 */
@Entity
@Table(schema = SCHEMA, name = "zaaktype_epistola_template_settings")
@SequenceGenerator(
    schema = SCHEMA,
    name = "sq_zaaktype_epistola_template_settings",
    sequenceName = "sq_zaaktype_epistola_template_settings",
    allocationSize = 1
)
@AllOpen
class ZaaktypeEpistolaTemplateSettings {
    @Id
    @GeneratedValue(generator = "sq_zaaktype_epistola_template_settings", strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zaaktype_configuration_id", nullable = false)
    lateinit var zaaktypeConfiguration: ZaaktypeConfiguration

    @Column(name = "epistola_id", nullable = false)
    lateinit var epistolaId: String

    @Column(name = "informatie_object_type_uuid")
    var informatieObjectTypeUUID: UUID? = null

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true

    @Column(name = "aanmaakdatum", nullable = false)
    lateinit var creationDate: ZonedDateTime

    override fun equals(other: Any?) = other is ZaaktypeEpistolaTemplateSettings && epistolaId == other.epistolaId

    override fun hashCode() = epistolaId.hashCode()
}
