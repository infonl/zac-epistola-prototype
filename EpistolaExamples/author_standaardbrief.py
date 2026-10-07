# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors `zac-standaardbrief`, a plain letter about the zaak with one variant, in the configured Epistola catalog.

It uses about every field ZAC sends: the zaak, its initiator, the signed-in user, dates and a list from the
startformulier. Run it from the ZAC checkout as
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_standaardbrief.py
and again after each daily reset of the test tenant. Pass --delete to remove the template.
"""
import epistola_api

TEMPLATE = "zac-standaardbrief"
epistola_api.delete_template_when_asked(TEMPLATE)

string = {"type": "string"}
# Written as Epistola's own data-contract editor writes one: draft-07, and dates as format: date, which
# Epistola then enforces, so this template rejects a date that is not ISO 8601.
date = {"type": "string", "format": "date"}
DATES = ["startdatum", "einddatumGepland", "uiterlijkeEinddatumAfdoening"]
data_model = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "zaak": {"type": "object", "properties": {**{name: string for name in [
            "identificatie", "omschrijving", "toelichting", "zaaktype", "status", "behandelaar", "groep",
            "communicatiekanaal", "vertrouwelijkheidaanduiding"]}, **{name: date for name in DATES}}},
        "aanvrager": {"type": "object", "properties": {name: string for name in [
            "naam", "straat", "huisnummer", "postcode", "woonplaats"]}},
        "gebruiker": {"type": "object", "properties": {"naam": string}},
        "startformulier": {"type": "object", "properties": {"data": {"type": "object", "properties": {
            "kinderen": {"type": "array", "items": {"$ref": "#/$defs/kind"}}}}}},
    },
    "$defs": {"kind": {"type": "object", "properties": {"naam": string}}},
}
example = {
    "zaak": {"identificatie": "ZAAK-2026-0000001", "omschrijving": "Voorbeeldaanvraag", "toelichting": "Voorbeeld",
             "zaaktype": "Parkeervergunning", "status": "In behandeling", "behandelaar": "Jan Jansen",
             "groep": "Team Vergunningen", "communicatiekanaal": "E-mail", "vertrouwelijkheidaanduiding": "openbaar",
             "startdatum": "2026-09-01", "einddatumGepland": "2026-09-29", "uiterlijkeEinddatumAfdoening": "2026-10-13"},
    "aanvrager": {"naam": "J. Voorbeeld", "straat": "Voorbeeldstraat", "huisnummer": "1", "postcode": "1234 AB",
                  "woonplaats": "Deventer"},
    "gebruiker": {"naam": "Jan Jansen"},
    "startformulier": {"data": {"kinderen": [{"naam": "Kind Een"}, {"naam": "Kind Twee"}]}},
}

template_model = epistola_api.base_template_model()
for node in template_model["nodes"].values():
    for paragraph in ((node.get("props") or {}).get("content") or {}).get("content", []):
        for part in paragraph.get("content", []):
            expression = part.get("attrs", {}).get("expression", "")
            if expression.removeprefix("zaak.") in DATES:
                part["attrs"]["expression"] = f"$formatDate({expression}, 'dd-MM-yyyy')"
template_model["nodes"]["n-kinderen"] = {
    "id": "n-kinderen", "type": "text", "slots": [], "styles": {"marginBottom": "15px"}, "stylePreset": None,
    "props": {"content": {"type": "doc", "content": [{"type": "paragraph", "content": [
        {"type": "text", "text": "Kinderen op de aanvraag: "},
        {"type": "expression", "attrs": {"isNew": False,
                                         "expression": "$join(startformulier.data.kinderen.naam, \", \")"}},
    ]}]}},
}
children = template_model["slots"]["s-root-children"]["children"]
children.insert(children.index("n-body") + 1, "n-kinderen")

epistola_api.publish_template(TEMPLATE, "ZAC Standaardbrief", data_model, example, template_model)
