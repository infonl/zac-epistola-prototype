# Testplan — Epistola-integratie in ZAC

| | |
|---|---|
| Opdracht | Opdracht 3 – B1-K1-W4 Test software |
| Issue | [#18](https://github.com/infonl/zac-epistola-prototype/issues/18) · uitvoering en resultaten in [het testrapport](testrapport.md) (#19) |
| Projectnaam | Epistola-integratie in ZAC (zaakafhandelcomponent) |
| Student | Symon Vleeshouwers (198462) |
| Klas | ZWSD24 |
| Datum | 25 september 2026 |
| Versie | 1.1 — concept, ter akkoord bij de stakeholders (#21) |
| Geteste versie | Branch `feat/epistola-document-dialog` op `6a91d8da2` ([PR #29](https://github.com/infonl/zac-epistola-prototype/pull/29)). Daaronder liggen #27, #25 en #24, dus deze versie bevat alle Epistola-onderdelen |

> **Beoordelingscriterium – T1 Testplan (cruciaal):** testcases sluiten aan op alle functionaliteiten en
> bevatten alle scenario's. De functionaliteiten uit [§3](#3-testscope) komen elk terug in [§8](#8-testcases).

## 1. Inleiding

### Doel van dit testplan

Dit testplan beschrijft hoe het prototype wordt getest dat ZAC documenten laat maken met Epistola in plaats
van met SmartDocuments. Het dekt de acht gebieden uit de examenafspraken: configuratie, autorisatie,
documentcreatie, datamapping, Open Zaak-opslag, zaakkoppeling, preview en foutafhandeling. Daarnaast dekt
het de twee randvoorwaarden uit de Definition of Done: SmartDocuments blijft werken (DoD 1) en Epistola werkt
alleen voor CMMN-zaken (DoD 2).

De opdrachtgever (Dimpact, met Team Geneva als eigenaar van ZAC) krijgt daarmee zekerheid op twee punten:

- **wat werkt**, met bewijs per scenario: een testresultaat, een screenshot of een geautomatiseerde test;
- **wat niet werkt of nog niet gebouwd is**, vooraf benoemd als verwachte beperking, zodat het testrapport
  dat niet achteraf hoeft te ontdekken.

DoD-item 8 vraagt om *overeengekomen* testcases. Deze versie 1.0 gaat daarom ter akkoord naar het
stakeholderoverleg (#21). De eerste uitvoering op 25 september staat in het testrapport. Worden er bij het
akkoord scenario's gewijzigd of toegevoegd, dan worden die opnieuw uitgevoerd en krijgt dit plan een nieuwe
versie.

### Versie

| Versie | Datum | Wijziging |
|---|---|---|
| 1.0 | 25 september 2026 | Eerste versie, voor akkoord |
| 1.1 | 25 september 2026 | Bijgesteld tijdens de eerste uitvoering: `behandelaar1` is niet geautoriseerd voor *Test zaaktype 1*, dus de behandelaarscenario's draaien op ZAAK-2026-0000000032 (*Test zaaktype 2*) en de scenario's met *ZAC Verplichte aanvrager* als `beheerder1` op ZAAK-2026-0000000001. TS-09 is gesplitst in TS-09a en TS-09b. Het pad naar de beheerkaart is rechtgezet |

## 2. Testdoelstellingen

- [x] **Functionaliteit** — werkt elke functie zoals de user stories (#2–#11) en de DoD het beschrijven?
- [x] **Beveiliging** — mag alleen een geautoriseerde gebruiker een document maken, en wordt dat aan de
  serverkant afgedwongen? Gaan alleen de velden naar Epistola die het template declareert (dataminimalisatie,
  #16)?
- [x] **Gebruikersvriendelijkheid** — ziet de behandelaar hetzelfde scherm als bij SmartDocuments? Laat het
  scherm zien dat er gegenereerd wordt? Zijn de meldingen begrijpelijk?
- [x] **Prestaties** — is het document er binnen de begrensde wachttijd (`EPISTOLA_GENERATION_TIMEOUT_SECONDS`,
  standaard 60 seconden), en hoe lang duurt het in de praktijk?
- [x] **Regressie** — blijft SmartDocuments werken zoals het werkte (DoD 1)?
- [x] **Robuustheid** — wat gebeurt er als Epistola onbereikbaar is, een job mislukt of een template verdwijnt?

## 3. Testscope

| Wat wordt WEL getest? | Wat wordt NIET getest? |
|---|---|
| Configuratie: de providerkeuze bij het opstarten en de controle van de Epistola-instellingen (#2) | De SmartDocuments-wizard zelf. Alleen regressie, via de geautomatiseerde tests |
| Configuratie: templategroepen en templates per zaaktype in Beheer (#3) | Documentcreatie voor BPMN-zaken, want die valt buiten de scope. Wel dat het niet kan (DoD 2) |
| Autorisatie: wie een document mag maken, in de frontend en op het endpoint (#11) | De interne werking van Epistola, zoals renderen, PDF/A en opslag |
| Documentcreatie: de dialoog, het genereren en het alleen-PDF (#5) | Belasting en volume: veel gelijktijdige gebruikers, batchgeneratie |
| Datamapping: zaakdata in het document, de allow-list, datums, ontbrekende velden (#4) | Andere browsers dan Chrome |
| Open Zaak-opslag: het document in de Documenten API, met metadata en vertrouwelijkheid (#6) | De Playwright/Cucumber-e2e-suite van ZAC. Die is onbetrouwbaar tegen de huidige ZAC (afspraak 16 september) |
| Zaakkoppeling: het document hoort bij de zaak, en bij de taak als het vanuit een taak komt (#6) | Productie-inrichting: sleutelrotatie, een echte Epistola-tenant, een verwerkersovereenkomst (#20) |
| Preview: het document openen in de browser, met metadata (#6) | Een nieuwe versie van een Epistola-document (#9, optioneel en nog niet gebouwd) |
| Foutafhandeling: Epistola onbereikbaar, een mislukte job, een template dat verdwijnt, een timeout (#8) | |
| Randvoorwaarden: SmartDocuments ongewijzigd (DoD 1), alleen CMMN (DoD 2, #7) | |

## 4. Testomgeving

| | |
|---|---|
| Besturingssysteem | macOS 27.0 |
| Browser | Chrome (Chromium, via Playwright), versie vastgelegd in het testrapport |
| Server | De lokale ZAC-stack via Docker Compose, gestart met `./start-docker-compose.sh -l -E`: een lokaal gebouwde ZAC-image (`ghcr.io/infonl/zaakafhandelcomponent:dev`, van de geteste versie hierboven) op WildFly 41.0.1, Open Zaak 1.29.3, Keycloak 26.7.3, Solr. Epistola is de testserver van Epistola (`demo.epistola.app`, contract 1.3.1), bereikt met ZAC's Jakarta-client 1.3.1 |
| Database | PostgreSQL 17.11 voor ZAC, met migratie `V100` (Epistola-mapping) |
| Documentcreatie | `DOCUMENT_CREATION_PROVIDER=EPISTOLA`, catalogus `default` |

### Testdata

Geen wachtwoorden in dit document: de testgebruikers staan in de lokale Keycloak-testrealm van ZAC
(`scripts/docker-compose/imports/keycloak/realms/`).

| Soort | Waarde | Waarvoor |
|---|---|---|
| Gebruiker | `beheerder1` — Test Beheerder 1 (groep beheerders-elk-domein) | Configuratie in Beheer, en de scenario's op *Test zaaktype 1* (TS-24, TS-32, TS-34) |
| Gebruiker | `behandelaar1` — Test Behandelaar 1 (groep behandelaars-test-1) | Document maken (positief), op *Test zaaktype 2*. Mag *Test zaaktype 1* niet lezen, en dat is zelf een negatief scenario (TS-09a) |
| Gebruiker | `raadpleger1` — Test Raadpleger 1 (groep raadplegers-test-1) | Mag de zaak lezen, geen document maken (negatief) |
| Zaaktype | *Test zaaktype 1* (CMMN): Epistola aan, groep *Brieven* met *ZAC Standaardbrief* → documenttype *e-mail* (zaakvertrouwelijk) | Configuratie, vertrouwelijkheid, contractfout |
| Zaaktype | *Test zaaktype 2* (CMMN): Epistola aan, groep *Brieven 2* met *ZAC Standaardbrief* → documenttype *brief* (openbaar) | Het hoofdscenario van de behandelaar; Epistola uit- en weer aanzetten |
| Zaaktype | *BPMN test zaaktype 1* (BPMN) | Alleen CMMN |
| Zaak | ZAAK-2026-0000000001 (*Test zaaktype 1*, open, status Intake, zonder initiator en zonder behandelaar) | Vertrouwelijkheid, contractfout, verdwenen template; niet leesbaar voor `behandelaar1` |
| Zaak | ZAAK-2026-0000000032 (*Test zaaktype 2*, open, status Intake, initiator uit de BRP-mock, zonder behandelaar) | Het hoofdscenario van de behandelaar en de raadpleger; Epistola uit |
| Zaak | ZAAK-2026-0000000035 (*BPMN test zaaktype 1*) | Alleen CMMN |
| Template | `zac-standaardbrief` — *ZAC Standaardbrief*. Draft-07-contract zoals Epistola's editor het maakt, met datums als `format: date` die de brief als `dd-MM-yyyy` toont | Het hoofdscenario |
| Template | `zac-verplichte-aanvrager` — *ZAC Verplichte aanvrager*. Het contract eist `aanvrager.naam` | Data die het contract breekt |

**Let op:** de testtenant van Epistola wordt dagelijks gereset. Vóór elke testronde zet
`~/Documents/Exam/epistola-live-check/author_template.py` de standaardbrief terug, en
`author_required_field_template.py` het tweede template.

## 5. Testcriteria

### 5.1 Acceptatiecriteria — wanneer is een test geslaagd?

Een test is geslaagd als het werkelijke resultaat overeenkomt met **alle** punten van het verwachte resultaat
in [§8](#8-testcases), zonder onverwachte foutmelding in het scherm of `ERROR` in het ZAC-log. Bij een
geautomatiseerde test betekent het dat de genoemde tests groen zijn in de testrun van de geteste versie.

### 5.2 Faalcriteria — wanneer is een test mislukt?

Een test is mislukt als:

- het systeem een onverwachte foutmelding geeft;
- het systeem vastloopt, of de dialoog blijft hangen zonder resultaat;
- het verwachte resultaat niet of maar gedeeltelijk bereikt wordt;
- er gegevens bij Epistola of in Open Zaak achterblijven die er volgens het verwachte resultaat niet horen te
  zijn;
- een scenario wordt goedgekeurd dat alleen slaagt omdat het niet echt is uitgevoerd. Een scenario dat niet
  uitgevoerd kon worden, krijgt de uitkomst *niet uitgevoerd*, met de reden.

Een scenario dat een bekende, nog niet gebouwde functie test (bijvoorbeeld #7 of #8) is **mislukt**, niet
"verwacht". Zo'n scenario levert een bug met status *open* op in de buglijst van het testrapport.

## 6. Testaanpak en tools

### Testmethoden

- **Handmatig testen:** de scenario's met *Handmatig* in [§8](#8-testcases) worden stap voor stap in de
  browser uitgevoerd, als de genoemde gebruiker. Per scenario wordt het resultaat genoteerd, en bij een
  zichtbaar resultaat ook een screenshot.
- **Geautomatiseerd testen:**
  - backend-unittests (Kotest en MockK), `./gradlew test`;
  - frontend-unittests (Jest en Testing Library), `npm test`;
  - integratietests (TestContainers), `./gradlew itest`. Die draaien met SmartDocuments als provider, en
    daarmee zijn ze de regressietest voor DoD 1;
  - de live-check: ZAC's eigen klassen tegen de echte Epistola, met een controle achteraf die rechtstreeks
    bij Epistola nagaat wat er aankwam.
- **Gebruikerstest (UAT):** bij de einddemo (#22) met de stakeholders. Die wordt in het testrapport
  aangevuld zodra hij heeft plaatsgevonden.

### Testtools

Chrome (via Playwright) met DevTools voor netwerkverkeer; Gradle, Kotest en MockK; npm, Jest en Testing
Library; TestContainers; Python-scripts die Epistola rechtstreeks bevragen en nooit sleutels tonen
(`~/Documents/Exam/epistola-live-check/`); `psql` voor de ZAC-database; `docker logs` voor het ZAC-log.

## 7. Rollen en verantwoordelijkheden

| Naam | Rol | Verantwoordelijkheid |
|---|---|---|
| Symon Vleeshouwers | Tester en testcoördinator | Testplan opstellen, alle scenario's uitvoeren, resultaten en bugs vastleggen, testrapport |
| Hanneke van de Horst, Team Geneva | Stakeholders | Akkoord op dit testplan (#21); gebruikerstest bij de einddemo (#22) |
| Marcel Evers, Edgar (`edgarvonk`) | Reviewers | Codereview van de pull requests, inclusief de geautomatiseerde tests erin |

## 8. Testcases

Elke functionaliteit uit [§3](#3-testscope) heeft minstens één positief en, waar dat kan, één negatief
scenario. **+** is positief, **−** is negatief. Onder de tabel staan de stappen van de handmatige
scenario's.

| # | Functionaliteit | Testbeschrijving | Verwacht resultaat | Prioriteit | Testmethode |
|---|---|---|---|---|---|
| **Configuratie** | | | | | |
| TS-01 | Providerkeuze (+) | Start ZAC met `DOCUMENT_CREATION_PROVIDER=EPISTOLA` en geldige Epistola-instellingen | ZAC start en logt `Active document creation provider: 'EPISTOLA'` | Hoog | Handmatig (log) en automatisch (`DocumentCreationProviderConfigurationTest`) |
| TS-02 | Onjuiste configuratie (−) | Start met SmartDocuments en Epistola tegelijk, met een ontbrekende API key, en met een tenant- of catalogus-id die geen slug is | ZAC start niet, en de melding noemt de instelling en wat er mis is | Hoog | Automatisch (`DocumentCreationProviderConfigurationTest`) |
| TS-03 | Templategroepen inrichten (+) | Als beheerder: voeg in Beheer bij *Test zaaktype 1* het template *ZAC Verplichte aanvrager* toe aan groep *Brieven*, met een documenttype. Sla op en herlaad | Na het herladen staan beide templates in de groep, met hun documenttype. De namen komen live uit Epistola | Hoog | Handmatig en automatisch (`EpistolaTemplatesServiceTest`, `epistola-templates-form.component.spec.ts`) |
| TS-04 | Ongeldige mapping (−) | Maak een tweede groep met dezelfde naam, of zet hetzelfde template twee keer in het zaaktype | Opslaan wordt geweigerd met een melding. Er wordt niets opgeslagen | Midden | Handmatig en automatisch (`RestEpistolaTemplateMappingValidatorTest`) |
| TS-05 | Epistola uit voor een zaaktype (−) | Zet Epistola uit bij *Test zaaktype 2*, open ZAAK-2026-0000000032 en zet het daarna weer aan | Met Epistola uit geen *Document maken* in het zaakmenu. Een directe aanroep wordt geweigerd (400). Met Epistola weer aan komt de actie terug, met de oude mapping | Hoog | Handmatig en automatisch (`EpistolaTemplatesServiceTest`, `zaak-view-menu.builder.spec.ts`) |
| TS-06 | Alleen CMMN (−) | Open in Beheer *BPMN test zaaktype 1*, en daarna zaak ZAAK-2026-0000000035 | Geen Epistola-kaart bij het BPMN-zaaktype. De BPMN-zaak kan geen Epistola-document maken, en de beperking is zichtbaar met een toelichting (DoD 2, #7) | Midden | Handmatig |
| TS-07 | Nieuwe zaaktypeversie (+) | Publiceer een nieuwe versie van een zaaktype met een Epistola-mapping | De mapping gaat mee naar de nieuwe versie, ook als Epistola dan niet bereikbaar is | Midden | Automatisch (`ZaaktypeCmmnConfigurationBeheerServiceTest`, `EpistolaTemplatesServiceTest`) |
| **Autorisatie** | | | | | |
| TS-08 | Geautoriseerde behandelaar (+) | Als `behandelaar1`: open ZAAK-2026-0000000032 | *Document maken* staat in het zaakmenu | Hoog | Handmatig |
| TS-09a | Gebruiker die de zaak niet mag lezen (−) | Als `behandelaar1`: stuur via DevTools een verzoek voor ZAAK-2026-0000000001, van een zaaktype waarvoor de gebruiker niet geautoriseerd is | 403. Wie de zaak niet mag lezen, kan er geen document voor maken (#11) | Hoog | Handmatig en automatisch (`DocumentCreationRestServiceTest`) |
| TS-09b | Gebruiker zonder recht (−) | Als `raadpleger1`: open ZAAK-2026-0000000032 en stuur via DevTools een verzoek naar `POST /rest/document-creation/epistola/create-document` | De raadpleger ziet de zaak, maar geen *Document maken*. Het verzoek geeft 403, en er komt geen document bij | Hoog | Handmatig en automatisch (`DocumentCreationRestServiceTest`, `zaak-view-menu.builder.spec.ts`) |
| TS-10 | Taakrechten (−) | Maak een document vanuit een taak zonder taakrecht, en vanuit een taak die niet meer open is | Geweigerd (403 of 404), zonder aanroep van Epistola | Midden | Automatisch (`DocumentCreationRestServiceTest`) |
| **Documentcreatie** | | | | | |
| TS-11 | Document maken vanuit de zaak (+) | Als `behandelaar1` op ZAAK-2026-0000000032: *Document maken* → *Brieven 2* → *ZAC Standaardbrief* → titel en beschrijving → *Genereren* | Melding *Document "…" is toegevoegd aan de zaak*, de dialoog sluit. Er opent geen wizard | Hoog | Handmatig en automatisch (`informatie-object-create-attended.component.spec.ts`, `EpistolaDocumentCreationServiceTest`) |
| TS-12 | Alleen PDF, auteur vast (+) | Open de dialoog | *Bestandsformaat* toont *PDF* en is niet te wijzigen. Er is geen creatiedatum. De auteur is de ingelogde gebruiker en niet te wijzigen. Documenttype en vertrouwelijkheid komen uit de mapping | Hoog | Handmatig |
| TS-13 | Voortgang tijdens genereren (+) | Klik op *Genereren* en kijk direct | Een spinner op de knop, beide knoppen uitgeschakeld, en de tekst *Het document wordt gegenereerd…* | Midden | Handmatig en automatisch (component spec) |
| TS-14 | Verplichte velden (−) | Open de dialoog en vul geen titel in | *Genereren* blijft uitgeschakeld | Midden | Handmatig en automatisch (component spec) |
| TS-15 | Niet-aangeboden template (−) | Stuur via DevTools een verzoek met `templateId` `niet-aangeboden` voor ZAAK-2026-0000000032 | 400 met de melding dat het sjabloon niet voor dit zaaktype is ingesteld. Niets naar Epistola | Hoog | Handmatig en automatisch (`EpistolaTemplatesServiceTest`, `EpistolaDocumentCreationServiceTest`) |
| TS-16 | Doorlooptijd (prestatie) | Meet bij TS-11 de tijd van de klik op *Genereren* tot het antwoord van het endpoint | Ruim binnen de timeout van 60 seconden. De gemeten tijd wordt vastgelegd | Laag | Handmatig (netwerk-timing) |
| **Datamapping** | | | | | |
| TS-17 | Zaakgegevens in het document (+) | Bekijk het document uit TS-11 in de preview | De brief toont zaaknummer, zaaktype, status, groep, communicatiekanaal, de naam van de initiator, en de datums als `dd-MM-yyyy` | Hoog | Handmatig en live-check |
| TS-18 | Alleen gedeclareerde velden (+) | Live-check: ZAC's klassen vullen de payload met tien geplante, niet-gedeclareerde waarden | Geen enkele geplante waarde staat in de payload of in de PDF. Elk veld in de payload is door het template gedeclareerd | Hoog | Live-check en automatisch (`EpistolaTemplateDataTest`) |
| TS-19 | Datums in ISO 8601 (+) | Live-check met een contract dat datums als `format: date` declareert | De payload bevat `2026-09-01`, Epistola accepteert het, en de brief toont `01-09-2026` | Hoog | Live-check en automatisch (`EpistolaTemplateDataTest`) |
| TS-20 | Ontbrekende optionele velden (+) | Bekijk in TS-17 de plekken voor de behandelaar en de geplande einddatum, die de zaak niet heeft | Die velden zijn leeg. Het document wordt toch gemaakt, zonder fout en zonder tekst als "null" | Midden | Handmatig en automatisch (`DocumentCreationDataServiceTest`) |
| TS-21 | Template zonder schema (−) | Genereer met een template dat geen schema declareert | Geweigerd met de melding dat het sjabloon niet aangeeft welke zaakgegevens het gebruikt. Er gaat niets naar Epistola | Midden | Automatisch (`EpistolaDocumentCreationServiceTest`) |
| TS-22 | SmartDocuments ongewijzigd (regressie) | Serialiseer de SmartDocuments-deposit, en draai de integratietests | De deposit houdt `dd-MM-yyyy`, en de SmartDocuments-integratietests slagen | Hoog | Automatisch (`EpistolaTemplateDataTest`, integratietests) |
| **Open Zaak-opslag** | | | | | |
| TS-23 | Opslag met alle verplichte velden (+) | Open de documentpagina van het document uit TS-11, en vraag het informatieobject op via ZAC's REST API | Titel, auteur, taal, documenttype, bestandsnaam `…pdf`, grootte, status *in bewerking*, versie 1, beschrijving. `formaat` is `application/pdf` | Hoog | Handmatig en automatisch (`EpistolaDocumentCreationServiceTest`) |
| TS-24 | Vertrouwelijkheid uit het documenttype (+) | Als `beheerder1`: genereer *ZAC Standaardbrief* op ZAAK-2026-0000000001, waar het documenttype *e-mail* zaakvertrouwelijk is | Het document is *zaakvertrouwelijk*, de waarde van het documenttype, en niet het vaste *openbaar* | Hoog | Handmatig en automatisch (`EpistolaDocumentCreationServiceTest`) |
| TS-25 | Kopie bij Epistola verwijderd (+) | Vraag na TS-11 bij Epistola de documenten van deze zaak op | Epistola heeft geen document meer voor deze zaak | Midden | Handmatig (script) en automatisch |
| TS-26 | Opslag in Open Zaak mislukt (−) | Laat het opslaan in de Documenten API falen | De fout komt bij de aanroeper, er wordt niets gekoppeld, en de kopie bij Epistola blijft staan | Midden | Automatisch (`EpistolaDocumentCreationServiceTest`) |
| **Zaakkoppeling** | | | | | |
| TS-27 | Document hoort bij de zaak (+) | Bekijk na TS-11 het tabblad Documenten, zonder de pagina te verversen | Het document staat er meteen. De documentpagina noemt de zaak | Hoog | Handmatig |
| TS-28 | Document hoort bij de taak (+) | Maak een document met een `taskId` | Het document hoort bij de zaak en bij de taak, en de taakrechten worden gecontroleerd | Midden | Automatisch (`EpistolaDocumentCreationServiceTest`, `DocumentCreationRestServiceTest`) |
| **Preview** | | | | | |
| TS-29 | Preview in de browser (+) | Klik in het tabblad Documenten op de titel van het document | De PDF opent in de browser, met de gerenderde brief | Hoog | Handmatig |
| TS-30 | Metadata op de documentpagina (+) | Open het document via het oog-icoon | Alle metadata uit TS-23, en de preview | Hoog | Handmatig |
| **Foutafhandeling** | | | | | |
| TS-31 | Epistola onbereikbaar (−) | Maak `demo.epistola.app` onbereikbaar voor de ZAC-container, genereer, en maak hem daarna weer bereikbaar | Een begrijpelijke melding in de dialoog, die open blijft. Er wordt niets opgeslagen (#8) | Hoog | Handmatig |
| TS-32 | Data breekt het contract (−) | Als `beheerder1`: genereer met *ZAC Verplichte aanvrager* op ZAAK-2026-0000000001, die geen initiator heeft | Epistola weigert de data, ZAC toont een melding die uitlegt dat het template gegevens mist, en er wordt niets opgeslagen (#8) | Hoog | Handmatig en automatisch (`EpistolaClientServiceTest`) |
| TS-33 | Timeout (−) | Laat een job langer duren dan de timeout | ZAC stopt met wachten, annuleert de job bij Epistola en meldt dat het te lang duurde | Midden | Automatisch (`EpistolaClientServiceTest`) |
| TS-34 | Template verdwenen bij Epistola (−) | Verwijder *ZAC Verplichte aanvrager* bij Epistola terwijl het nog in de mapping staat, en open de dialoog | De dialoog biedt het template niet meer aan en loopt niet vast. De mapping blijft bewaard | Midden | Handmatig |
| TS-35 | Gedeeltelijke mislukking (−) | Het document staat in de Documenten API, maar de koppeling aan de zaak mislukt | Het document wordt opgeruimd of zichtbaar gemaakt voor handmatige afhandeling (#8) | Midden | Niet uitvoerbaar zolang #8 niet gebouwd is |

### Stappen van de handmatige scenario's

Elke ronde begint met: stack draait (`./start-docker-compose.sh -l -E`), beide testtemplates zijn opnieuw
aangemaakt, en de browser is ingelogd als de genoemde gebruiker.

- **TS-01** — `docker logs zac-zac-1 | grep "Active document creation provider"`.
- **TS-03** — Als `beheerder1`: tandwiel (*Admin settings*) → *Case handling parameters* → *Test zaaktype 1* →
  met *Next* naar stap 7 (*Connections*) → kaart *Epistola document templates* → bij *Brieven* via *Add template*
  *ZAC Verplichte aanvrager* kiezen, met documenttype *bijlage* → *Save* → pagina herladen → kaart nakijken.
- **TS-04** — Op dezelfde kaart een groep *Brieven* toevoegen → Opslaan → melding. Wijziging weggooien.
- **TS-05** — Als `beheerder1`: bij *Test zaaktype 2* Epistola uitzetten → Opslaan. ZAAK-2026-0000000032 openen
  → zaakmenu nakijken. In DevTools
  `fetch('/rest/document-creation/epistola/create-document', {method: 'POST', …})` met een template uit
  *Brieven 2* → status. Daarna Epistola weer aanzetten → Opslaan → zaak opnieuw openen → menu nakijken.
- **TS-06** — Als `beheerder1`: Beheer → *BPMN test zaaktype 1* → kaarten nakijken. ZAAK-2026-0000000035 openen
  → zaakmenu nakijken, en het menu-item met de muis aanwijzen voor een toelichting.
- **TS-08, TS-11 t/m TS-14, TS-16** — Als `behandelaar1`: ZAAK-2026-0000000032 openen → menu nakijken (TS-08)
  → *Document maken* → velden nakijken (TS-12) → *Brieven 2* kiezen, zonder titel de knop nakijken (TS-14) →
  titel *Testrapport TS-11* en een beschrijving → *Genereren* → wachten op de melding (TS-11). De doorlooptijd
  uit het netwerkverkeer halen (TS-16). Voor de screenshot van TS-13 een tweede document (*Testrapport TS-13*)
  genereren, met klik en screenshot in één Playwright-script, omdat het genereren sneller klaar is dan een
  losse screenshot.
- **TS-09a** — Als `behandelaar1`: in DevTools het verzoek van TS-11 sturen, maar voor ZAAK-2026-0000000001 →
  status.
- **TS-09b** — Als `raadpleger1`: ZAAK-2026-0000000032 openen → menu nakijken → in DevTools het verzoek van
  TS-11 sturen → status → documenten van de zaak tellen.
- **TS-15** — Als `behandelaar1`: in DevTools een verzoek met `templateId` `niet-aangeboden` voor
  ZAAK-2026-0000000032 → status en melding → documenten van de zaak tellen.
- **TS-17, TS-20, TS-27, TS-29** — Na TS-11 in het tabblad Documenten: staat het document er (TS-27)? Titel
  aanklikken → preview (TS-29) → de brief lezen (TS-17, TS-20).
- **TS-23, TS-24, TS-30** — Oog-icoon → documentpagina nakijken. Via DevTools het informatieobject opvragen
  voor `formaat`.
- **TS-25** — `op run --env-file=./.env.epistola.tpl -- python3 …` met het controlescript dat de
  Epistola-documenten van de zaak telt.
- **TS-31** — Als `behandelaar1`: `docker exec -u root zac-zac-1 sh -c 'echo "127.0.0.1 demo.epistola.app" >> /etc/hosts'` →
  minstens 30 seconden wachten (Java's DNS-cache) → de dialoog openen als in TS-11 en een verzoek via DevTools →
  melding en documenten nakijken → de regel weer uit `/etc/hosts` halen → nagaan dat ZAC Epistola weer bereikt.
- **TS-24** — Als `beheerder1` op ZAAK-2026-0000000001: *Document maken* → *Brieven* → *ZAC Standaardbrief* →
  titel *Testrapport TS-24* → *Genereren* → vertrouwelijkheid in de documentenlijst en via de REST API.
- **TS-32** — Als `beheerder1` op ZAAK-2026-0000000001: als TS-24, maar met *ZAC Verplichte aanvrager* →
  melding nakijken → documenten van de zaak tellen → reden in het ZAC-log.
- **TS-34** — `author_required_field_template.py --delete` → als `beheerder1` de dialoog op
  ZAAK-2026-0000000001 openen → groep *Brieven* nakijken. In de ZAC-database nagaan dat de mapping-rij er nog
  is.

## 9. Testplanning

| Testfase | Datum | Verantwoordelijke | Status |
|---|---|---|---|
| Unittests (backend en frontend) | Doorlopend per pull request; testrun voor dit rapport op 25 september | Symon Vleeshouwers | Uitgevoerd, zie testrapport |
| Integratietests | 25 september, op de geteste versie | Symon Vleeshouwers | Uitgevoerd, zie testrapport |
| Handmatige systeemtest (dit plan, §8) | 25 september | Symon Vleeshouwers | Uitgevoerd, zie testrapport |
| Akkoord op het testplan | Eerstvolgend stakeholderoverleg (#21) | Stakeholders | Open |
| Gebruikerstest (UAT) | Einddemo (#22), datum volgt | Stakeholders | Nog te doen |
| Acceptatietest | Na akkoord en UAT, vóór 9 oktober | Stakeholders met Symon Vleeshouwers | Nog te doen |

> Een goed testplan is concreet en volledig: elke functionaliteit uit [§3](#3-testscope) komt terug als
> testcase in [§8](#8-testcases).
