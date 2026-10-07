# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors and publishes `zac-ontvangstbevestiging`, a second ZAC test template for another use case than the besluitbrief.

The besluitbrief answers a decision; this one confirms that an application was received. Its variants are shaped
differently on purpose, so that the language and variant pickers have something different to show:

- `initial`  Nederlands, digitaal (the default, for the Mijn Voorbeeldstad inbox)
- `post`     Nederlands, per post (with address block)
- `english`  English, digital only
- `deutsch`  Deutsch, digital only

So the languages are nl-NL, en-GB and de-DE, Dutch has both kanalen, and English and German have only `digitaal`.
Together with the besluitbrief (nl-NL, en-GB) the shared languages of a catalog are nl-NL and en-GB.

Run from the ZAC repository root, after the besluitbrief (it reuses its logo, theme and `kanaal` attribute):
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_ontvangstbevestiging.py
`--no-render` skips rendering each variant once. Prints statuses, never secrets. Safe to run again.
"""
import os
import sys

import author_besluitbrief as base
from author_besluitbrief import (BR, CATALOG, CATALOG_PATH, DATA_MODEL, E, H, LOGO_NAME, LOGO_SVG, Model, P, T, THEME,
                                 ATTRIBUTE, UL, call, date, ensure_image, or_dash, report, require)

TEMPLATE = "zac-ontvangstbevestiging"
TEMPLATE_PATH = f"{CATALOG_PATH}/templates/{TEMPLATE}"

RECEIVED = {
    "zaak": {"identificatie": "ZAAK-2026-0000000101", "zaaktype": "Melding openbare ruimte",
             "omschrijving": "Kapotte lantaarnpaal bij de brug", "status": "Intake", "groep": "Team Buitenruimte",
             "registratiedatum": "2026-10-05", "uiterlijkeEinddatumAfdoening": "2026-11-16"},
    "aanvrager": {"naam": "J. Voorbeeld", "straat": "Voorbeeldstraat", "huisnummer": "12A", "postcode": "9999 ZZ",
                  "woonplaats": "Voorbeeldstad"},
    "gebruiker": {"naam": "Sanne de Vries"},
}

TEXTS = {
    "nl": {
        "gemeente": "Gemeente Voorbeeldstad", "adresregel": "Postbus 1234 · 9999 ZZ Voorbeeldstad · 14 0999",
        "pagina": ("Kenmerk ", " · pagina ", " van "), "aan_onbekend": "Aan de aanvrager van zaak ",
        "datum": "Datum", "kenmerk": "Ons kenmerk", "digitaal": "Dit bericht staat ook in uw inbox op Mijn Voorbeeldstad.",
        "titel": "Wij hebben uw aanvraag ontvangen",
        "aanhef": ("Geachte heer/mevrouw ", "Geachte heer/mevrouw,"),
        "inleiding": ("Op ", " hebben wij uw aanvraag ontvangen: ", ". Uw kenmerk is "),
        "kop": "Wat gebeurt er nu?",
        "stappen": ("Een medewerker van ", " beoordeelt uw aanvraag.", "U hoort uiterlijk ", " van ons.",
                    "Heeft u vragen? Bel 14 0999 en noem uw kenmerk."),
        "groet": "Met vriendelijke groet,",
    },
    "en": {
        "gemeente": "Municipality of Voorbeeldstad", "adresregel": "PO Box 1234 · 9999 ZZ Voorbeeldstad · 14 0999",
        "pagina": ("Reference ", " · page ", " of "), "aan_onbekend": "To the applicant of case ",
        "datum": "Date", "kenmerk": "Our reference", "digitaal": "This message is also in your inbox on My Voorbeeldstad.",
        "titel": "We have received your application",
        "aanhef": ("Dear ", "Dear Sir or Madam,"),
        "inleiding": ("On ", " we received your application: ", ". Your reference is "),
        "kop": "What happens next?",
        "stappen": ("An employee of ", " will assess your application.", "You will hear from us by ", ".",
                    "Questions? Call 14 0999 and quote your reference."),
        "groet": "Kind regards,",
    },
    "de": {
        "gemeente": "Gemeinde Voorbeeldstad", "adresregel": "Postfach 1234 · 9999 ZZ Voorbeeldstad · 14 0999",
        "pagina": ("Aktenzeichen ", " · Seite ", " von "), "aan_onbekend": "An den Antragsteller des Falls ",
        "datum": "Datum", "kenmerk": "Unser Aktenzeichen", "digitaal": "Diese Nachricht finden Sie auch in Ihrem Postfach auf Mein Voorbeeldstad.",
        "titel": "Wir haben Ihren Antrag erhalten",
        "aanhef": ("Sehr geehrte/r ", "Sehr geehrte Damen und Herren,"),
        "inleiding": ("Am ", " haben wir Ihren Antrag erhalten: ", ". Ihr Aktenzeichen lautet "),
        "kop": "Wie geht es weiter?",
        "stappen": ("Ein Mitarbeiter von ", " prüft Ihren Antrag.", "Sie hören spätestens am ", " von uns.",
                    "Haben Sie Fragen? Rufen Sie 14 0999 an und nennen Sie Ihr Aktenzeichen."),
        "groet": "Mit freundlichen Grüßen",
    },
}

VARIANTS = [
    ("initial", "Nederlands, digitaal", "De standaardvariant, voor de inbox van Mijn Voorbeeldstad",
     {"system.locale": "nl-NL", f"{CATALOG}.{ATTRIBUTE}": "digitaal"}, "nl", "digitaal"),
    ("post", "Nederlands, per post", "Met adresblok",
     {"system.locale": "nl-NL", f"{CATALOG}.{ATTRIBUTE}": "post"}, "nl", "post"),
    ("english", "English, digital", "For applicants who asked for English; digital only",
     {"system.locale": "en-GB", f"{CATALOG}.{ATTRIBUTE}": "digitaal"}, "en", "digitaal"),
    ("deutsch", "Deutsch, digital", "Für Antragsteller, die Deutsch gewählt haben; nur digital",
     {"system.locale": "de-DE", f"{CATALOG}.{ATTRIBUTE}": "digitaal"}, "de", "digitaal"),
]


def ontvangstbevestiging(language, kanaal, logo):
    t = TEXTS[language]
    m = Model(f"{language}-{kanaal}")

    cover = m.add("pageheader", {"height": "64pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    logo_column, name_column = m.columns(cover, [1, 7], gap=10)
    m.add("image", {"assetId": logo, "catalogKey": CATALOG, "alt": t["gemeente"], "width": "38pt", "aspectRatioLocked": True},
          parent=logo_column)
    m.text(name_column, P(T(t["gemeente"], "bold")), styles={"fontSize": "15pt", "color": "#00566b", "marginBottom": "0sp"})
    m.text(name_column, P(t["adresregel"]), preset="klein")

    footer = m.add("pagefooter", {"height": "18pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    m.text(footer, P(t["pagina"][0], E("zaak.identificatie"), t["pagina"][1], E("sys.pages.current"), t["pagina"][2],
                     E("sys.pages.total")), preset="klein", styles={"textAlign": "center"})

    if kanaal == "post":
        _, address = m.add("addressblock", {"standard": "din-c56-left", "align": "left", "top": 45, "sideDistance": 20,
                                            "addressWidth": 85, "height": 45}, slot_names=["address", "aside"], parent=m.body)
        known = m.conditional(address["address"], "$exists(aanvrager.naam)")
        line = {"marginBottom": "0sp"}
        m.text(known, P(E("aanvrager.naam")), styles=line)
        street = m.conditional(known, "$exists(aanvrager.straat)")
        m.text(street, P(E("$join([aanvrager.straat, aanvrager.huisnummer], ' ')")), styles=line)
        place = m.conditional(known, "$exists(aanvrager.woonplaats)")
        m.text(place, P(E("$join([aanvrager.postcode, $uppercase(aanvrager.woonplaats)], '  ')")), styles=line)
        unknown = m.conditional(address["address"], "$exists(aanvrager.naam)", inverse=True)
        m.text(unknown, P(t["aan_onbekend"], E("zaak.identificatie")))
        for label, value in ((t["datum"], E(date("sys.render.time"))), (t["kenmerk"], E("zaak.identificatie"))):
            m.text(address["aside"], P(T(label, "bold"), BR, value), preset="klein", styles={"marginBottom": "1sp"})
    else:
        m.text(m.body, P(t["digitaal"]), preset="kader")

    m.text(m.body, H(2, t["titel"]), styles={"color": "#00566b", "marginTop": "2sp", "marginBottom": "1sp"})
    m.text(m.body, P(E(f"$exists(aanvrager.naam) ? '{t['aanhef'][0]}' & aanvrager.naam & ',' : '{t['aanhef'][1]}'")),
           styles={"marginBottom": "3sp"})
    m.text(m.body, P(t["inleiding"][0], E(date("zaak.registratiedatum")), t["inleiding"][1], E("zaak.zaaktype"),
                     E("$exists(zaak.omschrijving) ? ' (' & zaak.omschrijving & ')' : ''"), t["inleiding"][2],
                     E("zaak.identificatie"), "."))
    m.text(m.body, H(3, t["kop"]), preset="kop", styles={"keepWithNext": True})
    steps = [(t["stappen"][0], E(or_dash("zaak.groep")), t["stappen"][1])]
    m.text(m.body, UL(steps[0]), styles={"keepTogether": True})
    deadline = m.conditional(m.body, "$exists(zaak.uiterlijkeEinddatumAfdoening)")
    m.text(deadline, P(t["stappen"][2], E(date("zaak.uiterlijkeEinddatumAfdoening")), t["stappen"][3]))
    m.text(m.body, P(t["stappen"][4]))
    m.text(m.body, P(t["groet"]), styles={"marginTop": "3sp", "marginBottom": "0sp"})
    m.text(m.body, P(E("gebruiker.naam"), BR, t["gemeente"]))
    return m.document({"type": "override", "themeId": THEME, "catalogKey": CATALOG})


def main():
    logo = ensure_image(LOGO_NAME, "voorbeeldstad-logo.svg", LOGO_SVG.encode(), "image/svg+xml")

    status, body = call("POST", f"{CATALOG_PATH}/templates", {"id": TEMPLATE, "name": "ZAC Ontvangstbevestiging"})
    if not report("create template", status, body):
        if status not in (409, 500) or call("GET", TEMPLATE_PATH)[0] != 200:
            raise SystemExit(1)
        print("  (already exists, updating it)")
    contract = {"dataModel": DATA_MODEL, "dataExamples": [{"id": "ontvangen", "name": "Aanvraag ontvangen", "data": RECEIVED}]}
    status, body = call("PATCH", TEMPLATE_PATH, contract)
    if status == 409:
        status, body = call("PATCH", TEMPLATE_PATH, {**contract, "forceUpdate": True})
    require("publish data contract", status, body)

    existing = {v["id"] for v in call("GET", TEMPLATE_PATH)[1].get("variants", [])}
    for variant_id, title, description, attributes, language, kanaal in VARIANTS:
        if variant_id in existing:
            require(f"update variant {variant_id}", *call("PATCH", f"{TEMPLATE_PATH}/variants/{variant_id}",
                                                          {"title": title, "attributes": attributes}))
        else:
            require(f"create variant {variant_id}", *call("POST", f"{TEMPLATE_PATH}/variants",
                                                          {"id": variant_id, "title": title, "description": description,
                                                           "attributes": attributes}))
        model = ontvangstbevestiging(language, kanaal, logo)
        require(f"save draft {variant_id} ({len(model['nodes'])} nodes)",
                *call("PUT", f"{TEMPLATE_PATH}/variants/{variant_id}/draft", {"templateModel": model}))
        require(f"publish {variant_id}", *call("POST", f"{TEMPLATE_PATH}/variants/{variant_id}/draft/publish"))

    if "--no-render" not in sys.argv:
        base.TEMPLATE = TEMPLATE
        base.OUT = os.environ.get("ONTVANGSTBEVESTIGING_OUT", base.OUT)
        base.render("default variant, as ZAC asks", {}, RECEIVED, "ontvangstbevestiging-nl-digitaal")
        base.render("Dutch by post", {"variantId": "post"}, RECEIVED, "ontvangstbevestiging-nl-post")
        base.render("English, by attributes", {"attributes": [{"key": "system.locale", "value": "en-GB"},
                                                              {"key": f"{CATALOG}.{ATTRIBUTE}", "value": "digitaal"}]},
                    RECEIVED, "ontvangstbevestiging-en-digitaal")
        base.render("German, by attributes", {"attributes": [{"key": "system.locale", "value": "de-DE"},
                                                             {"key": f"{CATALOG}.{ATTRIBUTE}", "value": "digitaal"}]},
                    RECEIVED, "ontvangstbevestiging-de-digitaal")


if __name__ == "__main__":
    main()
