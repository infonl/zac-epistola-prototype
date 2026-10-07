/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

-- A new version of a document is generated in the variant of the first, also when a behandelaar chose that kanaal
-- against the zaak's communicatiekanaal. Documents generated before this column, or without a kanaal, keep null.
ALTER TABLE ${schema}.epistola_document
    ADD COLUMN kanaal VARCHAR;

COMMENT ON COLUMN ${schema}.epistola_document.kanaal IS 'Kanaal van de Epistola-variant waarin het document is gegenereerd, zoals post of digitaal; leeg als het template geen varianten per kanaal heeft';
