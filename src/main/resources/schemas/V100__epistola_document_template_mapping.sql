/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

ALTER TABLE ${schema}.zaaktype_configuration
    ADD COLUMN epistola_ingeschakeld BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_ingeschakeld IS 'Maak het aanmaken van documenten via Epistola mogelijk voor dit zaaktype';

-- Epistola has no template groups, so a group is created and named by the beheerder in ZAC and carries no
-- Epistola id. The groups are one level deep, so there is no parent_id.
CREATE TABLE ${schema}.zaaktype_epistola_document_template_group_parameters
(
    id                        BIGINT                   NOT NULL,
    naam                      VARCHAR                  NOT NULL,
    aanmaakdatum              TIMESTAMP WITH TIME ZONE NOT NULL,
    zaaktype_configuration_id BIGINT                   NOT NULL,
    CONSTRAINT pk_zaaktype_epistola_document_template_group_parameters
        PRIMARY KEY (id),
    CONSTRAINT fk_zaaktype_configuration
        FOREIGN KEY (zaaktype_configuration_id)
            REFERENCES ${schema}.zaaktype_configuration (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_zaaktype_epistola_document_template_group_naam
        UNIQUE (zaaktype_configuration_id, naam)
);
CREATE SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_group_parameters START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

COMMENT ON COLUMN ${schema}.zaaktype_epistola_document_template_group_parameters.naam IS 'Naam van de sjabloongroep, door de beheerder in ZAC gekozen';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_document_template_group_parameters.aanmaakdatum IS 'Datum waarop de sjabloongroep in deze tabel is opgeslagen';

-- A template appears at most once per zaaktype, so the informatieobjecttype a document is filed under never
-- depends on the group the behandelaar happened to open.
CREATE TABLE ${schema}.zaaktype_epistola_document_template_parameters
(
    id                          BIGINT                   NOT NULL,
    epistola_id                 VARCHAR                  NOT NULL,
    aanmaakdatum                TIMESTAMP WITH TIME ZONE NOT NULL,
    sjabloon_groep_id           BIGINT                   NOT NULL,
    zaaktype_configuration_id   BIGINT                   NOT NULL,
    informatie_object_type_uuid UUID                     NOT NULL,
    CONSTRAINT pk_zaaktype_epistola_document_template_parameters
        PRIMARY KEY (id),
    CONSTRAINT fk_zaaktype_epistola_document_template_group
        FOREIGN KEY (sjabloon_groep_id)
            REFERENCES ${schema}.zaaktype_epistola_document_template_group_parameters (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_zaaktype_configuration
        FOREIGN KEY (zaaktype_configuration_id)
            REFERENCES ${schema}.zaaktype_configuration (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_zaaktype_epistola_document_template_epistola_id
        UNIQUE (zaaktype_configuration_id, epistola_id)
);
CREATE SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_parameters START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE INDEX idx_zaaktype_epistola_document_template_sjabloon_groep_id
    ON ${schema}.zaaktype_epistola_document_template_parameters (sjabloon_groep_id);

COMMENT ON COLUMN ${schema}.zaaktype_epistola_document_template_parameters.epistola_id IS 'ID van het sjabloon in Epistola';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_document_template_parameters.sjabloon_groep_id IS 'ID van de sjabloongroep waar dit sjabloon deel van uitmaakt';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_document_template_parameters.informatie_object_type_uuid IS 'Informatieobjecttype waaronder het gegenereerde document in Open Zaak wordt opgeslagen';
