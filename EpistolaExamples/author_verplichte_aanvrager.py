# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors `zac-verplichte-aanvrager`, the standaardbrief's layout with a contract that requires the initiator's name.

A zaak without an initiator breaks that contract, so this template shows how ZAC reports data that Epistola
refuses. Run it from the ZAC checkout as
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_verplichte_aanvrager.py
Pass --delete to remove it again, which shows how ZAC handles a template that disappears from the catalog.
"""
import epistola_api

TEMPLATE = "zac-verplichte-aanvrager"
epistola_api.delete_template_when_asked(TEMPLATE)

string = {"type": "string"}
data_model = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["aanvrager"],
    "properties": {
        "zaak": {"type": "object", "properties": {"identificatie": string, "omschrijving": string}},
        "aanvrager": {"type": "object", "required": ["naam"], "properties": {"naam": string}},
    },
}
example = {"zaak": {"identificatie": "ZAAK-2026-0000000001", "omschrijving": "Voorbeeld"},
           "aanvrager": {"naam": "J. Voorbeeld"}}

epistola_api.publish_template(
    TEMPLATE, "ZAC Verplichte aanvrager", data_model, example, epistola_api.base_template_model())
