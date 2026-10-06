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
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_catalog_id IS 'Catalog in Epistola waarvan dit zaaktype alle templates aanbiedt; leeg tot de beheerder er een kiest, en dan geldt de catalog van EPISTOLA_CATALOG_ID';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_locale IS 'Taal (BCP-47-tag van system.locale in Epistola, zoals nl-NL) waarin ZAC Epistola om het document vraagt voor alle templates van dit zaaktype; leeg tot de beheerder er een kiest, en dan geldt Nederlands als het template dat heeft';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_informatie_object_type_uuid IS 'Informatieobjecttype waaronder een met Epistola gegenereerd document in Open Zaak wordt opgeslagen';

-- The PDF of an Epistola document lives in Open Zaak, and ZAC keeps no copy of it. This only remembers which Epistola
-- template produced the document there, in which catalog, language and variant, so that a behandelaar can generate a
-- new version of it.
CREATE TABLE ${schema}.epistola_document
(
    informatieobject_uuid UUID                     NOT NULL,
    template_id           VARCHAR                  NOT NULL,
    aanmaakdatum          TIMESTAMP WITH TIME ZONE NOT NULL,
    kanaal                VARCHAR,
    locale                VARCHAR,
    catalog_id            VARCHAR,
    CONSTRAINT pk_epistola_document
        PRIMARY KEY (informatieobject_uuid)
);

COMMENT ON COLUMN ${schema}.epistola_document.informatieobject_uuid IS 'UUID van het informatieobject in Open Zaak dat met Epistola is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.template_id IS 'ID van het Epistola-template waarmee het document is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.aanmaakdatum IS 'Moment waarop het document is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.kanaal IS 'Kanaal van de Epistola-variant waar ZAC om vroeg, zoals post of digitaal; leeg als ZAC om geen kanaal vroeg, bijvoorbeeld bij een template zonder varianten per kanaal of na een standaardrender';
COMMENT ON COLUMN ${schema}.epistola_document.locale IS 'Taal waarin het document is gegenereerd, als BCP-47-tag van het Epistola-attribuut system.locale, zoals nl-NL; leeg als de varianten van het template geen taal hebben';
COMMENT ON COLUMN ${schema}.epistola_document.catalog_id IS 'Catalog in Epistola waaruit het template komt waarmee het document is gegenereerd; leeg betekent de catalog van EPISTOLA_CATALOG_ID';
