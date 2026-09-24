/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.templates.model

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import nl.info.zac.admin.model.ZaaktypeConfiguration
import nl.info.zac.database.flyway.FlywayIntegrator
import nl.info.zac.util.AllOpen
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(schema = FlywayIntegrator.SCHEMA, name = "zaaktype_epistola_document_template_group_parameters")
@SequenceGenerator(
    schema = FlywayIntegrator.SCHEMA,
    name = "sq_zaaktype_epistola_document_template_group_parameters",
    sequenceName = "sq_zaaktype_epistola_document_template_group_parameters",
    allocationSize = 1
)
@AllOpen
class EpistolaTemplateGroup {
    @Id
    @GeneratedValue(
        generator = "sq_zaaktype_epistola_document_template_group_parameters",
        strategy = GenerationType.SEQUENCE
    )
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "naam", nullable = false)
    lateinit var name: String

    @Column(name = "aanmaakdatum", nullable = false)
    lateinit var creationDate: ZonedDateTime

    @OneToMany(mappedBy = "templateGroup", fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    var templates: MutableSet<EpistolaTemplate> = mutableSetOf()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zaaktype_configuration_id", nullable = false)
    lateinit var zaaktypeConfiguration: ZaaktypeConfiguration
}

fun EpistolaTemplateGroup.addTemplate(epistolaId: String, informatieObjectTypeUUID: UUID) {
    templates.add(
        EpistolaTemplate().apply {
            this.epistolaId = epistolaId
            this.informatieObjectTypeUUID = informatieObjectTypeUUID
            templateGroup = this@addTemplate
            zaaktypeConfiguration = this@addTemplate.zaaktypeConfiguration
            creationDate = ZonedDateTime.now()
        }
    )
}

fun EpistolaTemplateGroup.copyTo(zaaktypeConfiguration: ZaaktypeConfiguration) =
    EpistolaTemplateGroup().apply {
        name = this@copyTo.name
        creationDate = ZonedDateTime.now()
        this.zaaktypeConfiguration = zaaktypeConfiguration
        this@copyTo.templates.forEach {
            addTemplate(epistolaId = it.epistolaId, informatieObjectTypeUUID = it.informatieObjectTypeUUID)
        }
    }
