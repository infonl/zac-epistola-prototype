# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#
"""The few calls to Epistola's API that the example scripts share. Prints statuses, never secrets.

Reads the same four settings as ZAC: EPISTOLA_CLIENT_MP_REST_URL, EPISTOLA_CLIENT_API_KEY, EPISTOLA_TENANT_ID and
EPISTOLA_CATALOG_ID, which `op run --env-file=./.env.epistola.tpl` fills in from 1Password.
"""
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ["EPISTOLA_CLIENT_MP_REST_URL"].rstrip("/")
KEY = os.environ["EPISTOLA_CLIENT_API_KEY"]
TENANT = os.environ["EPISTOLA_TENANT_ID"]
CATALOG = os.environ["EPISTOLA_CATALOG_ID"]
MEDIA_TYPE = "application/vnd.epistola.v1+json"
HERE = os.path.dirname(os.path.abspath(__file__))


def call(method, path, body=None):
    request = urllib.request.Request(
        BASE + path,
        method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={
            "Authorization": f"ApiKey {KEY}",
            "Accept": f"{MEDIA_TYPE}, application/problem+json",
            "Content-Type": MEDIA_TYPE,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, raw[:300]


def report(step, status, body, ok=(200, 201, 204)):
    detail = "" if status in ok else {k: body.get(k) for k in ("title", "detail")} if isinstance(body, dict) else body
    print(f"{step}: HTTP {status}", detail)
    return status in ok


def require(step, status, body):
    if not report(step, status, body):
        raise SystemExit(1)


def template_path(template_id):
    return f"/tenants/{TENANT}/catalogs/{CATALOG}/templates/{template_id}"


def base_template_model():
    with open(f"{HERE}/template-model-base.json") as file:
        return json.load(file)


def publish_template(template_id, name, data_model, example, template_model):
    """Creates the template, or updates it when it exists, and publishes its one variant."""
    path = template_path(template_id)
    status, body = call("POST", f"/tenants/{TENANT}/catalogs/{CATALOG}/templates", {"id": template_id, "name": name})
    if not report("create template", status, body):
        # Epistola answered 409 for an existing template until September 2026, and 500 since.
        if status not in (409, 500) or call("GET", path)[0] != 200:
            raise SystemExit(1)
        print("  (already exists, updating it)")

    contract = {"dataModel": data_model, "dataExamples": [{"id": "voorbeeld", "name": "Voorbeeld", "data": example}]}
    status, body = call("PATCH", path, contract)
    if status == 409:
        print("  (a breaking contract change: publishing it through a confirmed contract draft)")
        require("create contract draft", *call("POST", f"{path}/contract/draft"))
        require("update contract draft", *call("PATCH", f"{path}/contract/draft", {**contract, "forceUpdate": True}))
        status, body = call("POST", f"{path}/contract/publish", {"confirmed": True})
    require("set data model", status, body)

    status, body = call("GET", path)
    require("read template", status, body)
    variant = body["variants"][0]["id"]
    require("save draft", *call("PUT", f"{path}/variants/{variant}/draft", {"templateModel": template_model}))
    require("publish", *call("POST", f"{path}/variants/{variant}/draft/publish"))


def delete_template_when_asked(template_id):
    if "--delete" in sys.argv:
        report("delete template", *call("DELETE", template_path(template_id)))
        raise SystemExit(0)
