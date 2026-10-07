# Een variant kiezen op kanaal

| | |
|---|---|
| Soort | Verkenning. Gebouwd als extra buiten de Definition of Done (#47, [PR #49](https://github.com/infonl/zac-epistola-prototype/pull/49); op 5 oktober hernoemd naar *Variant*, #53). Geen onderdeel van het testplan (B25) |
| Branch | `explore/epistola-variant-picker`. **Wordt niet in `main` gemerged** |
| Stand | **7 oktober 2026.** Op `main` tot en met `83555f5ff`; daarna uit `main` gehaald (B32). Deze branch is `main` van dat moment |

> **Waarom deze branch bestaat.** Het attribuut `kanaal` is niet van Epistola, niet van ZAC en niet van de
> stakeholders: dit prototype heeft het op 1 oktober zelf bedacht om #47 te kunnen laten zien. Symon besloot op
> 7 oktober (B32) dat het niet in `main` hoort. Hier blijft het staan, voor wie het later wil oppakken.

## Wat er op deze branch wel is en op `main` niet

- In *Document maken* een keuzelijst **Variant** (*Per post*, *Digitaal*) als het template varianten voor meer dan één
  kanaal heeft. Het communicatiekanaal van de zaak stelt een kanaal voor (e-mail, e-formulier, internet en
  medewerkersportaal: digitaal; post, balie en telefoon: post); de behandelaar kan het wijzigen.
- Het endpoint `GET /rest/documentcreation/epistola/create-document/{zaakUuid}/template/{templateId}/varianten` en het
  veld `variant` in de verzoeken om te genereren en een voorbeeld te maken.
- ZAC vraagt Epistola om `<catalog>.kanaal` naast `system.locale`, allebei verplicht. In één taal kiest het kanaal van
  de standaardvariant tussen twee varianten die anders gelijk zouden eindigen.
- De kolom `epistola_document.kanaal`: een nieuwe versie vraagt om hetzelfde kanaal zolang het template dat nog heeft.
- In de beheerkaart van het zaaktype toont een opengeklapt template ook zijn varianten: titel, *Standaard*, en per
  variant de taal, het kanaal en andere attributen.

## Op `main`

ZAC vraagt alleen om een taal (`system.locale`). Heeft een template in die taal meer dan één variant, dan antwoordt
Epistola `409 Ambiguous Variant`; de beheerder zet zo'n template dan uit in de beheerkaart. Een variant kiezen op id of
op andere attributen is #50.

## Templates om het mee te proberen

[`EpistolaExamples/`](../../EpistolaExamples/README.md) maakt de testtemplates met het attribuut `kanaal`: de
besluitbrief, de ontvangstbevestiging en de brief om aanvullende informatie.
