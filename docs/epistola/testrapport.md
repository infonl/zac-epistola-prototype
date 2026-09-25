# Testrapport — Epistola-integratie in ZAC

| | |
|---|---|
| Opdracht | Opdracht 3 – B1-K1-W4 Test software |
| Issue | [#19](https://github.com/infonl/zac-epistola-prototype/issues/19) · uitgevoerd volgens [het testplan](testplan.md) v1.1 (#18) |
| Projectnaam | Epistola-integratie in ZAC (zaakafhandelcomponent) |
| Student | Symon Vleeshouwers (198462) |
| Klas | ZWSD24 |
| Datum | 25 september 2026 |

> **Beoordelingscriterium – T3 Testrapport:** testresultaten van alle functionaliteiten, met juiste
> conclusies. Elke functionaliteit uit §3 van het testplan staat in [§2](#2-testresultaten-per-functionaliteit),
> elk scenario met zijn bewijs in [bijlage A](#bijlage-a--testuitvoering-per-scenario).

## 1. Samenvatting

| | |
|---|---|
| Projectnaam | Epistola-integratie in ZAC |
| Geteste versie | `6a91d8da2` op branch `feat/epistola-document-dialog` ([PR #29](https://github.com/infonl/zac-epistola-prototype/pull/29)), met daaronder #27, #25 en #24. ZAC-image `0290677afd13`, gebouwd van die commit |
| Testperiode | 25 september 2026 |
| Geteste functionaliteiten | 10 — de acht gebieden uit de examenafspraken en de twee randvoorwaarden uit de DoD |
| Aantal testscenario's uitgevoerd | 35 van de 36 (TS-09 is bij de uitvoering gesplitst in TS-09a en TS-09b). TS-35 was niet uitvoerbaar |
| Totaal geslaagd | 32 van de 35 uitgevoerde scenario's (91 %) |
| Totaal mislukt | 3 van de 35: TS-06, TS-31 en TS-32 |
| Gevonden bugs | 7 bugs: 4 opgelost, 3 open. De open bugs horen bij de nog niet gebouwde issues #7 en #8 |
| Geautomatiseerde tests | 2634 backend-unittests, 2960 frontend-unittests, de live-check (8 controles) en 386 integratietests. Alle geslaagd |

## 2. Testresultaten per functionaliteit

| # | Functionaliteit | Testscenario ID | Uitkomst | Bugs gevonden | Conclusie |
|---|---|---|---|---|---|
| 1 | Configuratie: providerkeuze en instellingen (#2) | TS-01, TS-02 | ✅ Geslaagd | Nee | ZAC start met Epistola en weigert een onjuiste configuratie |
| 2 | Configuratie: templategroepen en templates per zaaktype (#3) | TS-03, TS-04, TS-05, TS-07 | ✅ Geslaagd | Nee | De beheerder richt groepen in, en een ongeldige mapping wordt geweigerd. Epistola uitzetten haalt de actie weg en bewaart de mapping |
| 3 | Autorisatie (#11) | TS-08, TS-09a, TS-09b, TS-10 | ✅ Geslaagd | Nee | Alleen wie `creeren_document` heeft en de zaak mag lezen, kan een document maken. Het endpoint dwingt dat af met 403 |
| 4 | Documentcreatie (#5) | TS-11 t/m TS-16 | ✅ Geslaagd | Ja, B-04 (opgelost) | Genereren werkt vanuit de zaak, alleen als PDF, met voortgang, in 5 tot 10 seconden |
| 5 | Datamapping (#4) | TS-17 t/m TS-22 | ✅ Geslaagd | Ja, B-05 (opgelost) | Het document toont de zaakgegevens, en alleen gedeclareerde velden verlaten ZAC. Datums gaan als ISO en worden als `dd-MM-yyyy` getoond |
| 6 | Open Zaak-opslag (#6) | TS-23 t/m TS-26 | ✅ Geslaagd | Nee | Opgeslagen als PDF met alle verplichte velden. Vertrouwelijkheid uit het documenttype, en de kopie bij Epistola is weg |
| 7 | Zaakkoppeling (#6) | TS-27, TS-28 | ✅ Geslaagd | Nee | Het document staat direct in het tabblad Documenten van de zaak |
| 8 | Preview (#6) | TS-29, TS-30 | ✅ Geslaagd | Nee | De PDF opent in de browser, met alle metadata |
| 9 | Foutafhandeling (#8) | TS-31 t/m TS-35 | ❌ Mislukt: 2 van de 4 uitgevoerde scenario's | Ja: B-02 en B-03 (open), B-06 (opgelost) | Er gaat niets verloren en er blijft niets half achter, maar de meldingen leggen niet uit wat er mis is |
| 10 | Randvoorwaarden: SmartDocuments ongewijzigd, alleen CMMN (DoD 1 en 2) | TS-22, TS-06 | ❌ Mislukt: TS-06 | Ja: B-01 (open) | SmartDocuments werkt ongewijzigd. Een BPMN-zaak kan geen Epistola-document maken, maar de gebruiker ziet niet waarom |

## 3. Conclusies per functionaliteit

### Functionaliteit 1 – Configuratie: providerkeuze en instellingen

ZAC start met `DOCUMENT_CREATION_PROVIDER=EPISTOLA` en zegt dat in het log (TS-01). Een tegenstrijdige of
onvolledige configuratie laat ZAC niet starten, met een melding die de instelling noemt. Voorbeelden zijn
SmartDocuments en Epistola tegelijk, een ontbrekende API key, of een tenant- of catalogus-id die Epistola niet
accepteert (TS-02, twintig geautomatiseerde tests). Werkt correct.

### Functionaliteit 2 – Configuratie: templategroepen en templates per zaaktype

De beheerder voegde een tweede template aan een groep toe, sloeg op en zag het na het herladen terug, met zijn
eigen documenttype (TS-03). Een tweede groep met dezelfde naam gaf direct de melding *A template group with
this name already exists*, en *Save* bleef uitgeschakeld (TS-04). Met Epistola uit bij een zaaktype verdween
*Create document* van de zaak, en weigerde het endpoint met 400. Na het weer aanzetten kwam de oude mapping
terug (TS-05). De mapping gaat mee naar een nieuwe zaaktypeversie (TS-07, geautomatiseerd). Werkt correct.

### Functionaliteit 3 – Autorisatie

De behandelaar ziet de actie en kan genereren (TS-08). Twee negatieve gevallen zijn met de hand bevestigd:
- een gebruiker die de zaak niet mag lezen, krijgt 403 op het endpoint (TS-09a);
- een raadpleger ziet de zaak zonder één actie, en krijgt 403 als hij het verzoek toch stuurt (TS-09b).

In beide gevallen kwam er geen document bij. De taakrechten zijn geautomatiseerd getest (TS-10). De controle
zit aan de serverkant en gebeurt voordat er iets naar Epistola gaat. Werkt correct.

### Functionaliteit 4 – Documentcreatie

De behandelaar genereert vanuit de zaak een PDF, en de melding *Document "Testrapport TS-11" has been added to
the case* verschijnt (TS-11). De dialoog biedt alleen PDF, vraagt geen datum en zet de ingelogde gebruiker vast
als auteur (TS-12). Tijdens het genereren draait een spinner, zijn beide knoppen uitgeschakeld en staat er dat
het even kan duren (TS-13). Zonder titel kan er niet gegenereerd worden (TS-14). Een template dat het zaaktype
niet aanbiedt, wordt geweigerd (TS-15). Het genereren duurde 5,8 en 8,9 seconden, ruim binnen de timeout van 60
seconden (TS-16). Tijdens de voorbereiding bleek het Engelse label *format* niet met een hoofdletter te
beginnen. Dat is opgelost (B-04), en TS-12 heeft het nagegaan. Werkt correct.

### Functionaliteit 5 – Datamapping

De gerenderde brief toont zaaknummer, zaaktype, status, groep, communicatiekanaal en de naam van de initiator
uit de BRP. Datums staan er als `28-08-2026` en `27-09-2026` (TS-17). Gegevens die de zaak niet heeft, zoals de
behandelaar, blijven leeg, zonder "null" en zonder fout (TS-20). De live-check bevestigt twee dingen rechtstreeks
bij Epistola. Van tien geplante, niet-gedeclareerde waarden haalde er geen enkele de payload of de PDF (TS-18).
En een datum gaat als `2026-09-01` naar een contract met `format: date`, en verschijnt als `01-09-2026` (TS-19).
Die datumvorm was een bug uit de review van #24 (B-05): Epistola's editor had elke brief met een datum laten
mislukken. De SmartDocuments-deposit houdt `dd-MM-yyyy` (TS-22). Werkt correct.

### Functionaliteit 6 – Open Zaak-opslag

Het document staat in de Documenten API met `formaat` `application/pdf`, bestandsnaam `Testrapport
TS-11.pdf`, grootte, status *in bewerking*, taal, auteur en versie 1 (TS-23). De vertrouwelijkheid volgt het
documenttype: *zaakvertrouwelijk* bij *e-mail*, *openbaar* bij *brief* (TS-24). Het vaste *openbaar* van de
SmartDocuments-flow geldt hier dus niet. Na het opslaan heeft Epistola geen document meer voor de zaak (TS-25).
Mislukt het opslaan, dan blijft de kopie bij Epistola staan (TS-26, geautomatiseerd). Werkt correct.

### Functionaliteit 7 – Zaakkoppeling

Het document stond direct na het genereren in het tabblad Documenten, zonder de pagina te verversen. De
documentpagina noemt de zaak (TS-27). De koppeling aan een taak is alleen geautomatiseerd getest (TS-28),
omdat de dialoog nog niet vanuit een taak te openen is. Werkt correct, met die beperking.

### Functionaliteit 8 – Preview

Een klik op de titel opent de PDF in de browser (TS-29). De documentpagina toont alle metadata en de preview
(TS-30). Werkt correct.

### Functionaliteit 9 – Foutafhandeling

In elk foutgeval gaat er niets verloren en blijft er niets half achter: geen document in Open Zaak, geen
halve koppeling, en de dialoog blijft open. Maar de gebruiker krijgt nergens een melding die zegt wat er mis
is.
- **Epistola onbereikbaar (TS-31):** de lijst met sjabloongroepen blijft leeg, zonder melding, en een direct
  verzoek geeft een algemene 500 (B-02).
- **Data die het contract van het template breekt (TS-32):** de melding *The document could not be created.
  Try again or contact your administrator.* raadt een nieuwe poging aan, maar die helpt niet. De echte reden,
  `required property 'aanvrager' not found`, staat alleen in het log (B-03).
- **Timeout (TS-33, geautomatiseerd):** ZAC annuleert de job en meldt dat het te lang duurde. Het annuleren
  was een bug uit de review van #24 (B-06).
- **Verdwenen template (TS-34):** een template dat bij Epistola verdwijnt, wordt netjes niet meer aangeboden.
- **Gedeeltelijke mislukking (TS-35):** niet uitgevoerd, omdat de afhandeling nog niet bestaat.

De impact is dat een behandelaar bij een storing of een configuratiefout niet weet of hij moet wachten of de
beheerder moet bellen. Dit is het werk van #8.

### Functionaliteit 10 – Randvoorwaarden

SmartDocuments blijft werken. De deposit is ongewijzigd, en de SmartDocuments-integratietests slagen (TS-22,
DoD 1). Een BPMN-zaaktype heeft geen Epistola-kaart, een BPMN-zaak heeft geen *Create document*, en het
endpoint weigert een BPMN-zaak (TS-06). Maar DoD 2 vraagt ook dat de beperking zichtbaar is in de UI, en een
BPMN-zaak zegt nergens dat documenten maken daar niet kan (B-01). Dat is #7.

## 4. Gevonden bugs en status

| Bug # | Scenario | Beschrijving | Ernst | Opgelost? | Toelichting |
|---|---|---|---|---|---|
| B-01 | TS-06 | Een BPMN-zaak toont nergens dat documenten maken met Epistola alleen voor CMMN-zaken kan. De actie ontbreekt zonder uitleg | Midden | Nee | Open, hoort bij #7 (DoD 2). Het endpoint weigert BPMN-zaken wel |
| B-02 | TS-31 | Als Epistola onbereikbaar is, blijft de lijst met sjabloongroepen leeg zonder melding. Het mapping-endpoint en het genereer-endpoint geven een algemene 500 | Hoog | Nee | Open, hoort bij #8. Er wordt niets opgeslagen en ZAC herstelt vanzelf zodra Epistola terug is |
| B-03 | TS-32 | Data die het contract van het template breekt, geeft de algemene melding *probeer het opnieuw*, terwijl opnieuw proberen niet helpt. De reden staat alleen in het log | Midden | Nee | Open, hoort bij #8. Epistola's volgende contractrelease levert `missingFields` per veld, waarmee de melding het ontbrekende veld kan noemen |
| B-04 | TS-12 | Het Engelse label van het formaatveld was *format* met een kleine letter, tussen labels die met een hoofdletter beginnen | Laag | Ja | Gevonden bij de eind-tot-eindproef van 25 september. Opgelost in `6a91d8da2` (*File format*), en nagegaan in TS-12 |
| B-05 | TS-19 | Datums gingen als `dd-MM-yyyy` naar Epistola. Een template dat Epistola's editor maakt (`format: date`, draft-07) liet elke job daardoor mislukken | Hoog | Ja | Gevonden bij de review van #24 en bevestigd met een proef op de testserver. Opgelost in `a56fc2401`, nagegaan in TS-19 |
| B-06 | TS-33 | Bij een timeout bleef de job bij Epistola lopen, en de melding zei "controleer later" terwijl niets het document later toevoegt. Een fout tijdens het pollen annuleerde de job ook niet | Midden | Ja | Gevonden bij de review van #24 en de tweede Copilot-review. Opgelost in `0d3aa6ca7` en `b440d8f53`, nagegaan met unittests (TS-33) |
| B-07 | TS-05 | Een template dat het zaaktype aanbood, werd niet geweigerd terwijl de beheerder Epistola voor het zaaktype had uitgezet. De controle uit #27 keek alleen naar de mapping | Midden | Ja | Gevonden bij de bouw van #5. Opgelost in `ce1c8d806`, nagegaan in TS-05 |

### Bekende beperkingen

Deze staan niet als bug in de lijst, omdat ze bewust buiten de scope van het prototype vallen of buiten ZAC
liggen:

- **Gedeeltelijke mislukking** (TS-35): opgeslagen in de Documenten API, maar niet gekoppeld aan de zaak. Er
  bestaat nog geen afhandeling voor (#8).
- **Document maken vanuit een taak:** de dialoog is alleen vanuit de zaak te openen. Het endpoint ondersteunt
  een taak wel (TS-28).
- **De integratietests draaien met SmartDocuments.** Ze kunnen de Epistola-endpoints niet aanroepen. Die zijn
  getest met unittests, de live-check en de handmatige scenario's.
- **Epistola's testtenant wordt dagelijks gereset.** Een template dat verdwijnt, handelt ZAC netjes af (TS-34),
  maar vóór elke demo moeten de testtemplates opnieuw worden aangemaakt.
- **Buiten ZAC**, vastgelegd in het ontwerp (§3 en §5): Epistola's `validate` toetst tegen een conceptcontract,
  de allow-list kan een nieuwer contract volgen dan de gerenderde templateversie, en Epistola's bewaartermijn is
  drie tot vier maanden in plaats van de genoemde dertig dagen.

## 5. Eindconclusie

Het prototype voldoet aan de acceptatiecriteria voor de hoofdstroom: van de configuratie door de beheerder,
via de autorisatie, het genereren en de datamapping, tot de opslag in Open Zaak, de koppeling aan de zaak en de
preview. Alle 30 scenario's in die gebieden zijn geslaagd, met bewijs in bijlage A. Ook de geautomatiseerde
tests zijn allemaal groen: 2634 backend-, 2960 frontend- en 386 integratietests, en de
live-check. SmartDocuments werkt ongewijzigd, dus de eerste randvoorwaarde uit de DoD is gehaald.

Drie scenario's zijn mislukt. Ze hebben dezelfde oorzaak: de meldingen voor de gebruiker (#8) en de melding
voor BPMN-zaken (#7) zijn nog niet gebouwd. In geen van die gevallen gaat er iets verloren of blijft er iets half
achter, maar de gebruiker weet niet wat er mis is of wat hij moet doen. De vier andere bugs zijn bij de bouw, de
reviews en de eind-tot-eindproef gevonden, opgelost en in deze ronde nagegaan.

Voor een demonstratie en de gebruikerstest bij de einddemo is het prototype klaar. Voor oplevering als
afgerond product niet: #7 en #8 moeten eerst gebouwd worden, en daarna moeten TS-06, TS-31, TS-32 en TS-35
opnieuw worden uitgevoerd. Ook ontbreekt nog het akkoord van de stakeholders op het testplan (#21).

## 6. Aanbevelingen

- **Bouw #8 en test de foutpaden opnieuw.** Vertaal Epistola's 4xx/5xx en een onbereikbaar Epistola naar een
  melding die zegt wat er mis is. Een contractfout hoort geen nieuwe poging aan te raden: gebruik daar de
  `missingFields` uit Epistola's volgende contractrelease. Laat de dialoog ook melden dat de sjablonen niet
  geladen konden worden, in plaats van een lege lijst te tonen.
- **Bouw de BPMN-melding uit #7**, bijvoorbeeld een uitgeschakelde actie met een toelichting, zodat DoD 2
  helemaal gehaald wordt.
- **Neem een Epistola-stand-in op in de integratietests.** De WireMock-mappings uit `-W` bestaan al, en ermee
  worden het endpoint en de dialoog ook in de pipeline getest, niet alleen met de hand.
- **Test met meerdere gelijktijdige gebruikers.** Het verzoek wacht op Epistola en houdt zolang een thread vast.
  Bij één behandelaar is dat 5 tot 10 seconden, maar niemand heeft het onder belasting gemeten.
- **Test in meer browsers dan Chrome**, en voer de gebruikerstest (UAT) uit met de stakeholders bij de einddemo
  (#22).

## 7. Zelfbeoordeling rubric

*In te vullen door de kandidaat.* Vul voor jezelf in hoe je denkt te scoren (0 = niet/nauwelijks,
3 = volledig).

| Criterium | 0 | 1 | 2 | 3 (volledig) |
|---|---|---|---|---|
| T1 Testplan – testcases sluiten aan op alle functionaliteiten | ☐ | ☐ | ☐ | ☐ |
| T1 Testscenario – voor alle functionaliteiten gemaakt | ☐ | ☐ | ☐ | ☐ |
| T2 Testen – correct uitgevoerd volgens testplan | ☐ | ☐ | ☐ | ☐ |
| T3 Testrapport – resultaten en conclusies volledig | ☐ | ☐ | ☐ | ☐ |

**Toelichting op de zelfbeoordeling:** *in te vullen door de kandidaat.*

---

## Bijlage A — Testuitvoering per scenario

Uitgevoerd in de ochtend van 25 september 2026. De stack is gestart met `./start-docker-compose.sh -l
-E`, met de ZAC-image van de geteste versie, in Google Chrome 153.0.8010.53, bestuurd door Playwright. Beide
testtemplates waren die ochtend opnieuw aangemaakt. Bij *Handmatig* staat het werkelijke resultaat en het
bewijs. Bij *Automatisch* staan de tests uit de testrun van [bijlage B](#bijlage-b--geautomatiseerde-tests).

| # | Gebruiker | Werkelijk resultaat | Uitkomst | Bewijs |
|---|---|---|---|---|
| TS-01 | — | Log: `Active document creation provider: 'EPISTOLA' (DOCUMENT_CREATION_PROVIDER='EPISTOLA', SMARTDOCUMENTS_ENABLED='false')`. WildFly gestart, 0 regels met `ERROR` | ✅ | ZAC-log; `DocumentCreationProviderConfigurationTest` (20 tests) |
| TS-02 | — | Tegenstrijdige providers, een ontbrekende API key, en een tenant- of catalogus-id die geen slug is (te kort, met spaties, verkeerd patroon): alle geweigerd bij het opstarten, met de naam van de instelling in de melding | ✅ | `DocumentCreationProviderConfigurationTest` |
| TS-03 | beheerder1 | *ZAC Verplichte aanvrager* toegevoegd aan *Brieven* met documenttype *bijlage*. Opslaan gaf `PUT /rest/zaakafhandelparameters` 200 en `POST …/epistola-templates-mapping` 204. Na het herladen stonden beide templates in de groep. In de dialoog (TS-24) bood *Brieven* daarna beide aan | ✅ | [voor](testrapport/ts-03-voor.png) · [na opslaan en herladen](testrapport/ts-03-na-opslaan.png) |
| TS-04 | beheerder1 | Tweede groep *Brieven*: melding *A template group with this name already exists*, *Save* uitgeschakeld. De groep weggehaald, niets opgeslagen | ✅ | [screenshot](testrapport/ts-04-dubbele-groepsnaam.png); `RestEpistolaTemplateMappingValidatorTest` |
| TS-05 | beheerder1 | Epistola uit bij *Test zaaktype 2*: kaart meldt *switched off*, database `epistola_ingeschakeld = f`, groep bewaard. ZAAK-2026-0000000032 zonder *Create document*, direct verzoek 400 `msg.error.epistola.template.not-configured`. Weer aan: oude mapping terug, *Create document* terug | ✅ | [Epistola uit](testrapport/ts-05-epistola-uit-geen-actie.png) · [weer aan](testrapport/ts-05-epistola-weer-aan.png) |
| TS-06 | beheerder1 | *BPMN test zaaktype 1* heeft geen Epistola-kaart. ZAAK-2026-0000000035 heeft geen *Create document* en het endpoint weigert (400). Maar nergens staat dat documenten maken voor BPMN-zaken niet kan | ❌ (B-01) | [beheer](testrapport/ts-06-bpmn-geen-epistola-kaart.png) · [BPMN-zaak](testrapport/ts-06-bpmn-zaak-geen-melding.png) |
| TS-07 | — | De mapping gaat mee naar een nieuwe zaaktypeversie, ook zonder Epistola | ✅ | `ZaaktypeCmmnConfigurationBeheerServiceTest` (19 tests), `EpistolaTemplatesServiceTest` |
| TS-08 | behandelaar1 | ZAAK-2026-0000000032 toont *Create document* | ✅ | [screenshot](testrapport/ts-08-behandelaar-ziet-document-maken.png) |
| TS-09a | behandelaar1 | ZAAK-2026-0000000001 is niet leesbaar voor deze gebruiker (403 op de zaak). Het verzoek naar het endpoint gaf 403 `msg.error.server.forbidden` | ✅ | Netwerkverzoek; `DocumentCreationRestServiceTest` |
| TS-09b | raadpleger1 | ZAAK-2026-0000000032 zichtbaar met documentenlijst, zonder één actie in het menu. Het verzoek gaf 403 `msg.error.server.forbidden`, en de zaak hield twee documenten | ✅ | [screenshot](testrapport/ts-09b-raadpleger-geen-document-maken.png); `zaak-view-menu.builder.spec.ts` |
| TS-10 | — | Geweigerd zonder taakrecht (403) en voor een taak die niet meer open is, telkens zonder aanroep van Epistola | ✅ | `DocumentCreationRestServiceTest` (17 tests) |
| TS-11 | behandelaar1 | Melding *Document "Testrapport TS-11" has been added to the case*. De dialoog sloot en er opende geen wizard | ✅ | [screenshot](testrapport/ts-27-documenten-tab.png); component spec |
| TS-12 | behandelaar1 | *File format* = *PDF*, niet te wijzigen. Geen creatiedatum. *Author* = *Test Behandelaar 1*, niet te wijzigen. Documenttype *brief* en vertrouwelijkheid *Public* uit de mapping | ✅ | [screenshot](testrapport/ts-12-ts-14-dialoog-zonder-titel.png) |
| TS-13 | behandelaar1 | 0,4 seconde na de klik: spinner op *Generate*, *Generate* en *Cancel* uitgeschakeld, tekst *The document is being generated. This can take a few seconds.* | ✅ | [screenshot](testrapport/ts-13-bezig-met-genereren.png) |
| TS-14 | behandelaar1 | Groep en template ingevuld, titel leeg: *Generate* uitgeschakeld | ✅ | [screenshot](testrapport/ts-12-ts-14-dialoog-zonder-titel.png) |
| TS-15 | behandelaar1 | `templateId` `niet-aangeboden`: 400 `msg.error.epistola.template.not-configured`. De zaak hield dezelfde twee documenten | ✅ | Netwerkverzoek; `EpistolaTemplatesServiceTest` |
| TS-16 | behandelaar1 | Genereren duurde 5,75 seconden (TS-11) en 8,9 seconden tot de melding (TS-13). De mislukte job van TS-32 kwam na 10,3 seconden terug. Timeout: 60 seconden | ✅ | Resource timing van het verzoek |
| TS-17 | behandelaar1 | De brief toont *Kenmerk: ZAAK-2026-0000000032*, *Datum: 28-08-2026*, *type Test zaaktype 2*, *status is Intake*, *Test group behandelaars domein test 1*, *via E-mail*, *Geachte Héndrika Janse*, *uiterlijk af op 27-09-2026* | ✅ | [preview](testrapport/ts-29-preview.png) |
| TS-18 | — | Live-check: *no planted undeclared value reaches the payload — 10 planted values checked*, *every field in the payload is declared by the template*, *the rendered PDF shows none of the planted undeclared values* | ✅ | Live-check (bijlage B); `EpistolaTemplateDataTest` (25 tests) |
| TS-19 | — | Live-check: *a date reaches Epistola in ISO 8601 — 2026-09-01*, en de PDF toont `01-09-2026` | ✅ | Live-check; `EpistolaTemplateDataTest` |
| TS-20 | behandelaar1 | *Uw zaak wordt behandeld door .* en *De geplande einddatum is .*: leeg, geen "null", geen fout | ✅ | [preview](testrapport/ts-29-preview.png); `DocumentCreationDataServiceTest` |
| TS-21 | — | Een template zonder schema wordt geweigerd, en `generateDocument` wordt niet aangeroepen | ✅ | `EpistolaDocumentCreationServiceTest` |
| TS-22 | — | De deposit houdt `"startdatum":"01-01-2026"`. De 386 integratietests slagen, waaronder de 14 SmartDocuments-documentcreatietests (`DocumentCreationRestServiceTest`) en de 6 SmartDocuments-configuratietests | ✅ | `EpistolaTemplateDataTest`; integratietests (bijlage B) |
| TS-23 | behandelaar1 | DOCUMENT-2026-0000000091: `formaat` `application/pdf`, `bestandsnaam` `Testrapport TS-11.pdf`, `bestandsomvang` 32.265, `status` `in_bewerking`, `taal` Nederlands, `auteur` Test Behandelaar 1, type *brief*, `creatiedatum` 2026-09-25, versie 1 | ✅ | [documentpagina](testrapport/ts-30-documentpagina.png); REST-antwoord |
| TS-24 | beheerder1 | DOCUMENT-2026-0000000093 op ZAAK-2026-0000000001, type *e-mail*: `vertrouwelijkheidaanduiding` `ZAAKVERTROUWELIJK`. Ter vergelijking TS-23, type *brief*: `OPENBAAR` | ✅ | [dialoog](testrapport/ts-24-dialoog-zaakvertrouwelijk.png) · [documentenlijst](testrapport/ts-24-documenten-zaakvertrouwelijk.png) |
| TS-25 | behandelaar1 | Bij Epistola: *documents still at Epistola for this zaak: count 0*. Het ZAC-log toont geen mislukte verwijdering | ✅ | `epistola_copy_check.py` |
| TS-26 | — | Opslaan mislukt: de fout komt bij de aanroeper, en `deleteDocument` wordt niet aangeroepen | ✅ | `EpistolaDocumentCreationServiceTest` |
| TS-27 | behandelaar1 | Direct na de melding stonden *Testrapport TS-11* en *Testrapport TS-13* in het tabblad Documenten (brief, 32 kB, Being modified, Public), zonder verversen | ✅ | [screenshot](testrapport/ts-27-documenten-tab.png) |
| TS-28 | — | Met een `taskId` wordt het document aan de zaak en aan de taak gekoppeld, met de taakpolicy | ✅ | `EpistolaDocumentCreationServiceTest`, `DocumentCreationRestServiceTest` |
| TS-29 | behandelaar1 | Een klik op de titel opende de PDF-viewer met *ZAC Standaardbrief*, 1 pagina, de gerenderde brief | ✅ | [screenshot](testrapport/ts-29-preview.png) |
| TS-30 | behandelaar1 | De documentpagina toont identificatie, datums, titel, auteur, taal, type, bestandsnaam, grootte, status, vertrouwelijkheid, versie, beschrijving, de zaak en de preview | ✅ | [screenshot](testrapport/ts-30-documentpagina.png) |
| TS-31 | behandelaar1 | Met `demo.epistola.app` op `127.0.0.1` gaf het log *Connection refused*. De sjabloongroepen bleven leeg, zonder melding, want het mapping-endpoint gaf 500. Een direct verzoek gaf na 0,96 seconde 500 `msg.error.server.generic`, en er kwam geen document bij. Na het terugzetten van de hosts-regel bereikte ZAC Epistola meteen weer | ❌ (B-02) | [screenshot](testrapport/ts-31-epistola-onbereikbaar.png); ZAC-log |
| TS-32 | beheerder1 | Na 10,3 seconden 500 `msg.error.epistola.generation.failed`, met de melding *The document could not be created. Try again or contact your administrator.* De dialoog bleef open en er kwam geen document bij. Het log: *Data validation failed: : required property 'aanvrager' not found* | ❌ (B-03) | [screenshot](testrapport/ts-32-contract-gebroken.png); ZAC-log |
| TS-33 | — | Bij een timeout, en ook bij een 503 tijdens het pollen, annuleert ZAC de job. Een geweigerde annulering verandert de melding niet | ✅ | `EpistolaClientServiceTest` (23 tests) |
| TS-34 | beheerder1 | Na het verwijderen bij Epistola bood *Brieven* alleen nog *ZAC Standaardbrief* aan, zonder foutmelding. De mapping-rij voor `zac-verplichte-aanvrager` staat nog in de database | ✅ | [screenshot](testrapport/ts-34-template-verdwenen.png); `psql` |
| TS-35 | — | Niet uitgevoerd: de afhandeling van een gedeeltelijke mislukking bestaat nog niet (#8) | ⏸ | — |

### Testdata na afloop

- Op ZAAK-2026-0000000032 staan twee testdocumenten (*Testrapport TS-11*, *TS-13*), op ZAAK-2026-0000000001 één
  (*Testrapport TS-24*).
- *Test zaaktype 1* heeft nog een mapping-rij voor het verwijderde template `zac-verplichte-aanvrager`. Die
  staat nergens in beeld, en de volgende keer dat de beheerder opslaat, verdwijnt hij.
- *Test zaaktype 2* staat weer aan, met zijn oorspronkelijke mapping.

## Bijlage B — Geautomatiseerde tests

Alle runs op de geteste versie `6a91d8da2`, op 25 september 2026.

| Suite | Commando | Resultaat |
|---|---|---|
| Backend-unittests (Kotest, MockK) | `./gradlew test` | 2634 tests, 0 mislukt, 0 overgeslagen |
| Frontend-unittests (Jest, Testing Library) | `npm test` | 255 suites, 2960 tests, 0 mislukt |
| Integratietests (TestContainers, SmartDocuments als provider) | `./gradlew itest` | 386 tests, 0 mislukt, in 8 min 21 s (12:01–12:09). De taak bouwt zijn eigen image van dezelfde commit. Waaronder 14 in `DocumentCreationRestServiceTest` (de SmartDocuments-flow) en 6 in `ZaaktypeCmmnConfigurationRestServiceSmartDocumentsTest` en `ZaaktypeBpmnConfigurationRestServiceSmartDocumentsTest` |
| Live-check tegen Epistola's testserver | harness en `check_live_run.py` in `~/Documents/Exam/epistola-live-check/` | 8 van de 8 controles geslaagd |

De backendtests voor de Epistola-onderdelen, per testklasse:

| Testklasse | Tests | Dekt |
|---|---|---|
| `DocumentCreationProviderConfigurationTest` | 20 | TS-01, TS-02 |
| `EpistolaTemplatesServiceTest` | 19 | TS-03, TS-05, TS-07, TS-15 |
| `RestEpistolaTemplateMappingValidatorTest` | 7 | TS-04 |
| `RestMappedEpistolaTemplateGroupTest`, `EpistolaTemplateGroupTest`, `EpistolaTemplateGroupRepositoryTest` | 8 | TS-03 |
| `ZaaktypeCmmnConfigurationBeheerServiceTest` | 19 | TS-07 |
| `ZaaktypeConfigurationRestServiceTest`, `RestZaakafhandelParametersConverterTest` | 17 | TS-03, TS-05 |
| `DocumentCreationRestServiceTest` | 17 | TS-09, TS-10 |
| `EpistolaDocumentCreationServiceTest` | 11 | TS-11, TS-21, TS-23, TS-24, TS-26, TS-28 |
| `EpistolaTemplateDataTest` | 25 | TS-18, TS-19, TS-22 |
| `DocumentCreationDataServiceTest` | 18 | TS-17, TS-20 |
| `EpistolaClientServiceTest`, `EpistolaClientServiceRequestTest` | 29 | TS-25, TS-33 |
| `DocumentCreationServiceTest`, `DocumentCreationUserStoreTest` | 12 | TS-22 (SmartDocuments) |

In de frontend dekken `informatie-object-create-attended.component.spec.ts` (TS-11 t/m TS-14),
`zaak-view-menu.builder.spec.ts` (TS-05, TS-08, TS-09) en `epistola-templates-form.component.spec.ts` (TS-03,
TS-04) de Epistola-onderdelen.
