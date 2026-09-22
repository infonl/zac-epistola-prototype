# Epistola — projectdocumentatie

De documentatie van het Epistola-prototype: een configureerbare integratie waarmee ZAC documenten laat
genereren door [Epistola](https://epistola.app/) in plaats van door SmartDocuments, naast de bestaande
SmartDocuments-flow en wederzijds uitsluitend daarmee.

Deze documenten horen bij de prototyperepository
[`infonl/zac-epistola-prototype`](https://github.com/infonl/zac-epistola-prototype) en bij het
bijbehorende projectbord.

> **Het gepubliceerde artifact is de bron, deze Markdown is de kopie.** Elk document noemt bovenaan zijn
> bron-URL, het issue waar het bij hoort en de stand waarop de kopie is gemaakt. Wijzigt het artifact, dan
> moet de kopie hier opnieuw worden bijgewerkt — niet andersom.

## Documenten

| Document | Onderwerp | Issue |
|---|---|---|
| [Technisch en functioneel ontwerp](technisch-functioneel-ontwerp.md) | De flow, de provider-abstractie, de datamapping, het autorisatiemodel en de API-integratie. Bevat de sequencevergelijking van beide providers en de componentstructuur | #14 |
| [Datamodel](datamodel.md) | De zaaktypeconfiguratietabellen en de drie wijzigingen die Epistola nodig heeft, met ERD | #14 |

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
