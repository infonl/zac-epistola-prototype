# Wireframes — Epistola

| | |
|---|---|
| Issue | [#15](https://github.com/infonl/zac-epistola-prototype/issues/15) · werkproces B1-K1-W2 |
| Stand | Besproken met de stakeholders en bijgewerkt op 6 oktober 2026 naar wat #3, #7, #8, #30, #47 en #51 tot en met #53 hebben gebouwd. Op 7 oktober zijn de variantkeuze en de taalkeuze eruit gehaald (B32) |
| Raakt | #3 beheerscherm · #5 dialoog · #7 CMMN-poort · #8 foutafhandeling · #51 catalog per zaaktype |
| Fidelity | Laag — structuur, toestanden en labels zijn het onderwerp, visueel ontwerp niet |

Vier schermen, getekend tegen de Angular Material-componenten die ZAC al heeft in plaats van verzonnen.
Het leidende besluit: **de actie, het label en de dialoog verraden niet welke provider er geconfigureerd is** — de
provider is een zaak van de beheerder, dus de zaakkant van de interface blijft het scherm dat mensen al
kennen. De foutmeldingen van de gebouwde versie noemen Epistola wel (scherm 4).

De getekende schermen staan als los te openen HTML-mockups in [`wireframes/`](wireframes/). GitHub toont
die niet inline; download ze of open ze lokaal in een browser. De schermbeschrijvingen hieronder zijn
volledig genoeg om zonder de mockups te bouwen — die zijn er voor de vorm, niet voor de inhoud.

---

## 1 · Admin — Epistola-documenttemplates per zaaktype

> Mockup: [`wireframes/1-admin.html`](wireframes/1-admin.html) ·
> Route: `/admin/parameters` → CMMN-zaaktype → stap *Koppelingen*

Spiegelt `smart-documents-form.component`: een kaart die alleen verschijnt wanneer de provider actief is,
een schuifknop per zaaktype, en een keuzelijst voor de catalog en een voor het informatieobjecttype, elk één keer voor
het hele zaaktype (#51). Daaronder staan de templates van de gekozen catalog als de items van een accordeon: per
template ziet de beheerder het id, kiest hij een eigen documenttype en zet hij het template in of uit. Het zaaktype biedt elk template
aan, behalve wat hij uitzet.

### Opbouw

| Element | Type | Gedrag |
|---|---|---|
| Kaart *Epistola-documenttemplates* | `mat-card` | Alleen bij een CMMN-zaaktype, en volledig afwezig wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is |
| Schuifknop | `mat-slide-toggle` | Schrijft `epistola_ingeschakeld` op het zaaktype. Alles eronder is verborgen zolang hij uit staat. Zonder zichtbare tekst ernaast; de naam *Documenten maken met Epistola* staat als `aria-label` op de schuifknop |
| Catalog | `mat-select` | Verplicht. Biedt de catalogs van de tenant op naam, zonder Epistola's eigen `system`. Staat op de opgeslagen catalog, en zonder die op de catalog van `EPISTOLA_CATALOG_ID` (#51) |
| Documenttype | `mat-select` | Verplicht. Biedt de informatieobjecttypen van het zaaktype, en bepaalt hoe de PDF in Open Zaak wordt geregistreerd (#6). Eén voor het zaaktype: de standaard; een template met een eigen documenttype overschrijft die (`override ?: standaard`) |
| Vertrouwelijkheid | read-only tekst | Afgeleid van het gekozen informatieobjecttype, niet apart in te stellen |
| Templates in deze catalog | `mat-accordion` met één `mat-expansion-panel` per template | Live opgehaald bij Epistola. Het kopje van een item noemt de naam, en als omschrijving het documenttype dat geldt: het eigen documenttype van het template, *Zaaktype-standaard: Besluit* als het er geen heeft, of *Verborgen bij Document maken* als het uit staat. De inhoud van een item toont het template-id. Tot 7 oktober toonde het item ook de talen en eerder de varianten met hun attributen; dat staat nu op de branch `explore/epistola-variant-picker` (B32). Daaronder staat een `mat-select` *Documenttype*, met als eerste keuze *Zaaktype-standaard*, en een schuifknop *Aangeboden bij Document maken*. Is Epistola niet bereikbaar, dan toont het item het id en de naam uit het geheugen van ZAC (#30), met de select en de schuifknop. Heeft de catalog geen templates, dan staat er *Deze catalog heeft geen templates.* |

### Aantekeningen

1. **De kaart is volledig afwezig** wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is. Een beheerder
   ziet nooit twee providerkaarten tegelijk. Bij een BPMN-zaaktype ontbreekt de kaart altijd: #7 beperkt
   Epistola tot CMMN, dus instellen zou daar niets doen.
2. **De schuifknop** schrijft `epistola_ingeschakeld` op het zaaktype. Alles eronder blijft verborgen
   zolang hij uit staat.
3. **Geen groepen, één catalog.** De indeling maakt de templateauteur in Epistola, met catalogs, en de
   beheerder kiest per zaaktype welke catalog (#51).
4. **Geen selectievakje per template.** Bij SmartDocuments levert de provider de boom en vinkt de beheerder aan
   wat beschikbaar is. Bij Epistola is er geen boom om in aan te vinken: elk template in de catalog is beschikbaar,
   tenzij de beheerder het per template uitzet met de schuifknop in zijn accordeonitem. Een template dat de auteur aan
   de catalog toevoegt, staat zonder stap in ZAC in *Document maken* (#51).
5. **Informatieobjecttype per zaaktype**: een `mat-select`, precies zoals SmartDocuments het doet. Dit is wat #6
   nodig heeft om de PDF in Open Zaak te registreren. Opslaan weigert een type dat niet bij het zaaktype hoort. Het is
   de standaard; een template kan er in zijn accordeonitem een eigen naast zetten. Opslaan weigert ook daar een type
   dat niet bij het zaaktype hoort. Een template dat de beheerder uitzet en weer aanzet, houdt zijn eigen documenttype
   zolang de beheerder niet opslaat; opgeslagen wordt alleen een rij voor een template met een eigen documenttype of
   dat uit staat.
6. **Vertrouwelijkheidaanduiding is read-only** en afgeleid van het gekozen informatieobjecttype. Geen
   nieuw idee: zo gedraagt de SmartDocuments-rij zich al.
7. **Opslaan wacht op het zaaktype.** De templateinstellingen worden pas opgeslagen nadat het zaaktype zelf
   is opgeslagen, omdat ze bij die zaaktypeconfiguratie horen. Een ongeldige instelling (geen catalog of geen
   documenttype) houdt de knop *Opslaan* van de hele stap uitgeschakeld. Opslaan toetst de catalog aan de live lijst
   van Epistola.
8. **Geen taal te kiezen** (B32). Tot 7 oktober koos de beheerder hier één taal voor alle templates van het zaaktype
   (#51). Een taal is ook een variant, en ZAC vraagt Epistola nu om geen variant, dus elk template wordt gemaakt in zijn
   standaardvariant ([scherm 3](#3--dialoog--document-genereren)). De keuzelijst *Taal* staat op de branch
   `explore/epistola-variant-picker`.

De documenttype-select en de afgeleide vertrouwelijkheid reproduceren
`smart-documents-form-item.component.html` veld voor veld; het selectievakje niet (aantekening 4).

---

## 2 · Behandelaar — de actie in de zijbalk

> Mockup: [`wireframes/2-behandelaar.html`](wireframes/2-behandelaar.html) ·
> Route: `/zaken/{identificatie}`

Het verschil tussen een CMMN- en een BPMN-zaak naast elkaar — dit is alles wat #7 aan de interface
verandert.

| Zaaktype | Actie *Document maken* | Toelichting |
|---|---|---|
| CMMN | Actief | De bestaande actie, met de bestaande vertaalsleutel `actie.document.maken` |
| BPMN | Zichtbaar maar uitgeschakeld, met tooltip | "Een document maken met Epistola kan voorlopig alleen bij zaken van een CMMN-zaaktype, niet bij zaken die door een BPMN-proces worden gestuurd." |

### Aantekeningen

1. **Het label blijft "Document maken".** ZAC heeft deze actie en deze vertaalsleutel al
   (`actie.document.maken`). Issue #5 formuleert het als "Genereer document", maar een tweede label voor
   dezelfde taak introduceren zou de behandelaar vertellen welke provider er geconfigureerd is — en dat is
   niet hun zorg en niet hun beslissing.
2. **Uitgeschakeld, niet verborgen, voor BPMN.** Een verdwenen actie leest als een rechtenprobleem; een
   uitgeschakelde actie met een tooltip benoemt de werkelijke beperking, en dat is wat #7 in de interface
   vraagt.
3. **De backend weigert het hoe dan ook** met 400 `msg.error.epistola.cmmn-only` — de uitgeschakelde staat is
   een beleefdheid, geen handhaving. Is Epistola niet de actieve provider, dan blijft de actie voor BPMN-zaken
   zoals ze was.
4. **De knop zit in een focusbare omhulling**, zodat de toelichting ook met het toetsenbord en voor een
   schermlezer bereikbaar is. Een uitgeschakelde knop kan geen focus krijgen, en dan was de uitleg onzichtbaar.
   Gebouwd in #7.

---

## 3 · Dialoog — document genereren

> Mockup: [`wireframes/3-dialoog.html`](wireframes/3-dialoog.html) ·
> Component: dialoog vanuit de zaakdetailpagina

Dezelfde velden als de bestaande SmartDocuments-dialoog, met als verschillen: bij Epistola ontbreekt het veld
*Templategroep* (#51), en *Formaat* ligt vast op PDF. Er is geen veld *Taal* en geen veld *Variant*: ZAC vraagt
Epistola om geen variant, dus Epistola maakt de standaardvariant van het template. Tot 7 oktober stond er een veld
*Variant* bij een template met varianten voor twee of meer kanalen (#47, #53), en vroeg ZAC om de taal die de beheerder
voor het zaaktype koos ([scherm 1](#1--admin--epistola-documenttemplates-per-zaaktype)); dat staat nu op de branch
`explore/epistola-variant-picker` (B32).

| Veld | Type | Verplicht | Herkomst |
|---|---|---|---|
| Templategroep | `mat-select` | Ja | De groepen die voor dit zaaktype zijn geconfigureerd. Alleen bij SmartDocuments; bij Epistola ontbreekt het veld (#51) |
| Template | `mat-select` | Ja | Bij Epistola elk template in de catalog van het zaaktype dat aanstaat, op naam. Is er maar één, dan is het gekozen (#51) |
| Titel | tekstveld | Ja | Door de behandelaar in te vullen |
| Toelichting | tekstveld | Nee | Door de behandelaar in te vullen |
| Documenttype | read-only | — | Dat van het gekozen template, en anders dat van het zaaktype (#51) |
| Vertrouwelijkheid | read-only | — | Afgeleid van het documenttype |
| Formaat | read-only, statische tekst "PDF" | — | Vast; er is geen keuze |
| Auteur | read-only | Ja | De ingelogde gebruiker |

Tijdens het genereren blokkeert de dialoog: een voortgangsbalk met de stappen *Voorbereiden*, *In de wachtrij*,
*Maken* en *Opslaan*, en een uitgeschakelde knop *Genereren*. Een regel eronder zegt wat Epistola met de job doet, en
wordt elke seconde ververst: *Het document staat in de wachtrij bij Epistola*, *Epistola maakt het document*, en bij
een lange wachttijd *… langer dan gebruikelijk. ZAC wacht nog even.* (#8).

Kan de dialoog de templates niet laden omdat Epistola niet bereikbaar is, dan staat er *De templates kunnen nu
niet worden geladen*, en niet een lege lijst (#8).

### Aantekeningen

1. **Geen keuze voor het uitvoerformaat.** "Formaat: PDF" staat er als statische tekst en niet als een
   keuzelijst met één optie — een select die de gebruiker niet kan wijzigen is een dode besturing. #5
   vereist alleen PDF.
2. **Documenttype en vertrouwelijkheid zijn read-only**, gevuld uit de configuratie van het zaaktype zodra er een
   template gekozen is. De behandelaar kan een document niet onder het verkeerde informatieobjecttype wegzetten.
3. **Bij Epistola is er geen groep te kiezen.** *Template* biedt meteen de templates van de catalog van het zaaktype
   (#51). Bij SmartDocuments blijft *Template* leeg tot er een groep is gekozen.
4. **Genereren blokkeert de dialoog** in plaats van hem te sluiten, zodat een mislukking in context getoond
   kan worden en niet als een losse toast op de zaakpagina.
5. **Geen variant te kiezen** (B32). ZAC vraagt Epistola om geen taal en geen variant, dus Epistola maakt altijd de
   standaardvariant van het template, ook als het template er meer heeft
   ([ontwerp §5](technisch-functioneel-ontwerp.md#verzoek)). Een andere variant kiezen is het vervolg in #50.

Anders dan bij SmartDocuments is er geen overdracht aan een wizard: Epistola genereert server-side, dus de
flow blijft binnen ZAC en vertrekt nooit naar een externe editor.

---

## 4 · Foutpaden

> Mockup: [`wireframes/4-foutpaden.html`](wireframes/4-foutpaden.html) (tekent de eerste twee meldingen)

Drie mislukkingen die andere woorden nodig hebben, omdat elke een andere oorzaak heeft en de behandelaar iets anders
moet weten of doen. De teksten zijn die van de gebouwde versie (#8), elk met een eigen foutcode. De overige staan in
[§5 van het ontwerp](technisch-functioneel-ontwerp.md#foutafhandeling).

| Situatie | Melding |
|---|---|
| Epistola onbereikbaar | **Epistola is op dit moment niet bereikbaar.** Probeer het later opnieuw. |
| Gegenereerd, opslag mislukt | **Het document is gemaakt, maar het opslaan in Open Zaak is mislukt.** Er is niets aan de zaak toegevoegd. Probeer het later opnieuw. |
| Data breekt het contract van het template | **Het template vraagt zaakgegevens die deze zaak niet heeft, of in een andere vorm.** Opnieuw proberen helpt niet. Geef de melding hieronder door aan de beheerder. *(met Epistola's reden eronder)* |

### Aantekeningen

1. **De tweede melding zegt wat er met het document gebeurd is**, en niet alleen dat er iets misging.
   "Genereren gelukt, opslaan niet" is het geval waar een gebruiker anders geen chocola van kan maken — en
   het is het pad van gedeeltelijke mislukking dat #8 gedefinieerd wil zien.
2. **Nooit een stacktrace.** De melding noemt de oorzaak, en bij een gebroken contract Epistola's eigen
   reden, zodat de behandelaar genoeg heeft om de beheerder te vertellen wat er mis is. Voor het log geldt de regel uit #16: identificaties
   en statussen, nooit de payload.
3. **Fouten verschijnen binnen de dialoog**, die nog openstaat omdat het genereren hem blokkeerde.
4. **De meldingen noemen Epistola wel**, omdat ze het systeem noemen dat niet reageert. Het ontwerpbesluit hieronder
   dat de behandelaar de provider niet hoeft te kennen, geldt voor de *actie* en het *label*: die blijven
   *Document maken*. Dat de foutmeldingen daarvan afwijken, is nog niet met de stakeholders besproken.

---

## Ontwerpbesluiten

### De actie en het label verraden de provider niet

Elk zaakkant-scherm hierboven is het scherm dat ZAC al heeft. Zelfde actie, zelfde label, vrijwel dezelfde
dialoogvelden — alleen de templates erachter verschillen, en bij Epistola heeft de dialoog geen veld *Templategroep*
(#51). De dialoog noemt de provider niet.

Daarom blijft het "Document maken" in plaats van het "Genereer document" uit #5. Welke documentengine een
installatie draait is een beslissing van de beheerder; die in de zaakzijbalk tonen zou elke behandelaar een
onderscheid laten leren dat niets aan hun werk verandert. Het wijkt af van de formulering van het issue,
en is samen met de rest van deze wireframes met de stakeholders besproken.

### Vertrouwelijkheid is al opgelost door een bestaand patroon

Issue #6 vraagt dat `vertrouwelijkheidaanduiding` wordt afgeleid in plaats van standaard op `openbaar` te
staan. De SmartDocuments-beheerrij toont hem al als read-only veld, gestuurd door het gekozen
informatieobjecttype.

Dit vraagt dus geen nieuw ontwerp — de bestaande binding hergebruiken voldoet aan het criterium, en ervan
afwijken zou de risicovollere keuze zijn.

### Uitgeschakeld met een reden is beter dan verborgen

Voor BPMN-zaken blijft de actie zichtbaar en uitgeschakeld, met een tooltip. Verbergen zou niet te
onderscheiden zijn van een ontbrekend recht, en #7 vraagt dat de beperking zichtbaar is in het gedrag dat
de gebruiker ziet — een onzichtbare besturing communiceert niets.

### Een platte lijst, geen boom

Het beheerscherm is een platte lijst. SmartDocuments gebruikt `mat-tree` omdat zijn groepen willekeurig diep nesten.
Epistola heeft geen templategroepen in ZAC: de templateauteur deelt de templates in Epistola in met catalogs, en de
beheerder kiest per zaaktype één catalog (#51). Het beheerscherm heeft daarvoor een keuzelijst, en toont de templates
van de catalog als een accordeon met per template de eigen instellingen. Er hoeft niets te nesten en niets te worden
toegevoegd; ook het selectievakje per template ontbreekt, omdat dat een boom veronderstelt die de provider levert
(scherm 1, aantekening 4).

---

Wireframes voor het Epistola-prototype, getekend tegen `smart-documents-form.component`,
`smart-documents-form-item.component` en de bestaande zaakzijbalkacties op de examenfork. Bewust lage
fidelity: structuur, toestanden en labels zijn het onderwerp, visueel ontwerp niet.
