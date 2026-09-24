# Wireframes — Epistola

| | |
|---|---|
| Issue | [#15](https://github.com/infonl/zac-epistola-prototype/issues/15) · werkproces B1-K1-W2 |
| Stand | Besproken met de stakeholders; bijgewerkt na het overleg van 21 september 2026 |
| Raakt | #3 beheerscherm · #5 dialoog · #7 CMMN-poort · #8 foutafhandeling |
| Fidelity | Laag — structuur, toestanden en labels zijn het onderwerp, visueel ontwerp niet |

Vier schermen, getekend tegen de Angular Material-componenten die ZAC al heeft in plaats van verzonnen.
Het leidende besluit: **de behandelaar hoort nooit te weten welke provider er geconfigureerd is** — de
provider is een zaak van de beheerder, dus de zaakkant van de interface blijft het scherm dat mensen al
kennen.

De getekende schermen staan als los te openen HTML-mockups in [`wireframes/`](wireframes/). GitHub toont
die niet inline; download ze of open ze lokaal in een browser. De schermbeschrijvingen hieronder zijn
volledig genoeg om zonder de mockups te bouwen — die zijn er voor de vorm, niet voor de inhoud.

---

## 1 · Admin — Epistola documentsjablonen per zaaktype

> Mockup: [`wireframes/1-admin.html`](wireframes/1-admin.html) ·
> Route: `/admin/parameters` → zaaktype → Documentcreatie

Spiegelt `smart-documents-form.component`: een kaart die alleen verschijnt wanneer de provider actief is,
een schuifknop per zaaktype, en een lijst met templates die elk aan een informatieobjecttype gekoppeld zijn.

### Opbouw

| Element | Type | Gedrag |
|---|---|---|
| Kaart *Epistola documentsjablonen* | `mat-card` | Volledig afwezig wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is |
| Schuifknop | `mat-slide-toggle` | Schrijft `epistola_ingeschakeld` op het zaaktype. Alles eronder is verborgen zolang hij uit staat |
| Groepskop | tekst, één niveau | Een templategroep die de beheerder zelf in ZAC heeft gemaakt |
| Selectievakje per template | `mat-checkbox` | Of dit template voor dit zaaktype beschikbaar is |
| Templatenaam | tekst | Live opgehaald bij Epistola; er wordt geen naam opgeslagen |
| Informatieobjecttype | `mat-select` | Verplicht zodra het template is aangevinkt. Bepaalt hoe de PDF in Open Zaak wordt geregistreerd (#6) |
| Vertrouwelijkheid | read-only tekst | Afgeleid van het gekozen informatieobjecttype, niet apart in te stellen |

### Aantekeningen

1. **De kaart is volledig afwezig** wanneer `DOCUMENT_CREATION_PROVIDER` niet `EPISTOLA` is — dezelfde
   `*ngIf="enabledGlobally"`-guard die SmartDocuments gebruikt. Een beheerder ziet nooit twee
   providerkaarten tegelijk.
2. **De schuifknop** schrijft `epistola_ingeschakeld` op het zaaktype. Alles eronder blijft verborgen
   zolang hij uit staat.
3. **Groepen zijn een platte lijst, geen uitklapbare boom.** SmartDocuments gebruikt `mat-tree` omdat zijn
   groepen nesten. Epistola heeft helemaal geen groepen, dus dit zijn die van ZAC zelf: de beheerder maakt
   ze hier aan en zet er platte Epistola-templates in, één niveau diep.
4. **Informatieobjecttype per template** — een `mat-select`, precies zoals SmartDocuments het doet. Dit is
   wat #6 nodig heeft om de PDF in Open Zaak te registreren.
5. **Vertrouwelijkheidaanduiding is read-only** en afgeleid van het gekozen informatieobjecttype. Geen
   nieuw idee: zo gedraagt de SmartDocuments-rij zich al.

Het selectievakje, de naam, de informatieobjecttype-select en de afgeleide vertrouwelijkheid reproduceren
`smart-documents-form-item.component.html` veld voor veld.

---

## 2 · Behandelaar — de actie in de zijbalk

> Mockup: [`wireframes/2-behandelaar.html`](wireframes/2-behandelaar.html) ·
> Route: `/zaken/{identificatie}`

Het verschil tussen een CMMN- en een BPMN-zaak naast elkaar — dit is alles wat #7 aan de interface
verandert.

| Zaaktype | Actie *Document maken* | Toelichting |
|---|---|---|
| CMMN | Actief | De bestaande actie, met de bestaande vertaalsleutel `actie.document.maken` |
| BPMN | Zichtbaar maar uitgeschakeld, met tooltip | "Documentcreatie met Epistola is in deze versie alleen beschikbaar voor CMMN-zaken." |

### Aantekeningen

1. **Het label blijft "Document maken".** ZAC heeft deze actie en deze vertaalsleutel al
   (`actie.document.maken`). Issue #5 formuleert het als "Genereer document", maar een tweede label voor
   dezelfde taak introduceren zou de behandelaar vertellen welke provider er geconfigureerd is — en dat is
   niet hun zorg en niet hun beslissing.
2. **Uitgeschakeld, niet verborgen, voor BPMN.** Een verdwenen actie leest als een rechtenprobleem; een
   uitgeschakelde actie met een tooltip benoemt de werkelijke beperking, en dat is wat #7 in de interface
   vraagt.
3. **De backend weigert het hoe dan ook** met 400/422 — de uitgeschakelde staat is een beleefdheid, geen
   handhaving.

---

## 3 · Dialoog — document genereren

> Mockup: [`wireframes/3-dialoog.html`](wireframes/3-dialoog.html) ·
> Component: dialoog vanuit de zaakdetailpagina

Dezelfde velden als de bestaande SmartDocuments-dialoog, met één veld eruit en één veld
niet-onderhandelbaar.

| Veld | Type | Verplicht | Herkomst |
|---|---|---|---|
| Sjabloongroep | `mat-select` | Ja | De groepen die voor dit zaaktype zijn geconfigureerd |
| Sjabloon | `mat-select` | Ja | Leeg tot er een groep is gekozen; toont alleen templates die aan dit zaaktype gekoppeld zijn |
| Titel | tekstveld | Ja | Door de behandelaar in te vullen |
| Toelichting | tekstveld | Nee | Door de behandelaar in te vullen |
| Documenttype | read-only | — | Uit de beheermapping, zodra het sjabloon is gekozen |
| Vertrouwelijkheid | read-only | — | Afgeleid van het documenttype |
| Formaat | read-only, statische tekst "PDF" | — | Vast; er is geen keuze |
| Auteur | read-only | Ja | De ingelogde gebruiker |

Tijdens het genereren blokkeert de dialoog: een spinner met "Document wordt gegenereerd…", een melding dat
het enkele seconden kan duren, en een uitgeschakelde knop *Genereren*.

### Aantekeningen

1. **Geen keuze voor het uitvoerformaat.** "Formaat: PDF" staat er als statische tekst en niet als een
   keuzelijst met één optie — een select die de gebruiker niet kan wijzigen is een dode besturing. #5
   vereist alleen PDF.
2. **Documenttype en vertrouwelijkheid zijn read-only**, gevuld uit de beheermapping zodra er een sjabloon
   gekozen is. De behandelaar kan een document niet onder het verkeerde informatieobjecttype wegzetten.
3. **Sjabloon is leeg tot er een groep gekozen is** en toont alleen templates die voor dit zaaktype zijn
   geconfigureerd.
4. **Genereren blokkeert de dialoog** in plaats van hem te sluiten, zodat een mislukking in context getoond
   kan worden en niet als een losse toast op de zaakpagina.

Anders dan bij SmartDocuments is er geen overdracht aan een wizard: Epistola genereert server-side, dus de
flow blijft binnen ZAC en vertrekt nooit naar een externe editor.

---

## 4 · Foutpaden

> Mockup: [`wireframes/4-foutpaden.html`](wireframes/4-foutpaden.html)

Twee mislukkingen die andere woorden nodig hebben, omdat ze het systeem in een andere toestand achterlaten.

| Situatie | Melding |
|---|---|
| Epistola onbereikbaar | **Genereren mislukt.** Epistola is niet bereikbaar. Probeer het later opnieuw of neem contact op met de beheerder. *(referentie: 7f3a91c4)* |
| Gegenereerd, opslag mislukt | **Document gegenereerd, maar opslag in Open Zaak is mislukt.** Het document is niet aan de zaak gekoppeld en moet opnieuw worden gegenereerd. *(referentie: 91ba02de)* |

### Aantekeningen

1. **De tweede melding zegt wat er met het document gebeurd is**, en niet alleen dat er iets misging.
   "Genereren gelukt, opslaan niet" is het geval waar een gebruiker anders geen chocola van kan maken — en
   het is het pad van gedeeltelijke mislukking dat #8 gedefinieerd wil zien.
2. **Een correlatiereferentie, nooit een stacktrace.** Daarmee kan support de logregel vinden; #8 vereist
   dat de log zelf detail draagt zonder BSN of andere persoonsgegevens in platte tekst.
3. **Fouten verschijnen binnen de dialoog**, die nog openstaat omdat het genereren hem blokkeerde.

---

## Ontwerpbesluiten

### De behandelaar krijgt nooit te zien welke provider er draait

Elk zaakkant-scherm hierboven is het scherm dat ZAC al heeft. Zelfde actie, zelfde label, zelfde
dialoogvelden — alleen de templates erachter verschillen.

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

Het beheerscherm is getekend als een platte lijst met groepen. SmartDocuments gebruikt `mat-tree` omdat
zijn groepen willekeurig diep nesten.

Beslist op 21 september: Epistola heeft helemaal geen templategroepen — zijn templates zijn plat binnen een
tenant — dus de beheerder maakt de groepen hier in ZAC en zet er platte Epistola-templates in. Eén niveau
is genoeg, het scherm blijft een lijst, en de groeptabel heeft geen zelfverwijzende `parent_id` nodig
(#3, [datamodel](datamodel.md)).

---

Wireframes voor het Epistola-prototype, getekend tegen `smart-documents-form.component`,
`smart-documents-form-item.component` en de bestaande zaakzijbalkacties op de examenfork. Bewust lage
fidelity: structuur, toestanden en labels zijn het onderwerp, visueel ontwerp niet.
