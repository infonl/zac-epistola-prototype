# Wireframes — Epistola

| | |
|---|---|
| Issue | [#15](https://github.com/infonl/zac-epistola-prototype/issues/15) · werkproces B1-K1-W2 |
| Stand | Besproken met de stakeholders en bijgewerkt op 6 oktober 2026 naar wat #3, #7, #8, #30, #47 en #51 tot en met #53 hebben gebouwd. Op 7 oktober zijn de variantkeuze en de taalkeuze eruit gehaald (B32). Op 12 oktober 2026 nagelopen tegen `main`: het zijpaneel, *Voorbeeld bekijken* (#54), de voortgang in stappen en *Nieuwe versie genereren* (#9) |
| Raakt | #3 beheerscherm · #5 Document maken · #7 CMMN-poort · #8 foutafhandeling · #9 nieuwe versie · #51 catalog per zaaktype · #54 voorbeeld |
| Fidelity | Laag — structuur, toestanden en labels zijn het onderwerp, visueel ontwerp niet |

Vijf schermen, getekend tegen de Angular Material-componenten die ZAC al heeft in plaats van verzonnen.
Het leidende besluit: **de actie en het label verraden niet welke provider er geconfigureerd is** — de
provider is een zaak van de beheerder, dus de zaakkant van de interface blijft het scherm dat mensen al
kennen. De voortgangsregels en de foutmeldingen noemen Epistola wel (scherm 3 en 4), omdat ze zeggen welk systeem
bezig is of niet reageert.

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
| Kaart *Epistola-documenttemplates* | `mat-card` | Alleen bij een CMMN-zaaktype, en volledig afwezig wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is. Onder de titel: *Kies de Epistola-catalog waarvan dit zaaktype de templates aanbiedt, en het documenttype waaronder de documenten worden opgeslagen.* |
| Schuifknop | `mat-slide-toggle` | Schrijft `epistola_ingeschakeld` op het zaaktype. Zonder zichtbare tekst ernaast; de naam *Documenten maken met Epistola* staat als `aria-label` op de schuifknop. Staat hij uit, dan toont de kaart alleen *Documenten maken met Epistola staat uit voor dit zaaktype.* |
| Catalog | `mat-select` | Verplicht. Biedt de catalogs van de tenant op naam, zonder Epistola's eigen `system`. Staat op de opgeslagen catalog, en zonder die op de catalog van `EPISTOLA_CATALOG_ID` (#51). Heeft de tenant geen catalogs, dan staat onder het veld *Epistola heeft voor deze tenant geen catalogs om uit te kiezen.* |
| Documenttype | `mat-select` | Verplicht, met de tekst *- Kies een documenttype -* zolang er niets gekozen is. Biedt de informatieobjecttypen van het zaaktype, en bepaalt hoe de PDF in Open Zaak wordt geregistreerd (#6). Eén voor het zaaktype: de standaard; een template met een eigen documenttype overschrijft die (`override ?: standaard`) |
| Vertrouwelijkheidaanduiding | read-only veld | Afgeleid van het gekozen informatieobjecttype, niet apart in te stellen |
| Templates in deze catalog | `mat-accordion` met één `mat-expansion-panel` per template | Verschijnt pas als er een catalog gekozen is. Live opgehaald bij Epistola. Het kopje van een item noemt de naam, en als omschrijving het documenttype dat geldt: het eigen documenttype van het template, *Zaaktype-standaard: Besluit* als het er geen heeft, of *Verborgen bij Document maken* als het uit staat. De inhoud van een item toont het *Template-id*. Tot 7 oktober toonde het item ook de talen en eerder de varianten met hun attributen; dat staat nu op de branch `explore/epistola-variant-picker` (B32). Daaronder staat een `mat-select` *Documenttype*, met als eerste keuze *Zaaktype-standaard*, en een schuifknop *Aangeboden bij Document maken*. Is Epistola niet bereikbaar, dan toont het item het id en de naam uit het geheugen van ZAC (#30), met de select en de schuifknop. Heeft de catalog geen templates, dan staat er *Deze catalog heeft geen templates.* |

### Aantekeningen

1. **De kaart is volledig afwezig** wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is. Een beheerder
   ziet nooit twee providerkaarten tegelijk. Bij een BPMN-zaaktype ontbreekt de kaart altijd: #7 beperkt
   Epistola tot CMMN, dus instellen zou daar niets doen.
2. **De schuifknop** schrijft `epistola_ingeschakeld` op het zaaktype. Alles eronder blijft verborgen
   zolang hij uit staat; de kaart zegt dan dat Epistola voor dit zaaktype uit staat.
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
   van Epistola. Een nieuwe versie van het zaaktype neemt de instellingen over, ook die per template.
8. **Geen taal te kiezen** (B32). Tot 7 oktober koos de beheerder hier één taal voor alle templates van het zaaktype
   (#51). Een taal is ook een variant, en ZAC vraagt Epistola nu om geen variant, dus elk template wordt gemaakt in zijn
   standaardvariant ([scherm 3](#3--zijpaneel--document-maken)). De keuzelijst *Taal* staat op de branch
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
| CMMN | Actief | De bestaande actie, met de bestaande vertaalsleutel `actie.document.maken`. Alleen als Epistola voor het zaaktype aanstaat en de gebruiker documenten mag maken; anders ontbreekt de actie |
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
5. **Alleen vanuit de zaak.** Vanuit een taak biedt ZAC *Document maken* met Epistola niet aan; het endpoint
   accepteert wel een taak (VV-13). Een mogelijke uitwerking staat op de branch `explore/epistola-document-from-task`,
   die niet in `main` is gemerged.

---

## 3 · Zijpaneel — Document maken

> Mockup: [`wireframes/3-dialoog.html`](wireframes/3-dialoog.html) ·
> Component: zijpaneel vanuit het zaakmenu

*Document maken* opent een zijpaneel met dezelfde velden als het SmartDocuments-formulier, met als verschillen: bij
Epistola ontbreekt het veld *Templategroep* (#51), en *Bestandsformaat* ligt vast op PDF. Er is geen veld *Taal* en
geen veld *Variant*: ZAC vraagt Epistola om geen variant, dus Epistola maakt de standaardvariant van het template. Tot
7 oktober stond er een veld *Variant* bij een template met varianten voor twee of meer kanalen (#47, #53), en vroeg ZAC
om de taal die de beheerder voor het zaaktype koos ([scherm 1](#1--admin--epistola-documenttemplates-per-zaaktype));
dat staat nu op de branch `explore/epistola-variant-picker` (B32). Sinds #64 is het een eigen formulier voor Epistola,
los van ZAC's SmartDocuments-formulier, achter dezelfde actie.

| Veld | Type | Verplicht | Herkomst |
|---|---|---|---|
| Template | autocomplete op naam | Ja | Elk template in de catalog van het zaaktype dat aanstaat. Is er maar één, dan is het gekozen en ligt het veld vast (#51) |
| Titel | tekstveld, ten hoogste 100 tekens | Ja | Door de behandelaar in te vullen |
| Beschrijving | tekstveld, ten hoogste 100 tekens | Nee | Door de behandelaar in te vullen |
| Documenttype | read-only | — | Dat van het gekozen template, en anders dat van het zaaktype (#51) |
| Vertrouwelijkheidaanduiding | read-only | — | Afgeleid van het documenttype |
| Bestandsformaat | read-only veld met "PDF" | — | Vast; er is geen keuze |
| Auteur | read-only | — | De ingelogde gebruiker |

Onder de velden staan drie knoppen: **Voorbeeld bekijken**, **Genereren** en **Annuleren**.

**Voorbeeld bekijken** werkt zodra er een template gekozen is (#54). Het opent de dialoog *Voorbeeld van het
document*: de naam van het template, de PDF die Epistola rendert (met een watermerk van Epistola), en de uitleg
*Dit is een voorbeeld en nog niet in de zaak opgeslagen. Pas bij Genereren maakt ZAC het document dat in de zaak komt.*
ZAC stuurt dezelfde zaakgegevens als bij genereren en slaat niets op.

Tijdens het genereren blijft het zijpaneel open en zijn de drie knoppen uitgeschakeld; *Genereren* toont een spinner.
Onder de velden staat de voortgang: vier genummerde stappen *Voorbereiden*, *In de wachtrij*, *Maken* en *Opslaan*, een
voortgangsbalk en een regel die zegt wat er gebeurt. Die regel wordt elke seconde ververst: eerst *Het document wordt
gegenereerd. Dat kan enkele seconden duren.*, dan *Het document staat in de wachtrij bij Epistola.* en *Epistola maakt
het document.*, bij een lange wachttijd *… langer dan gebruikelijk. ZAC wacht nog even.*, en bij het opslaan *Het document
wordt opgeslagen in de zaak.* (#8). Is het klaar, dan zijn alle stappen afgevinkt met *Het document is klaar.*, volgt de
melding dat het document aan de zaak is toegevoegd, en sluit het zijpaneel. Het document staat zonder herladen in de lijst.

Kan het zijpaneel de templates niet laden omdat Epistola niet bereikbaar is, dan staat boven *Template* *De templates
kunnen nu niet worden geladen.* met de reden erachter, en niet een lege lijst (#8). Kent ZAC de namen van een eerdere
lijst, dan toont het die (#30).

### Aantekeningen

1. **Geen keuze voor het uitvoerformaat.** *Bestandsformaat: PDF* staat er als read-only veld en niet als een
   keuzelijst met één optie — een select die de gebruiker niet kan wijzigen is een dode besturing. #5
   vereist alleen PDF.
2. **Documenttype en vertrouwelijkheid zijn read-only**, gevuld uit de configuratie van het zaaktype zodra er een
   template gekozen is. De behandelaar kan een document niet onder het verkeerde informatieobjecttype wegzetten.
3. **Bij Epistola is er geen groep te kiezen.** *Template* biedt meteen de templates van de catalog van het zaaktype
   (#51). Bij SmartDocuments blijft *Template* leeg tot er een groep is gekozen.
4. **Genereren houdt het zijpaneel open** in plaats van het te sluiten. Mislukt het, dan verschijnt ZAC's foutdialoog
   boven het zijpaneel, en staat de invoer er nog als de behandelaar die dialoog sluit (scherm 4).
5. **Geen variant te kiezen** (B32). ZAC vraagt Epistola om geen taal en geen variant, dus Epistola maakt altijd de
   standaardvariant van het template, ook als het template er meer heeft
   ([ontwerp §5](technisch-functioneel-ontwerp.md#verzoek)). Een andere variant kiezen is het vervolg in #50.
6. **Het voorbeeld is geen dossierstuk.** Het komt uit Epistola's `preview`: geen PDF/A en niet bewaard. Wat in de zaak
   komt, maakt pas *Genereren*.

Anders dan bij SmartDocuments is er geen overdracht aan een wizard: Epistola genereert server-side, dus de
flow blijft binnen ZAC en vertrekt nooit naar een externe editor.

---

## 4 · Foutpaden

> Mockup: [`wireframes/4-foutpaden.html`](wireframes/4-foutpaden.html)

Drie mislukkingen die andere woorden nodig hebben, omdat elke een andere oorzaak heeft en de behandelaar iets anders
moet weten of doen. De teksten zijn die van de gebouwde versie (#8), elk met een eigen foutcode. De overige staan in
[§5 van het ontwerp](technisch-functioneel-ontwerp.md#foutafhandeling).

| Situatie | Melding |
|---|---|
| Epistola onbereikbaar | Epistola is op dit moment niet bereikbaar. Probeer het later opnieuw. |
| Gegenereerd, opslag mislukt | Het document is gemaakt, maar het opslaan in Open Zaak is mislukt. Er is niets aan de zaak toegevoegd. Probeer het later opnieuw. |
| Data breekt het contract van het template | Het template vraagt zaakgegevens die deze zaak niet heeft, of in een andere vorm. Opnieuw proberen helpt niet. Geef de melding hieronder door aan de beheerder. *(met Epistola's reden eronder)* |

Elke melding verschijnt in ZAC's foutdialoog *Er is een fout opgetreden*, met een knop *Sluiten*.

### Aantekeningen

1. **De tweede melding zegt wat er met het document gebeurd is**, en niet alleen dat er iets misging.
   "Genereren gelukt, opslaan niet" is het geval waar een gebruiker anders geen chocola van kan maken — en
   het is het pad van gedeeltelijke mislukking dat #8 gedefinieerd wil zien.
2. **Nooit een stacktrace.** De melding noemt de oorzaak, en bij een gebroken contract Epistola's eigen
   reden, zodat de behandelaar genoeg heeft om de beheerder te vertellen wat er mis is. Voor het log geldt de regel uit #16: identificaties
   en statussen, nooit de payload.
3. **Fouten verschijnen in ZAC's foutdialoog** boven het zijpaneel. Het zijpaneel blijft open met de invoer, zodat de
   behandelaar het na het sluiten van de dialoog opnieuw kan proberen.
4. **De meldingen noemen Epistola wel**, omdat ze het systeem noemen dat niet reageert. Het ontwerpbesluit hieronder
   dat de behandelaar de provider niet hoeft te kennen, geldt voor de *actie* en het *label*: die blijven
   *Document maken*. Een melding moet zo duidelijk mogelijk zijn, en daarom noemt ze de provider.

---

## 5 · Documentpagina — Nieuwe versie genereren

> Mockup: [`wireframes/5-nieuwe-versie.html`](wireframes/5-nieuwe-versie.html) ·
> Route: `/informatie-objecten/{uuid}`

Op de pagina van een document dat Epistola maakte, staat in het menu **Nieuwe versie genereren**, naast het bestaande
*Nieuwe versie* (uploaden) (#9). Het maakt de PDF opnieuw met hetzelfde template uit dezelfde catalog, ook als het
zaaktype inmiddels een andere catalog heeft, en met de zaakgegevens van dat moment.

| Element | Gedrag |
|---|---|
| Menu-item *Nieuwe versie genereren* | Alleen bij een document waarvan ZAC weet met welk template het is gemaakt (`epistola_document`), als de gebruiker documenten in de zaak mag maken en een nieuwe versie mag toevoegen. Niet bij een ondertekend document |
| Dialoog *Nieuwe versie genereren* | De titel van het document en dezelfde vier stappen als in scherm 3, met dezelfde statusregel |
| Klaar | De melding *Er is een nieuwe versie van "…" gemaakt met Epistola.* De documentpagina toont de nieuwe versie (*Versie 2 van 2*); met de pijltjes is de vorige ongewijzigd terug te zien |
| Mislukt | De huidige versie blijft zoals ze was; de melding is die van scherm 4. Kan er geen nieuwe versie met Epistola worden gemaakt: *Van dit document kan geen nieuwe versie met Epistola worden gemaakt. Het is niet met Epistola gemaakt, of het is niet aan een zaak gekoppeld.* |

### Aantekeningen

1. **Geen formulier.** Template, catalog, titel en documenttype liggen vast door de eerste versie; er is niets te kiezen.
2. **Een template dat de beheerder heeft uitgezet**, maakt nog steeds een nieuwe versie van een document dat er al mee
   is gemaakt. Uitzetten haalt het alleen uit de lijst van *Document maken*.

---

## Ontwerpbesluiten

### De actie en het label verraden de provider niet

Elk zaakkant-scherm hierboven is het scherm dat ZAC al heeft. Zelfde actie, zelfde label, vrijwel dezelfde
velden — alleen de templates erachter verschillen, en bij Epistola heeft het zijpaneel geen veld *Templategroep*
(#51). De actie en het label noemen de provider niet; de voortgangsregels en de foutmeldingen wel.

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
