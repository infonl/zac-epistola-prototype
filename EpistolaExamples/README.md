# Epistola example templates

Scripts that author placeholder templates in an Epistola catalog, so that ZAC has something to offer under
*Document maken* on a fresh or reset tenant. Each template has one variant, which is what ZAC gets: it asks
Epistola for no variant at all.

| Script | Template | What it is for |
|---|---|---|
| `author_standaardbrief.py` | `zac-standaardbrief` (*ZAC Standaardbrief*) | A plain letter with about every field ZAC sends: the zaak, its initiator's name and address, the signed-in user, three dates as dd-mm-jjjj and a list from the startformulier |
| `author_verplichte_aanvrager.py` | `zac-verplichte-aanvrager` (*ZAC Verplichte aanvrager*) | The same layout with a contract that requires the initiator's name, so a zaak without an initiator shows how ZAC reports data that Epistola refuses |

`template-model-base.json` is the letter's layout; `epistola_api.py` holds the calls both scripts make.

## Running them

From the root of the ZAC checkout, with the Epistola key, tenant and catalog from 1Password:

```bash
op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_standaardbrief.py
op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_verplichte_aanvrager.py
```

Every step should print `HTTP 200` or `201`; `201` on *create template* means the template did not exist yet. Each
script is safe to run again and updates what exists, which is what the test tenant needs after its daily reset.
Pass `--delete` to remove a template again. The scripts print statuses, never secrets.

In ZAC, the templates show up under *Document maken* for every zaaktype whose Epistola card has this catalog.
