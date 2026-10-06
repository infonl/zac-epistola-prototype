/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

-- A zaaktype offers every template of one Epistola catalog, so the template groups and the per-template mapping of V100
-- give way to columns on the zaaktype. A Flyway migration cannot read EPISTOLA_CATALOG_ID, so the catalog stays
-- empty here, and ZAC then uses the catalog that variable names, which is the one every template came from until now.
ALTER TABLE ${schema}.zaaktype_configuration
    ADD COLUMN epistola_catalog_id                  VARCHAR,
    ADD COLUMN epistola_informatie_object_type_uuid UUID,
    ADD COLUMN epistola_locale                      VARCHAR;

COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_catalog_id IS 'Catalog in Epistola waarvan dit zaaktype alle templates aanbiedt; leeg tot de beheerder er een kiest, en dan geldt de catalog van EPISTOLA_CATALOG_ID';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_locale IS 'Taal (BCP-47-tag van system.locale in Epistola, zoals nl-NL) waarin ZAC Epistola om het document vraagt voor alle templates van dit zaaktype; leeg tot de beheerder er een kiest, en dan geldt Nederlands als het template dat heeft';
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

-- Each template had its own informatieobjecttype. The zaaktype keeps the one most of its templates had, so the fewest
-- documents change type, and of two that are used equally often the one of the template stored first.
UPDATE ${schema}.zaaktype_configuration
SET epistola_informatie_object_type_uuid = most_used.informatie_object_type_uuid
FROM (
    SELECT DISTINCT ON (zaaktype_configuration_id)
        zaaktype_configuration_id,
        informatie_object_type_uuid
    FROM ${schema}.zaaktype_epistola_document_template_parameters
    GROUP BY zaaktype_configuration_id, informatie_object_type_uuid
    ORDER BY zaaktype_configuration_id, COUNT(*) DESC, MIN(id)
) AS most_used
WHERE zaaktype_configuration.id = most_used.zaaktype_configuration_id;

-- A template whose informatieobjecttype differs from the one the zaaktype keeps keeps its own, so that no document
-- changes type by this migration.
INSERT INTO ${schema}.zaaktype_epistola_template_settings
    (id, zaaktype_configuration_id, epistola_id, informatie_object_type_uuid, is_enabled, aanmaakdatum)
SELECT nextval('${schema}.sq_zaaktype_epistola_template_settings'),
       template.zaaktype_configuration_id,
       template.epistola_id,
       template.informatie_object_type_uuid,
       TRUE,
       template.aanmaakdatum
FROM ${schema}.zaaktype_epistola_document_template_parameters AS template
         JOIN ${schema}.zaaktype_configuration AS configuration
              ON configuration.id = template.zaaktype_configuration_id
WHERE template.informatie_object_type_uuid IS DISTINCT FROM configuration.epistola_informatie_object_type_uuid
ORDER BY template.id;

DROP TABLE ${schema}.zaaktype_epistola_document_template_parameters;
DROP TABLE ${schema}.zaaktype_epistola_document_template_group_parameters;
DROP SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_parameters;
DROP SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_group_parameters;

-- A new version of a document is generated from the catalog of the first, also after its zaaktype has moved to another.
-- A document generated before this column came from the catalog of EPISTOLA_CATALOG_ID, and keeps null.
ALTER TABLE ${schema}.epistola_document
    ADD COLUMN catalog_id VARCHAR;

COMMENT ON COLUMN ${schema}.epistola_document.catalog_id IS 'Catalog in Epistola waaruit het template komt waarmee het document is gegenereerd; leeg voor een document van voor deze kolom, dat uit de catalog van EPISTOLA_CATALOG_ID komt';
