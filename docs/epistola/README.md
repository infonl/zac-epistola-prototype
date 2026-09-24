# Epistola — projectdocumentatie

De documentatie van het Epistola-prototype: een configureerbare integratie waarmee ZAC documenten laat
genereren door [Epistola](https://epistola.app/) in plaats van door SmartDocuments, naast de bestaande
SmartDocuments-flow en wederzijds uitsluitend daarmee.

Deze documenten horen bij de prototyperepository
[`infonl/zac-epistola-prototype`](https://github.com/infonl/zac-epistola-prototype) en bij het
bijbehorende projectbord.

> **Deze Markdown is de bron.** Tot 24 september 2026 waren gepubliceerde artifacts leidend en was dit de
> kopie. Die artifacts worden niet meer bijgewerkt, en wie iets wil wijzigen, wijzigt het hier. Elk document
> noemt bovenaan het issue waar het bij hoort en de stand waarop het is bijgewerkt.

## Documenten

| Document | Onderwerp | Issue |
|---|---|---|
| [Technisch en functioneel ontwerp](technisch-functioneel-ontwerp.md) | De flow, de provider-abstractie, de datamapping, het autorisatiemodel en de API-integratie. Bevat de sequencevergelijking van beide providers en de componentstructuur | #14 |
| [Datamodel](datamodel.md) | De zaaktypeconfiguratietabellen en de drie wijzigingen die Epistola nodig heeft, met ERD | #14 |
| [Wireframes](wireframes.md) | De vier schermen — beheer, zaakzijbalk, dialoog en foutpaden — met veldenlijsten en de getekende mockups in [`wireframes/`](wireframes/) | #15 |

De uitgangspunten, eisen en wensen (#13) en de ontwerpverantwoording (#16) staan hier nog niet. Die bestaan
voorlopig alleen als artifact, gelinkt vanuit hun issue.

## Kernbesluiten in één oogopslag

| | Besluit |
|---|---|
| Provider-keuze | `DOCUMENT_CREATION_PROVIDER` met drie waarden — SmartDocuments, Epistola of geen. Twee tegelijk wordt bij het opstarten geweigerd |
| Scope | Alleen CMMN-zaken, alleen PDF |
| Client | De officiële Jakarta EE-client van Epistola wordt overgenomen, niet zelf gegenereerd |
| Generatie | Asynchroon: indienen, de job pollen, downloaden — alles binnen de al geauthenticeerde aanroep |
| Authenticatie | Een statische API key. Self-signed JWT en OAuth 2.0 client credentials zijn vastgelegd als afgewezen alternatieven, met OAuth als productiepad |
| Templategroepen | Bestaan in ZAC, niet in Epistola: de beheerder maakt ze zelf en hangt er platte Epistola-templates onder |
| Autorisatie | Het bestaande recht `creeren_document` wordt hergebruikt, er komt geen Epistola-specifiek recht |
| Payload | Allow-listed tegen het JSON Schema van het gekozen template — alleen gedeclareerde variabelen gaan mee |

De onderbouwing van elk van deze besluiten staat in het
[technisch en functioneel ontwerp](technisch-functioneel-ontwerp.md).
