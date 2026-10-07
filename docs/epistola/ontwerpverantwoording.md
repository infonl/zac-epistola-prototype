# Ontwerpverantwoording — Common Ground, AVG en security

| | |
|---|---|
| Issue | [#16](https://github.com/infonl/zac-epistola-prototype/issues/16) · werkproces B1-K1-W2 |
| Scope | Prototype, alleen CMMN |
| Bouwt op | #14 ontwerp · #2 provider-abstractie |
| Gaat naar | #20 verbetervoorstellen · #10 Confluence |
| Basis | De fork op `aeb888cc2` |
| Stand | Bijgewerkt op 6 oktober 2026, na het stakeholderoverleg van 5 oktober, met contract en client 1.4.0 van Epistola |

Why the Epistola document creation integration is defensible against Common Ground, the AVG and ZAC's own security
practice — and what would still have to change before it could carry real citizen data. [The improvement
proposals](verbetervoorstellen.md) §3 give each risk in §5 its outcome.

## Het oordeel in het kort

| § | Onderwerp | Oordeel | Kort |
|---|---|---|---|
| §1 | Common Ground | Onderbouwd | The integration adds no copy of zaakdata. Case data is read from Open Zaak and person data from the BRP at generation time; the PDF is stored in the registry, not in ZAC. |
| §2 | AVG en privacy | Opgelost in #4 | The BSN never reaches the document provider — it is a lookup key only. The one unbounded payload, the citizen's submitted form, is allow-listed against the template's schema (#4). |
| §3 | Token-afhandeling | Bewuste keuze | A static API key, on a par with BRP and SmartDocuments and weaker than ZGW. A self-signed JWT and OAuth client credentials are recorded as the rejected alternatives; contract 1.3.1 marks both as experimental. |
| §4 | Logging | Niet bevestigd | Cannot be confirmed as asked. ZAC's shipped configuration writes the BSN to the application log at `INFO`, and document creation triggers exactly that call. |
| §5 | Risico's richting productie | Vier opgelost | Nine risks recorded and carried into #20: four resolved, three accepted with a reason, two turned into a proposal. |

## 1. Common Ground

*Acceptatiecriterium 1*

Common Ground asks that data is used at the source rather than copied, that components talk through open APIs, and
that a component can be replaced without taking the data with it. The Epistola integration is measured against those
three, and it holds on all three — with two honest caveats.

### Gegevens bij de bron

Nothing about the case is cached for document creation. Every generation reads the zaak from Open Zaak
(`zrcClientService.readZaak`), the zaaktype and statustype from the catalogus, the initiator's details from the BRP
or the KvK, and the task from Flowable — all at the moment the behandelaar presses the button. A stale document is
therefore impossible by construction, not by cache invalidation.

The datamodel in #14 confirms this from the other side: the integration adds four columns on
`zaaktype_configuration`, a table with the settings per template and the table `epistola_document`, and no table that
holds zaakdata. The generated PDF is an `EnkelvoudigInformatieObject` in Open Zaak's Documenten API, linked through a
`ZaakInformatieObject`; versioning is Open Zaak's `versie` field rather than ZAC state. ZAC stores the configuration,
the registry stores the record.

For a new version of a document (#9), ZAC remembers which template made each Epistola document, in the table
`epistola_document`. It holds the informatieobject, the template and its catalog, the kanaal (#47) and the language
(#52) ZAC asked Epistola for, and no content, title, status or zaak, so the versions themselves are still Open Zaak's
`versie` field. Whether ZAC may keep that table is for the stakeholders to decide if they take the prototype into ZAC.

### API-first

Every external system ZAC talks to has its client generated from an OpenAPI specification under
`src/main/resources/api-specs/` — bag, brp, klanten, kvk, or, pabc, zgw. Epistola is the eighth integration, by a
different route. Epistola publishes a Jakarta EE client generated from its own contract, built for exactly ZAC's stack
and carrying no runtime dependencies, so #4 adopted that rather than generating a client from a specification ZAC
would have to keep in step. The house rule is "generated from the contract, never hand-rolled"; the vendor had already
done the generating. Recorded as a decision on #14.

### Vervangbaarheid

`DOCUMENT_CREATION_PROVIDER` makes the document engine a deployment decision with three values and a startup check
that refuses contradictory combinations. An installation can move from SmartDocuments to Epistola, or run neither,
without a code change.

Stated precisely, though: that abstraction is configuration-level only. There is no shared interface. Each provider
has its own endpoint, which checks its own provider, behind one dialog (`create-document-attended` for
SmartDocuments, `epistola/create-document` for Epistola). The claim is "one provider is selected by configuration",
not "two providers sit behind one interface". With two providers a shared interface is structure bought too early; R7
is accepted with that reason.

> **Twee eerlijke kanttekeningen.** ZAC does maintain a Solr index containing zaakdata, which is duplication in the
> strict Common Ground reading. It is pre-existing, it serves search, and this integration neither extends nor depends
> on it.
>
> And the PDF itself is, by definition, a frozen copy of case data. That is inherent to producing a document — the
> meaningful question is where the copy lives, and the answer is the registry rather than ZAC. The templates, however,
> live in Epistola: a store of a kind ZAC does not own, exactly as SmartDocuments is today.

## 2. AVG, persoonsgegevens en dataminimalisatie

*Acceptatiecriterium 2*

The question "which personal data flows to Epistola" has an exact answer, because the payload is the one
SmartDocuments already receives: `DocumentCreationDataService.createData` builds it, and #4 maps the same fields onto
Epistola's template variables. The table below is that payload, field by field.

| Veld | Bron | Persoonsgegeven | Naar provider | Opmerking |
|---|---|---|---|---|
| burgerservicenummer | Rol op de zaak (ZRC) | Wettelijk identificatienummer (artikel 87 AVG en artikel 46 UAVG), geen bijzondere categorie onder artikel 9 | **Nee** | Used only as the BRP lookup key. It is never placed in `AanvragerData`, so it does not leave ZAC toward the document engine. |
| naam, straat, huisnummer, postcode, woonplaats | BRP (Haal Centraal) | Ja | Ja | The resolved NAW of the initiator. This is the whole of what the provider receives about a natural person. |
| geslacht, geboorte, indicatieCurateleRegister | BRP response | Ja | Nee | Retrieved by the shared BRP client's fixed field set, then discarded. Received but never mapped — see finding P2. |
| bedrijfsgegevens vestiging / rechtspersoon | KvK | Soms | Ja | Company data is not persoonsgegevens, except for an eenmanszaak, where it identifies a person. |
| behandelaar, groep, gebruiker id en naam | ZRC rollen, ZAC-sessie | Ja | Ja | Employee data. Still persoonsgegevens, and still part of what is shared with a third party. |
| identificatie, zaaktype, status, resultaat, data | ZRC / ZTC | Indirect | Ja | The case number is a pseudo-identifier: linkable to a person by anyone holding the registry. |
| omschrijving, toelichting, opschortingReden, verlengingReden | ZRC | Onbegrensd | Ja | Free text written by behandelaars. It can contain anything a colleague typed, including health or family circumstances. |
| startformulier aanvraaggegevens | Objecten API | Onbegrensd | Ja | A flattened map of everything the citizen submitted, **reduced to the template's declared variables** before it is sent (#4) — see finding P1. |
| taaknaam en taakbehandelaar | Flowable | Ja | Ja | Employee data, present only when a document is generated from a task. |

*Source: `DocumentCreationDataService.kt`, the payload builder shared by both providers. Classification is mine; the
field list is the code's.*

### P1 — The submitted form data is forwarded whole · *Opgelost in #4*

`getAanvraaggegevens` flattens the citizen's submitted form into a `Map<String, Any>` and hands it over as
`StartformulierData.data`. The shared builder does not filter it, so on the SmartDocuments path whatever the
formulier collected — a BSN, a telephone number, a free-text motivation, a medical circumstance — is shared with the
document engine whether the chosen template uses it or not.

The Epistola path filters it (#4). The template's JSON Schema is the allow-list: only declared variables are sent, the
filter recurses into nested objects, and it applies even where a schema would permit additional properties. A
template declaring no schema is refused rather than treated as permitting everything. The Epistola path is therefore
*better* on dataminimalisatie than the SmartDocuments path it copies.

One boundary is deliberate and recorded in a test: a template that declares a section as a free-form object
(`type: object`, without `properties`) gives the filter nothing to narrow to, and receives it whole. That is the
template author asking for the whole bag rather than a hole in the filter, and the stakeholders decided on 28
September that it is allowed, as the template author's explicit choice (B17). It is the one route by which unreviewed
startformulier data reaches a document; a warning in the admin card is a proposal (VV-09, #39).

`ProductaanvraagService.kt:122` · `DocumentCreationDataService.kt:166–177`

### P2 — The BRP is asked for more than document creation needs · *Middel*

The shared client requests a fixed field set — burgerservicenummer, geslacht, naam, geboorte, verblijfplaats,
indicatieCurateleRegister — while the document mapping uses only name and address. Three fields, one of them the
curatele indication, are retrieved and thrown away.

Pre-existing and shared with other ZAC features, so not this prototype's to fix unilaterally. It is worth naming: a
per-purpose field set would be the proper minimisation, and the BRP API supports it.

`BrpClientService.kt:59–67`

### Grondslag

Document creation introduces no new processing purpose. Generating a besluit or a brief is part of the
zaakbehandeling the gemeente was already carrying out, so it rests on the same grondslag as the zaak: in practice
article 6(1)(e) AVG, the public task, grounded in whatever sectoral law governs that zaaktype. ZAC does not determine
that grondslag and cannot — it is set per zaaktype by the gemeente.

What *is* new is a recipient. Epistola becomes a party that receives persoonsgegevens, which means it belongs in the
verwerkingsregister before any production use, with its own retention entry.

### Doelbinding en protocollering

One detail deserves credit rather than criticism. The BRP consultation that document creation triggers goes through
the same protocollering path as every other BRP call: when protocollering is enabled, ZAC sends the doelbinding
configured for that zaaktype, the verwerkingsregister entry, the origin OIN and the *acting user*, resolved from the
session rather than from a system account. Purpose limitation is therefore enforced and logged at the source, per
zaaktype and per employee, which is exactly what the BRP's verwerkingenlogging is for.

The open question is whether the doelbinding already configured for a zaaktype covers consultation for document
creation, or whether that is a separate doel. That is a question for the gemeente's privacy officer, not a technical
one.

### Verwerker en verwerkersovereenkomst

**Standpunt.** In a production deployment, Epistola processes persoonsgegevens exclusively on the gemeente's
instruction, for the gemeente's purpose, with no purpose of its own, and it determines neither the means nor the ends
of the processing. On that reading it is a **verwerker** and the gemeente remains verwerkingsverantwoordelijke — the
same relationship the gemeente already has with SmartDocuments. A verwerkersovereenkomst under article 28 AVG is
therefore **required before any production use**, and it has to settle retention: Epistola keeps a
generated document for three to four months, because it stays until its month partition is dropped, after three
months. That is a term the agreement needs to name rather than inherit.

**The prototype needs no verwerkersovereenkomst, because only test data goes to Epistola.** The prototype, its tests
and the final demo run against Epistola's hosted test server (`demo.epistola.app`), so data does leave the local
environment, but what goes there is the zaken in the local test environment and the fictitious persons of the BRP
mock (`ghcr.io/brp-api/personen-mock`). Epistola processes no persoonsgegevens of real people. The condition is the
data, not where Epistola runs. An agreement with Epistola is a production matter, not one for now.

That reasoning holds only while the data is test data. The moment real case data is involved, Epistola becomes a
verwerker and the agreement comes first.

This is a reasoned position, not a legal opinion. It should be confirmed by the gemeente's functionaris
gegevensbescherming — the conclusion is theirs to draw, and the question this document cannot answer is listed
in §6.

## 3. Token-afhandeling en credentials

*Acceptatiecriterium 3*

#2 requires one authentication mechanism to be chosen and the rejected ones recorded as out of scope. **The choice is
the static API key** — `EPISTOLA_CLIENT_API_KEY` alongside `EPISTOLA_TENANT_ID`, both validated at startup.
Epistola's contract offers two alternatives, a self-signed JWT and OAuth 2.0 client credentials. Contract 1.3.1 (21
September) makes API keys the supported method, also for production, and marks both alternatives as experimental, so
both are out of scope and neither is a production path. This section is that record, and the argument for it.

ZAC already contains three credential patterns, which is a more useful comparison than a textbook one:

| Integratie | Mechanisme | Levensduur | Gebruiker herleidbaar | Opslag |
|---|---|---|---|---|
| Open Zaak (ZGW) | JWT per request, HMAC-signed with `ZGW_API_SECRET` | Per request | Ja — `user_id` en `user_representation` als claim | Secret in Kubernetes Secret; token exists only in memory |
| BRP | Statische API key plus protocolleringheaders | Onbeperkt | Ja — via de gebruikersheader | Kubernetes Secret |
| SmartDocuments | Statische Basic-credential, plus een `Username`-header | Onbeperkt | Deels — header, niet ondertekend | Kubernetes Secret |
| Epistola *(nieuw)* | Statische API key, per geregistreerde consumer uitgegeven door een Epistola-beheerder | Met vervaldatum (contract 1.3.1) | Nee — één geregistreerde consumer, geen persoon | Kubernetes Secret |

The ZGW row is the in-house gold standard: a per-request, signed token that names the acting employee, so Open Zaak's
audit trail attributes every write to a person rather than to "ZAC". Epistola sits with BRP and SmartDocuments rather
than with ZGW — a long-lived secret in a Kubernetes Secret, naming a system and not a person. That is the house norm
for a third-party integration, and it is the weakest of the four on attribution.

### Waarom de API key gekozen is

- **It is Epistola's supported method.** Contract 1.3.1 makes API keys the supported method, also for production. Only
  the old `X-API-Key` header is marked deprecated, and the Jakarta client sends the key in `Authorization`, so that
  marking does not touch ZAC.
- **It matches ZAC's existing pattern for a third-party integration.** BRP and SmartDocuments are both a long-lived
  secret in a Kubernetes Secret. An operator deploying Epistola does what they already do, and the deployment story
  needs no new concept.
- **It is the cheapest credential to operate for a prototype.** The JWT path needs a key pair generated, registered
  with an Epistola administrator, approved, mounted and eventually rotated. That is real operational surface bought to
  protect a machine-to-machine call that is already outbound-only from ZAC over TLS — and this prototype exists to
  answer a question about templates and data mapping, not about credential lifecycle.
- **The client implements it natively**, on ZAC's exact stack, and refuses a configuration that supplies both a key
  and a JWT consumer id. OAuth token acquisition it does not implement at all — that would mean ZAC supplying its own
  filter and token cache.
- The key is injected as an environment variable, which in a production deployment comes from
  `charts/zac/templates/secret.yaml` rather than the ConfigMap, so it does not reach git, a values file in a
  repository, or a `kubectl describe` of the deployment.
- Its absence fails at startup with the variable named, rather than at the first generation attempt with a 401 the
  behandelaar cannot interpret.

### Wat het mechanisme feitelijk niet doet

Stated plainly, so that nobody mistakes the prototype for a production posture. The key **has no rotation
procedure** in ZAC — rotating it means issuing a new key at Epistola and redeploying the secret, with no overlap
window, which is a step nobody has written down (VV-01, #35). And it **names no person**: every generation reaches
Epistola as the same registered consumer, so Epistola's own logs cannot tell one behandelaar from another, and
enforcement stays entirely ZAC's (§4 of #14; VV-08). Epistola's own API keys have an expiry date and can be revoked
(contract 1.3.1).

Those two together are R1, which is *Middel* and no longer blocks production. It is a deliberate, recorded trade for
a prototype running against Epistola's test server with synthetic data, not an oversight — but the trade only holds
while the data stays synthetic.

`EPISTOLA_TENANT_ID` is not a secret but it is an access scope: startup rejects anything that is not a valid tenant
slug, which closes the typo case. An API key belongs to one tenant, so any other tenant id answers `403` on every
call (checked against three other tenants). R8 is resolved.

### Wat productie zou vereisen

- Per-request user attribution, modelled on the ZGW JWT: a claim naming the acting employee so that Epistola's audit
  trail matches Open Zaak's. No mechanism Epistola offers provides this today, which is why enforcement remains ZAC's
  responsibility alone. *VV-08, dependent on Epistola.*
- A documented rotation procedure for the key, with an owner and an interval. *VV-01, #35.*
- TLS enforced rather than assumed — `EPISTOLA_CLIENT_MP_REST_URL` validated as `https` at startup, next to the
  existing presence and format checks. *VV-03, #36.*
- A startup connectivity check that resolves the tenant and logs its name, so a well-formed but wrong tenant id is
  visible at boot rather than in a document. *Optional, because R8 is resolved; VV-07.*

Helm chart support is **done**: the credential is a Kubernetes Secret and the remaining settings are ConfigMap
entries, documented in the chart README (#2).

> **Wat het Epistola-ontwerp juist weghaalt.** The SmartDocuments flow is an attended wizard in a second browser tab,
> and it costs four things that the Epistola design does not need. Because Epistola generates server-to-server and
> returns the PDF inside the authenticated request, the integration *removes*:
>
> - an unauthenticated callback endpoint, excluded from the authorization filter (`RequestAuthorizationFilter.kt:79`);
> - a bearer-equivalent token carried in a URL query string, where proxies, access logs and browser history record it
>   (`DocumentCreationService.kt:147`);
> - `skipPolicyCheck = true` on the storage path, because the ZAC session may have expired by the time the wizard
>   finishes (`DocumentCreationService.kt:93`);
> - a 60-minute in-memory user store, capped at 1000 entries with LRU eviction and lost on restart
>   (`DocumentCreationUserStore.kt:30–39`).
>
> That is a genuine security improvement, and it is the strongest architectural argument in favour of Epistola that
> this prototype has produced. It should be stated in the comparison for #10.

## 4. Logging

*Acceptatiecriterium 4*

The criterion asks for confirmation that BSN and other PII never reach logs in plaintext. **That confirmation cannot
be given.** The opposite is true in ZAC's shipped configuration, and document creation is one of the paths that
triggers it.

### L1 — The BSN reaches the application log at `INFO` · *Blokkerend*

`BrpClientService` logs both the request and the response of every BRP call at a configurable level. The request
object is a generated model whose `toString()` prints burgerservicenummer; the response prints name, birth data and
address. The code's own default is `OFF` — but `.env.example` and `charts/zac/values.yaml` both ship `INFO`.

Generating a document for a case whose initiator is a natural person calls
`brpClientService.retrievePersoon(bsn, zaaktypeUuid, userName)`. So on a deployment that follows the chart defaults,
every generated brief writes that citizen's BSN and address into the application log, where it is retained by
whatever log pipeline the municipality runs.

Pre-existing and not introduced by this integration — but document creation exercises it, which is precisely why this
issue asked. **Fix:** set `brpApi.logLevel` to `OFF` in production values and treat `INFO` as a development-only
setting. Longer term the log statement should print the query type and field set rather than the query object.

`BrpClientService.kt:118–133` · `RaadpleegMetBurgerservicenummer.java:103` · `.env.example:74` ·
`charts/zac/values.yaml:100`

### L2 — The document payload itself is not logged · *Akkoord*

`SmartDocumentsService` logs the username at `FINE` and the response — a wizard ticket — at `FINE`. The `Deposit`
carrying the NAW and the form data is never logged. That is the right behaviour and the rule Epistola's client must
follow: log a correlation id, the template id, the zaak identificatie and the HTTP status; never the request payload
and never the response body.

`SmartDocumentsService.kt:92, 105`

### L3 — Document titles travel through a third party in a URL · *Middel*

The SmartDocuments callback URL carries `title`, `description`, `userName` and `creationDate` as query parameters,
which means they pass through SmartDocuments and return in a URL that reverse proxies and access logs record.
Document titles routinely name the citizen — "Besluit inzake ...".

The Epistola flow has no such round trip. The rule to carry forward is narrow and worth writing down: never put
case-identifying text in a URL.

`DocumentCreationService.kt:157–176`

Cross-reference to #8: its criterion "logs actionable details without leaking sensitive personal case data in plain
text" is satisfiable for the Epistola error path under the L2 rule. L1 sits outside #8 — it is a deployment
configuration finding, and it belongs in the risk register rather than in an error-handling story.

## 5. Risico's die productie in de weg staan

*Acceptatiecriterium 5 · gaat naar #20*

The last column is the outcome of each risk, as [the improvement proposals](verbetervoorstellen.md) §3 record it.

| Id | Risico | Ernst | Wat productie vereist | Stand |
|---|---|---|---|---|
| R1 | A static credential. Epistola's API key has an expiry date and can be revoked (contract 1.3.1), but ZAC has no rotation procedure for it, and Epistola sees one registered consumer, not the behandelaar. | Middel | A rotation procedure for the key, plus a signed claim naming the acting employee modelled on ZAC's ZGW JWT. Until then, enforcement stays ZAC's responsibility and Epistola's log attributes to "ZAC". | **Voorstel**, *Middel*, no longer blocking: the API key is Epistola's supported method. A rotation procedure (VV-01, #35) and attribution per employee (VV-08) |
| R2 | Epistola settings could not be delivered the way every other ZAC secret is, because the chart had no Epistola entries. **Opgelost in #2.** | Opgelost | Nothing further. `EPISTOLA_CLIENT_API_KEY` is a Kubernetes Secret; URL, tenant and generation timeout are ConfigMap entries, documented in the chart README. | **Opgelost** |
| R3 | BRP request and response logged at `INFO` by shipped defaults, putting the BSN and address in the application log on every generation for a natural person. | Blokkerend | `brpApi.logLevel: OFF` in production values; log statement rewritten to print query type rather than query object. | **Geaccepteerd** voor dit project: logging of BRP traffic is handled by another team |
| R4 | No verwerkersovereenkomst with Epistola. **Niet van toepassing op het prototype**: only test data goes to Epistola (§2). Epistola keeps a document for three to four months. | Blokkerend | Article 28 agreement naming the retention, sub-processors and location, plus a verwerkingsregister entry. Confirmation by the FG. Applies the moment real case data is involved. | **Geaccepteerd voor het prototype**, **voorstel** VV-02 voor productie |
| R5 | The citizen's submitted form data is forwarded to the provider as an unfiltered map (P1). **Opgelost in #4.** | Laag | Done: the payload is allow-listed against the template's JSON Schema, recursively. A template declaring a section as a free-form object receives it whole; that is the template author's call (B17). | **Opgelost**; the free-form section was allowed on 28 September (B17), a warning is VV-09 (#39) |
| R6 | `vertrouwelijkheidaanduiding` is hardcoded to `OPENBAAR` on the generated document, so a besluit containing someone's NAW is filed as public. | Hoog | Derive it from the zaak or the informatieobjecttype. An acceptance criterion on #6; recorded here because it is a privacy defect, not only a data defect. | **Opgelost** for Epistola in #6 (from the informatieobjecttype); **voorstel** VV-11 for SmartDocuments, which sets `OPENBAAR` |
| R7 | The provider abstraction is configuration-level only: there is no shared interface, and each provider has its own endpoint that checks its own provider. | Middel | A provider interface with both implementations behind it, which is worth building with a third provider. | **Geaccepteerd**: in #5 each provider got its own endpoint behind one dialog; a shared interface is too early with two providers |
| R8 | A wrong `EPISTOLA_TENANT_ID` could point an installation at another tenant's templates. Startup rejects anything that is not a valid tenant slug (#4). | Laag | Optional: a startup connectivity check that resolves the tenant and logs its name, so a well-formed but wrong identifier is visible at boot rather than in a document. | **Opgelost**: a key belongs to one tenant, so another tenant id gives `403`. Optional remainder VV-07 |
| R9 | Transport security for the Epistola endpoint is assumed rather than enforced. | Laag | Validate that `EPISTOLA_CLIENT_MP_REST_URL` uses `https`, alongside the existing presence check. | **Voorstel** VV-03 (#36) |

R3 and R4 are marked *Blokkerend* for production use with real citizen data, and of the two only R3 is an outright
defect. R3 is handled by another team, and R4 does not arise while only test data goes to Epistola. R2, R5 and R8 are
closed, and R6 is closed for Epistola. R1 is *Middel*, and R7 and R9 are hardening.

What still stands between the prototype and production is in §1 of the improvement proposals: the agreements with
Epistola and the organisation (VV-02), key management (VV-01) and `https` for the Epistola URL (VV-03).

## 6. Beantwoord in het overleg, en wat nog open staat

*Na het overleg · #21*

### Beantwoord op 21 september

**Does Epistola retain the request payload, the generated PDF, or both — and for how long?**
A generated document stays for three to four months (§2). For production it is a term the verwerkersovereenkomst has
to name.

**Is a test tenant with non-production templates available for the prototype and the demo?**
Yes. The prototype and the final demo run against Epistola's hosted test server (`demo.epistola.app`), with a tenant of
its own that holds only test templates, so no production tenant is involved. What keeps the §2 position standing is
that only test data goes there.

**Who holds the agreement with Epistola — Dimpact centrally, or each gemeente separately?**
For the prototype, neither: no persoonsgegevens of real people are processed (§2). Who holds a production agreement
is a production question.

### Nog open

**Does the doelbinding already configured per zaaktype cover BRP consultation for document creation, or is that a
separate doel?** A question for the privacy officer, and not blocking. If it is separate, the zaaktype BRP parameters
need a third value alongside zoekWaarde and raadpleegWaarde. It is a question for when the stakeholders want to use
the prototype; it was not put on the agenda of 5 October.

### Voorgestelde wijzigingen op het bord

- **#2** — *done.* The authentication criterion is met by `EPISTOLA_CLIENT_API_KEY`, with the JWT and OAuth
  alternatives recorded as out of scope in §3. The Helm chart entries (R2) are delivered.
- **#4** — *done.* The criterion was added and built: the payload sent to Epistola contains only variables declared by
  the selected template's JSON Schema (R5).
- **#8** — add the L2 logging rule verbatim as a criterion, so the error handler is written against it rather than
  reviewed against it.
- **#20** — R1 through R9 transfer, each with its outcome (R2, R5 and R8 closed, R1 on *Middel*).

---

**Verantwoording.** Every claim in this document was checked against the fork at commit `aeb888cc2` rather than
against the documentation. The files read, with the lines cited: `zac/documentcreation/DocumentCreationDataService.kt`,
`zac/documentcreation/DocumentCreationService.kt`, `zac/documentcreation/DocumentCreationUserStore.kt`,
`zac/app/documentcreation/DocumentCreationRestService.kt`, `zac/configuration/DocumentCreationProviderConfiguration.kt`,
`zac/smartdocuments/SmartDocumentsService.kt`, `zac/authentication/RequestAuthorizationFilter.kt`,
`client/brp/BrpClientService.kt`, `client/zgw/util/ZgwJwtTokenUtils.kt`,
`zac/productaanvraag/ProductaanvraagService.kt`, `charts/zac/templates/secret.yaml`, `charts/zac/values.yaml` and
`.env.example`.
