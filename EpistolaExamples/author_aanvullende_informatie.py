# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors and publishes `zac-aanvullende-informatie`, a template whose variants differ in several ways at once.

Use case: a municipality asks an applicant for missing information. The same data contract (ZAC's own payload) serves
five variants, because the same message has to reach people in different ways:

| variant id         | language | kanaal   | extra attribute          | what is different                                   |
|--------------------|----------|----------|--------------------------|-----------------------------------------------------|
| `initial`          | nl-NL    | post     | -                        | the formal letter, with address block (the default) |
| `groot-lettertype` | nl-NL    | post     | weergave = groot         | large print: 15 pt, black, one column, more spacing |
| `digitaal`         | nl-NL    | digitaal | -                        | for the inbox: banner, no address block             |
| `eenvoudig`        | nl-NL    | digitaal | taalniveau = eenvoudig   | plain language (B1): short sentences, numbered steps |
| `english`          | en-GB    | digitaal | -                        | English                                              |

`system.locale` and `kanaal` are what ZAC asks for. `weergave` and `taalniveau` are defined here as catalog
attributes; ZAC cannot choose them yet, but Epistola can (by attributes or by variant id).

Run from the ZAC repository root, after the besluitbrief (it reuses its logo, theme and the `kanaal` attribute):
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_aanvullende_informatie.py
`--no-render` skips rendering. Prints statuses, never secrets. Safe to run again.
"""
import os
import sys

import author_besluitbrief as base
from author_besluitbrief import (BR, CATALOG, CATALOG_PATH, DATA_MODEL, E, H, LOGO_NAME, LOGO_SVG, Model, P, T, THEME,
                                 THEME_BODY, ATTRIBUTE, UL, call, date, ensure_image, or_dash, report, require)

THEME_GROOT = "voorbeeldstad-groot"
THEME_GROOT_BODY = {
    **THEME_BODY, "id": THEME_GROOT, "name": "Gemeente Voorbeeldstad, groot lettertype",
    "description": "Huisstijl voor slechtzienden: 15 pt, zwart op wit, ruime regelafstand.",
    "documentStyles": {**THEME_BODY["documentStyles"], "fontSize": "15pt", "lineHeight": 1.0, "color": "#000000"},
    "blockStylePresets": {
        "kop": {"label": "Kop", "styles": {"color": "#000000", "fontSize": "17pt", "marginTop": "3sp", "marginBottom": "1sp"}},
        "klein": {"label": "Klein", "styles": {"fontSize": "12pt", "color": "#000000"}},
        "kader": {"label": "Kader", "styles": {"borderLeft": "4pt solid #000000", "paddingTop": "2sp", "paddingBottom": "2sp",
                                                "paddingLeft": "4sp", "marginBottom": "4sp"}},
    },
}

TEMPLATE = "zac-aanvullende-informatie"
TEMPLATE_PATH = f"{CATALOG_PATH}/templates/{TEMPLATE}"

EXAMPLE = {
    "zaak": {"identificatie": "ZAAK-2026-0000000102", "zaaktype": "Omgevingsvergunning kappen",
             "omschrijving": "Kappen van een eik in de achtertuin", "status": "Opgeschort", "groep": "Team Vergunningen",
             "registratiedatum": "2026-10-01",
             "opschortingReden": "Een kopie van uw legitimatiebewijs en een foto van de boom met een duidelijke maatvoering."},
    "aanvrager": {"naam": "J. Voorbeeld", "straat": "Voorbeeldstraat", "huisnummer": "12A", "postcode": "9999 ZZ",
                  "woonplaats": "Voorbeeldstad"},
    "gebruiker": {"naam": "Sanne de Vries"},
}

ATTRIBUTES = [
    ("weergave", "Weergave", ["normaal", "groot"]),
    ("taalniveau", "Taalniveau", ["standaard", "eenvoudig"]),
]

LOCALE, KANAAL = "system.locale", f"{CATALOG}.{ATTRIBUTE}"
VARIANTS = [
    ("initial", "Nederlands, per post", "De standaardbrief met adresblok",
     {LOCALE: "nl-NL", KANAAL: "post"}),
    ("groot-lettertype", "Nederlands, per post, groot lettertype", "Voor slechtzienden: 15 pt, zwart, één kolom",
     {LOCALE: "nl-NL", KANAAL: "post", f"{CATALOG}.weergave": "groot"}),
    ("digitaal", "Nederlands, digitaal", "Voor de inbox van Mijn Voorbeeldstad",
     {LOCALE: "nl-NL", KANAAL: "digitaal"}),
    ("eenvoudig", "Nederlands, digitaal, eenvoudige taal", "Taalniveau B1: korte zinnen en genummerde stappen",
     {LOCALE: "nl-NL", KANAAL: "digitaal", f"{CATALOG}.taalniveau": "eenvoudig"}),
    ("english", "English, digital", "For applicants who asked for English",
     {LOCALE: "en-GB", KANAAL: "digitaal"}),
]

TEXTS = {
    "nl": {
        "gemeente": "Gemeente Voorbeeldstad", "adresregel": "Postbus 1234 · 9999 ZZ Voorbeeldstad · 14 0999",
        "pagina": ("Kenmerk ", " · pagina ", " van "), "aan_onbekend": "Aan de aanvrager van zaak ",
        "datum": "Datum", "kenmerk": "Ons kenmerk", "digitaal": "Dit bericht staat ook in uw inbox op Mijn Voorbeeldstad.",
        "titel": "Wij hebben meer gegevens van u nodig",
        "aanhef": ("Geachte heer/mevrouw ", "Geachte heer/mevrouw,"),
        "inleiding": ("Wij behandelen uw aanvraag ", " van ", ". Om verder te kunnen, missen wij nog gegevens."),
        "nodig": "Wat hebben wij nodig?", "geen_reden": "Wij nemen contact met u op over wat er nog mist.",
        "termijn": "Stuur ons deze gegevens binnen 14 dagen na de datum van deze brief. Zolang wij ze niet hebben, "
                   "staat de behandeling van uw aanvraag stil.",
        "hoe": "Hoe stuurt u ze op?",
        "hoe_post": "Antwoord op deze brief of mail naar gegevens@voorbeeldstad.example en noem uw kenmerk.",
        "hoe_digitaal": "Upload ze in Mijn Voorbeeldstad bij uw aanvraag, of mail naar gegevens@voorbeeldstad.example.",
        "vragen": "Heeft u vragen? Bel 14 0999 en noem uw kenmerk.",
        "groet": "Met vriendelijke groet,",
        # eenvoudige taal
        "e_titel": "Wij missen nog iets", "e_aanhef": ("Dag ", "Dag,"),
        "e_inleiding": ("U heeft een aanvraag gedaan: ", ". Wij kunnen nog niet verder. Er ontbreekt iets."),
        "e_stappen_kop": "Wat moet u doen?",
        "e_stappen": ("Lees hieronder wat wij nodig hebben.", "Stuur dat naar ons binnen 14 dagen.",
                      "Dan gaan wij verder met uw aanvraag."),
        "e_hoe": "Zo stuurt u het op: ga naar Mijn Voorbeeldstad en kies uw aanvraag. Of mail naar gegevens@voorbeeldstad.example.",
        "e_vragen": "Snapt u het niet? Bel ons: 14 0999.",
    },
    "en": {
        "gemeente": "Municipality of Voorbeeldstad", "adresregel": "PO Box 1234 · 9999 ZZ Voorbeeldstad · 14 0999",
        "pagina": ("Reference ", " · page ", " of "), "aan_onbekend": "To the applicant of case ",
        "datum": "Date", "kenmerk": "Our reference", "digitaal": "This message is also in your inbox on My Voorbeeldstad.",
        "titel": "We need more information from you",
        "aanhef": ("Dear ", "Dear Sir or Madam,"),
        "inleiding": ("We are handling your application ", " of ", ". To continue, we are still missing information."),
        "nodig": "What do we need?", "geen_reden": "We will contact you about what is still missing.",
        "termijn": "Please send us this within 14 days of the date of this letter. Until we have it, we cannot continue "
                   "with your application.",
        "hoe": "How do you send it?",
        "hoe_post": "Reply to this letter or email gegevens@voorbeeldstad.example and quote your reference.",
        "hoe_digitaal": "Upload it in My Voorbeeldstad on your application, or email gegevens@voorbeeldstad.example.",
        "vragen": "Questions? Call 14 0999 and quote your reference.",
        "groet": "Kind regards,",
    },
}


def aanhef(t, prefix, fallback):
    return P(E(f"$exists(aanvrager.naam) ? '{prefix}' & aanvrager.naam & ',' : '{fallback}'"))


def frame(m, language, logo, kanaal, large=False):
    t = TEXTS[language]
    size = {}
    cover = m.add("pageheader", {"height": "64pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    logo_column, name_column = m.columns(cover, [1, 7], gap=10)
    m.add("image", {"assetId": logo, "catalogKey": CATALOG, "alt": t["gemeente"],
                    "width": "50pt" if large else "38pt", "aspectRatioLocked": True}, parent=logo_column)
    m.text(name_column, P(T(t["gemeente"], "bold")),
           styles={"fontSize": "17pt" if large else "15pt", "color": "#000000" if large else "#00566b", "marginBottom": "0sp"})
    m.text(name_column, P(t["adresregel"]), preset="klein")
    footer = m.add("pagefooter", {"height": "18pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    m.text(footer, P(t["pagina"][0], E("zaak.identificatie"), t["pagina"][1], E("sys.pages.current"), t["pagina"][2],
                     E("sys.pages.total")), preset="klein", styles={"textAlign": "center"})
    if kanaal == "post":
        _, address = m.add("addressblock", {"standard": "din-c56-left", "align": "left", "top": 45, "sideDistance": 20,
                                            "addressWidth": 85, "height": 45}, slot_names=["address", "aside"], parent=m.body)
        known = m.conditional(address["address"], "$exists(aanvrager.naam)")
        line = {"marginBottom": "0sp", **size}
        m.text(known, P(E("aanvrager.naam")), styles=line)
        street = m.conditional(known, "$exists(aanvrager.straat)")
        m.text(street, P(E("$join([aanvrager.straat, aanvrager.huisnummer], ' ')")), styles=line)
        place = m.conditional(known, "$exists(aanvrager.woonplaats)")
        m.text(place, P(E("$join([aanvrager.postcode, $uppercase(aanvrager.woonplaats)], '  ')")), styles=line)
        unknown = m.conditional(address["address"], "$exists(aanvrager.naam)", inverse=True)
        m.text(unknown, P(t["aan_onbekend"], E("zaak.identificatie")), styles=size)
        for label, value in ((t["datum"], E(base.date("sys.render.time"))), (t["kenmerk"], E("zaak.identificatie"))):
            m.text(address["aside"], P(T(label, "bold"), BR, value), preset="klein", styles={"marginBottom": "1sp"})
    else:
        m.text(m.body, P(t["digitaal"]), preset="kader")


def formal(language, kanaal, logo, large=False):
    t = TEXTS[language]
    m = Model(f"{language}-{kanaal}{'-groot' if large else ''}")
    frame(m, language, logo, kanaal, large)
    size = {"marginBottom": "4sp"} if large else {"marginBottom": "3sp"}
    head = {"fontSize": "22pt", "color": "#000000"} if large else {"color": "#00566b", "marginTop": "2sp", "marginBottom": "1sp"}
    m.text(m.body, H(2, t["titel"]), styles=head)
    m.text(m.body, aanhef(t, *t["aanhef"]), styles=size)
    m.text(m.body, P(t["inleiding"][0], E("zaak.zaaktype"), t["inleiding"][1], E(date("zaak.registratiedatum")),
                     t["inleiding"][2]), styles=size)
    m.text(m.body, H(3, t["nodig"]), preset="kop", styles={"keepWithNext": True})
    needed = m.container(m.body, preset="kader")
    m.text(needed, P(E(f"$exists(zaak.opschortingReden) ? zaak.opschortingReden : '{t['geen_reden']}'")), styles=size)
    m.text(m.body, P(t["termijn"]), styles=size)
    m.text(m.body, H(3, t["hoe"]), preset="kop", styles={"keepWithNext": True})
    m.text(m.body, P(t["hoe_post"] if kanaal == "post" else t["hoe_digitaal"]), styles=size)
    m.text(m.body, P(t["vragen"]), styles=size)
    m.text(m.body, P(t["groet"]), styles={**size, "marginTop": "3sp", "marginBottom": "0sp"})
    m.text(m.body, P(E("gebruiker.naam"), BR, t["gemeente"]), styles=size)
    return m.document({"type": "override", "themeId": THEME_GROOT if large else THEME, "catalogKey": CATALOG})


def eenvoudig(logo):
    t = TEXTS["nl"]
    m = Model("nl-digitaal-b1")
    frame(m, "nl", logo, "digitaal")
    m.text(m.body, H(2, t["e_titel"]), styles={"color": "#00566b", "marginTop": "2sp", "marginBottom": "1sp"})
    m.text(m.body, aanhef(t, *t["e_aanhef"]), styles={"marginBottom": "3sp"})
    m.text(m.body, P(t["e_inleiding"][0], E("zaak.zaaktype"), t["e_inleiding"][1]), styles={"marginBottom": "3sp"})
    m.text(m.body, H(3, t["nodig"]), preset="kop", styles={"keepWithNext": True})
    box = m.container(m.body, preset="kader")
    m.text(box, P(E(f"$exists(zaak.opschortingReden) ? zaak.opschortingReden : '{t['geen_reden']}'")))
    m.text(m.body, H(3, t["e_stappen_kop"]), preset="kop", styles={"keepWithNext": True})
    m.text(m.body, {"type": "ordered_list", "attrs": {"order": 1, "listStyle": "decimal"},
                    "content": [{"type": "list_item", "content": [P(step)]} for step in t["e_stappen"]]},
           styles={"keepTogether": True})
    m.text(m.body, P(t["e_hoe"]))
    m.text(m.body, P(T(t["e_vragen"], "bold")))
    m.text(m.body, P(E("gebruiker.naam"), BR, t["gemeente"]), styles={"marginTop": "3sp"})
    return m.document({"type": "override", "themeId": THEME, "catalogKey": CATALOG})


def model_for(variant_id, logo):
    return {"initial": lambda: formal("nl", "post", logo),
            "groot-lettertype": lambda: formal("nl", "post", logo, large=True),
            "digitaal": lambda: formal("nl", "digitaal", logo),
            "eenvoudig": lambda: eenvoudig(logo),
            "english": lambda: formal("en", "digitaal", logo)}[variant_id]()


def main():
    logo = ensure_image(LOGO_NAME, "voorbeeldstad-logo.svg", LOGO_SVG.encode(), "image/svg+xml")
    for key, display_name, allowed in ATTRIBUTES:
        status, body = call("POST", f"{CATALOG_PATH}/attributes", {"key": key, "displayName": display_name, "allowedValues": allowed})
        if not report(f"define attribute {key}", status, body) and call("GET", f"{CATALOG_PATH}/attributes/{key}")[0] != 200:
            raise SystemExit(1)

    status, body = call("POST", f"{CATALOG_PATH}/themes", THEME_GROOT_BODY)
    if not report(f"create theme {THEME_GROOT}", status, body):
        require(f"update theme {THEME_GROOT}", *call("PATCH", f"{CATALOG_PATH}/themes/{THEME_GROOT}",
                                                     {k: v for k, v in THEME_GROOT_BODY.items() if k != "id"}))
    status, body = call("POST", f"{CATALOG_PATH}/themes", THEME_BODY)
    if not report(f"create theme {THEME}", status, body):
        require(f"update theme {THEME}", *call("PATCH", f"{CATALOG_PATH}/themes/{THEME}", {k: v for k, v in THEME_BODY.items() if k != "id"}))

    status, body = call("POST", f"{CATALOG_PATH}/templates", {"id": TEMPLATE, "name": "ZAC Verzoek om aanvullende informatie"})
    if not report("create template", status, body):
        if status not in (409, 500) or call("GET", TEMPLATE_PATH)[0] != 200:
            raise SystemExit(1)
        print("  (already exists, updating it)")
    contract = {"dataModel": DATA_MODEL, "dataExamples": [{"id": "ontbrekende-stukken", "name": "Stukken ontbreken", "data": EXAMPLE}]}
    status, body = call("PATCH", TEMPLATE_PATH, contract)
    if status == 409:
        status, body = call("PATCH", TEMPLATE_PATH, {**contract, "forceUpdate": True})
    require("publish data contract", status, body)

    existing = {v["id"] for v in call("GET", TEMPLATE_PATH)[1].get("variants", [])}
    for variant_id, title, description, attributes in VARIANTS:
        if variant_id in existing:
            require(f"update variant {variant_id}", *call("PATCH", f"{TEMPLATE_PATH}/variants/{variant_id}",
                                                          {"title": title, "attributes": attributes}))
        else:
            require(f"create variant {variant_id}", *call("POST", f"{TEMPLATE_PATH}/variants",
                                                          {"id": variant_id, "title": title, "description": description,
                                                           "attributes": attributes}))
        model = model_for(variant_id, logo)
        require(f"save draft {variant_id} ({len(model['nodes'])} nodes)",
                *call("PUT", f"{TEMPLATE_PATH}/variants/{variant_id}/draft", {"templateModel": model}))
        require(f"publish {variant_id}", *call("POST", f"{TEMPLATE_PATH}/variants/{variant_id}/draft/publish"))

    if "--no-render" not in sys.argv:
        base.TEMPLATE = TEMPLATE
        base.OUT = os.environ.get("AANVULLENDE_INFORMATIE_OUT", base.OUT)
        print("what ZAC asks (language and kanaal, both required) against these variants:")
        for label, selection in [("nl-NL + post", [(LOCALE, "nl-NL"), (KANAAL, "post")]),
                                 ("nl-NL + digitaal", [(LOCALE, "nl-NL"), (KANAAL, "digitaal")]),
                                 ("en-GB + digitaal", [(LOCALE, "en-GB"), (KANAAL, "digitaal")])]:
            attributes = [{"catalog": "system" if key == LOCALE else CATALOG, "key": key.split(".")[-1], "value": value,
                           "required": True} for key, value in selection]
            status, body = call("POST", f"/tenants/{base.TENANT}/documents/generate",
                                {"catalogId": CATALOG, "templateId": TEMPLATE, "data": EXAMPLE, "filename": "probe.pdf",
                                 "attributes": attributes})
            detail = "" if status in (200, 202) else (body.get("detail") if isinstance(body, dict) else body)
            print(f"  {label}: HTTP {status} {detail}")
            if status in (200, 202):
                call("DELETE", f"/tenants/{base.TENANT}/documents/jobs/{body.get('requestId')}")
        for variant_id, _title, _description, _attributes in VARIANTS:
            base.render(f"by variant id {variant_id}", {"variantId": variant_id}, EXAMPLE, f"aanvullende-informatie-{variant_id}")

if __name__ == "__main__":
    main()
