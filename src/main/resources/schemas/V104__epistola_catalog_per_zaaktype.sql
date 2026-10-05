/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

-- A zaaktype offers every template of one Epistola catalog, so the template groups and the per-template mapping of V100
-- give way to two columns on the zaaktype. A Flyway migration cannot read EPISTOLA_CATALOG_ID, so the catalog stays
-- empty here, and ZAC then uses the catalog that variable names, which is the one every template came from until now.
ALTER TABLE ${schema}.zaaktype_configuration
    ADD COLUMN epistola_catalog_id                  VARCHAR,
    ADD COLUMN epistola_informatie_object_type_uuid UUID;

COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_catalog_id IS 'Catalog in Epistola waarvan dit zaaktype alle templates aanbiedt; leeg tot de beheerder er een kiest, en dan geldt de catalog van EPISTOLA_CATALOG_ID';
COMMENT ON COLUMN ${schema}.zaaktype_configuration.epistola_informatie_object_type_uuid IS 'Informatieobjecttype waaronder een met Epistola gegenereerd document in Open Zaak wordt opgeslagen';

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

DROP TABLE ${schema}.zaaktype_epistola_document_template_parameters;
DROP TABLE ${schema}.zaaktype_epistola_document_template_group_parameters;
DROP SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_parameters;
DROP SEQUENCE ${schema}.sq_zaaktype_epistola_document_template_group_parameters;

-- A new version of a document is generated from the catalog of the first, also after its zaaktype has moved to another.
-- A document generated before this column came from the catalog of EPISTOLA_CATALOG_ID, and keeps null.
ALTER TABLE ${schema}.epistola_document
    ADD COLUMN catalog_id VARCHAR;

COMMENT ON COLUMN ${schema}.epistola_document.catalog_id IS 'Catalog in Epistola waaruit het template komt waarmee het document is gegenereerd; leeg voor een document van voor deze kolom, dat uit de catalog van EPISTOLA_CATALOG_ID komt';
