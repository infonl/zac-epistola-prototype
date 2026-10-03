# Verkenning: een voorbeeld van het document, vóór het wordt opgeslagen

| | |
|---|---|
| Soort | Verkenning (`explore/`) en een extra buiten de Definition of Done. Geen onderdeel van het testplan, zoals de andere extra's (B25) |
| Branch | `explore/epistola-preview-before-saving`, gebouwd op `feat/epistola-variant-by-kanaal` (#49) |
| Stand | **3 oktober 2026.** Gebouwd, en gecontroleerd met unittests, Epistola's testserver en een draaiende ZAC met een echte browser ([§5](#5-wat-is-gecontroleerd-en-wat-niet)) |

> **Waarom deze branch bestaat.** Een voorstel dat de stakeholders kunnen bekijken en overnemen of laten liggen,
> zonder dat iemand de achtergrond hoeft te kennen. Het staat los van de rest van het prototype: zonder deze branch
> werkt alles zoals het was.

## 1. Het idee

Epistola kan een document **in één keer renderen en teruggeven zonder het te bewaren**: het endpoint `preview`.
ZAC gebruikt dat nu niet. Een behandelaar die een brief genereert, ziet hem pas als hij al in het dossier staat. Is
er iets mis, dan blijft het document in Open Zaak staan, en de omweg is *Nieuwe versie genereren*.

Met deze verkenning krijgt het formulier *Document maken* een knop **Voorbeeld bekijken**. ZAC vraagt Epistola om
een preview van het gekozen template, met dezelfde zaakgegevens en in dezelfde variant als bij genereren, en toont de
PDF in een dialoog. Er wordt niets opgeslagen.

Het sluit aan op wat de stakeholders vaak vragen als ze een brief-generator zien: *kan ik het zien voordat het in de
zaak staat?* Het is de bedoelde toepassing van het endpoint, dus het botst niet met besluit B5 (de preview niet
gebruiken **als** het dossierdocument).

## 2. Wat een behandelaar ziet

1. *Document maken*, een templategroep, een template en eventueel een kanaal kiezen.
2. **Voorbeeld bekijken** (naast *Genereren*). De knop is pas actief als het template en, waar nodig, het kanaal
   bekend zijn, zodat een voorbeeld nooit een andere variant toont dan die ZAC straks genereert.
3. Binnen enkele seconden opent een dialoog met de PDF, en de zin dat dit een voorbeeld is dat nog niet in de zaak
   staat. Pas *Genereren* maakt het document dat in het dossier komt.
4. Breekt de zaak het datacontract van het template, dan meldt ZAC dat **meteen**, met dezelfde melding en dezelfde
   details als bij een mislukte generatie. Bij genereren komt die melding pas nadat de job is gestart en gepold.

Er verandert niets aan de rechten: een voorbeeld vraagt hetzelfde als genereren (`creeren_document` op de zaak, en op
de taak als het document bij een taak hoort), omdat het de zaakgegevens op dezelfde manier naar Epistola stuurt.

## 3. Hoe het is gebouwd

| Onderdeel | Waar |
|---|---|
| Endpoint `POST /rest/document-creation/epistola/preview-document`, antwoordt `application/pdf` | `src/main/kotlin/nl/info/zac/app/documentcreation/DocumentCreationRestService.kt` |
| De zaakgegevens en het kanaal voor een preview en voor genereren komen uit één stap, `readGenerationInput`, zodat ze niet uit elkaar kunnen lopen | `src/main/kotlin/nl/info/zac/documentcreation/EpistolaDocumentCreationService.kt` |
| De aanroep van Epistola's `previewDocument`; geen job, niets te verwijderen | `src/main/kotlin/nl/info/client/epistola/EpistolaClientService.kt` |
| Een 400 van Epistola met `Data validation failed: …` wordt dezelfde `EpistolaTemplateDataRejectedException` als bij een mislukte job | `src/main/kotlin/nl/info/client/epistola/EpistolaApiExceptions.kt` |
| De knop, de dialoog en het lezen van een foutmelding die als Blob aankomt | `informatie-object-create-attended.component.*`, `epistola-preview-dialog/`, `shared/http/parse-blob-error.ts` (onder `src/main/app/src/app/`) |
| WireMock-antwoord voor de lokale stand-in | `scripts/docker-compose/imports/epistola-wiremock/mappings/preview-document.json` |

## 4. Wat Epistola's testserver liet zien

Gemeten op 3 oktober 2026 met een eigen probe tegen de testtenant (Suite 1.3.0, client 1.4.0). De templates waren die
dag opnieuw ingericht, omdat de tenant elke dag wordt gereset.

| Waargenomen | Gevolg voor ZAC |
|---|---|
| Een preview geeft een PDF terug in 0,2 tot 0,6 s voor de standaardbrief en 1,4 tot 3,6 s voor de besluitbrief met afbeeldingen | Een behandelaar wacht kort. De knop toont een spinner |
| De meegestuurde gegevens verschijnen in de PDF | Het voorbeeld toont echt de gegevens van deze zaak |
| Een verbroken datacontract geeft **direct** `400 template-data-invalid`, met `detail` `Data validation failed: /aanvrager: is required`: dezelfde tekst als een mislukte job | ZAC gebruikt alleen `detail` en laat de gebruiker die zien. `missingFields` bevat ook velden die niet verplicht zijn (`required: false`), en is dus niet geschikt om te tonen |
| Een onbekend template geeft `404 Default Variant Not Found` | Wordt de bestaande melding *het template bestaat niet meer* |
| Het kanaal `post` zonder voorkeur voor `system.locale` geeft `409 ambiguous-variant`, omdat de Nederlandse en de Engelse variant voor post even goed passen | Raakt ZAC niet: de variantkeuze uit #49 stuurt `nl-NL` als voorkeur mee |
| Epistola zet de tekst **Epistola Preview** in de PDF (twee keer per pagina); een gegenereerd document heeft die niet | Een voorbeeld is zichtbaar een voorbeeld en niet te verwarren met het echte document. ZAC hoeft er niets voor te doen |
| De preview-PDF heeft op Suite 1.3.0 dezelfde PDF/A-kenmerken en dezelfde producer als een gegenereerd document | Het contract belooft dat niet. ZAC bewaart een preview daarom nooit, en zegt in de dialoog alleen dat het niet is opgeslagen |

## 5. Wat is gecontroleerd, en wat niet

- **Backend:** 2.824 unittests, 0 mislukt. Nieuw zijn onder meer `EpistolaDocumentPreviewTest`, `EpistolaClientServicePreviewTest` en
  `DocumentCreationRestServicePreviewTest`. Daarnaast draait `EpistolaClientServiceRequestTest` een **echte** MicroProfile-client tegen
  een nepserver. Alleen zo is te zien dat de tekst van Epistola's 400 nog te lezen is uit de exception die de client gooit, en dat hij
  nergens in de oorzaak-keten van de eigen exception terugkomt.
- **Frontend:** 3.039 tests, 0 mislukt, en de strikte speclint op de aangeraakte specs.
- **Epistola:** de probes van §4, tegen de echte testserver.
- **Live, in ZAC met een echte browser** (3 oktober, `beheerder1`, Epistola's testserver):

  | Wat | Uitkomst |
  |---|---|
  | De knop zonder template, en met een template waarvan de kanalen nog niet bekend zijn | Niet actief. Met template actief, met een spinner terwijl Epistola rendert |
  | De dialoog | Toont de standaardbrief met de gegevens van de zaak (zaaknummer, zaaktype, status, de naam van de initiator) in de PDF-viewer van de browser |
  | Drie voorbeelden achter elkaar bij ZAAK-2026-0000000032 | Het aantal documenten in de zaak bleef 15: er wordt niets opgeslagen |
  | Besluitbrief zonder kanaal, bij een zaak met communicatiekanaal e-mail | De variant *digitaal*, byte voor byte gelijk aan een voorbeeld waar *digitaal* expliciet is gevraagd. *Post* geeft een ander bestand |
  | Een template dat het zaaktype niet aanbiedt, en een onbestaand template | 400 `template.not-configured`, voordat er zaakgegevens naar Epistola gaan |
  | Een zaak zonder initiator en het template *ZAC Verplichte aanvrager* | Na 0,5 s de standaardfoutdialoog: *Het template vraagt zaakgegevens die deze zaak niet heeft…* met daaronder `/aanvrager: is required`. Het aantal documenten bleef gelijk |
  | Snelheid | Standaardbrief 0,2 tot 0,6 s, besluitbrief met afbeeldingen 1,7 tot 2,1 s |

- **Gevonden door het log te lezen, en opgelost:** het log bevatte de redenen van Epistola (`/aanvrager: is required`), omdat de afwijzing
  de exception van de client als oorzaak meekreeg en die de reden in zijn eigen bericht herhaalt. Dat strijdt met de afspraak dat die
  tekst nooit in het log komt. De afwijzing heeft nu geen oorzaak (`b1eee445d`). Dit is bewezen met de test op de echte client, maar niet
  opnieuw live bekeken, omdat daarvoor de image opnieuw gebouwd en de gebruiker opnieuw ingelogd moet worden.
- **Niet gecontroleerd:** de variantkeuze in de browser met de kanaalkiezer (alleen via het endpoint en in de specs), een document bij
  een taak, en meer dan één gebruiker tegelijk.

## 6. Open punten, en wat ik bewust niet deed

1. **Lege gegevens.** ZAC stuurt alleen wat het datacontract van het template noemt. Noemt het contract alleen gegevens die deze zaak
   niet heeft (bijvoorbeeld alleen `taak`, bij een zaak zonder taak), dan is dat `{}`. Epistola rendert een preview met `{}` met de
   **voorbeeldgegevens van het template**: de PDF was byte voor byte gelijk aan die met het voorbeeld. Het voorbeeld toont dan een brief
   met verzonnen gegevens. Het is een smal geval, maar het misleidt precies waar een voorbeeld voor dient, en hoort afgevangen te worden
   voordat dit in productie komt. Wat genereren met `{}` doet, is niet gemeten.
2. **Van voorbeeld naar genereren.** De dialoog heeft alleen een sluitknop. Een knop *Ziet er goed uit, genereer* is de logische stap, maar
   vraagt om de titel en de beschrijving van het formulier in dezelfde flow.
3. **Voor beheerders.** Een voorbeeld op de templatekaart (met een gekozen zaak) valt buiten deze verkenning.
4. **Vooraf valideren.** Epistola's `validateTemplateData` zou al vóór de preview kunnen zeggen wat ontbreekt. De preview geeft dat
   nu zelf al, dus dit is niet gebouwd.
5. **Rate limit.** Epistola belooft een limiet, maar noemt geen getal en ZAC beperkt niets. Een 429 geeft de bestaande melding *te veel
   verzoeken*.
6. **Content-Security-Policy.** De dialoog toont de PDF in een `<object>` met een `blob:`-adres. ZAC zet zelf geen CSP, maar een
   organisatie die er een afdwingt, moet `blob:` toestaan voor `object-src`.
7. **Het log bij andere fouten.** `EpistolaRequestFailedException` kent dezelfde constructie als de afwijzing hierboven had: de
   exception van de client is de oorzaak, en die herhaalt Epistola's `title` en `detail` in zijn bericht. Voor die andere fouten is niet
   gemeten welke tekst dat oplevert, en deze verkenning wijzigt ze niet.
8. **Klein.** *Genereren* blijft aan terwijl een voorbeeld wordt gemaakt. De sluitknop van de gedeelde dialoog heeft geen
   toegankelijke naam. Dat laatste was er al.

## 7. Zelf proberen

```bash
./gradlew test --tests "nl.info.zac.documentcreation.EpistolaDocumentPreviewTest" \
               --tests "nl.info.client.epistola.EpistolaClientServicePreviewTest" \
               --tests "nl.info.client.epistola.EpistolaClientServiceRequestTest" \
               --tests "nl.info.zac.app.documentcreation.DocumentCreationRestServicePreviewTest"
cd src/main/app && npx ng test --test-path-pattern="informatie-object-create-attended|epistola-preview-dialog|parse-blob-error"
```

Live: `./gradlew buildDockerImage`, dan `./start-docker-compose.sh -l -E` (Epistola's testserver) of `-W` (WireMock, zonder
inloggegevens). Open een CMMN-zaak van een zaaktype met Epistola, kies *Document maken* en een template, en druk op
*Voorbeeld bekijken*.
