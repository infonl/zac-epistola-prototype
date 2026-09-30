/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

-- The PDF of an Epistola document lives in Open Zaak, and ZAC keeps no copy of it. This only remembers which
-- Epistola template produced the document there, so that a behandelaar can generate a new version of it.
CREATE TABLE ${schema}.epistola_document
(
    informatieobject_uuid UUID                     NOT NULL,
    template_id           VARCHAR                  NOT NULL,
    aanmaakdatum          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_epistola_document
        PRIMARY KEY (informatieobject_uuid)
);

COMMENT ON COLUMN ${schema}.epistola_document.informatieobject_uuid IS 'UUID van het informatieobject in Open Zaak dat met Epistola is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.template_id IS 'ID van het Epistola-template waarmee het document is gegenereerd';
COMMENT ON COLUMN ${schema}.epistola_document.aanmaakdatum IS 'Moment waarop het document is gegenereerd';
