# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""Authors the ZAC Besluitbrief in the configured Epistola catalog, with everything it uses, and renders it once.

Re-runnable after the daily reset of the test tenant: what already exists is reused or updated. Run it as
    op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_besluitbrief.py
from the ZAC checkout. Prints statuses and timings, never secrets.

What it exercises, for ZAC to support later:
- three variants chosen by attributes: `system.locale` (Epistola's own) and `kanaal` (defined here);
  ZAC sends neither, so it always gets the default variant, "Nederlands, per post";
- a theme with style presets, a cover header and a running header, a footer with "pagina X van Y";
- an address block, conditionals, a table, a datatable over `zaak.eigenschappen`, columns, a numbered datalist,
  a QR code, a loop, a page break, and the bezwaarclausule as a stencil shared by the two Dutch variants;
- two uploaded images: a logo (SVG) and a situatieschets (RGBA PNG). The PNG's alpha channel makes Epistola's PDF
  library decode and re-encode it, twice because of "van Y"; that is what keeps the render long enough to see
  "Maken" in ZAC. Its size is set by SKETCH_WIDTH.
"""
import json
import os
import random
import struct
import sys
import time
import urllib.error
import urllib.request
import uuid
import zlib

BASE = os.environ["EPISTOLA_CLIENT_MP_REST_URL"].rstrip("/")
KEY = os.environ["EPISTOLA_CLIENT_API_KEY"]
TENANT = os.environ["EPISTOLA_TENANT_ID"]
CATALOG = os.environ["EPISTOLA_CATALOG_ID"]
MEDIA_TYPE = "application/vnd.epistola.v1+json"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("BESLUITBRIEF_OUT", HERE)

TEMPLATE = "zac-besluitbrief"
THEME = "voorbeeldstad"
STENCIL = "bezwaarclausule"
ATTRIBUTE = "kanaal"
LOGO_NAME = "voorbeeldstad-logo"
SKETCH_NAME = "voorbeeldstad-situatieschets"
SKETCH_WIDTH = int(os.environ.get("SKETCH_WIDTH", "5200"))
CATALOG_PATH = f"/tenants/{TENANT}/catalogs/{CATALOG}"
TEMPLATE_PATH = f"{CATALOG_PATH}/templates/{TEMPLATE}"


def call(method, path, body=None, accept=None, raw_body=None, content_type=MEDIA_TYPE):
    request = urllib.request.Request(
        BASE + path,
        method=method,
        data=raw_body if raw_body is not None else None if body is None else json.dumps(body).encode(),
        headers={
            "Authorization": f"ApiKey {KEY}",
            "Accept": accept or f"{MEDIA_TYPE}, application/problem+json",
            "Content-Type": content_type,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            if accept and "pdf" in accept:
                return response.status, raw
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, raw[:300]


def report(step, status, body, ok=(200, 201, 202, 204)):
    detail = "" if status in ok else (
        {k: body.get(k) for k in ("title", "detail", "errors", "violations") if body.get(k)} if isinstance(body, dict) else body)
    print(f"{step}: HTTP {status}", detail)
    return status in ok


def require(step, status, body, ok=(200, 201, 202, 204)):
    if not report(step, status, body, ok):
        raise SystemExit(1)
    return body


def items_of(body):
    return body.get("items", []) if isinstance(body, dict) else body or []


# --------------------------------------------------------------------------------------------- images

def png(width, height, rows):
    """An RGBA PNG from rows of bytes; colour type 6 is what makes Epistola's PDF library decode it."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def situatieschets(width):
    """A schematic site plan: blocks, lots, buildings, a park, a canal, the parcel in red, a north arrow and a scale."""
    height = int(width / 1.414)
    rng = random.Random(42)
    rows = [bytearray(b"\x00\x00\x00\x00" * width) for _ in range(height)]

    def fill(x0, y0, x1, y1, rgba):
        x0, x1 = max(0, int(x0)), min(width, int(x1))
        y0, y1 = max(0, int(y0)), min(height, int(y1))
        if x1 <= x0:
            return
        span = bytes(rgba) * (x1 - x0)
        for y in range(y0, y1):
            rows[y][x0 * 4:x1 * 4] = span

    def outline(x0, y0, x1, y1, thickness, rgba):
        fill(x0, y0, x1, y0 + thickness, rgba)
        fill(x0, y1 - thickness, x1, y1, rgba)
        fill(x0, y0, x0 + thickness, y1, rgba)
        fill(x1 - thickness, y0, x1, y1, rgba)

    unit = width / 100
    border = int(unit * 1.2)
    fill(border, border, width - border, height - border, (244, 241, 232, 255))
    street, main_street = unit * 2.2, unit * 4
    columns, rows_of_blocks = 7, 5
    block_w = (width - 2 * border - (columns + 1) * street) / columns
    block_h = (height - 2 * border - (rows_of_blocks + 1) * street) / rows_of_blocks
    canal_row = 3
    target = (4, 2)
    for column in range(columns):
        for row in range(rows_of_blocks):
            x0 = border + street + column * (block_w + street)
            y0 = border + street + row * (block_h + street)
            x1, y1 = x0 + block_w, y0 + block_h
            if row == canal_row and column in (0, 1, 2):
                fill(x0, y0 + block_h * 0.35, x1 + street, y0 + block_h * 0.65, (188, 216, 232, 255))
                fill(x0, y0, x1, y0 + block_h * 0.3, (207, 227, 196, 255))
                continue
            if (column, row) in ((5, 0), (6, 0), (5, 1)):
                fill(x0, y0, x1, y1, (207, 227, 196, 255))
                for _ in range(int(block_w * block_h / (unit * unit) * 1.6)):
                    tx, ty, r = x0 + rng.random() * block_w, y0 + rng.random() * block_h, unit * (0.25 + rng.random() * 0.35)
                    fill(tx - r, ty - r, tx + r, ty + r, (143, 181, 125, 255))
                continue
            fill(x0, y0, x1, y1, (227, 224, 214, 255))
            lots = rng.randint(4, 7)
            lot_w = block_w / lots
            for lot in range(lots):
                lx0 = x0 + lot * lot_w
                for half in (0, 1):
                    ly0 = y0 + half * block_h / 2
                    fill(lx0, ly0, lx0 + 3, ly0 + block_h / 2, (201, 195, 179, 255))
                    building = (lx0 + lot_w * 0.15, ly0 + block_h * 0.06 if half == 0 else ly0 + block_h * 0.2,
                                lx0 + lot_w * (0.7 + rng.random() * 0.2), ly0 + block_h * 0.3 if half == 0 else ly0 + block_h * 0.44)
                    fill(*building, (185, 178, 161, 255))
                    if (column, row) == target and lot == 1 and half == 1:
                        fill(lx0, ly0, lx0 + lot_w, ly0 + block_h / 2, (246, 213, 213, 255))
                        fill(*building, (214, 120, 120, 255))
                        outline(lx0, ly0, lx0 + lot_w, ly0 + block_h / 2, int(unit * 0.45), (214, 69, 69, 255))
                fill(x0, y0 + block_h / 2 - 2, x1, y0 + block_h / 2 + 2, (201, 195, 179, 255))
    middle = border + street + 3 * (block_w + street) - street
    fill(middle - main_street / 2 + street / 2, border, middle + main_street / 2 + street / 2, height - border, (253, 246, 216, 255))
    # north arrow and scale bar
    ax, ay, size = width - border - unit * 6, border + unit * 3, unit * 3
    for step in range(int(size)):
        half = step / size * unit * 1.2
        fill(ax - half, ay + step, ax + half, ay + step + 1, (40, 40, 40, 255))
    fill(ax - unit * 0.3, ay + size, ax + unit * 0.3, ay + size * 1.6, (40, 40, 40, 255))
    sx, sy = border + unit * 3, height - border - unit * 3
    for part in range(4):
        fill(sx + part * unit * 4, sy, sx + (part + 1) * unit * 4, sy + unit * 0.8,
             (40, 40, 40, 255) if part % 2 == 0 else (255, 255, 255, 255))
    outline(sx, sy, sx + unit * 16, sy + unit * 0.8, 3, (40, 40, 40, 255))
    return png(width, height, rows)


LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 140" width="120" height="140">
  <path d="M10 10 H110 V70 C110 105 85 125 60 135 C35 125 10 105 10 70 Z" fill="#00566b"/>
  <path d="M22 22 H98 V68 C98 96 80 112 60 121 C40 112 22 96 22 68 Z" fill="#ffffff"/>
  <path d="M34 34 L60 104 L86 34 H72 L60 72 L48 34 Z" fill="#00566b"/>
  <rect x="28" y="22" width="12" height="9" fill="#e0a526"/><rect x="54" y="22" width="12" height="9" fill="#e0a526"/>
  <rect x="80" y="22" width="12" height="9" fill="#e0a526"/>
</svg>"""


def multipart(fields, file_name, file_bytes, file_type):
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
                 f'Content-Type: {file_type}\r\n\r\n'.encode() + file_bytes + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def ensure_image(name, file_name, file_bytes, file_type):
    status, body = call("GET", f"{CATALOG_PATH}/images")
    for image in items_of(body) if status == 200 else []:
        if image.get("name") == name:
            print(f"image {name}: reusing {image.get('slug')}")
            return image["slug"]
    data, content_type = multipart({"name": name, "mediaType": file_type}, file_name, file_bytes, file_type)
    status, body = call("POST", f"{CATALOG_PATH}/images", raw_body=data, content_type=content_type)
    require(f"upload image {name} ({len(file_bytes) // 1024} KiB)", status, body)
    return body["slug"]


# --------------------------------------------------------------------------------------------- template model

def T(text, *marks):
    node = {"type": "text", "text": text}
    if marks:
        node["marks"] = [{"type": mark} for mark in marks]
    return node


def E(expression):
    return {"type": "expression", "attrs": {"isNew": False, "expression": expression}}


def J(script):
    """Epistola's JavaScript expression language: a sandbox per evaluation, for what JSONata cannot say well."""
    return {"type": "expression", "attrs": {"isNew": False, "expression": script, "language": "javascript"}}


WORKING_DAYS = ("((from, to) => {{ if (!from || !to) return null; let day = new Date(from + 'T00:00:00Z'); "
                "const end = new Date(to + 'T00:00:00Z'); let count = 0; while (day < end) {{ const weekday = day.getUTCDay(); "
                "if (weekday > 0 && weekday < 6) count++; day.setUTCDate(day.getUTCDate() + 1); }} return {result}; }})({arguments})")


def working_days(start, end, phrase):
    """Counts working days from start to end (ISO dates, or today when end is absent) as a phrase."""
    return J(WORKING_DAYS.format(result=phrase, arguments=f"{start}, {end} || new Date().toISOString().slice(0, 10)"))


BR = {"type": "hard_break"}


def inline(parts):
    return [part if isinstance(part, dict) else T(part) for part in parts if part != ""]


def P(*parts):
    content = inline(parts)
    return {"type": "paragraph", "content": content} if content else {"type": "paragraph"}


def H(level, *parts):
    return {"type": "heading", "attrs": {"level": level}, "content": inline(parts)}


def UL(*items):
    return {"type": "bullet_list", "attrs": {"listStyle": "disc"},
            "content": [{"type": "list_item", "content": [P(*(item if isinstance(item, tuple) else (item,)))]} for item in items]}


def X(raw):
    return {"raw": raw, "language": "jsonata"}


def date(path, pattern="d MMMM yyyy"):
    return f"$formatDate({path}, '{pattern}')"


def or_dash(path, expression=None):
    return f"$exists({path}) ? {expression or path} : '–'"


class Model:
    def __init__(self, prefix):
        self.prefix, self.count, self.nodes, self.slots = prefix, 0, {}, {}
        self.root, slots = self.add("root", slot_names=["children"])
        self.body = slots["children"]

    def add(self, node_type, props=None, styles=None, preset=None, slot_names=(), parent=None):
        self.count += 1
        node_id = f"{self.prefix}-{self.count}"
        slots = {}
        for name in slot_names:
            slot_id = f"{node_id}-{name}"
            self.slots[slot_id] = {"id": slot_id, "nodeId": node_id, "name": name, "children": []}
            slots[name] = slot_id
        node = {"id": node_id, "type": node_type, "slots": list(slots.values())}
        if styles:
            node["styles"] = styles
        if preset:
            node["stylePreset"] = preset
        if props is not None:
            node["props"] = props
        self.nodes[node_id] = node
        if parent:
            self.slots[parent]["children"].append(node_id)
        return node_id, slots

    def text(self, parent, *blocks, styles=None, preset=None):
        return self.add("text", {"content": {"type": "doc", "content": list(blocks)}}, styles, preset, parent=parent)[0]

    def conditional(self, parent, condition, inverse=False, styles=None):
        return self.add("conditional", {"condition": X(condition), "inverse": inverse}, styles,
                        slot_names=["body"], parent=parent)[1]["body"]

    def container(self, parent, styles=None, preset=None):
        return self.add("container", styles=styles, preset=preset, slot_names=["children"], parent=parent)[1]["children"]

    def columns(self, parent, sizes, gap=16):
        return list(self.add("columns", {"columnSizes": sizes, "gap": gap},
                             slot_names=[f"column-{i}" for i in range(len(sizes))], parent=parent)[1].values())

    def document(self, theme_ref):
        return {"modelVersion": 1, "root": self.root, "nodes": self.nodes, "slots": self.slots, "themeRef": theme_ref}


def bezwaarclausule(model, parent):
    """The stencil's content, written once and placed both in the stencil and, as Epistola requires, in its instance."""
    model.text(parent, H(3, "Bent u het niet eens met dit besluit?"), preset="kop", styles={"keepWithNext": True})
    model.text(parent,
               P("Dan kunt u binnen ", E("params.termijnWeken"), " weken na de datum van deze brief bezwaar maken bij ",
                 E("params.bestuursorgaan"), ". Zet in uw bezwaarschrift in ieder geval:"),
               UL("uw naam en adres;", "de datum;", ("het kenmerk van deze brief: ", E("zaak.identificatie"), ";"),
                  "waarom u het niet eens bent met het besluit;", "uw handtekening."),
               P("U kunt ook eerst bellen met ", T("14 0999", "bold"), ". Vaak lossen we het samen op, zonder bezwaarprocedure."),
               styles={"keepTogether": True})


STENCIL_SCHEMA = {
    "type": "object",
    "properties": {
        "termijnWeken": {"type": "integer", "default": 6, "description": "Bezwaartermijn in weken"},
        "bestuursorgaan": {"type": "string", "default": "het college van burgemeester en wethouders",
                           "description": "Bij wie de ontvanger bezwaar maakt"},
    },
    "required": ["termijnWeken", "bestuursorgaan"],
}

TEXTS = {
    "nl": {
        "gemeente": "Gemeente Voorbeeldstad",
        "adresregel": "Postbus 1234 · 9999 ZZ Voorbeeldstad · 14 0999 · voorbeeldstad.example",
        "lopend": "Besluit op uw aanvraag · kenmerk ",
        "pagina": ("Kenmerk ", " · pagina ", " van "),
        "aan_onbekend": "Aan de aanvrager van zaak ",
        "datum": "Datum", "kenmerk": "Ons kenmerk", "behandeld": "Behandeld door", "telefoon": "Telefoon",
        "titel": "Besluit op uw aanvraag",
        "aanhef": ("Geachte heer/mevrouw ", "Geachte heer/mevrouw,"),
        "inleiding": ("Op ", " hebben wij uw aanvraag ontvangen voor ",
                      ". In deze brief leest u wat wij hebben besloten en wat u kunt doen als u het daar niet mee eens bent."),
        "besluit_kop": "Ons besluit", "besluit": "Wij hebben besloten: ",
        "stand_kop": "De stand van zaken",
        "stand": ("Wij hebben nog geen besluit genomen. Uw aanvraag heeft nu de status ", ". U hoort uiterlijk ", " van ons."),
        "opgeschort": "Wij hebben de behandeling van uw aanvraag tijdelijk stilgezet. De reden is: ",
        "verlengd": ("Wij hebben meer tijd nodig om uw aanvraag te behandelen. De reden is: ", ". U hoort uiterlijk ", " van ons."),
        "gegevens_kop": "Gegevens van uw aanvraag",
        "rijen": ["Zaaknummer", "Soort aanvraag", "Omschrijving", "Ontvangen op", "Status", "Behandelaar", "Team", "Streefdatum",
                  "Behandelduur"],
        "duur": "count === 1 ? '1 werkdag' : count + ' werkdagen'",
        "na_ontvangst": "from === to ? 'de dag van ontvangst' : count === 1 ? '1 werkdag na ontvangst' : count + ' werkdagen na ontvangst'",
        "opgegeven_kop": "Wat u ons heeft opgegeven", "opgegeven": ("Gegeven", "Waarde"),
        "locatie": ("Locatie van de aanvraag: ", " noorderbreedte, ", " oosterlengte (zie bijlage 1)."),
        "stappen_kop": "Wat gebeurt er nu?",
        "stappen_besluit": '["U vindt dit besluit ook in Mijn Voorbeeldstad.", "Wilt u bezwaar maken? Hieronder leest u hoe dat gaat.", "Bewaar deze brief bij uw administratie."]',
        "stappen_open": '["Wij behandelen uw aanvraag verder.", "U ontvangt ons besluit uiterlijk op " & {deadline} & ".", "Heeft u vragen? Bel dan 14 0999."]',
        "online_kop": "Uw aanvraag online", "online": "Scan de code om uw aanvraag te bekijken in Mijn Voorbeeldstad.",
        "digitaal": "Dit bericht staat ook in Mijn Voorbeeldstad. U ontvangt het niet per post.",
        "groet": ("Met vriendelijke groet,", "namens het college van burgemeester en wethouders van Voorbeeldstad,"),
        "bijlagen_kop": "Bijlagen", "bijlagen": '["Bijlage 1: situatieschets van de locatie", "Bijlage 2: de termijnen van uw aanvraag"]',
        "schets_kop": "Bijlage 1 · Situatieschets",
        "schets": "Schematische weergave van de omgeving. Het perceel van de aanvraag is rood omlijnd. Deze schets is een testafbeelding.",
        "termijnen_kop": "Bijlage 2 · De termijnen van uw aanvraag",
        "termijnen": '[{"label": "Aanvraag ontvangen", "datum": zaak.registratiedatum}, {"label": "Behandeling gestart", "datum": zaak.startdatum}, {"label": "Streefdatum", "datum": zaak.einddatumGepland}, {"label": "Uiterlijke datum", "datum": zaak.uiterlijkeEinddatumAfdoening}, {"label": "Afgehandeld", "datum": zaak.einddatum}][$exists(datum)]^(datum)',
    },
}
TEXTS["en"] = {
    **TEXTS["nl"],
    "adresregel": "PO Box 1234 · 9999 ZZ Voorbeeldstad · +31 14 0999 · voorbeeldstad.example",
    "lopend": "Decision on your application · reference ",
    "pagina": ("Reference ", " · page ", " of "),
    "aan_onbekend": "To the applicant of case ",
    "datum": "Date", "kenmerk": "Our reference", "behandeld": "Handled by", "telefoon": "Telephone",
    "titel": "Decision on your application",
    "aanhef": ("Dear ", "Dear Sir or Madam,"),
    "inleiding": ("On ", " we received your application for ",
                  ". This letter tells you what we have decided, and what you can do if you disagree."),
    "besluit_kop": "Our decision", "besluit": "We have decided: ",
    "stand_kop": "Where things stand",
    "stand": ("We have not made a decision yet. Your application now has the status ", ". You will hear from us by ", "."),
    "opgeschort": "We have paused the handling of your application. The reason is: ",
    "verlengd": ("We need more time to handle your application. The reason is: ", ". You will hear from us by ", "."),
    "gegevens_kop": "Details of your application",
    "rijen": ["Case number", "Type of application", "Description", "Received on", "Status", "Handled by", "Team", "Target date",
              "Handling time"],
    "duur": "count === 1 ? '1 working day' : count + ' working days'",
    "na_ontvangst": "from === to ? 'the day of receipt' : count === 1 ? '1 working day after receipt' : count + ' working days after receipt'",
    "opgegeven_kop": "What you told us", "opgegeven": ("Item", "Value"),
    "locatie": ("Location of the application: ", " north, ", " east (see appendix 1)."),
    "stappen_kop": "What happens next?",
    "stappen_besluit": '["You can also find this decision in Mijn Voorbeeldstad.", "Want to object? Read below how to do so.", "Keep this letter for your records."]',
    "stappen_open": '["We will continue handling your application.", "You will receive our decision by " & {deadline} & ".", "Questions? Call +31 14 0999."]',
    "online_kop": "Your application online", "online": "Scan the code to view your application in Mijn Voorbeeldstad.",
    "groet": ("Yours sincerely,", "on behalf of the Mayor and Aldermen of Voorbeeldstad,"),
    "bijlagen_kop": "Appendices", "bijlagen": '["Appendix 1: site sketch of the location", "Appendix 2: the dates of your application"]',
    "schets_kop": "Appendix 1 · Site sketch",
    "schets": "Schematic view of the surroundings. The plot of the application is outlined in red. This sketch is a test image.",
    "termijnen_kop": "Appendix 2 · The dates of your application",
    "termijnen": TEXTS["nl"]["termijnen"].replace("Aanvraag ontvangen", "Application received").replace(
        "Behandeling gestart", "Handling started").replace("Streefdatum", "Target date").replace(
        "Uiterlijke datum", "Final date").replace("Afgehandeld", "Completed"),
}


def besluitbrief(language, kanaal, logo, sketch, stencil_version):
    t = TEXTS[language]
    m = Model(f"{language}-{kanaal}")

    cover = m.add("pageheader", {"height": "64pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    logo_column, name_column = m.columns(cover, [1, 7], gap=10)
    m.add("image", {"assetId": logo, "catalogKey": CATALOG, "alt": t["gemeente"], "width": "38pt", "aspectRatioLocked": True},
          parent=logo_column)
    m.text(name_column, P(T(t["gemeente"], "bold")), styles={"fontSize": "15pt", "color": "#00566b", "marginBottom": "0sp"})
    m.text(name_column, P(t["adresregel"]), preset="klein")

    running = m.add("pageheader", {"height": "22pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    m.text(running, P(T(t["gemeente"], "bold"), " · ", t["lopend"], E("zaak.identificatie")), preset="klein",
           styles={"borderBottom": "0.5pt solid #9aa5b1", "paddingBottom": "1sp"})

    footer = m.add("pagefooter", {"height": "18pt"}, parent=m.body, slot_names=["children"])[1]["children"]
    m.text(footer, P(t["pagina"][0], E("zaak.identificatie"), t["pagina"][1], E("sys.pages.current"), t["pagina"][2],
                     E("sys.pages.total")), preset="klein", styles={"textAlign": "center"})

    aside_rows = [
        (t["datum"], E(date("sys.render.time"))),
        (t["kenmerk"], E("zaak.identificatie")),
        (t["behandeld"], E(or_dash("zaak.behandelaar"))),
        (t["telefoon"], "14 0999"),
    ]
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
        for label, value in aside_rows:
            m.text(address["aside"], P(T(label, "bold"), BR, value), preset="klein", styles={"marginBottom": "1sp"})
    else:
        m.text(m.body, P(t["digitaal"]), preset="kader")
        table, cells = m.add("table", {"rows": 2, "columns": len(aside_rows), "columnWidths": [25] * len(aside_rows),
                                       "headerRows": 0, "merges": []},
                             styles={"marginBottom": "4sp"},
                             slot_names=[f"cell-{r}-{c}" for r in range(2) for c in range(len(aside_rows))], parent=m.body)
        for column, (label, value) in enumerate(aside_rows):
            m.text(cells[f"cell-0-{column}"], P(T(label, "bold")), preset="klein")
            m.text(cells[f"cell-1-{column}"], P(value), preset="klein")

    m.text(m.body, H(2, t["titel"]), styles={"color": "#00566b", "marginTop": "2sp", "marginBottom": "1sp"})
    m.text(m.body, P(T(t["rijen"][1] + ": ", "bold"), E("zaak.zaaktype")), styles={"marginBottom": "3sp"})
    m.text(m.body, P(E(f"$exists(aanvrager.naam) ? '{t['aanhef'][0]}' & aanvrager.naam & ',' : '{t['aanhef'][1]}'")),
           styles={"marginBottom": "3sp"})
    m.text(m.body, P(t["inleiding"][0], E(date("zaak.registratiedatum")), t["inleiding"][1], E("zaak.zaaktype"),
                     E("$exists(zaak.omschrijving) ? ' (' & zaak.omschrijving & ')' : ''"), t["inleiding"][2]))

    decided = m.conditional(m.body, "$exists(zaak.resultaat)")
    box = m.container(decided, preset="kader")
    m.text(box, H(3, t["besluit_kop"]), preset="kop")
    m.text(box, P(T(t["besluit"], "bold"), E("zaak.resultaat")), styles={"marginBottom": "0sp"})
    undecided = m.conditional(m.body, "$exists(zaak.resultaat)", inverse=True)
    m.text(undecided, H(3, t["stand_kop"]), preset="kop")
    m.text(undecided, P(t["stand"][0], E(or_dash("zaak.status", "'“' & zaak.status & '”'")), t["stand"][1],
                        E(or_dash("zaak.uiterlijkeEinddatumAfdoening", date("zaak.uiterlijkeEinddatumAfdoening"))), t["stand"][2]))
    suspended = m.conditional(m.body, "$exists(zaak.opschortingReden)")
    m.text(suspended, P(t["opgeschort"], E("zaak.opschortingReden"), "."), preset="kader")
    extended = m.conditional(m.body, "$exists(zaak.verlengingReden)")
    m.text(extended, P(t["verlengd"][0], E("zaak.verlengingReden"), t["verlengd"][1],
                       E(or_dash("zaak.uiterlijkeEinddatumAfdoening", date("zaak.uiterlijkeEinddatumAfdoening"))), t["verlengd"][2]),
           preset="kader")

    m.text(m.body, H(3, t["gegevens_kop"]), preset="kop", styles={"keepWithNext": True})
    values = ["zaak.identificatie", or_dash("zaak.zaaktype"), or_dash("zaak.omschrijving"),
              or_dash("zaak.registratiedatum", date("zaak.registratiedatum")), or_dash("zaak.status"), or_dash("zaak.behandelaar"),
              or_dash("zaak.groep"), or_dash("zaak.einddatumGepland", date("zaak.einddatumGepland"))]
    rows = len(values) + 1
    _, cells = m.add("table", {"rows": rows, "columns": 2, "columnWidths": [35, 65], "headerRows": 0, "merges": []},
                     styles={"marginBottom": "4sp", "keepTogether": True},
                     slot_names=[f"cell-{r}-{c}" for r in range(rows) for c in range(2)], parent=m.body)
    values = [E(value) for value in values] + [working_days("zaak.registratiedatum", "zaak.einddatum", t["duur"])]
    for row, (label, value) in enumerate(zip(t["rijen"], values)):
        m.text(cells[f"cell-{row}-0"], P(T(label, "bold")), styles={"marginBottom": "0sp", "paddingTop": "1sp", "paddingBottom": "1sp", "paddingLeft": "1sp"})
        m.text(cells[f"cell-{row}-1"], P(value), styles={"marginBottom": "0sp", "paddingTop": "1sp", "paddingBottom": "1sp", "paddingLeft": "1sp"})

    stated = m.conditional(m.body, "$exists(zaak.eigenschappen)")
    m.text(stated, H(3, t["opgegeven_kop"]), preset="kop", styles={"keepWithNext": True})
    _, table = m.add("datatable", {"expression": X('[$each(zaak.eigenschappen, function($waarde, $naam) { {"naam": $naam, "waarde": $waarde} })]'),
                                   "itemAlias": "eigenschap", "borderStyle": "horizontal", "headerEnabled": True},
                     styles={"marginBottom": "4sp"}, slot_names=["columns"], parent=stated)
    for header, width, expression in ((t["opgegeven"][0], 45, "eigenschap.naam"), (t["opgegeven"][1], 55, "eigenschap.waarde")):
        _, column = m.add("datatable-column", {"header": header, "width": width}, slot_names=["body"], parent=table["columns"])
        m.text(column["body"], P(E(expression)), styles={"marginBottom": "0sp"})

    located = m.conditional(m.body, "$exists(zaak.zaakgeometrie.latitude)")
    m.text(located, P(t["locatie"][0], E("$formatLocaleNumber(zaak.zaakgeometrie.latitude, '0.00000')"), t["locatie"][1],
                      E("$formatLocaleNumber(zaak.zaakgeometrie.longitude, '0.00000')"), t["locatie"][2]))

    steps_column, online_column = m.columns(m.body, [3, 2], gap=18)
    m.text(steps_column, H(3, t["stappen_kop"]), preset="kop")
    deadline = or_dash("zaak.uiterlijkeEinddatumAfdoening", date("zaak.uiterlijkeEinddatumAfdoening"))
    steps = m.add("datalist", {"expression": X(f"$exists(zaak.resultaat) ? {t['stappen_besluit']} : {t['stappen_open'].format(deadline='(' + deadline + ')')}"),
                               "itemAlias": "stap", "listType": "decimal", "bulletStyle": "disc"},
                  styles={"listItemSpacing": "1sp"}, slot_names=["item-template"], parent=steps_column)[1]["item-template"]
    m.text(steps, P(E("stap")), styles={"marginBottom": "0sp"})
    if kanaal == "post":
        m.text(online_column, H(3, t["online_kop"]), preset="kop")
        m.add("qrcode", {"value": X("'https://mijn.voorbeeldstad.example/zaken/' & zaak.identificatie"), "size": "78pt"},
              parent=online_column)
        m.text(online_column, P(t["online"]), preset="klein")
    else:
        m.text(online_column, H(3, t["online_kop"]), preset="kop")
        m.text(online_column, P(T("mijn.voorbeeldstad.example", "underline")), preset="klein")

    appeal = m.conditional(m.body, "$exists(zaak.resultaat)")
    if language == "nl":
        stencil_node, stencil_slots = m.add("stencil", {
            "stencilId": STENCIL, "catalogKey": CATALOG, "version": stencil_version, "paramsAlias": "params",
            "parameterBindings": {"termijnWeken": "6", "bestuursorgaan": '"het college van burgemeester en wethouders"'},
            "parameterSchemaSnapshot": STENCIL_SCHEMA}, slot_names=["children"], parent=appeal)
        bezwaarclausule(m, stencil_slots["children"])
    else:
        m.text(appeal, H(3, "Do you disagree with this decision?"), preset="kop")
        m.text(appeal, P("Then you can lodge an objection with the Mayor and Aldermen within 6 weeks of the date of this "
                         "letter. Include your name and address, the date, our reference ", E("zaak.identificatie"),
                         ", why you disagree, and your signature."), styles={"keepTogether": True})

    closing = m.container(m.body, styles={"keepTogether": True, "marginTop": "4sp"})
    m.text(closing, P(t["groet"][0], BR, t["groet"][1]), styles={"marginBottom": "6sp"})
    m.text(closing, P(E("gebruiker.naam"), BR, E(or_dash("zaak.groep"))))

    m.add("separator", {"thickness": "0.5pt", "width": "100%", "color": "#9aa5b1", "style": "solid"},
          styles={"marginTop": "3sp", "marginBottom": "2sp"}, parent=m.body)
    m.text(m.body, P(T(t["bijlagen_kop"], "bold")), styles={"marginBottom": "0sp"})
    attachments = m.add("datalist", {"expression": X(t["bijlagen"]), "itemAlias": "bijlage", "listType": "bullet",
                                     "bulletStyle": "square"}, slot_names=["item-template"], parent=m.body)[1]["item-template"]
    m.text(attachments, P(E("bijlage")), styles={"marginBottom": "0sp"})

    m.add("pagebreak", parent=m.body)
    m.text(m.body, H(3, t["schets_kop"]), preset="kop")
    m.add("image", {"assetId": sketch, "catalogKey": CATALOG, "alt": t["schets_kop"], "width": "100%", "aspectRatioLocked": True},
          styles={"marginBottom": "2sp"}, parent=m.body)
    m.text(m.body, P(t["schets"]), preset="klein")
    m.text(m.body, H(3, t["termijnen_kop"]), preset="kop", styles={"marginTop": "6sp"})
    loop = m.add("loop", {"expression": X(t["termijnen"]), "itemAlias": "termijn"}, slot_names=["body"], parent=m.body)[1]["body"]
    item = m.container(loop, styles={"borderLeft": "3pt solid #00566b", "paddingLeft": "3sp", "marginBottom": "2sp", "keepTogether": True})
    m.text(item, P(E("$string(termijn_index + 1) & '. ' & termijn.label"), BR,
                   E(date("termijn.datum", "EEEE d MMMM yyyy")), " (",
                   working_days("zaak.registratiedatum", "termijn.datum", t["na_ontvangst"]), ")"), styles={"marginBottom": "0sp"})
    return m.document({"type": "override", "themeId": THEME, "catalogKey": CATALOG})


# --------------------------------------------------------------------------------------------- contract

string, number, iso_date = {"type": "string"}, {"type": "number"}, {"type": "string", "format": "date"}
DATA_MODEL = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["zaak"],
    "properties": {
        "zaak": {
            "type": "object",
            "required": ["identificatie"],
            "properties": {
                **{name: string for name in ["identificatie", "omschrijving", "toelichting", "zaaktype", "status", "resultaat",
                                             "behandelaar", "groep", "communicatiekanaal", "vertrouwelijkheidaanduiding",
                                             "opschortingReden", "verlengingReden"]},
                **{name: iso_date for name in ["registratiedatum", "startdatum", "einddatumGepland",
                                               "uiterlijkeEinddatumAfdoening", "einddatum"]},
                "zaakgeometrie": {"type": "object", "properties": {"type": string, "latitude": number, "longitude": number}},
                "eigenschappen": {"type": "object", "additionalProperties": string},
            },
        },
        "aanvrager": {"type": "object", "properties": {name: string for name in ["naam", "straat", "huisnummer", "postcode", "woonplaats"]}},
        "gebruiker": {"type": "object", "properties": {"naam": string}},
        "taak": {"type": "object", "properties": {"naam": string, "behandelaar": string}},
    },
}
DECIDED = {
    "zaak": {"identificatie": "ZAAK-2026-0000000099", "zaaktype": "Omgevingsvergunning kappen",
             "omschrijving": "Kappen van een eik in de achtertuin", "status": "Afgerond", "resultaat": "Vergunning verleend",
             "behandelaar": "Sanne de Vries", "groep": "Team Vergunningen", "communicatiekanaal": "Post",
             "vertrouwelijkheidaanduiding": "openbaar", "registratiedatum": "2026-09-01", "startdatum": "2026-09-02",
             "einddatumGepland": "2026-09-29", "uiterlijkeEinddatumAfdoening": "2026-10-27", "einddatum": "2026-10-01",
             "zaakgeometrie": {"type": "Point", "latitude": 52.25521, "longitude": 6.16337},
             "eigenschappen": {"Boomsoort": "Zomereik", "Stamomtrek (cm)": "185", "Herplantplicht": "Ja, binnen een jaar"}},
    "aanvrager": {"naam": "J. Voorbeeld", "straat": "Voorbeeldstraat", "huisnummer": "12A", "postcode": "9999 ZZ",
                  "woonplaats": "Voorbeeldstad"},
    "gebruiker": {"naam": "Sanne de Vries"},
}
OPEN = {
    "zaak": {"identificatie": "ZAAK-2026-0000000100", "zaaktype": "Melding openbare ruimte", "status": "In behandeling",
             "groep": "Team Buitenruimte", "registratiedatum": "2026-09-20", "startdatum": "2026-09-21",
             "uiterlijkeEinddatumAfdoening": "2026-11-15", "opschortingReden": "Wij wachten op aanvullende foto's van u.",
             "verlengingReden": "Er is advies nodig van de afdeling Groen."},
    "gebruiker": {"naam": "Sanne de Vries"},
}

THEME_BODY = {
    "id": THEME, "name": "Gemeente Voorbeeldstad", "description": "Huisstijl van de fictieve gemeente Voorbeeldstad.",
    "documentStyles": {"fontFamily": {"slug": "source-sans-3", "catalogKey": "system"}, "fontSize": "10.5pt",
                       "lineHeight": 0.9, "color": "#1f2933"},
    "pageSettings": {"format": "A4", "orientation": "portrait", "margins": {"top": 16, "right": 20, "bottom": 16, "left": 24}},
    "blockStylePresets": {
        "kop": {"label": "Kop", "styles": {"color": "#00566b",
                                            "marginTop": "3sp", "marginBottom": "1sp"}},
        "klein": {"label": "Klein", "styles": {"fontSize": "8.5pt", "color": "#52606d"}},
        "kader": {"label": "Kader", "styles": {"backgroundColor": "#eef6f8", "borderLeft": "3pt solid #00566b",
                                                "paddingTop": "2sp", "paddingBottom": "2sp", "paddingLeft": "3sp",
                                                "paddingRight": "3sp", "marginBottom": "3sp"}},
    },
    "spacingUnit": 4.0,
}

VARIANTS = [
    ("initial", "Nederlands, per post", None, {"system.locale": "nl-NL", f"{CATALOG}.{ATTRIBUTE}": "post"}, "nl", "post"),
    ("digitaal", "Nederlands, digitaal", "Zonder adresblok, voor Mijn Voorbeeldstad",
     {"system.locale": "nl-NL", f"{CATALOG}.{ATTRIBUTE}": "digitaal"}, "nl", "digitaal"),
    ("english", "English, by post", "For applicants who asked for English",
     {"system.locale": "en-GB", f"{CATALOG}.{ATTRIBUTE}": "post"}, "en", "post"),
]


def without_nulls(value):
    """Epistola stores absent node fields as null, so compare without them."""
    if isinstance(value, dict):
        return {key: without_nulls(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [without_nulls(item) for item in value]
    return value


def main():
    sketch_path = f"{OUT}/besluitbrief-situatieschets-{SKETCH_WIDTH}.png"
    if not os.path.exists(sketch_path):
        started = time.time()
        sketch_png = situatieschets(SKETCH_WIDTH)
        open(sketch_path, "wb").write(sketch_png)
        print(f"drew situatieschets {SKETCH_WIDTH}px in {time.time() - started:.1f} s")
    sketch_png = open(sketch_path, "rb").read()

    logo = ensure_image(LOGO_NAME, "voorbeeldstad-logo.svg", LOGO_SVG.encode(), "image/svg+xml")
    sketch = ensure_image(f"{SKETCH_NAME}-{SKETCH_WIDTH}", f"situatieschets-{SKETCH_WIDTH}.png", sketch_png, "image/png")

    status, body = call("POST", f"{CATALOG_PATH}/attributes",
                        {"key": ATTRIBUTE, "displayName": "Kanaal", "allowedValues": ["post", "digitaal"]})
    if not report("define attribute kanaal", status, body) and call("GET", f"{CATALOG_PATH}/attributes/{ATTRIBUTE}")[0] != 200:
        raise SystemExit(1)

    status, body = call("POST", f"{CATALOG_PATH}/themes", THEME_BODY)
    if not report("create theme", status, body):
        require("update theme", *call("PATCH", f"{CATALOG_PATH}/themes/{THEME}", {k: v for k, v in THEME_BODY.items() if k != "id"}))

    stencil_model = Model("bz")
    bezwaarclausule(stencil_model, stencil_model.body)
    stencil_content = stencil_model.document({"type": "inherit"})
    status, body = call("GET", f"{CATALOG_PATH}/stencils/{STENCIL}/versions")
    versions = items_of(body) if status == 200 else []
    published = [v.get("id") or v.get("version") for v in versions if v.get("status") == "published"]
    if published:
        stencil_version = max(published)
        current = call("GET", f"{CATALOG_PATH}/stencils/{STENCIL}/versions/{stencil_version}")[1] or {}
        if without_nulls(current.get("content", {}).get("nodes")) == without_nulls(stencil_content["nodes"]):
            print(f"stencil {STENCIL}: reusing published version {stencil_version}")
        else:
            draft = require("new stencil version", *call("POST", f"{CATALOG_PATH}/stencils/{STENCIL}/versions"))
            stencil_version = draft.get("id") or draft.get("version")
            require(f"update stencil version {stencil_version}", *call("PATCH", f"{CATALOG_PATH}/stencils/{STENCIL}/versions/{stencil_version}",
                                                                       {"content": stencil_content, "parameterSchema": STENCIL_SCHEMA}))
            require(f"publish stencil version {stencil_version}", *call("POST", f"{CATALOG_PATH}/stencils/{STENCIL}/versions/{stencil_version}/publish"))
    else:
        require("create stencil", *call("POST", f"{CATALOG_PATH}/stencils", {
            "id": STENCIL, "name": "Bezwaarclausule", "description": "Hoe de ontvanger bezwaar maakt tegen een besluit.",
            "tags": ["besluit", "rechtsmiddelen"], "content": stencil_content,
            "parameterSchema": STENCIL_SCHEMA}))
        require("publish stencil", *call("POST", f"{CATALOG_PATH}/stencils/{STENCIL}/versions/1/publish"))
        stencil_version = 1

    status, body = call("POST", f"{CATALOG_PATH}/templates", {"id": TEMPLATE, "name": "ZAC Besluitbrief"})
    if not report("create template", status, body):
        if status not in (409, 500) or call("GET", TEMPLATE_PATH)[0] != 200:
            raise SystemExit(1)
        print("  (already exists, updating it)")
    contract = {"dataModel": DATA_MODEL, "dataExamples": [{"id": "besloten", "name": "Besluit genomen", "data": DECIDED},
                                                            {"id": "in-behandeling", "name": "Nog in behandeling", "data": OPEN}]}
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
        model = besluitbrief(language, kanaal, logo, sketch, stencil_version)
        require(f"save draft {variant_id} ({len(model['nodes'])} nodes)",
                *call("PUT", f"{TEMPLATE_PATH}/variants/{variant_id}/draft", {"templateModel": model}))
        require(f"publish {variant_id}", *call("POST", f"{TEMPLATE_PATH}/variants/{variant_id}/draft/publish"))

    if "--no-render" not in sys.argv:
        render("default variant, as ZAC asks", {}, DECIDED, "besluitbrief-nl-post")
        render("English, chosen by attributes", {"attributes": [{"key": "system.locale", "value": "en-GB"},
                                                                 {"key": f"{CATALOG}.{ATTRIBUTE}", "value": "post"}]},
               DECIDED, "besluitbrief-en-post")
        render("digitaal, by variant id", {"variantId": "digitaal"}, OPEN, "besluitbrief-nl-digitaal")


def render(label, selection, data, file_stem):
    status, body = call("POST", f"/tenants/{TENANT}/documents/generate",
                        {"catalogId": CATALOG, "templateId": TEMPLATE, "data": data, "filename": f"{file_stem}.pdf",
                         "correlationId": f"besluitbrief-check-{file_stem}", **selection})
    request_id = require(f"render {label}", status, body)["requestId"]
    deadline = time.time() + 90
    while time.time() < deadline:
        time.sleep(0.5)
        status, job = call("GET", f"/tenants/{TENANT}/documents/jobs/{request_id}")
        item = (job.get("items") or [{}])[0] if status == 200 else {}
        if item.get("status") in ("COMPLETED", "FAILED", "CANCELLED"):
            break
    print(f"  {item.get('status')}: created {item.get('createdAt')} started {item.get('startedAt')} completed {item.get('completedAt')}"
          + (f" error {item.get('errorMessage')}" if item.get("errorMessage") else ""))
    for name in ("startedAt", "completedAt", "createdAt"):
        item[name] = item.get(name) and item[name].replace("Z", "+00:00")
    if item.get("startedAt") and item.get("completedAt"):
        from datetime import datetime
        waited = (datetime.fromisoformat(item["startedAt"]) - datetime.fromisoformat(item["createdAt"])).total_seconds()
        rendered = (datetime.fromisoformat(item["completedAt"]) - datetime.fromisoformat(item["startedAt"])).total_seconds()
        print(f"  waited {waited:.2f} s for a render slot, rendered in {rendered:.2f} s")
    if item.get("status") == "COMPLETED":
        status, pdf = call("GET", f"/tenants/{TENANT}/documents/{item['documentId']}", accept="application/pdf")
        if status == 200:
            open(f"{OUT}/{file_stem}.pdf", "wb").write(pdf)
            print(f"  saved {file_stem}.pdf ({len(pdf) // 1024} KiB)")
        report("  delete it from Epistola", *call("DELETE", f"/tenants/{TENANT}/documents/{item['documentId']}"))


if __name__ == "__main__":
    main()
