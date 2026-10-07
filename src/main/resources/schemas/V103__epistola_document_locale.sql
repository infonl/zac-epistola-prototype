/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

-- A new version of a document is generated in the language of the first while the template still offers it. The column
-- is named after Epistola's attribute system.locale, whose BCP-47 tag it holds, as kanaal is named after the attribute
-- kanaal. Documents generated before this column, or from a template whose variants carry no language, keep null.
ALTER TABLE ${schema}.epistola_document
    ADD COLUMN locale VARCHAR;

COMMENT ON COLUMN ${schema}.epistola_document.locale IS 'Taal waarin het document is gegenereerd, als BCP-47-tag van het Epistola-attribuut system.locale, zoals nl-NL; leeg als de varianten van het template geen taal hebben';
