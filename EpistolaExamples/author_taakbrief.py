# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors `zac-taakbrief`, a letter sent from a task, naming the task and the employee who handles it.

Its contract requires `taak.naam` and `taak.behandelaar`. ZAC sends `taak` only when a document is made from a task, and
`taak.behandelaar` only once the task is assigned to someone. So Epistola refuses this template from the zaak and from an
unassigned task, and renders it from an assigned one. Run it from the ZAC checkout as
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_taakbrief.py
and again after each daily reset of the test tenant. Pass --delete to remove the template.
"""
import epistola_api

TEMPLATE = "zac-taakbrief"
epistola_api.delete_template_when_asked(TEMPLATE)

string = {"type": "string"}
date = {"type": "string", "format": "date"}
data_model = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["zaak", "taak"],
    "properties": {
        "zaak": {
            "type": "object",
            "required": ["identificatie"],
            "properties": {"identificatie": string, "omschrijving": string, "groep": string, "startdatum": date},
        },
        "taak": {
            "type": "object",
            "required": ["naam", "behandelaar"],
            "properties": {"naam": string, "behandelaar": string},
        },
        "aanvrager": {"type": "object", "properties": {name: string for name in [
            "naam", "straat", "huisnummer", "postcode", "woonplaats"]}},
        "gebruiker": {"type": "object", "properties": {"naam": string}},
    },
}
example = {
    "zaak": {"identificatie": "ZAAK-2026-0000001", "omschrijving": "Aanvraag evenementenvergunning",
             "groep": "Team Vergunningen", "startdatum": "2026-10-01"},
    "taak": {"naam": "Aanvullende informatie", "behandelaar": "Sanne de Vries"},
    "aanvrager": {"naam": "J. Voorbeeld", "straat": "Voorbeeldstraat", "huisnummer": "1", "postcode": "1234 AB",
                  "woonplaats": "Deventer"},
    "gebruiker": {"naam": "Jan Jansen"},
}


def text(value):
    return {"type": "text", "text": value}


def expression(value):
    return {"type": "expression", "attrs": {"isNew": False, "expression": value}}


def paragraphs(*lines):
    return {"content": {"type": "doc", "content": [{"type": "paragraph", "content": line} for line in lines]}}


template_model = epistola_api.base_template_model()
nodes = template_model["nodes"]
nodes["n-kenmerk"]["props"] = paragraphs([
    text("Kenmerk: "), expression("zaak.identificatie"),
    text("        Datum aanvraag: "), expression("$formatDate(zaak.startdatum, 'dd-MM-yyyy')"),
])
nodes["n-onderwerp"]["props"] = paragraphs([
    text("Betreft: "), expression("taak.naam"), text(", "), expression("zaak.omschrijving"),
])
nodes["n-aanhef"]["props"] = paragraphs([
    text("Geachte "), expression("aanvrager.naam ? aanvrager.naam : 'heer, mevrouw'"), text(","),
])
nodes["n-body"]["props"] = paragraphs(
    [text("Uw aanvraag is in behandeling. Op dit moment werken wij aan de stap "), expression("taak.naam"),
     text(".")],
    [text("Deze stap wordt behandeld door "), expression("taak.behandelaar"),
     text(". Hebt u vragen over deze stap, neem dan contact met ons op en noem het kenmerk van uw zaak.")],
)

epistola_api.publish_template(TEMPLATE, "ZAC Taakbrief", data_model, example, template_model)
