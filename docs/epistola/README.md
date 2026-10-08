# Epistola — projectdocumentatie

De documentatie van het Epistola-prototype: een configureerbare integratie waarmee ZAC documenten laat
genereren door [Epistola](https://epistola.app/) in plaats van door SmartDocuments, naast de bestaande
SmartDocuments-flow en wederzijds uitsluitend daarmee.

Deze documenten horen bij de prototyperepository
[`infonl/zac-epistola-prototype`](https://github.com/infonl/zac-epistola-prototype) en bij het
bijbehorende projectbord.

> **Deze Markdown is de bron.** Wie iets wil wijzigen, wijzigt het hier. Elk document noemt bovenaan het issue
> waar het bij hoort en de stand waarop het is bijgewerkt.

## Documenten

| Document | Onderwerp | Issue |
|---|---|---|
| [Technisch en functioneel ontwerp](technisch-functioneel-ontwerp.md) | De flow, de provider-abstractie, de datamapping, het autorisatiemodel en de API-integratie. Bevat de sequencevergelijking van beide providers en de componentstructuur | #14 |
| [Datamodel](datamodel.md) | De zaaktypeconfiguratietabellen en wat Epistola eraan toevoegt: drie kolommen op het zaaktype, de tabel met de instellingen per template en de tabel `epistola_document` (#51), met ERD | #14 |
| [Wireframes](wireframes.md) | De vier schermen — beheer, zaakzijbalk, dialoog en foutpaden — met veldenlijsten en de getekende mockups in [`wireframes/`](wireframes/) | #15 |
| [Testplan](testplan.md) | Scope, omgeving, testdata en 36 testcases, positief en negatief, over de acht afgesproken gebieden, plus 8 voor het optionele #9. Volgens het examensjabloon (B1-K1-W4) | #18 |
| [Testscenario's](testscenarios.md) | De testcases uit het testplan uitgewerkt tot elf testscenario's, één per functionaliteit, met randvoorwaarden, testdata en teststappen, positief en negatief. Volgens het examensjabloon (B1-K1-W4) | #18 |
| [Testrapport](testrapport.md) | De uitvoering in drie rondes, op 25 en 30 september (de derde voor #9): resultaat en bewijs per testcase, buglijst, conclusies en aanbevelingen, met de screenshots in [`testrapport/`](testrapport/) | #19 |
| [Uitgangspunten, eisen en wensen](uitgangspunten-eisen-wensen.md) | Projectdoel en afbakening, de Definition of Done met de stand per item, de eisen buiten de user stories, de techniek, de MoSCoW-prioritering en de werkwijze. In het Engels | #13 |
| [Ontwerpverantwoording](ontwerpverantwoording.md) | Common Ground, AVG en dataminimalisatie, de token-afhandeling, logging en het risicoregister R1 t/m R9 met de stand per risico. In het Engels | #16 |
| [Verbetervoorstellen](verbetervoorstellen.md) | De uitkomst van het prototype als besluiten: de negen risico's uit de ontwerpverantwoording met hun uitkomst, vijftien voorstellen voor productie en daarna, wat al is opgelost, en wat bewust niet wordt voorgesteld. Procesverbeteringen staan in de reflectie (#23) | #20 |

## Kernbesluiten in één oogopslag

| | Besluit |
|---|---|
| Provider-keuze | `DOCUMENT_CREATION_PROVIDER` met drie waarden — SmartDocuments, Epistola of geen. Twee tegelijk wordt bij het opstarten geweigerd |
| Scope | Alleen CMMN-zaken, alleen PDF |
| Client | De officiële Jakarta EE-client van Epistola wordt overgenomen, niet zelf gegenereerd |
| Generatie | Asynchroon: indienen, de job pollen, downloaden — alles binnen de al geauthenticeerde aanroep |
| Authenticatie | Een API key van de tenant, met de rollen `DOCUMENT_GENERATOR` en `CONTENT_VIEWER`. Sinds contract 1.3.1 is dat Epistola's ondersteunde methode; de twee JWT-methoden zijn experimenteel |
| Catalog | ZAC houdt voor Epistola geen templategroepen bij. Elk zaaktype kiest één Epistola-catalog en biedt elk template daarin aan, met één documenttype voor het hele zaaktype (#51). Dat documenttype is de standaard; een template kan een eigen hebben en kan uit staan |
| Autorisatie | Het bestaande recht `creeren_document` wordt hergebruikt, er komt geen Epistola-specifiek recht |
| Payload | Allow-listed tegen het JSON Schema van het gekozen template — alleen gedeclareerde variabelen gaan mee. Datums in ISO 8601 |
| Templatenaam | Het id is de sleutel en Epistola de bron. ZAC onthoudt de namen van de laatste geslaagde lijst per catalog in het geheugen, en toont ze als Epistola niet bereikbaar is (B16) |
| Foutafhandeling | Elke Epistola-fout krijgt een eigen foutcode en een melding die zegt of opnieuw proberen helpt. Mislukt de opslag in Open Zaak, dan wordt het document niet bewaard en ook bij Epistola verwijderd (B20, B21) |
| Namen | Volgen Epistola, niet SmartDocuments: *template* en *catalog*, ook in het Nederlands (B18) |

De onderbouwing van elk van deze besluiten staat in het
[technisch en functioneel ontwerp](technisch-functioneel-ontwerp.md).
