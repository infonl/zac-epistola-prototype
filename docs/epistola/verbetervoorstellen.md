# Verbetervoorstellen en vervolgstappen — Epistola-integratie in ZAC

| | |
|---|---|
| Werkproces | B1-K1-W5 · Verbetervoorstellen |
| Issue | [#20](https://github.com/infonl/zac-epistola-prototype/issues/20) · bouwt op het [testrapport](testrapport.md) (#19) en het risicoregister van de ontwerpverantwoording (#16) |
| Projectnaam | Epistola-integratie in ZAC (zaakafhandelcomponent) |
| Student | Symon Vleeshouwers (198462) |
| Klas | ZWSD24 |
| Stand | **29 september 2026, bijgewerkt op 30 september, op 1 oktober door Symon nagelezen** (de samenvatting volgt de indeling van §4). Op 30 september gespiegeld naar de Confluence-pagina (#10, sectie 6). Nog niet gedeeld met de stakeholders. Op 5 oktober bijgewerkt naar de catalog per zaaktype (#51): VV-12, §5 en de verantwoording. Op 6 oktober opgeschoond: wat is gebouwd staat niet meer als voorstel (VV-14, en in VV-10 het deel over de client), en één voorstel is toegevoegd (VV-16, varianten) |

> **Wat dit document is.** De uitkomst van het prototype, vertaald naar besluiten die Dimpact kan nemen: wat er
> tussen dit prototype en productiegebruik ligt, wat nu al is opgelost, en welke voorstellen realiseerbaar zijn.
> Verbeteringen aan **hoe het team werkte** staan hier niet in. Die horen bij de reflectie (#23) en staan in
> [§6](#6-doorgeschoven-naar-de-reflectie-23) alleen als verwijzing.

## 1. Samenvatting

Het prototype laat ZAC documenten maken met Epistola, per zaaktype in te stellen, alleen als PDF, opgeslagen in
Open Zaak en gekoppeld aan de zaak. De hoofdstroom is getest, in twee rondes: in ronde 1 (25 september) slaagden 32 van de
35 uitgevoerde testcases. De drie die mislukten (TS-06, TS-31, TS-32) betroffen meldingen voor de gebruiker. Die zijn
daarna gebouwd (#7, #8, #30), en ronde 2 (30 september) heeft ze op de gemergede versie opnieuw uitgevoerd: alle 36
testcases slagen nu. Een derde ronde testte het optionele #9, een nieuwe versie van een document, met acht testcases
die ook slagen.

**Wat productiegebruik nog vraagt** zijn drie dingen, de voorstellen onder *Voor productie: moet* in §4:

1. **Afspraken met Epistola en de organisatie** (VV-02): een verwerkersovereenkomst, een vermelding in het
   verwerkingsregister, en de bewaartermijn die Epistola *werkelijk* toepast, want die is drie tot vier maanden en
   niet de dertig dagen die genoemd werd.
2. **Sleutelbeheer** (VV-01): de API key is Epistola's ondersteunde methode en geen blokkade meer (R1), maar ZAC heeft geen vervaldatum en geen
   beschreven rotatieprocedure voor hem.
3. **`https` voor de Epistola-URL** (VV-03): ZAC controleert bij het opstarten dat de URL is ingesteld, maar niet
   dat hij met `https` begint.

Daarnaast zijn er **zes verstandige verbeteringen** die het ZAC-team zelf kan doen (VV-05, -06, -07, -09, -11, -16). De
belangrijkste is een belastingtest (VV-05): een verzoek houdt een thread vast zolang Epistola rendert, bij één
gebruiker 3 tot 10 seconden, en niemand heeft het met meerdere gebruikers tegelijk gemeten. VV-16 is van 6 oktober: ZAC
kan alleen op taal en kanaal kiezen, en Epistola weigert een verzoek waarbij meer varianten even goed passen. Er zijn
verder **drie punten die van Epistola afhangen** (VV-04, VV-08, VV-10) en **drie uitbreidingen** die pas zin hebben na
een besluit over de richting (VV-12, VV-13, VV-15).

Van de negen risico's uit #16 zijn er vier tijdens het prototype opgelost, drie bewust geaccepteerd met een reden
(waaronder één dat bij een ander team ligt), en twee omgezet in een voorstel. Zie [§3](#3-het-risicoregister-alle-negen).

## 2. Werkwijze en bronnen

| Bron | Gebruikt voor |
|---|---|
| [Testrapport](testrapport.md) (#19), 25 en 30 september | 36 testcases in twee rondes, 8 bugs, bekende beperkingen, aanbevelingen |
| Risicoregister R1 t/m R9 (#16, bijgehouden in #20) | [§3](#3-het-risicoregister-alle-negen) |
| Stakeholderoverleggen van 21 en 28 september (#21) | Besluiten die risico's sloten of vernauwden, en nieuwe vragen |
| Reviews op #24 (Marcel, Edgar, Copilot) | Vragen aan Epistola, contractversie, bewaartermijn |
| De gebouwde code, nagelopen op 29 september | Elke bewering over de huidige stand hieronder, met bestand erbij |

Een voorstel staat alleen in dit document als het (a) volgt uit een testuitkomst, een risico of een besluit, en (b)
in een paar zinnen te zeggen is wat er gebouwd moet worden. **De omvang is een grove schatting van de student en
niet gemeten**: S is hooguit een dag, M is twee tot vijf dagen, L is meer dan een week voor het ZAC-team.

## 3. Het risicoregister: alle negen

Elk risico heeft één uitkomst: **opgelost** tijdens het prototype (met de oplossing erbij), **voorstel** (met een
verwijzing naar [§4](#4-de-verbetervoorstellen)), of **geaccepteerd** (met de reden).

| Id | Risico | Uitkomst | Toelichting |
|---|---|---|---|
| R1 | Statische API key: geen attributie per medewerker, en ZAC kan zijn sleutel niet zelf roteren | **Voorstel** VV-01, VV-08 | Middel, niet meer blokkerend. Contract 1.3.1 maakt de API key Epistola's ondersteunde methode, ook voor productie, met verval en intrekking. Er is nergens beschreven hoe een sleutel wordt vernieuwd. Het opgeslagen document draagt wel de naam van de behandelaar als auteur (`EpistolaDocumentCreationService.kt`); Epistola ziet alleen de installatie |
| R2 | Helm-chart kon de Epistola-instellingen niet leveren | **Opgelost** | #2, op `main` sinds PR #1. De sleutel is een Kubernetes Secret, de rest ConfigMap-entries, allemaal in de chart-README |
| R3 | Persoonsgegevens uit de BRP staan in het applicatielog door de standaard-logniveaus | **Geaccepteerd voor dit project** | Buiten de scope van dit prototype en dit team: het loggen van BRP-verkeer wordt door een ander team beheerd. Het staat hier omdat het productiegebruik met echte burgergegevens raakt |
| R4 | Geen verwerkersovereenkomst met Epistola | **Geaccepteerd voor het prototype**, **voorstel** VV-02 voor productie | Besloten op 21 september: ~~Epistola draait lokaal, dus er is geen tweede partij en Dimpact is zelf verwerkingsverantwoordelijke. Dat geldt alleen zolang beide voorwaarden gelden.~~ *Rechtgezet op 1 oktober:* het prototype, de tests en de einddemo draaien tegen Epistola's gehoste testserver, niet tegen een lokale Epistola. Er is toch geen overeenkomst nodig, omdat er alleen testgegevens heen gaan: de zaken uit de lokale testomgeving en de fictieve personen uit de BRP-mock. Dat geldt zolang het testgegevens zijn. De bewaartermijn is gecorrigeerd: in de broncode van Epistola Suite leest geen code `documents.retention-days: 30`, een document blijft tot zijn maandpartitie na drie maanden wordt verwijderd |
| R5 | Startformuliergegevens gingen ongefilterd naar Epistola | **Opgelost** (#4), restpunt **voorstel** VV-09 | De payload wordt recursief afgestemd op het JSON Schema van het gekozen template, ook waar het schema extra eigenschappen toelaat. Een template zonder schema wordt geweigerd. Een template mag een sectie als vormvrij object vragen: besloten op 28 september als keuze van de templatebouwer |
| R6 | Vertrouwelijkheidaanduiding stond hard op `OPENBAAR` | **Opgelost** voor Epistola (#6), **voorstel** VV-11 voor SmartDocuments | Het Epistola-document krijgt de vertrouwelijkheid van zijn informatieobjecttype; op 25 september eind-tot-eind nagegaan met een zaakvertrouwelijk type. De SmartDocuments-flow codeert nog steeds `OPENBAAR` (`DocumentCreationService.kt:236`) |
| R7 | De providerabstractie is alleen op configuratieniveau | **Geaccepteerd**, met reden | Versmald in #5: elke provider heeft een eigen endpoint dat zijn eigen provider toetst, achter één dialoog. De gedeelde interface uit het ontwerp is er niet. Bij twee providers is dat structuur die te vroeg wordt gekocht; zie [§5](#5-bewust-niet-voorgesteld) |
| R8 | Een verkeerd tenant-id wijst ZAC stilletjes naar een andere tenant | **Opgelost**, optioneel restpunt **voorstel** VV-07 | Een API key hoort bij één tenant, dus een ander tenant-id geeft `403` op elke aanroep (nagegaan tegen drie andere tenants). Een fout geformuleerd id wordt bij het opstarten geweigerd |
| R9 | Transport naar Epistola wordt aangenomen, niet afgedwongen | **Voorstel** VV-03 | Nagelopen op 29 september: `EpistolaSettings` controleert aanwezigheid en formaat van de URL, niet het schema |

## 4. De verbetervoorstellen

Drie groepen. **A** is wat productiegebruik vraagt. **B** is wat het verstandig maakt. **C** is wat het uitbreidt.

### A. Voor productie: moet

**VV-01 · Sleutelbeheer: verval en een rotatieprocedure** (R1) · S · ZAC-team met de tenantbeheerder van Epistola
*Probleem.* De API key heeft in ZAC geen vervaldatum en er is geen beschreven manier om hem te vernieuwen.
*Voorstel.* Geef de sleutel bij het uitgeven een vervaldatum. Leg de procedure vast in de chart-README: de
tenantbeheerder geeft een nieuwe sleutel uit, die gaat in het Kubernetes Secret, ZAC start opnieuw, daarna wordt de
oude ingetrokken. Geef de sleutel alleen de rollen `DOCUMENT_GENERATOR` en `CONTENT_VIEWER`.
*Waarom realiseerbaar.* Het is documentatie en een uitgiftebesluit; er verandert niets in de code.

**VV-02 · Verwerkersovereenkomst, verwerkingsregister en de echte bewaartermijn** (R4) · organisatie, geen code
*Probleem.* ~~Zolang Epistola lokaal draait, is Dimpact zelf verwerkingsverantwoordelijk. Zodra een gehoste Epistola
of echte zaakgegevens meespelen, vervalt die uitzondering.~~ Zolang er alleen testgegevens naar Epistola gaan, verwerkt
Epistola geen persoonsgegevens van echte mensen en is er geen overeenkomst nodig (rechtgezet op 1 oktober: het
prototype draait tegen Epistola's gehoste testserver). Zodra echte zaakgegevens meespelen, vervalt die uitzondering.
*Voorstel.* Sluit een overeenkomst volgens artikel 28 AVG die de bewaartermijn noemt die Epistola werkelijk
toepast (drie tot vier maanden, niet dertig dagen), subverwerkers en locatie. Voeg een vermelding toe aan het
verwerkingsregister en laat de functionaris gegevensbescherming dit bevestigen.
*Al gedaan.* ZAC verwijdert het document bij Epistola zodra het in Open Zaak staat (#6), en ook als opslaan mislukt
(#8). Die verwijdering is *best effort*: mislukt ze, dan geldt de bewaartermijn van Epistola alsnog.

**VV-03 · `https` afdwingen voor de Epistola-URL** (R9) · S · ZAC-team
*Voorstel.* Laat `EpistolaSettings` bij het opstarten weigeren wat niet met `https` begint, naast de bestaande
controle op aanwezigheid. Een test per schema volstaat. Let op lokaal gebruik: de WireMock-stand-in draait op
`http://epistola-wiremock:8080`, geen loopback-adres, dus een uitzondering voor alleen localhost is niet genoeg. Kies een
uitdrukkelijke instelling voor lokaal gebruik of zet de stand-in op `https`. Dit past in één kleine pull request.

### B. Voor productie: verstandig

**VV-04 · Rendertimeout en maximum aantal pogingen bij Epistola** · vraag aan Epistola
*Aanleiding.* Uit de toets van Epistola's wachtrij op 28 september (broncode `e2484c7`): er is één wachtrij voor alle
tenants met een vast aantal renderplekken, een render heeft geen deadline, en er is geen maximum aantal pogingen.
Eén document dat bij elke poging hangt, kan zo steeds een plek bezetten (afgeleid uit de broncode, niet
uitgeprobeerd). De health-check blijft `UP` als alle plekken hangen.
*Voorstel.* Vraag Epistola om een rendertimeout en een maximum aantal pogingen. Vraag de beheerder van Epistola om in
productie `epistola.generation.queue.depth`, `epistola.generation.queue.wait` en `epistola.jobs.active` te bewaken
naast `epistola.jobs.max_concurrent`. ZAC annuleert een job al bij een time-out, en een geannuleerde job wordt nooit
opnieuw opgepakt. Aan de ZAC-kant is dus niets nieuws nodig.

**VV-05 · Een belastingtest met gelijktijdige gebruikers** · M · ZAC-team
*Aanleiding.* Testrapport §6: het verzoek wacht op Epistola en houdt zolang een thread vast. Bij één behandelaar is
dat 3 tot 10 seconden; onder belasting is het niet gemeten.
*Voorstel.* Meet met bijvoorbeeld tien tot vijftig gelijktijdige generaties tegen de lokale stand-in (VV-06) en tegen
de testserver, en kijk naar de threads van de applicatieserver en de wachttijd. Blijkt het te zwaar, dan is het
alternatief het verzoek direct met een taak-id te beantwoorden en de dialoog te laten pollen. De statusroute die #8
toevoegde, is daar al een eerste stap naar.

**VV-06 · Een Epistola-stand-in in de integratietests** · S–M · ZAC-team
*Aanleiding.* De integratietests draaien met SmartDocuments en kunnen de Epistola-routes niet aanroepen; die zijn
alleen met unittests, de live-check en de hand getest.
*Voorstel.* Gebruik de WireMock-mappings die er al zijn (`scripts/docker-compose/imports/epistola-wiremock/`, ~~zeven~~
negen stuks sinds het voorbeeld (#54) en de catalogs (#51), gestart met `-W`) voor een tweede integratietestrun met Epistola als provider. Dan lopen het endpoint en de
dialoog ook in de pipeline, en hangen de tests niet meer af van een testtenant die dagelijks wordt gereset.

**VV-07 · Een controle bij het opstarten op bereikbaarheid en tenant** (R8) · S · ZAC-team
*Voorstel.* Laat ZAC bij het opstarten één lichte aanroep doen (bijvoorbeeld de eerste pagina van de templatelijst)
en het resultaat loggen. Een
verkeerde URL of tenant blijkt dan bij het opstarten, niet bij het eerste document. Wees voorzichtig met weigeren:
een tijdelijk onbereikbare Epistola mag ZAC niet laten falen. Loggen volstaat.

**VV-08 · Attributie per medewerker in Epistola** (R1, restant) · M · afhankelijk van Epistola
*Probleem.* Epistola ziet de installatie, niet de behandelaar. Beide JWT-methoden zijn in contract 1.3.1
experimenteel, dus OAuth is nu geen productiepad.
*Voorstel.* Volg wat Epistola met OAuth en token-uitwisseling doet, en neem dit op zodra het niet meer
experimenteel is. Tot dan ligt de herleidbaarheid in ZAC: het opgeslagen document heeft de behandelaar als auteur.
Leg dat vast in het verwerkingsregister (VV-02).

**VV-09 · Een waarschuwing in de beheerkaart bij een vormvrije sectie** (R5) · S · ZAC-team
*Aanleiding.* Besluit van 28 september: een template mag een sectie van het startformulier als vormvrij object
vragen, en krijgt die dan heel.
*Voorstel.* Toon in de beheerkaart een waarschuwing bij een template dat zo'n sectie declareert, zodat de beheerder
weet dat de allow-list daar niets versmalt. De controle staat al in de allow-list en is getest.

**VV-10 · Velden en contractversie uit Epistola halen** · S–M · afhankelijk van Epistola
*Aanleiding.* Twee vragen uit de review op #24 en een open bug.
*Voorstel.* (a) Vraag welk contract bij de gerenderde templateversie hoort, of beter: welke velden die versie werkelijk
leest. Dat geeft een strakkere allow-list dan een contract declareert en beslist de versievraag. (b) Contract 1.4.0
(30 september) beschrijft voor `validate` per veld `missingFields` en `invalidFields`, en een `variantId`, `versionId` of
`environmentId` om tegen de versie te toetsen die gerenderd wordt. De server doet dat voor `validate` nog niet: op
30 september gaf de testserver op data met vier fouten alleen de velden `errors` en `valid` terug, en in
[epistola-suite#978](https://github.com/epistola-app/epistola-suite/issues/978) staat dat onderdeel te wachten op deze
contractrelease. ZAC's client kent de velden al, want hij staat sinds [PR #42](https://github.com/infonl/zac-epistola-prototype/pull/42) op 1.4.0. Zodra de server ze levert, noemt
de foutmelding bij een contractfout het ontbrekende veld. Nu zegt de melding al dat opnieuw proberen niet helpt (#8); het
veld noemen is de volgende stap.

**VV-11 · Vertrouwelijkheid ook bij SmartDocuments afleiden** (R6, restant) · S · ZAC-team
*Voorstel.* Laat de SmartDocuments-flow de vertrouwelijkheid van het informatieobjecttype overnemen, zoals de
uploadformulieren van ZAC. Het is één regel (`DocumentCreationService.kt:236`), maar het ligt buiten dit prototype,
want SmartDocuments mocht niet van gedrag veranderen (DoD 1). Het is een fout die daar al bestond.

**VV-16 · Varianten kiezen op meer dan taal en kanaal, en een duidelijke melding bij een dubbelzinnige variant** (#50) · S–M · ZAC-team
*Aanleiding.* Een template mag varianten hebben die op meer verschillen dan taal en kanaal, bijvoorbeeld groot lettertype of
eenvoudige taal. Proef op 6 oktober met de testtemplate `zac-aanvullende-informatie`: ZAC vraagt Epistola om taal en kanaal, en
als twee varianten daarvoor even goed passen geeft Epistola `409 Ambiguous Variant`. ZAC toont dan alleen de algemene melding
*Epistola could not process ZAC's request*. De beheerkaart toont per template alle varianten, maar de behandelaar kan er niet tussen kiezen.
*Voorstel.* (a) Laat de behandelaar kiezen uit de varianten van het template zelf (eventueel op titel), of laat ZAC de overige
attributen als voorkeur meesturen. (b) Vertaal `Ambiguous Variant` naar een eigen foutcode die zegt welke varianten botsen.
Tot dan: de beheerder zet zo'n template uit in de beheerkaart. Het sluit aan op #50.

### C. Uitbreidingen: later

**VV-12 · Ondersteuning voor BPMN-zaken** · M · besluit van het ZAC-team over de richting
*Nu.* Alleen CMMN. Sinds #7 is de beperking zichtbaar (uitgeschakelde actie met toelichting) en weigert de backend
een BPMN-zaak met een eigen foutcode.
*Wat er wél al is.* ~~De templategroepen hangen aan de basisklasse `ZaaktypeConfiguration`, niet aan de CMMN-variant,~~
*5 oktober:* de catalog en het informatieobjecttype staan op de basisklasse `ZaaktypeConfiguration` (#51), niet op de
CMMN-variant, dus het datamodel staat het al toe. De payloadbouw bevat niets CMMN-specifieks.
*Wat ontbreekt.* (1) De beheerkaart bestaat alleen in het CMMN-bewerkscherm. (2) Bij een BPMN-configuratie is
Epistola nooit "ingeschakeld voor het zaaktype". (3) De backend-controle moet weg. *5 oktober:* ook het opslaan van de
catalog vraagt sinds #51 een CMMN-zaaktype. (4) Een besluit *waar* een
behandelaar in een procesgestuurde zaak een document maakt, want ZAC verbergt daar al veel acties. Dat vierde is een
productkeuze en geen technische.

**VV-13 · Een document maken vanuit een taak** · S–M · besluit van de stakeholders nodig
*Nu.* De dialoog opent alleen vanuit de zaak. Het endpoint ondersteunt een taak wel (getest in TS-28).
*Vraag.* Blijft dit een bekende beperking, of moet het kunnen? Op 28 september geagendeerd, niet beantwoord.

**VV-15 · Meerdere documenten in één keer maken** · L · pas na VV-05
*Voorstel.* Niet beginnen voordat de belastingtest laat zien wat één document kost. Een bulkstroom die een thread
per document vasthoudt, herhaalt het probleem uit VV-05 op grotere schaal.

## 5. Bewust niet voorgesteld

| Onderwerp | Reden |
|---|---|
| Een gedeelde `DocumentCreationProvider`-interface (R7) | Met twee providers is het structuur die te vroeg wordt gekocht. Bij een derde provider wel doen, met een sealed uitkomst zoals het ontwerp beschreef |
| De templatenamen bewaren na een herstart van ZAC | #30 bewaart de namen in het geheugen. Een naam naast het id had `V100` en alle PR's erboven geraakt en schrijft bij een leesactie. Alleen nodig als "Epistola valt uit direct na een herstart van ZAC" een reëel scenario wordt; dan is het ~~een nieuwe migratie van één kolom~~ *5 oktober:* een tabel met de namen per catalog, want sinds `V104` is er geen rij per template meer om een kolom aan toe te voegen (#51) |
| `preview` gebruiken voor het dossierdocument | Geen PDF/A, rate-limited, niet opgeslagen (besluit B5) |
| Een eigen provider-onafhankelijke templatetaal | Buiten scope. Namen en variabelen volgen Epistola, niet SmartDocuments (besluit van 28 september) |

## 6. Doorgeschoven naar de reflectie (#23)

Dit zijn verbeteringen aan het werkproces. Ze staan hier alleen als verwijzing, zodat het onderscheid tussen product
en proces zichtbaar blijft. De uitwerking hoort in de reflectie.

- Meermaals is geredeneerd vanuit SmartDocuments in plaats van vanuit Epistola's contract. Dat gaf zes fouten in de
  backlog, en later een wireframe met een boom die Epistola niet levert.
- Een voorbeeld in een contract is niet wat de leverancier zelf maakt: het filter dat op het eerste was getest, miste
  wat Epistola's eigen templates gebruiken.
- De keuze voor de API key is twee keer gedraaid. Wat ontbrak, was de bedoeling van de leverancier, en die staat niet in
  het contract.
- Een testomgeving die dagelijks wordt gereset, vraagt een vast herstelscript vóór elke demo.

## 7. Voorstel voor het backlog

Zes voorstellen zijn op 29 september als user story aangemaakt op het bord, in Backlog, met het label `optional` en buiten de
mijlpaal van het prototype. De overige negen blijven in dit document. Ze zijn organisatorisch (VV-02), een vraag aan of een
afhankelijkheid van Epistola (VV-04, VV-08, VV-10), een besluit van de stakeholders (VV-13), een wijziging buiten dit
prototype (VV-11, SmartDocuments), een optioneel restpunt van een opgelost risico (VV-07), staan al op het bord (VV-16 is
[#50](https://github.com/infonl/zac-epistola-prototype/issues/50)), of hebben pas zin na een ander voorstel (VV-15, na de
belastingtest).

| Voorstel | Story |
|---|---|
| VV-01 · [#35](https://github.com/infonl/zac-epistola-prototype/issues/35) | Als beheerder wil ik een beschreven rotatie- en verstrijkprocedure voor de Epistola-sleutel, zodat een sleutel vernieuwd kan worden zonder onderbreking |
| VV-03 · [#36](https://github.com/infonl/zac-epistola-prototype/issues/36) | Als beheerder wil ik dat ZAC weigert te starten met een Epistola-URL zonder `https`, zodat gegevens niet onversleuteld het netwerk op gaan |
| VV-05 · [#37](https://github.com/infonl/zac-epistola-prototype/issues/37) | Als product owner wil ik weten hoeveel gelijktijdige generaties ZAC aankan, zodat ik weet of het verzoek asynchroon moet |
| VV-06 · [#38](https://github.com/infonl/zac-epistola-prototype/issues/38) | Als ontwikkelaar wil ik integratietests met Epistola als provider, zodat het endpoint en de dialoog in de pipeline worden getest |
| VV-09 · [#39](https://github.com/infonl/zac-epistola-prototype/issues/39) | Als beheerder wil ik een waarschuwing bij een template met een vormvrije sectie, zodat ik weet welke gegevens er volledig naar Epistola gaan |
| VV-12 · [#40](https://github.com/infonl/zac-epistola-prototype/issues/40) | Als behandelaar van een BPMN-zaak wil ik een document met Epistola kunnen maken, zodat ik niet van provider hoef te wisselen |

## 8. Openstaande vragen

| Vraag | Aan | Stand |
|---|---|---|
| Dekt de bestaande BRP-doelbinding per zaaktype ook documentcreatie? | Privacy officer | Op 28 september geagendeerd, niet beantwoord |
| Mag document maken alleen vanuit de zaak blijven, niet vanuit een taak? | Stakeholders | Idem (VV-13) |
| Rendertimeout en maximum aantal pogingen | Epistola | Nog niet gesteld (VV-04) |
| Welk contract hoort bij de gerenderde templateversie, en welke velden leest die versie? | Epistola | Nog niet gesteld (VV-10) |

## 9. Wat al is opgelost, met bewijs

| Bevinding | Opgelost in | Nagegaan |
|---|---|---|
| B-01 BPMN-zaak toont niet waarom er geen Epistola-actie is | #7, [PR #33](https://github.com/infonl/zac-epistola-prototype/pull/33) | Live op 29 september; TS-06 opnieuw uitgevoerd op 30 september, geslaagd |
| B-02 Lege lijst zonder melding als Epistola onbereikbaar is | #8, [PR #32](https://github.com/infonl/zac-epistola-prototype/pull/32) | Live op 29 september; TS-31 opnieuw uitgevoerd op 30 september, geslaagd |
| B-03 Contractfout raadt "probeer opnieuw" aan | #8, [PR #32](https://github.com/infonl/zac-epistola-prototype/pull/32) | Live op 29 september; TS-32 opnieuw uitgevoerd op 30 september, geslaagd |
| TS-35 Gedeeltelijke mislukking: opgeslagen, niet gekoppeld | #8: het informatieobject wordt verwijderd | TS-35 uitgevoerd op 30 september, geslaagd: Open Zaak weigerde de koppeling, het document is verwijderd, en de kopie bij Epistola ook |
| B-04 tot en met B-07 | Bij de bouw en de reviews | Nagegaan in het testrapport |
| Nieuwe versie van een Epistola-document (was VV-14, nu gebouwd) | #9, [PR #43](https://github.com/infonl/zac-epistola-prototype/pull/43) | Live op 30 september met de echte testserver; TS-36 t/m TS-41 handmatig, TS-42 en TS-43 met unittests |
| Templatenamen bij een onbereikbare Epistola | #30, [PR #34](https://github.com/infonl/zac-epistola-prototype/pull/34) | Live op 29 september; TS-31 met de namen uit het geheugen op 30 september, geslaagd |

## Verantwoording

Nagelopen op 29 september 2026 tegen de branch `feat/epistola-template-name-cache` (PR #34, gestapeld op #33 en #32):
`EpistolaSettings.kt` (geen controle van het schema, R9), `DocumentCreationService.kt:236` (`OPENBAAR`, R6),
`EpistolaDocumentCreationService.kt` (auteur van het opgeslagen document), ~~`EpistolaTemplateGroup.kt` (hangt aan
`ZaaktypeConfiguration`)~~ *5 oktober:* `ZaaktypeConfiguration.kt` (draagt sinds #51 de catalog en het
informatieobjecttype; `EpistolaTemplateGroup.kt` is weg) en `EpistolaTemplatesService.kt` (biedt alleen bij een
CMMN-zaaktype templates aan), `parameters-edit-cmmn.component.html` (de beheerkaart bestaat alleen in het CMMN-scherm),
`scripts/docker-compose/imports/epistola-wiremock/mappings/` (~~zeven~~ negen mappings sinds #54 en #51), en de chart-README en `.env.example`
(geen rotatie- of verstrijktekst). Op 6 oktober is de lijst opnieuw naast de code gelegd (stapel tot en met #60): er is nog geen controle op `https` (VV-03), geen
rotatietekst (VV-01), geen opstartcontrole (VV-07), geen waarschuwing bij een vormvrije sectie (VV-09), geen
integratietest met Epistola als provider (VV-06), geen document vanuit een taak (VV-13) en geen BPMN-ondersteuning (VV-12); de
client staat wel op 1.4.0 (#42) en een nieuwe versie van een document is gebouwd (#9). De Epistola-kant komt uit de reviews op #24 (contract 1.3.1, Epistola Suite
`3c92193`) en uit de toets van de wachtrij op 28 september (Epistola Suite `e2484c7`, contract `257770d`).
