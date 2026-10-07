# Uitgangspunten, eisen en wensen — Epistola-integratie in ZAC

| | |
|---|---|
| Issue | [#13](https://github.com/infonl/zac-epistola-prototype/issues/13) · werkproces B1-K1-W1 · op te leveren product *Document uitgangspunten, eisen en wensen* |
| Kandidaat | Symon Vleeshouwers (198462), ZWSD24 |
| Opdrachtgever | Team Geneva · Hanneke van de Horst |
| Periode | 9 september – 9 oktober 2026 |
| Bronnen | Examenafspraken v5 (getekend) · projectbeschrijving *PRJ-Epistola integration in ZAC* |
| Stand | Afgestemd met de stakeholders op 21 september 2026, met de feedback verwerkt. Bijgewerkt op 6 oktober 2026, na het stakeholderoverleg van 5 oktober |

What the client wants from the Epistola integration, what counts as done, what falls outside a user story, and in
which order it gets built.

## 1. Projectdoel

*Criterium 1*

ZAC supports document creation through SmartDocuments today. The goal of this project is **a configurable
integration with Epistola for generating and storing documents automatically within CMMN zaken**. ZAC must be
configurable through environment variables for SmartDocuments, Epistola, or no document creation at all — mutually
exclusive.

What the client gets from that is optionality. A municipality running ZAC is currently tied to one document engine;
after this prototype the engine is a deployment decision rather than a code dependency, and Dimpact can judge
Epistola on evidence — a working integration, a test report and a recorded list of limitations — instead of on a
proposal.

The deliverable is explicitly a *prototype*. It exists to inform a production decision, not to be that production
system. §5 records what that distinction costs.

### Afbakening

**Binnen scope:** CMMN zaken; PDF as the only output format; configuration per zaaktype of the Epistola catalog whose
templates the zaaktype offers, the language of its documents, and the document type they are stored under, with an own
type per template and the option to switch a template off (#51); generation, storage in Open Zaak and linking to the
zaak; a new version of a generated document (the optional DoD 11, #9); the admin and behandelaar frontends;
authorisation; error handling; test execution and knowledge transfer.

**Buiten scope:** BPMN zaken; output formats other than PDF; batch generation; migrating existing SmartDocuments
templates; and production hardening of the credential model.

## 2. Definition of Done

*Criterium 4*

The eleven requirements from the examenafspraken, restated and traced to the board. Where the wording of the two
source documents differs, the signed examenafspraken is leading — the projectbeschrijving lists ten because it folds
the template-groups requirement into the per-zaaktype one.

| # | Requirement | Issue | Status |
|---|---|---|---|
| 1 | Configurable through environment variables with three values — SmartDocuments, Epistola, or none. Both at once is impossible. Documented, validated, and does not break the existing SmartDocuments flow. | #2 | Done |
| 2 | The prototype supports CMMN zaken only; the limitation is explicitly visible in documentation and in the user interface. | #7 | Done |
| 3 | Epistola integration is configurable per zaaktype — which templates are available for zaken of that type. | #3 | Done |
| 4 | An authorised user can generate a PDF from a CMMN zaak. Other formats are not supported. | #5, #11 | Done |
| 5 | The generated document contains zaakspecifieke data per the chosen template. At minimum the same zaakdata fields as SmartDocuments. | #4 | Done |
| 6 | ZAC stores the generated PDF in Open Zaak and shows a clear error message when storage fails. | #6, #8 | Done |
| 7 | ZAC links the stored document to the zaak; the zaakdetailpagina shows metadata and a preview. | #6 | Done |
| 8 | The agreed test cases — configuratie, autorisatie, creatie, datamapping, Open Zaak-opslag, zaakkoppeling, preview, foutafhandeling — are executed and recorded, including known limitations. | #18, #19 | Done (test report in [PR #41](https://github.com/infonl/zac-epistola-prototype/pull/41)) |
| 9 | The beheerder can set the available Epistola **templategroepen and templates** per zaaktype. See the note below. | #3 | Done |
| 10 | Main lessons learned, recommendations and follow-up steps documented on the Confluence wiki page. | #10 | In review (the page exists; the final demo is on Monday 12 October) |
| 11 | *Optional:* a new version of a document previously created through Epistola can be created and displayed per the document-detail requirements. | #9 | Done (built in [PR #43](https://github.com/infonl/zac-epistola-prototype/pull/43)) |

> **Besluit op DoD-item 9 — 5 oktober (B26, #51).** "Templategroepen" assumes a group structure, and Epistola has none.
> Its published contract models templates as flat within a tenant, with *variants* selected by *attributes* — the same
> letter in another form — and *catalogs* as installable packages. The stakeholders decided that the catalog is the
> grouping: ZAC keeps no template groups for Epistola, and each zaaktype chooses one Epistola catalog and offers every
> template in it. For **DoD 9**, the catalog takes the place of the template group, and the templates are those in the
> catalog. For **DoD 3**, the templates available for a zaaktype are those of its catalog; the beheerder chooses the
> catalog, not the templates. The document type is set for the zaaktype, next to the catalog; a template can have an own
> type and can be switched off.

## 3. Doelen

*Criterium 2*

**Doel van de opdrachtgever.** An informed decision about adopting Epistola in production, supported by a working
integration, an executed test plan, and an honest record of what the prototype does not do.

**Doel van het team.** A change that Team Geneva can review and reason about: it follows ZAC's existing conventions,
it leaves the SmartDocuments flow untouched, and it does not commit the product to a provider.

**Persoonlijk doel.** Learning to build a genuinely working prototype inside an existing production codebase rather
than a greenfield one — and orchestrating more of that work with AI, deliberately and verifiably.

> **Wat "verifieerbaar" hier betekent.** The second personal goal carries an obligation, and it is worth stating as a
> project principle rather than an aspiration: **anything produced with AI assistance is checked against the
> codebase or the running system before it is called done.** The project has already produced two cases where that
> mattered — a design built on an assumed synchronous API that the published contract contradicted, and a CDI
> defect that 2510 passing unit tests could not see but a container boot did. Both are recorded rather than quietly
> corrected.

## 4. Eisen buiten de user stories

*Criterium 2*

Requirements that hold across every story, and would be invisible if only the stories were read.

### Compatibiliteit

The hardest constraint in the whole project, and the first DoD item: **the existing SmartDocuments flow must keep
working unchanged.** Every installation that runs ZAC today does so without `DOCUMENT_CREATION_PROVIDER` set, so
absence of the setting must behave exactly as before. This is why the provider is derived from the old flag when the
new one is missing.

### Privacy (AVG)

- The BSN is a lookup key for the BRP and must never be sent on to the document provider.
- Only variables the selected template declares may be sent — the submitted form data is not forwarded wholesale.
- No BSN or other directly identifying data in application logs in plain text.
- Epistola acts as verwerker; a verwerkersovereenkomst is required before any production use with real
  persoonsgegevens.

### Security

- Authorisation is enforced server-side on the generation endpoint, not by hiding a button.
- Credentials are stored as Kubernetes Secrets, never in a values file or in git. Epistola is reached with a static
  API key, which is Epistola's supported method, also for production. ZAC has no written rotation procedure for it
  (#16 §3, #20 R1, VV-01, #35).
- No unauthenticated inbound endpoints are added; no case-identifying data in URLs.
- Transport to Epistola is HTTPS: assumed, not enforced. ZAC checks at startup that the URL is set and well-formed, but
  not that it starts with `https` (R9, VV-03, #36).

### Common Ground

- Zaakdata is read from Open Zaak at generation time; no copy is kept in ZAC.
- The generated PDF lives in the registry, not in ZAC's database. For a new version of a document (#9), ZAC remembers
  which template made each Epistola document, with its catalog and the kanaal and language ZAC asked for, in the table
  `epistola_document`. It holds no content, title, status or zaak; see the [datamodel](datamodel.md).
- The integration is API-first and the provider is replaceable by configuration.

### Kwaliteit

- ZAC's linters and formatters pass: `spotlessApply`, `detekt`, ESLint.
- New behaviour is covered by unit tests, and anything touching startup or CDI wiring by integration tests — unit
  tests construct beans directly and cannot see a deployment failure.
- Work happens on feature branches with atomic commits and pull requests; at least one onderlinge codereview is
  recorded on a pull request.

### Performance

Document generation is user-facing: a behandelaar waits for it. Epistola generates asynchronously, so ZAC polls the
job with a bounded timeout and reports a retryable error rather than holding a request thread indefinitely.
Throughput is explicitly not a goal — one user, one document.

## 5. Technieken, frameworks en codeconventies

*Criterium 3*

> **Correctie op de examenafspraken.** The examenafspraken describe the tooling as "ZAC stack (Python/Django,
> frontend framework, Docker)". That is not the stack. ZAC's backend is **Kotlin on Jakarta EE, running on WildFly**,
> with an **Angular** frontend. Nothing in the repository is Python or Django. Recorded here so the correction is
> agreed rather than silently assumed.

| Laag | Techniek | Aantekening |
|---|---|---|
| Backend | Kotlin, Jakarta EE, WildFly (bootable JAR), JDK 25 | Java that is touched gets converted to Kotlin |
| Dependency injection | Weld CDI, constructor injection | Not field injection |
| Frontend | Angular, TypeScript strict, TanStack Query | Jest + Testing Library, accessibility-first selectors |
| Persistentie | PostgreSQL, Flyway migraties | The configuration of the integration, and which template made each Epistola document (`epistola_document`, #9) |
| Workflow | Flowable — CMMN in scope, BPMN out | |
| Autorisatie | Keycloak (OIDC) + Open Policy Agent | Rego policies, reusing `creeren_document` |
| Integraties | Open Zaak (ZGW), BRP, KvK, Objecten, Epistola | Clients generated from OpenAPI, or the vendor's own |
| Build & test | Gradle, Kotest, TestContainers, Playwright | Node 24 for the frontend |
| Deploy | Docker, Helm charts onder `charts/` | Secrets as Kubernetes Secrets |

### Codeconventies

ZAC's own conventions apply unchanged; they are recorded in `CONTRIBUTING.md` and `CLAUDE.md` in the repository. The
ones that bite most often here: SPDX licence headers on every source file; Kotlin expression bodies and named
parameters; narrow exception catches rather than `catch (Exception)`; boolean properties prefixed `is`/`has`; Kotest
`given`/`when`/`then` with `checkUnnecessaryStub()`; tests that document behaviour through their names instead of
comments; and Conventional Commits.

One deliberate deviation from the repository's documented convention: commit footers reference
`infonl/zac-epistola-prototype#N` rather than a Jira ticket, because the project description names GitHub Projects
as the issue tracker and says explicitly "so not JIRA".

## 6. Prioritering — MoSCoW

*Criterium 7*

The board carries a P0/P1/P2 field; MoSCoW is expressed alongside it rather than replacing it. The mapping was part
of the stakeholder review of this document.

| MoSCoW | Issues | Reden |
|---|---|---|
| Must have | #12, #13, #14, #15, #16, #2, #3, #4, #5, #6, #7, #8, #11, #18, #19, #10, #20, #21, #22, #23 | Every DoD item except the optional one, plus the four named deliverables and the B1-K2 activities. Nothing here can be dropped without failing a requirement. |
| Should have | #17 | Code quality, version control and the onderlinge codereview. Assessed, and the practice matters, but the prototype would still function without the review having happened. |
| Could have | #9 | New version of an Epistola document — the DoD itself marks it optional. Built in PR #43. |
| Won't have | — | BPMN support; batch generation; the collect-based background collector; migration of existing SmartDocuments templates. All recorded as verbetervoorstellen in #20. |

Two extras beyond the DoD are not in the rows above, because they are no requirement: #44, the progress of a generation
shown as steps ([PR #46](https://github.com/infonl/zac-epistola-prototype/pull/46)), and #47, a template's variant
chosen by kanaal ([PR #49](https://github.com/infonl/zac-epistola-prototype/pull/49)). By decision B25 of 1 October they
get no test case in [the test plan](testplan.md#versie); their evidence is the unit and integration tests and the live
checks in their PRs.

Board: the 22 planned issues (#2 to #23, 70 estimate points) across werkprocessen B1-K1-W1 through W5 and B1-K2-W1
through W3, plus follow-up items, such as #30 and #31 from the overleg of 28 September and #35 to #40 from the
improvement proposals (#20).

> **Twee tegenstrijdigheden tussen bord en DoD.**
>
> **#7 is P2 but DoD item 2 is a Must.** The requirement that the CMMN-only limitation is visible in documentation
> *and* in the user interface is not optional, and a P2 priority invites it to be dropped at the end of a tight
> schedule. Either the priority rises or the DoD item is not met.
>
> **#8 is P1 but part of DoD item 6.** "A clear error message when storage fails" is inside the same requirement as
> storing the document at all. The error handling cannot be deferred past #6 without leaving item 6 half-met.
>
> **Decided at the overleg of 21 September:** the priorities stay as they are (see #21). Both issues are still needed
> to meet DoD items 2 and 6, and both are built and merged (#7 in PR #33, #8 in PR #32).

## 7. Werkwijze

- **Light-weight Kanban** on a GitHub Project, with Backlog / Ready / In progress / In review / Done and the fields
  priority, size, estimate and werkproces.
- **Weekly demo** to the available stakeholders, showing progress and inviting feedback; a final demo of the working
  prototype to all stakeholders and assessors (#22).
- **Weekly overleg** with Team Geneva and Hanneke van de Horst on progress, acceptance criteria and blockers (#21).
  Open questions accumulate on the issues and are taken there. The overleg took place on 21 and 28 September and on
  Monday 5 October, the last one; the weeks of 9 and 14 September had none. The final demo is on Monday 12 October.
- **Retrospective** at the end, with the notulen as a deliverable (#23).
- **Verification runs locally** — unit tests for every change, integration tests for anything touching startup or
  CDI. The fork's CI is deliberately disabled: without the upstream secrets its jobs fail for reasons unrelated to
  the code, and a red check that means nothing is worse than no check.

## 8. Afstemming met de stakeholders

*Criteria 5 en 6*

The two criteria on #13 that writing alone could not meet have both been met. The user stories on the board were
presented at the overleg of 21 September, with their intent, scope and acceptance criteria, and this document was
reviewed with the stakeholders and their feedback processed.

Where the overleggen of 21 September and 5 October changed the plan, the outcome sits where it applies: the catalog a
zaaktype chooses is the grouping of its templates (§2), and the board priorities of #7 and #8 stay as they are (§6).
All decisions of the overleggen are kept together on #21.

---

Sources: *SD Examenafspraken v5 — signed* (projectdoel, DoD, werkprocessen, afnamecondities) and *PRJ-Epistola
integration in ZAC — detailed description* (overall plan, tooling, process). Technical statements are grounded in the
examenfork and in [epistola-app/epistola-contract](https://github.com/epistola-app/epistola-contract).
