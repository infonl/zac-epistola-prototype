/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.epistola.documents.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import nl.info.zac.database.flyway.FlywayIntegrator
import nl.info.zac.util.AllOpen
import java.time.ZonedDateTime
import java.util.UUID

/**
 * The Epistola template that produced a document in Open Zaak, and the catalog it came from. The document itself is
 * only stored there.
 */
@Entity
@Table(schema = FlywayIntegrator.SCHEMA, name = "epistola_document")
@AllOpen
class EpistolaDocument {
    @Id
    @Column(name = "informatieobject_uuid")
    lateinit var informatieObjectUUID: UUID

    /** Null for a document generated before ZAC remembered the catalog, which came from that of `EPISTOLA_CATALOG_ID`. */
    @Column(name = "catalog_id")
    var catalogId: String? = null

    @Column(name = "template_id", nullable = false)
    lateinit var templateId: String

    @Column(name = "aanmaakdatum", nullable = false)
    lateinit var creationDate: ZonedDateTime
}
