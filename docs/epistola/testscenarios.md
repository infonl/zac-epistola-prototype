# Testscenario's — Epistola-integratie in ZAC

| | |
|---|---|
| Opdracht | Opdracht 3 – B1-K1-W4 Test software |
| Issue | [#18](https://github.com/infonl/zac-epistola-prototype/issues/18) · werkt de testcases uit [het testplan](testplan.md) v1.4 uit; uitvoering en resultaten in [het testrapport](testrapport.md) (#19) |
| Projectnaam | Epistola-integratie in ZAC (zaakafhandelcomponent) |
| Student | Symon Vleeshouwers (198462) |
| Klas | ZWSD24 |
| Datum | 30 september 2026, bijgewerkt op 1 oktober 2026 |

> **Beoordelingscriterium – T1 Testscenario (cruciaal):** voor alle geplande functionaliteiten zijn testscenario's of
> testcases gemaakt. Elke functionaliteit uit §3 van het testplan heeft hieronder een eigen testscenario.

Dit document werkt de testcases uit §8 van [het testplan](testplan.md) (versie 1.4) uit tot testscenario's. Er is één testscenario per
functionaliteit uit §3 van het testplan, elf in totaal. De teststappen van een scenario zijn de testcases van die
functionaliteit. Elke stap noemt zijn nummer uit het testplan (TS-01 t/m TS-43), met het verwachte resultaat uit het
testplan en of de stap positief of negatief is. Een regel die met *Automatisch* begint, noemt de geautomatiseerde test
die de stap ook dekt, of, als er geen handmatige actie staat, de test die de stap uitvoert. De uitkomsten en het bewijs
staan in [het testrapport](testrapport.md).

Nummers als #8 verwijzen naar issues en pull requests in de repository
[infonl/zac-epistola-prototype](https://github.com/infonl/zac-epistola-prototype) op GitHub.

## Overzicht

| Testscenario | Functionaliteit (testplan §3) | Testcases | Positief | Negatief |
|---|---|---|---|---|
| 1 | Configuratie: providerkeuze en instellingen (#2) | TS-01, TS-02 | 1 | 1 |
| 2 | Configuratie: templategroepen en templates per zaaktype (#3) | TS-03, TS-04, TS-05, TS-07 | 2 | 2 |
| 3 | Autorisatie (#11) | TS-08, TS-09a, TS-09b, TS-10 | 1 | 3 |
| 4 | Documentcreatie (#5) | TS-11 t/m TS-16 | 4 | 2 |
| 5 | Datamapping (#4) | TS-17 t/m TS-21 | 4 | 1 |
| 6 | Open Zaak-opslag (#6) | TS-23 t/m TS-26 | 3 | 1 |
| 7 | Zaakkoppeling (#6) | TS-27, TS-28 | 2 | 2 (verwijzing naar TS-10 en TS-35) |
| 8 | Preview (#6) | TS-29, TS-30 | 2 | 0 |
| 9 | Foutafhandeling (#8) | TS-31 t/m TS-35 | 1 | 5 |
| 10 | Randvoorwaarden: SmartDocuments ongewijzigd, alleen CMMN (DoD 1 en 2, #7) | TS-06, TS-22 | 1 | 1 |
| 11 | Nieuwe versie van een Epistola-document (DoD 11, optioneel, #9) | TS-36 t/m TS-43 | 3 | 6 |

Type: *Positief* = geldige invoer of het verwachte pad. *Negatief* = foute invoer, geen recht of een randgeval.

## Algemene randvoorwaarden

Deze gelden voor elk scenario, en staan daarom niet bij elk scenario opnieuw:

- De lokale ZAC-stack draait, gestart met `./start-docker-compose.sh -l -E`, met de ZAC-image van de geteste versie
  (testplan §4) en `DOCUMENT_CREATION_PROVIDER=EPISTOLA`, catalogus `default`.
- Beide testtemplates zijn opnieuw aangemaakt op Epistola's testserver (`demo.epistola.app`), omdat de testtenant dagelijks
  wordt gereset: `author_template.py` zet *ZAC Standaardbrief* terug, en `author_required_field_template.py` *ZAC Verplichte
  aanvrager* (beide in `~/Documents/Exam/epistola-live-check/`).
- De browser (Chrome, bestuurd door Playwright) is ingelogd als de gebruiker die de stap noemt. De testgebruikers staan in de
  lokale Keycloak-testrealm van ZAC; wachtwoorden staan niet in dit document.
- Voor een directe aanroep van een endpoint staat DevTools open op de ZAC-pagina van die gebruiker.

De testdata (gebruikers, zaaktypen, zaken en templates) staan in §4 van [het testplan](testplan.md). Per scenario staat hieronder wat het
gebruikt.

## Testscenario 1 – Configuratie: providerkeuze en instellingen

| Testscenario 1 | |
|---|---|
| Testscenario ID | TS-01 en TS-02 |
| Functionaliteit | Configuratie: de providerkeuze bij het opstarten en de controle van de Epistola-instellingen (#2) |
| Omschrijving | ZAC kiest bij het opstarten welke provider documenten maakt, en controleert de Epistola-instellingen. Met een geldige configuratie start ZAC met Epistola; een tegenstrijdige of onvolledige configuratie laat ZAC niet starten |
| Randvoorwaarden | De algemene randvoorwaarden. Voor TS-02 de backend-unittests (`./gradlew test`) |
| Testdata | `DOCUMENT_CREATION_PROVIDER=EPISTOLA` met geldige Epistola-instellingen. Voor TS-02: SmartDocuments en Epistola tegelijk aan, een ontbrekende API key, en een tenant- of catalogus-id die geen slug is |

### Teststappen – Scenario 1

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 1.1<br>**TS-01** | Start ZAC met `DOCUMENT_CREATION_PROVIDER=EPISTOLA` en geldige Epistola-instellingen, en zoek de regel in het log: `docker logs zac-zac-1 \| grep "Active document creation provider"`.<br>Automatisch: `DocumentCreationProviderConfigurationTest` | ZAC start en logt `Active document creation provider: 'EPISTOLA'` | Positief |
| 1.2<br>**TS-02** | Automatisch: start met SmartDocuments en Epistola tegelijk, met een ontbrekende API key, en met een tenant- of catalogus-id die geen slug is (`DocumentCreationProviderConfigurationTest`) | ZAC start niet, en de melding noemt de instelling en wat er mis is | Negatief |

## Testscenario 2 – Configuratie: templategroepen en templates per zaaktype

| Testscenario 2 | |
|---|---|
| Testscenario ID | TS-03, TS-04, TS-05 en TS-07. TS-06 (alleen CMMN) staat bij testscenario 10 |
| Functionaliteit | Configuratie: templategroepen en templates per zaaktype in Beheer (#3) |
| Omschrijving | De beheerder richt per zaaktype templategroepen in, met per template een documenttype. Een ongeldige mapping wordt geweigerd, Epistola is per zaaktype uit en weer aan te zetten, en de mapping gaat mee naar een nieuwe zaaktypeversie |
| Randvoorwaarden | De algemene randvoorwaarden. Ingelogd als `beheerder1`. *Test zaaktype 1* heeft Epistola aan, met groep *Brieven* (*ZAC Standaardbrief* → documenttype *e-mail*). *Test zaaktype 2* heeft Epistola aan, met groep *Brieven 2* (*ZAC Standaardbrief* → documenttype *brief*) |
| Testdata | Gebruiker `beheerder1`; zaaktypen *Test zaaktype 1* en *Test zaaktype 2*; zaak ZAAK-2026-0000000032; templates *ZAC Standaardbrief* en *ZAC Verplichte aanvrager*; documenttype *bijlage* |

### Teststappen – Scenario 2

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 2.1<br>**TS-03** | Tandwiel (*Admin settings*) → *Case handling parameters* → *Test zaaktype 1* → met *Next* naar stap 7 (*Connections*) → kaart *Epistola document templates* → bij *Brieven* via *Add template* *ZAC Verplichte aanvrager* kiezen, met documenttype *bijlage* → *Save* → pagina herladen → kaart nakijken.<br>Automatisch: `EpistolaTemplatesServiceTest`, `epistola-templates-form.component.spec.ts` | Na het herladen staan beide templates in de groep, met hun documenttype. De namen komen live uit Epistola | Positief |
| 2.2<br>**TS-04** | Op dezelfde kaart een tweede groep *Brieven* toevoegen → Opslaan → melding nakijken. Daarna de wijziging weggooien.<br>Automatisch: `RestEpistolaTemplateMappingValidatorTest`, ook voor één template in twee groepen | Opslaan wordt geweigerd met een melding. Er wordt niets opgeslagen | Negatief |
| 2.3<br>**TS-05** | Bij *Test zaaktype 2* Epistola uitzetten → Opslaan. ZAAK-2026-0000000032 openen → zaakmenu nakijken. In DevTools `fetch('/rest/document-creation/epistola/create-document', {method: 'POST', …})` sturen met een template uit *Brieven 2* → status nakijken. Daarna Epistola weer aanzetten → Opslaan → de zaak opnieuw openen → menu nakijken.<br>Automatisch: `EpistolaTemplatesServiceTest`, `zaak-view-menu.builder.spec.ts` | Met Epistola uit geen *Document maken* in het zaakmenu. Een directe aanroep wordt geweigerd (400). Met Epistola weer aan komt de actie terug, met de oude mapping | Negatief |
| 2.4<br>**TS-07** | Automatisch: publiceer een nieuwe versie van een zaaktype met een Epistola-mapping (`ZaaktypeCmmnConfigurationBeheerServiceTest`, `EpistolaTemplatesServiceTest`) | De mapping gaat mee naar de nieuwe versie, ook als Epistola dan niet bereikbaar is | Positief |

## Testscenario 3 – Autorisatie

| Testscenario 3 | |
|---|---|
| Testscenario ID | TS-08, TS-09a, TS-09b en TS-10 |
| Functionaliteit | Autorisatie: wie een document mag maken, in de frontend en op het endpoint (#11) |
| Omschrijving | Alleen een gebruiker die de zaak mag lezen en een document mag maken, kan met Epistola een document maken. De frontend toont de actie alleen aan die gebruiker, en het endpoint dwingt het aan de serverkant af |
| Randvoorwaarden | De algemene randvoorwaarden. `behandelaar1` is geautoriseerd voor *Test zaaktype 2* en niet voor *Test zaaktype 1*. `raadpleger1` mag ZAAK-2026-0000000032 lezen |
| Testdata | Gebruikers `behandelaar1` en `raadpleger1`; zaken ZAAK-2026-0000000032 (*Test zaaktype 2*) en ZAAK-2026-0000000001 (*Test zaaktype 1*); endpoint `POST /rest/document-creation/epistola/create-document` |

### Teststappen – Scenario 3

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 3.1<br>**TS-08** | Als `behandelaar1`: ZAAK-2026-0000000032 openen → zaakmenu nakijken | *Document maken* staat in het zaakmenu | Positief |
| 3.2<br>**TS-09a** | Als `behandelaar1`: in DevTools het verzoek van TS-11 sturen, maar voor ZAAK-2026-0000000001, van een zaaktype waarvoor de gebruiker niet geautoriseerd is → status nakijken.<br>Automatisch: `DocumentCreationRestServiceTest` | 403. Wie de zaak niet mag lezen, kan er geen document voor maken (#11) | Negatief |
| 3.3<br>**TS-09b** | Als `raadpleger1`: ZAAK-2026-0000000032 openen → menu nakijken → in DevTools het verzoek van TS-11 sturen naar `POST /rest/document-creation/epistola/create-document` → status nakijken → documenten van de zaak tellen.<br>Automatisch: `DocumentCreationRestServiceTest`, `zaak-view-menu.builder.spec.ts` | De raadpleger ziet de zaak, maar geen *Document maken*. Het verzoek geeft 403, en er komt geen document bij | Negatief |
| 3.4<br>**TS-10** | Automatisch: maak een document vanuit een taak zonder taakrecht, en vanuit een taak die niet meer open is (`DocumentCreationRestServiceTest`) | Geweigerd (403 of 404), zonder aanroep van Epistola | Negatief |

## Testscenario 4 – Documentcreatie

| Testscenario 4 | |
|---|---|
| Testscenario ID | TS-11 t/m TS-16 |
| Functionaliteit | Documentcreatie: de dialoog, het genereren en het alleen-PDF (#5) |
| Omschrijving | De behandelaar maakt vanuit de zaak een document met Epistola. De dialoog biedt de templates van het zaaktype aan, maakt alleen PDF, toont de voortgang en voegt het document aan de zaak toe. Een onvolledige invoer of een template dat het zaaktype niet aanbiedt, wordt geweigerd |
| Randvoorwaarden | De algemene randvoorwaarden. Ingelogd als `behandelaar1`, met ZAAK-2026-0000000032 open (TS-08, testscenario 3). Voor de screenshot van TS-13 een Playwright-script dat klikt en direct een screenshot maakt, omdat het genereren sneller klaar is dan een losse screenshot |
| Testdata | ZAAK-2026-0000000032; groep *Brieven 2* met *ZAC Standaardbrief* (`zac-standaardbrief`) → documenttype *brief*; titels *Testrapport TS-11* en *Testrapport TS-13*; `templateId` `niet-aangeboden` voor TS-15; de timeout `EPISTOLA_GENERATION_TIMEOUT_SECONDS`, standaard 60 seconden |

### Teststappen – Scenario 4

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 4.1<br>**TS-12** | *Document maken* → de velden van de dialoog nakijken | *Bestandsformaat* toont *PDF* en is niet te wijzigen. Er is geen creatiedatum. De auteur is de ingelogde gebruiker en niet te wijzigen. Documenttype en vertrouwelijkheid komen uit de mapping | Positief |
| 4.2<br>**TS-14** | *Brieven 2* kiezen en geen titel invullen → de knop *Genereren* nakijken.<br>Automatisch: component spec `informatie-object-create-attended.component.spec.ts` | *Genereren* blijft uitgeschakeld | Negatief |
| 4.3<br>**TS-11** | Titel *Testrapport TS-11* en een beschrijving invullen → *Genereren* → wachten op de melding.<br>Automatisch: `informatie-object-create-attended.component.spec.ts`, `EpistolaDocumentCreationServiceTest` | Melding *Document "…" is toegevoegd aan de zaak*, de dialoog sluit. Er opent geen wizard | Positief |
| 4.4<br>**TS-16** | Bij stap 4.3 in het netwerkverkeer de tijd meten van de klik op *Genereren* tot het antwoord van het endpoint | Ruim binnen de timeout van 60 seconden. De gemeten tijd wordt vastgelegd | Positief (prestatie) |
| 4.5<br>**TS-13** | Een tweede document genereren, *Testrapport TS-13*: op *Genereren* klikken en direct kijken (klik en screenshot in één Playwright-script).<br>Automatisch: component spec | Een spinner op de knop, beide knoppen uitgeschakeld, en de tekst *Het document wordt gegenereerd…* | Positief |
| 4.6<br>**TS-15** | In DevTools een verzoek sturen met `templateId` `niet-aangeboden` voor ZAAK-2026-0000000032 → status en melding nakijken → documenten van de zaak tellen.<br>Automatisch: `EpistolaTemplatesServiceTest`, `EpistolaDocumentCreationServiceTest` | 400 met de melding dat het template niet voor dit zaaktype is ingesteld. Niets naar Epistola | Negatief |

## Testscenario 5 – Datamapping

| Testscenario 5 | |
|---|---|
| Testscenario ID | TS-17 t/m TS-21. TS-22 (SmartDocuments ongewijzigd) staat bij testscenario 10 |
| Functionaliteit | Datamapping: zaakdata in het document, de allow-list, datums, ontbrekende velden (#4) |
| Omschrijving | ZAC vult het template met de gegevens van de zaak. Alleen de velden die het template declareert, gaan naar Epistola (dataminimalisatie, #16). Datums gaan als ISO 8601 naar Epistola en staan in de brief als `dd-MM-yyyy`. Gegevens die de zaak niet heeft, blijven leeg |
| Randvoorwaarden | De algemene randvoorwaarden. Stap 4.3 (TS-11) is uitgevoerd, zodat er een document is om te lezen. Voor TS-18 en TS-19 de live-check in `~/Documents/Exam/epistola-live-check/`: ZAC's eigen klassen tegen de echte Epistola, met een controle achteraf die rechtstreeks bij Epistola nagaat wat er aankwam |
| Testdata | Het document *Testrapport TS-11* op ZAAK-2026-0000000032 (initiator uit de BRP-mock, zonder behandelaar en zonder geplande einddatum); template `zac-standaardbrief` (draft-07-contract met datums als `format: date`); tien geplante, niet-gedeclareerde waarden in de live-check; de datum `2026-09-01` |

### Teststappen – Scenario 5

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 5.1<br>**TS-17** | Als `behandelaar1`: in het tabblad Documenten op de titel van het document uit TS-11 klikken → de brief in de preview lezen.<br>Ook in de live-check | De brief toont zaaknummer, zaaktype, status, groep, communicatiekanaal, de naam van de initiator, en de datums als `dd-MM-yyyy` | Positief |
| 5.2<br>**TS-20** | In dezelfde brief de plekken voor de behandelaar en de geplande einddatum bekijken, die de zaak niet heeft.<br>Automatisch: `DocumentCreationDataServiceTest` | Die velden zijn leeg. Het document wordt toch gemaakt, zonder fout en zonder tekst als "null" | Positief |
| 5.3<br>**TS-18** | Live-check: ZAC's klassen vullen de payload met tien geplante, niet-gedeclareerde waarden; de controle achteraf zoekt ze in de payload en in de PDF.<br>Automatisch: `EpistolaTemplateDataTest` | Geen enkele geplante waarde staat in de payload of in de PDF. Elk veld in de payload is door het template gedeclareerd | Positief |
| 5.4<br>**TS-19** | Live-check met een contract dat datums als `format: date` declareert.<br>Automatisch: `EpistolaTemplateDataTest` | De payload bevat `2026-09-01`, Epistola accepteert het, en de brief toont `01-09-2026` | Positief |
| 5.5<br>**TS-21** | Automatisch: genereer met een template dat geen schema declareert (`EpistolaDocumentCreationServiceTest`) | Geweigerd met de melding dat het template niet aangeeft welke zaakgegevens het gebruikt. Er gaat niets naar Epistola | Negatief |

## Testscenario 6 – Open Zaak-opslag

| Testscenario 6 | |
|---|---|
| Testscenario ID | TS-23 t/m TS-26 |
| Functionaliteit | Open Zaak-opslag: het document in de Documenten API, met metadata en vertrouwelijkheid (#6) |
| Omschrijving | Het gegenereerde document wordt opgeslagen in de Documenten API van Open Zaak, als PDF met alle verplichte metadata en met de vertrouwelijkheid van het documenttype. Daarna is de kopie bij Epistola weg. Mislukt het opslaan, dan hoort de behandelaar dat en blijft er niets achter |
| Randvoorwaarden | De algemene randvoorwaarden. Stap 4.3 (TS-11) is uitgevoerd. Voor TS-24 heeft *Test zaaktype 1* groep *Brieven* met *ZAC Standaardbrief* → documenttype *e-mail*, dat zaakvertrouwelijk is. Voor TS-25 het controlescript, dat Epistola rechtstreeks bevraagt en nooit sleutels toont |
| Testdata | Het document *Testrapport TS-11* op ZAAK-2026-0000000032 (documenttype *brief*); ZAAK-2026-0000000001 met de titel *Testrapport TS-24*; gebruikers `behandelaar1` en `beheerder1` |

### Teststappen – Scenario 6

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 6.1<br>**TS-23** | Als `behandelaar1`: bij het document uit TS-11 op het oog-icoon klikken → de documentpagina nakijken. Via DevTools het informatieobject opvragen bij ZAC's REST API, voor `formaat`.<br>Automatisch: `EpistolaDocumentCreationServiceTest` | Titel, auteur, taal, documenttype, bestandsnaam `…pdf`, grootte, status *in bewerking*, versie 1, beschrijving. `formaat` is `application/pdf` | Positief |
| 6.2<br>**TS-24** | Als `beheerder1` op ZAAK-2026-0000000001: *Document maken* → *Brieven* → *ZAC Standaardbrief* → titel *Testrapport TS-24* → *Genereren* → de vertrouwelijkheid nakijken in de documentenlijst en via de REST API.<br>Automatisch: `EpistolaDocumentCreationServiceTest` | Het document is *zaakvertrouwelijk*, de waarde van het documenttype, en niet het vaste *openbaar* | Positief |
| 6.3<br>**TS-25** | Na TS-11 met het controlescript de documenten van deze zaak bij Epistola opvragen: `op run --env-file=./.env.epistola.tpl -- python3 …`.<br>Automatisch: `EpistolaClientServiceTest` | Epistola heeft geen document meer voor deze zaak | Positief |
| 6.4<br>**TS-26** | Automatisch: laat het opslaan in de Documenten API falen (`EpistolaDocumentCreationServiceTest`) | De behandelaar krijgt de melding dat het document is gemaakt maar niet is opgeslagen, er wordt niets aan de zaak gekoppeld, en de kopie bij Epistola wordt verwijderd (B20) | Negatief |

## Testscenario 7 – Zaakkoppeling

| Testscenario 7 | |
|---|---|
| Testscenario ID | TS-27 en TS-28, met TS-10 en TS-35 als negatieve stappen (uit testscenario 3 en 9) |
| Functionaliteit | Zaakkoppeling: het document hoort bij de zaak, en bij de taak als het vanuit een taak komt (#6) |
| Omschrijving | Het document hoort bij de zaak en staat direct in het tabblad Documenten. Komt het vanuit een taak, dan hoort het ook bij de taak en worden de taakrechten gecontroleerd. Mislukt de koppeling, dan blijft er niets half achter |
| Randvoorwaarden | De algemene randvoorwaarden. Stap 4.3 (TS-11) is net uitgevoerd, en de pagina is sindsdien niet ververst |
| Testdata | ZAAK-2026-0000000032 met het document *Testrapport TS-11*; een `taskId` voor TS-28, in de unittests |

### Teststappen – Scenario 7

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 7.1<br>**TS-27** | Na TS-11 het tabblad Documenten bekijken, zonder de pagina te verversen, en daarna de documentpagina openen | Het document staat er meteen. De documentpagina noemt de zaak | Positief |
| 7.2<br>**TS-28** | Automatisch: maak een document met een `taskId` (`EpistolaDocumentCreationServiceTest`, `DocumentCreationRestServiceTest`) | Het document hoort bij de zaak en bij de taak, en de taakrechten worden gecontroleerd | Positief |
| 7.3<br>**TS-10** | Verwijzing naar stap 3.4: een document maken vanuit een taak zonder taakrecht, en vanuit een taak die niet meer open is | Geweigerd (403 of 404), zonder aanroep van Epistola | Negatief |
| 7.4<br>**TS-35** | Verwijzing naar stap 9.6: het document staat in de Documenten API, maar de koppeling aan de zaak mislukt | Het document wordt opgeruimd, de behandelaar krijgt een melding die zegt dat er niets aan de zaak is toegevoegd, en de kopie bij Epistola wordt verwijderd | Negatief |

## Testscenario 8 – Preview

| Testscenario 8 | |
|---|---|
| Testscenario ID | TS-29 en TS-30 |
| Functionaliteit | Preview: het document openen in de browser, met metadata (#6) |
| Omschrijving | De behandelaar opent het gegenereerde document in de browser, en ziet op de documentpagina de metadata en de preview |
| Randvoorwaarden | De algemene randvoorwaarden. Stap 4.3 (TS-11) is uitgevoerd. Ingelogd als `behandelaar1` |
| Testdata | Het document *Testrapport TS-11* op ZAAK-2026-0000000032 |

### Teststappen – Scenario 8

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 8.1<br>**TS-29** | In het tabblad Documenten op de titel van het document klikken | De PDF opent in de browser, met de gerenderde brief | Positief |
| 8.2<br>**TS-30** | Het document openen via het oog-icoon | Alle metadata uit TS-23, en de preview | Positief |

Dit scenario heeft geen negatieve stap. Het testplan vraagt een negatief scenario *waar dat kan* (§8), en voor de preview staat er geen in het testplan.

## Testscenario 9 – Foutafhandeling

| Testscenario 9 | |
|---|---|
| Testscenario ID | TS-31 t/m TS-35 |
| Functionaliteit | Foutafhandeling: Epistola onbereikbaar, een mislukte job, een template dat verdwijnt, een timeout (#8) |
| Omschrijving | Is Epistola onbereikbaar, breekt de data het contract van het template, duurt een job te lang, verdwijnt een template of mislukt de opslag halverwege, dan krijgt de gebruiker een melding die zegt wat er mis is. Er wordt niets opgeslagen dat er niet hoort, en er blijft niets half achter |
| Randvoorwaarden | De algemene randvoorwaarden. Toegang tot de ZAC-container als root (`docker exec -u root zac-zac-1`), tot de ZAC-database (`psql`) en tot de logs van ZAC en Open Zaak (`docker logs`). Voor TS-31: minstens drie minuten geen aanroep van Epistola, omdat ZAC anders zijn open verbinding hergebruikt en het blokkeren niets doet |
| Testdata | Gebruikers `beheerder1` en `behandelaar1`; ZAAK-2026-0000000001 (zonder initiator) en ZAAK-2026-0000000032; template *ZAC Verplichte aanvrager*, waarvan het contract `aanvrager.naam` eist; de hosts-regel `127.0.0.1 demo.epistola.app`; voor TS-35 een informatieobjecttype dat in Open Zaak bij geen enkel zaaktype hoort |

### Teststappen – Scenario 9

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 9.1<br>**TS-31** | 1. Als `beheerder1` de dialoog openen, zodat ZAC de namen van de live lijst onthoudt. Daarna minstens drie minuten geen Epistola aanroepen.<br>2. `docker exec -u root zac-zac-1 sh -c 'cp /etc/hosts /tmp/h && echo "127.0.0.1 demo.epistola.app" >> /etc/hosts'`, en minstens 30 seconden wachten (Java's DNS-cache).<br>3. Als `beheerder1` de beheerkaart van *Test zaaktype 1* openen, en nagaan dat de templates bij naam staan (#30).<br>4. Als `behandelaar1` de dialoog op ZAAK-2026-0000000032 openen en genereren → de melding, het open blijven van de dialoog, de documenten en het ZAC-log nakijken (*listing the templates by the names read at …*) | Een begrijpelijke melding die zegt dat Epistola niet bereikbaar is, en de dialoog blijft open. De beheerkaart en de dialoog tonen de templates nog bij naam (#30). Er wordt niets opgeslagen (#8) | Negatief |
| 9.2<br>**TS-31** | `/tmp/h` terugzetten als `/etc/hosts`, en opnieuw genereren | ZAC genereert weer. Een poging direct na het herstel kan nog mislukken, omdat Java het adres bewaart; na ongeveer een halve minuut lukt het | Positief |
| 9.3<br>**TS-32** | Als `beheerder1` op ZAAK-2026-0000000001: *Document maken* → *Brieven* → *ZAC Verplichte aanvrager* → titel → *Genereren* → de melding nakijken → documenten van de zaak tellen → nagaan dat Epistola's reden niet in het ZAC-log staat.<br>Automatisch: `EpistolaClientServiceTest` | Epistola weigert de data, ZAC toont een melding die uitlegt dat het template gegevens mist, en er wordt niets opgeslagen (#8) | Negatief |
| 9.4<br>**TS-33** | Automatisch: laat een job langer duren dan de timeout (`EpistolaClientServiceTest`) | ZAC stopt met wachten, annuleert de job bij Epistola en meldt dat het te lang duurde | Negatief |
| 9.5<br>**TS-34** | `author_required_field_template.py --delete` → als `beheerder1` de dialoog op ZAAK-2026-0000000001 openen → groep *Brieven* nakijken. In de ZAC-database nagaan dat de mapping-rij er nog is | De dialoog biedt het template niet meer aan en loopt niet vast. De mapping blijft bewaard | Negatief |
| 9.6<br>**TS-35** | 1. Als `beheerder1`: in de ZAC-database (`zaaktype_epistola_document_template_parameters`) het `informatie_object_type_uuid` van *ZAC Standaardbrief* bij *Test zaaktype 1* tijdelijk op een informatieobjecttype zetten dat in Open Zaak bij geen enkel zaaktype hoort.<br>2. Op ZAAK-2026-0000000001 genereren → de melding nakijken.<br>3. In het log van Open Zaak de volgorde nagaan: `POST` document 201, `POST` zaakkoppeling 400, `DELETE` document 204.<br>4. De aantallen documenten en zaakkoppelingen in Open Zaak vergelijken met de aantallen van vóór de proef.<br>5. Met `epistola_copy_check.py` nagaan dat Epistola niets meer voor de zaak heeft, en de waarde terugzetten.<br>Automatisch: `ZgwApiServiceTest`, `EpistolaDocumentCreationServiceTest` | Het document wordt opgeruimd, de behandelaar krijgt een melding die zegt dat er niets aan de zaak is toegevoegd, en de kopie bij Epistola wordt verwijderd (#8, B20, B21) | Negatief |

## Testscenario 10 – Randvoorwaarden: SmartDocuments ongewijzigd, alleen CMMN

| Testscenario 10 | |
|---|---|
| Testscenario ID | TS-06 en TS-22 |
| Functionaliteit | Randvoorwaarden: SmartDocuments ongewijzigd (DoD 1), alleen CMMN (DoD 2, #7) |
| Omschrijving | De integratie laat de documentcreatie met SmartDocuments ongemoeid. Epistola werkt alleen voor CMMN-zaken, en bij een BPMN-zaak is die beperking zichtbaar, met een toelichting |
| Randvoorwaarden | De algemene randvoorwaarden. Ingelogd als `beheerder1`. Voor TS-22 de integratietests (`./gradlew itest`, TestContainers), die met SmartDocuments als provider draaien en zelf een ZAC-image van de geteste versie bouwen |
| Testdata | Zaaktype *BPMN test zaaktype 1* en zaak ZAAK-2026-0000000035; de SmartDocuments-deposit in `EpistolaTemplateDataTest` |

### Teststappen – Scenario 10

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 10.1<br>**TS-06** | Beheer → *BPMN test zaaktype 1* → kaarten nakijken. ZAAK-2026-0000000035 openen → zaakmenu nakijken, en het menu-item met de muis aanwijzen voor een toelichting | Geen Epistola-kaart bij het BPMN-zaaktype. De BPMN-zaak kan geen Epistola-document maken, en de beperking is zichtbaar met een toelichting (DoD 2, #7) | Negatief |
| 10.2<br>**TS-22** | Automatisch: serialiseer de SmartDocuments-deposit (`EpistolaTemplateDataTest`), en draai de integratietests (`./gradlew itest`) | De deposit houdt `dd-MM-yyyy`, en de SmartDocuments-integratietests slagen | Positief (regressie) |

## Testscenario 11 – Nieuwe versie van een Epistola-document (optioneel)

| Testscenario 11 | |
|---|---|
| Testscenario ID | TS-36 t/m TS-43 |
| Functionaliteit | Nieuwe versie: een Epistola-document opnieuw genereren als volgende versie (DoD 11, optioneel, #9) |
| Omschrijving | Bij een document dat met Epistola is gemaakt, genereert de behandelaar een nieuwe versie uit hetzelfde template, met de zaakgegevens van dat moment. De actie ontbreekt of wordt geweigerd als het document geen herkomst heeft, het template niet meer wordt aangeboden, Epistola onbereikbaar is of het document definitief is. Dit blok staat in testplan 1.3 en is niet door de stakeholders goedgekeurd |
| Randvoorwaarden | De algemene randvoorwaarden, met de ZAC-image van de branch `feat/epistola-new-document-version` (PR #43) en migratie `V101` toegepast. Ingelogd als `behandelaar1`. Toegang tot de ZAC-database (TS-39) en tot de ZAC-container als root (TS-40) |
| Testdata | ZAAK-2026-0000000032 (*Test zaaktype 2*, groep *Brieven 2*); de titel *Proef nieuwe versie*; het document *Testrapport TS-11*, van vóór `V101` (TS-38); `pdftotext` om de regel *Betreft* te vergelijken; endpoint `POST /rest/epistola-documents/{uuid}/versions` |

### Teststappen – Scenario 11

| Stap # | Actie (wat doe je?) | Verwacht resultaat | Type |
|---|---|---|---|
| 11.1<br>**TS-36** | *Document maken* → *Brieven 2* → titel *Proef nieuwe versie* → *Genereren* → de documentpagina openen.<br>Automatisch: `informatie-object-view.component.spec.ts` | Het menu toont *Nieuwe versie genereren*, naast *Nieuwe versie* | Positief |
| 11.2<br>**TS-37** | De beschrijving van de zaak wijzigen (*Edit case details*, met een reden) → terug naar de documentpagina → *Nieuwe versie genereren* → wachten op de melding en op de pagina van versie 2. Versie 1 en 2 downloaden en de regel *Betreft* vergelijken (`pdftotext`). Met `epistola_copy_check.py` nagaan dat Epistola niets meer voor de zaak heeft. Daarna de beschrijving terugzetten | Versie 2 van hetzelfde document, met de nieuwe beschrijving in de PDF. Versie 1 is nog te downloaden en ongewijzigd. Epistola bewaart niets van de zaak. De pagina toont de nieuwe versie | Positief |
| 11.3<br>**TS-38** | Op de documentpagina van *Testrapport TS-11* (van vóór `V101`): het menu nakijken, en in DevTools `GET /rest/epistola-documents/{uuid}` en `POST /rest/epistola-documents/{uuid}/versions` sturen.<br>Automatisch: `EpistolaDocumentVersionServiceTest`, `EpistolaDocumentRestServiceTest` | Geen actie *Nieuwe versie genereren*. Het `POST`-verzoek geeft 400 `msg.error.epistola.document.no-new-version` en er gaat niets naar Epistola | Negatief |
| 11.4<br>**TS-39** | In de ZAC-database (`zaaktype_epistola_document_template_parameters`) de `epistola_id` van het template van *Test zaaktype 2* tijdelijk wijzigen → de actie kiezen → de melding nakijken en nagaan dat het versienummer gelijk blijft → de waarde terugzetten.<br>Automatisch: `EpistolaDocumentVersionServiceTest` | 400 `msg.error.epistola.template.not-configured`, de versie blijft zoals ze was en er gaat niets naar Epistola | Negatief |
| 11.5<br>**TS-40** | Minstens drie minuten geen Epistola aanroepen, `demo.epistola.app` in `/etc/hosts` van de ZAC-container op `127.0.0.1` zetten, 35 seconden wachten, en de actie kiezen → de melding en het versienummer nakijken | Een melding dat Epistola niet bereikbaar is, en de versie blijft zoals ze was | Negatief |
| 11.6<br>**TS-40** | `/etc/hosts` terugzetten → nog eens 35 seconden wachten (Java bewaart het adres zolang) → de actie opnieuw kiezen | Na het herstel lukt de actie weer | Positief |
| 11.7<br>**TS-41** | Op de documentpagina *Sign* → *Yes* → het menu nakijken → in DevTools `POST …/versions` sturen.<br>Automatisch: `EpistolaDocumentRestServiceTest` (het recht `toevoegen_nieuwe_versie` ontbreekt) | Geen actie. Het `POST`-verzoek geeft 403 | Negatief |
| 11.8<br>**TS-42** | Automatisch: laat het opslaan van de nieuwe versie in de Documenten API falen (`EpistolaDocumentVersionServiceTest`) | De behandelaar krijgt de melding dat het document is gemaakt maar niet is opgeslagen, de huidige versie blijft en Epistola's kopie wordt verwijderd | Negatief |
| 11.9<br>**TS-43** | Automatisch: vraag een nieuwe versie van een document dat niet aan een zaak hangt (`EpistolaDocumentRestServiceTest`) | Geweigerd met `msg.error.epistola.document.no-new-version`. De zaak komt uit het document en niet uit het verzoek, en zonder zaak is er niets om te genereren | Negatief |

> 💡 Controle: elke functionaliteit uit §3 van het testplan heeft een testscenario (zie het overzicht), en elke testcase uit
> §8 hoort bij precies één scenario. De stappen 7.3 en 7.4 verwijzen naar testcases uit testscenario 3 en 9.
