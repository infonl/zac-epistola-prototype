/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

ALTER TABLE ${schema}.zaaktype_configuration
    ADD COLUMN epistola_ingeschakeld                BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN epistola_catalog_id                  VARCHAR,
    ADD COLUMN epistola_informatie_object_type_uuid UUID,
    ADD COLUMN epistola_locale                      VARCHAR;

COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_ingeschakeld IS 'Maak het aanmaken van documenten via Epistola mogelijk voor dit zaaktype';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_catalog_id IS 'Catalog in Epistola waarvan dit zaaktype alle templates aanbiedt; zolang dit leeg is, geldt de catalog van EPISTOLA_CATALOG_ID';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_locale IS 'Taal (BCP-47-tag van system.locale in Epistola, zoals nl-NL) waarin ZAC Epistola om het document vraagt voor alle templates van dit zaaktype; zolang dit leeg is, vraagt ZAC om Nederlands als het template dat heeft';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_informatie_object_type_uuid IS 'Informatieobjecttype waaronder een met Epistola gegenereerd document in Open Zaak wordt opgeslagen';

-- A template can have an informatieobjecttype of its own, and be switched off so that Document maken does not offer it.
-- A template is named by its id within the zaaktype's catalog, and has a row only when it differs from the zaaktype: it
-- has its own informatieobjecttype, or is not offered.
CREATE TABLE ${schema}.zaaktype_epistola_template_settings
(
    id                          BIGINT                   NOT NULL,
    zaaktype_configuration_id   BIGINT                   NOT NULL,
    epistola_id                 VARCHAR                  NOT NULL,
    informatie_object_type_uuid UUID,
    is_enabled                  BOOLEAN                  NOT NULL DEFAULT TRUE,
    aanmaakdatum                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_zaaktype_epistola_template_settings
        PRIMARY KEY (id),
    CONSTRAINT fk_zaaktype_epistola_template_settings_zaaktype_configuration
        FOREIGN KEY (zaaktype_configuration_id)
            REFERENCES ${schema}.zaaktype_configuration (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_zaaktype_epistola_template_settings_epistola_id
        UNIQUE (zaaktype_configuration_id, epistola_id)
);
CREATE SEQUENCE ${schema}.sq_zaaktype_epistola_template_settings START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

COMMENT ON COLUMN ${schema}.zaaktype_epistola_template_settings.zaaktype_configuration_id IS 'Zaaktype waarvoor dit template een eigen instelling heeft';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_template_settings.epistola_id IS 'ID van het template in de catalog van het zaaktype in Epistola';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_template_settings.informatie_object_type_uuid IS 'Informatieobjecttype waaronder een met dit template gegenereerd document in Open Zaak wordt opgeslagen, in plaats van dat van het zaaktype; leeg als het template dat van het zaaktype gebruikt';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_template_settings.is_enabled IS 'Of Document maken dit template aanbiedt; een template dat de beheerder uitzet, blijft staan in Epistola maar verdwijnt uit de keuzelijst';
COMMENT ON COLUMN ${schema}.zaaktype_epistola_template_settings.aanmaakdatum IS 'Datum waarop de instelling van het template in deze tabel is opgeslagen';

-- The PDF of an Epistola document lives in Open Zaak, and ZAC keeps no copy of it. This only remembers which Epistola
-- template produced the document there, in which catalog and language, so that a behandelaar can generate a
-- new version of it.
CREATE TABLE ${schema}.epistola_document
(
    informatieobject_uuid UUID                     NOT NULL,
    template_id           VARCHAR                  NOT NULL,
    aanmaakdatum          TIMESTAMP WITH TIME ZONE NOT NULL,
    locale                VARCHAR,
    catalog_id            VARCHAR,
    CONSTRAINT pk_epistola_document
        PRIMARY KEY (informatieobject_uuid)
);

COMMENT ON COLUMN ${schema}.epistola_document.informatieobject_uuid IS 'UUID van het informatieobject in Open Zaak dat met Epistola is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.template_id IS 'ID van het Epistola-template waarmee het document is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.aanmaakdatum IS 'Moment waarop het document is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.locale IS 'Taal waarin het document is gegenereerd, als BCP-47-tag van het Epistola-attribuut system.locale, zoals nl-NL; leeg als de varianten van het template geen taal hebben';
COMMENT ON COLUMN ${schema}.epistola_document.catalog_id IS 'Catalog in Epistola waaruit het template komt waarmee het document is gegenereerd; leeg betekent de catalog van EPISTOLA_CATALOG_ID';
