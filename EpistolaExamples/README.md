# Epistola example templates

Scripts that author ZAC's test templates in an Epistola catalog. Their variants carry the catalog attribute `kanaal`
(`post`/`digitaal`) next to `system.locale`, which is what the kanaal/variant picker on this branch
(`explore/epistola-variant-picker`) chooses by. ZAC's `main` asks Epistola only for a language (decision B32,
7 October 2026), so a template here with two variants in one language gets `409 Ambiguous Variant` from `main`.

| Script | Template | Variants |
|---|---|---|
| `author_besluitbrief.py` | `zac-besluitbrief` | Dutch by post (default), Dutch digital, English. Also makes the attribute `kanaal`, the theme `voorbeeldstad`, the stencil `bezwaarclausule` and two images |
| `author_ontvangstbevestiging.py` | `zac-ontvangstbevestiging` | Dutch digital (default), Dutch by post, English digital, German digital |
| `author_aanvullende_informatie.py` | `zac-aanvullende-informatie` | Five variants that also differ by `weergave` and `taalniveau`, which ZAC cannot choose (#50), so Dutch by post and Dutch digital tie even on this branch |

## Running them

From the root of the ZAC checkout, with the Epistola key, tenant and catalog from 1Password:

```bash
op run --env-file=./.env.epistola.tpl -- python3 EpistolaExamples/author_besluitbrief.py
```

Run `author_besluitbrief.py` first: the other two import it and reuse its logo, theme and `kanaal` attribute. Each
script is safe to run again and reuses or updates what exists, which is what the test tenant needs after its daily
reset. Each renders every variant once, saves the PDFs next to the script (or in `BESLUITBRIEF_OUT`,
`ONTVANGSTBEVESTIGING_OUT`, `AANVULLENDE_INFORMATIE_OUT`) and deletes them from Epistola; `--no-render` skips that.
They print statuses and timings, never secrets. Rendered PDFs and the cached situation sketch are ignored by git.

In ZAC, the templates show up for every zaaktype whose Epistola card has this catalog.
