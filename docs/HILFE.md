# Hilfe- und Funktionsdokumentation (deutsch)

Diese Dokumentation beschreibt die wichtigsten Funktionen der Anwendung **VEMMiNA Assistent**.

Die Navigation ist bewusst flach aufgebaut: **Gutschriftservice**, **Validierungen**, **Auswertungen** und **GoAffPro Sync** stehen direkt in der Sidebar. Die **Hilfe** ist über den Button im Kopfbereich erreichbar. Eine zusätzliche VEMMiNA-Obergruppe wird nicht mehr angezeigt.

## 1) Hauptfunktionen im Arbeitsbereich

- **Polling starten/stoppen**
  - Ruft neue Zahlungen aus GoAffPro ab.
  - Aktualisiert die Tabelle und die Zahllauf-Historie.
- **Zahllauf-Auswahl**
  - Dropdown zeigt Zahlläufe, neuester oben.
  - Beeinflusst den Startpunkt (`sinceId`) für das Polling.
- **Neusten Zahllauf hinzufügen**
  - Ermittelt den neuesten Zahllauf aus GoAffPro und ergänzt ihn in die Historie.
- **Gutschrift & Provisionsübersicht exportieren**
  - Erstellt für die markierten Zeilen die Gutschrift (bzw. bei Altfällen Rechnung) als PDF mit der Provisionsübersicht als Anlage ab Seite 2, dazu die JSON-Rohdaten.
  - Mit hinterlegter Steuernummer oder USt-IdNr. der Beraterin wird die Gutschrift als E-Gutschrift (Factur-X/ZUGFeRD) erzeugt, sonst als PDF ohne E-Rechnung.
  - Ist „E-Gutschrift (ZUGFeRD + Gutschrift-PDF) mitschicken & ablegen“ ausgeschaltet, entsteht kein Gutschrift-PDF; die Provisionsübersicht wird dann als eigenes PDF (`provisionsnachweis_…pdf`) abgelegt und verschickt.
  - Kann optional E-Mails versenden.
- **Aktion „Vorschau“ (je Zeile)**
  - Zeigt den Beleg zur Sichtprüfung im Browser: gleiche Daten, gleiche Vorlage, gleiche Provisionsübersicht wie beim echten Erzeugen.
  - Statt einer Nummer steht „ENTWURF“ im Beleg, dazu ein Wasserzeichen „ENTWURF – kein gültiger Beleg“ auf jeder Seite.
  - Verbraucht keine Belegnummer, legt keine Datei ab, verschickt nichts und schreibt nichts ins Versandprotokoll. Eine E-Rechnungs-XML gibt es in der Vorschau nicht.
  - Gibt es keine Vorschau, steht der Grund im Vorschaufenster und als rote Meldung über der Tabelle. Die Meldung bleibt stehen, bis man sie schließt; die graue Statuszeile darüber überschreibt das Polling jede Minute.
  - Enthält der Zahllauf Altbuchungen vor dem Provisionsstart, die sich nicht aufheben (siehe 4a), zeigt die Vorschau den Beleg mit diesen Buchungen, und eine gelbe Meldung weist darauf hin.
- **Meldungen über der Tabelle**
  - Rot: Ein Beleg, eine Vorschau oder das Polling ist fehlgeschlagen. Der Knopf „Fehlerprotokoll“ führt zu den technischen Details.
  - Gelb: Hinweis, z. B. auf Altbuchungen in der Vorschau.
  - Klappt das Polling wieder, verschwindet seine Fehlermeldung von selbst.
- **Filterfeld**
  - Filtert die Tabelle nach Name, Land, Steuernummer, Belegdatum usw.

## 2) Tabellenfunktionen

- **Sortierung per Spaltenkopf** (auf/absteigend).
- **Mehrfachauswahl per Checkboxen**.
- **Ja/Nein-Spalten** zeigen ein grünes Häkchen bzw. ein rotes Kreuz. Im Spaltenfilter (Rechtsklick)
  heißen die Werte „Ja“ und „Nein“.
- **Spalte „IBAN vorhanden“**
  - Häkchen, wenn eine IBAN vorhanden ist, sonst Kreuz.
- **Spalte „IBAN korrekt“**
  - Häkchen, wenn die IBAN Syntax- und Prüfsummenprüfung besteht, sonst Kreuz.
  - Geprüft werden:
    - zulässige Zeichen
    - Länderkennung mit zwei Prüfziffern
    - die Länge je Land (z. B. DE 22, AT 20, CH 21 Zeichen)
    - bei Ländern mit rein numerischem Kontoteil (z. B. DE, AT, BE) nur Ziffern nach den Prüfziffern
    - die Prüfsumme (Modulo 97)
  - Leerzeichen und Groß-/Kleinschreibung spielen keine Rolle.
  - Beim Darüberfahren steht der Grund, z. B. „Die IBAN hat 23 statt 22 Zeichen“.
- **Zeilenwarnung (leicht rot)**
  - Wird gesetzt, wenn
    - Name fehlt,
    - IBAN fehlt,
    - oder IBAN ungültig ist.
- **Spalte „Adresse“ rot mit Warndreieck**
  - Die Anschrift der Beraterin ist unvollständig, meist fehlt die Straße. Beim Darüberfahren steht, welcher Teil fehlt.
  - Die Gutschrift wird trotzdem erzeugt. Die Statuszeile bzw. die Ergebnisliste des Sammelexports weist aber darauf hin. Die Gutschrift braucht die vollständige Anschrift (§ 14 Abs. 4 UStG), darum bitte in GoAffPro nachtragen.
- **Aktuelle Daten**
  - Jeder Polling-Abruf holt die Beraterinnendaten frisch aus GoAffPro und aktualisiert auch Zeilen, die schon in der Tabelle stehen. Eine in GoAffPro nachgetragene Anschrift erscheint so beim nächsten Abruf.
  - Beim Erzeugen einer Gutschrift werden Zahllauf und Beraterin unabhängig davon noch einmal direkt aus GoAffPro geladen.

## 3) Einstellungen

- **GoAffPro API-Key**
  - Wird aus Sicherheitsgründen nie angezeigt; das Feld zeigt nur, ob ein Schlüssel hinterlegt ist.
  - Leer lassen und speichern: Der bisherige Schlüssel bleibt erhalten. Ein neuer Schlüssel wird einfach eingetragen und gespeichert.
  - Kommt der Schlüssel aus der Umgebungsvariable `GOAFFPRO_API_KEY` (z. B. `.env`), ist das Feld gesperrt; geändert wird er dann dort.
- **Export-Zielordner**
- **E-Mail-Konfiguration**
  - Kontakt-E-Mail
  - SMTP Host/Port/Benutzer/Passwort
  - Versand aktivieren/deaktivieren
  - Versandziel: Kontakt-E-Mail oder Beraterinnen-E-Mail. „Kontakt-E-Mail“ ist der Testmodus: Alle Mails – auch die Stammdaten-Erinnerungen – gehen dann an die Kontakt-E-Mail.
  - BCC: bekommt eine Kopie jeder Mail (Gutschriften, Erinnerungen, Wochenmails).
  - Buchhaltungs-E-Mail: bekommt jede Gutschrift (bzw. Altfall-Rechnung), die an die Beraterin verschickt wird, als eigene Mail mit Gutschrift-PDF (inkl. Provisionsübersicht) und – falls vorhanden – der E-Rechnungs-XML. Betreff: „Gutschrift GS-… – Name – Zahllauf …“; bei einer neuen Version nennt die Mail den ersetzten Beleg. Im Testmodus (Versand an Kontakt-E-Mail) geht nichts an die Buchhaltung. Schlägt nur diese Mail fehl, meldet die Statuszeile das; der Beleg an die Beraterin ist dann trotzdem raus.
- **Zahllauf-Historie bearbeiten**
  - Zahlläufe können entfernt werden.
- **Speichern-Feedback**
  - Meldet, ob tatsächlich Änderungen erkannt wurden oder nicht.
- **Fehlerprotokoll** (Kopfzeile → Einstellungen, ganz unten)
  - Sammelt Fehler aus allen Bereichen zum Kopieren oder Herunterladen, siehe Abschnitt 16.

## 4) Exportlogik

- Pro Exportlauf wird automatisch ein Unterordner erzeugt:
  - `export_<belegdatum>_<hoechste-payment-id>_<beraterinnenname>`
- Dort werden die generierten Dateien (PDF/JSON) abgelegt.

### Provisionsübersicht als Anlage

Die Provisionsübersicht steht im selben PDF wie die Gutschrift, ab Seite 2, im Stil der Gutschrift
und mit Seitenzahlen („Seite x von y“) auf allen Seiten. Sie entsteht im selben Durchgang wie die
Gutschrift (eingebettete Schrift, A4, PDF/A-3); die E-Gutschrift-Daten (factur-x.xml) bleiben
dadurch unberührt. Maßgeblich für die E-Rechnung ist weiterhin die XML; die Übersicht ist eine
erläuternde Anlage.

Inhalt: Kennzahlen des Zahllaufs (maskierte Bankverbindung, Zeitraum, Anzahl, Summen), Aufteilung
Direkt/Team, alle Einzeltransaktionen mit Summenzeile, Stammdaten der Beraterin, Hinweise zur
eigenen Nachweisführung und ein Inhalts-Hash.

- **Stornos** (GoAffPro-Buchung „delete“) mindern Bestellwert und Bemessungsgrundlage und sind in
  der Liste als „Storno“ gekennzeichnet.
- **Änderungen** (Buchung „update“) zählen nach derselben Regel wie ihre Provision. GoAffPro bucht
  eine Provision erst, wenn sie freigegeben ist; eine Bestellung, die als „offen“ ankommt, hat
  deshalb keine eigene Erstbuchung. Daraus folgt:
  - **Freigabe** (offen → freigegeben) zählt mit Bestellwert und BMG der Bestellung.
  - **Ablehnung** und **zurück auf offen** ziehen Bestellwert und BMG wieder ab.
  - **Korrektur** (bleibt freigegeben, z. B. geänderte Provision) zählt mit der Differenz.
- „–“ steht, wenn die Buchung keinen Bestellwert enthält (z. B. Team-Provisionen) oder ihre Werte
  nicht centgenau zur gebuchten Provision passen.
- Der Betrag des Zahllaufs heißt in der Übersicht „Gutschriftbetrag (netto)“ – „Auszahlungsbetrag“
  auf Seite 1 ist netto plus Umsatzsteuer.

## 4a) Altfälle bis 31.12.2025 (Gutschrift oder Rechnung)

Die Dokumentart wird **automatisch aus dem Auszahlungsdatum des Zahllaufs** abgeleitet — es gibt
bewusst keinen manuellen Schalter:

- Zahlläufe, die **vor** dem Stichtag ausgezahlt wurden (Standard: 01.01.2026, Europe/Berlin) →
  **Rechnung** gegen die **VEMMiNA Qualitäts- Haushaltsprodukte GmbH**, eigener Nummernkreis
  `RE-JJJJ-NNNN`, ZUGFeRD-TypeCode 380, ohne §-14-Gutschrifts- und Widerspruchshinweise.
- Zahlläufe, die **ab** dem Stichtag ausgezahlt wurden → **Gutschrift** gegen die
  **S+R Linear Technology GmbH**, Nummernkreis `GS-JJJJ-NNNN`, TypeCode 389, mit §-14-Hinweisen.

Grundlage ist `payment.created_at`, nicht das Datum der einzelnen Provisionen. Da jeder Zahllauf
genau ein Auszahlungsdatum hat, kann er nicht auf beiden Seiten des Stichtags liegen. Fehlt das
Datum, wird als sicherer Rückfall eine Gutschrift erstellt.

**Provisionen vor dem 01.10.2026** erscheinen nicht auf Gutschriften: Vom 01.01. bis 30.09.2026
erhielten die Beraterinnen Codes statt Provision; die in GoAffPro trotzdem erfassten und wieder
stornierten Buchungen werden ausgeblendet. Ausgeblendet wird je Bestellung bzw. Belohnung und nur,
was sich zu 0 aufhebt – der Betrag der Gutschrift passt dadurch weiter zur Auszahlung.

**Altbuchungen, die sich nicht aufheben,** gibt es trotzdem. Beispiel Zahllauf 3597878: zwei
Provisionskorrekturen vom 25.02.2026 (−3,30 € und −8,14 €) und eine manuelle Anpassung in GoAffPro
vom 30.09.2026 (+11,43 €), zusammen −0,01 €. Für solche Fälle gilt:

- Beim Erstellen der Gutschrift kommt eine **Rückfrage**. Sie nennt jede betroffene Bestellung bzw.
  Belohnung mit ihrem Restsaldo und die Summe.
- **Abbrechen:** Es entsteht kein Beleg, und es wird keine Belegnummer vergeben.
- **Bestätigen:** Die Buchungen stehen mit Datum in den Einzeltransaktionen der Provisionsübersicht.
  Die Kennzahlen nennen sie als „Aufgeführt trotz Datum vor 01.10.2026“ mit ihrem Saldo. Der
  Zeitraum der Gutschrift beginnt dann mit der ältesten von ihnen. Die sauber stornierten Paare
  bleiben ausgeblendet.
- Beim Sammelexport kommt die Rückfrage für jeden betroffenen Zahllauf einzeln. Wer abbricht,
  findet den Zahllauf in der Fehlerliste.
- Die **Vorschau** zeigt den Beleg so, wie er nach der Bestätigung aussähe, mit gelbem Hinweis.
- Manuelle Anpassungen (GoAffPro-Belohnungstyp `wallet_adjustment`) stehen in der Übersicht als
  „Team (Reward) · manuelle Anpassung“.
- Besteht ein Zahllauf ausschließlich aus stornierten Altbuchungen, entsteht weiterhin keine
  Gutschrift – es gäbe nichts gutzuschreiben.

Einstellungen dafür: Gruppe „Altfälle bis 31.12.2025“ (Stichtag, Anschrift, USt-IdNr. und
Steuernummer der Alt-Gesellschaft). Die Rechnungsvorlagen sind im E-Mail-Designer als
„Rechnung PDF-Ansicht (Altfälle)“ und „Rechnungsmail (Altfälle)“ getrennt pflegbar, damit die
Gutschriftsvorlagen unberührt bleiben.

Rechnungsnummer und Rechnungsdatum tragen den **Ausstellungstag** (der Zähler setzt pro Jahr
zurück; eine Rückdatierung würde doppelte Nummern erzeugen). Leistungszeitraum und
Auszahlungsdatum werden im Dokument separat ausgewiesen.

## 4b) Erneute Erzeugung: Versionen statt neuer Nummer

Jeder Zahllauf bekommt genau **eine** Belegnummer (Stammnummer). Wird der Beleg für denselben
Zahllauf noch einmal erzeugt – etwa weil sich die Daten in GoAffPro geändert haben –, zieht er
keine neue fortlaufende Nummer, sondern eine **Version** der Stammnummer:

`GS-2026-0042` → `GS-2026-0042-1` → `GS-2026-0042-2` …

- Jede Version nennt den Beleg, den sie ersetzt: im Gutschrift-PDF auf Seite 1 und in der
  Provisionsübersicht als Zeile „Ersetzt Gutschrift …“, in der E-Gutschrift (ZUGFeRD) als Verweis auf den Vorgängerbeleg
  (BT-25). Ersetzt wird immer der unmittelbar vorherige Beleg: `-1` ersetzt die Stammnummer, `-2`
  ersetzt `-1`.
- Die Statuszeile zeigt nach dem Erzeugen „(ersetzt …)“.
- Altfall-Rechnungen werden genauso versioniert (`RE-2025-0003-1`).
- **Belege aus der Zeit vor dieser Funktion:** Kennt das Register einen Zahllauf noch nicht, sucht
  das Programm in den Export-Ordnern und im Versandprotokoll nach bereits vergebenen Nummern. Die
  früheste wird zur Stammnummer, die nächste Erzeugung bekommt `-1` und ersetzt die zuletzt
  vergebene alte Nummer.
- Die Zuordnung Zahllauf → Stammnummer liegt in der GoAffPro-Datenbank (Tabelle
  `document_numbers`) und ist damit Teil jeder Datensicherung.
- **Nummernvergabe während eines Syncs:** Sync und Nummernvergabe schreiben in dieselbe
  Datenbank. Die Vergabe holt sich deshalb zuerst die Schreibsperre und zieht erst dann eine
  Nummer. Schreibt der Sync gerade, wartet sie bis zu 20 Sekunden. Ist die Datenbank danach noch
  belegt, kommt die Meldung „Die Datenbank ist gerade belegt …“, und es wird keine Nummer
  verbraucht. Einfach kurz warten oder unter GoAffPro Sync „Sync pausieren“ und erneut erstellen.
  (Bis zum 23.11.2026 konnte die Vergabe in diesem Fall abbrechen, nachdem die Nummer schon gezogen
  war; diese Nummer blieb dann unbenutzt.)

## 5) E-Mail-Text und Versandverhalten

- E-Mail-Anrede wird personalisiert (wenn Name vorhanden).
- Text erklärt, dass ein Zahllauf stattgefunden hat.
- Hinweis auf Auszahlung in der Regel innerhalb der nächsten 2 Bankarbeitstage.
- Enthält eine kompakte Zusammenfassung der Provisionsdaten.
- Wenn Versandziel „Beraterinnen-E-Mail“ aktiv ist:
  - wird dies im Arbeitsbereich prominent angezeigt,
  - und relevante Buttons werden rot hervorgehoben.

## 6) API-Endpunkte (Übersicht)

- `GET /api/executables` – verfügbare ausführbare Module
- `POST /api/provisionen-goaffpro/poll` – neue Zahlungsdaten abrufen
- `GET/POST /api/settings` – Einstellungen lesen/speichern
- `POST /api/provisionen-goaffpro/export-pdf` – Tabellen-PDF-Export
- `POST /api/provisionen-goaffpro/invoice-details-pdf` – Gutschrift bzw. Rechnung erzeugen (PDF, JSON, ZUGFeRD); `confirmUnbalancedBeforeStart: true` bestätigt die Rückfrage zu Altbuchungen (Antwort sonst HTTP 409 mit `confirmable`)
- `GET /api/provisionen-goaffpro/invoice-preview-pdf?paymentId=…` – Vorschau als ENTWURF; Kopfzeile `X-Beleg-Hinweis` bei aufgeführten Altbuchungen
- `GET /api/fehlerprotokoll` – Fehlerprotokoll mit Kopiertext (`?kurz=1&seit=…`: nur Anzahl neuer Einträge für die Kopfzeile)
- `POST /api/fehlerprotokoll` – Fehler aus dem Browser melden
- `POST /api/fehlerprotokoll/leeren` – Fehlerprotokoll leeren
- `GET /api/fehlerprotokoll/download` – Fehlerprotokoll als Textdatei
- `GET /api/version` – aktuelle Version
- `GET /api/version/history` – letzte Versionen
- `POST /api/analytics/fetch` – Auswertungsdaten
- `POST /api/analytics/advisor-detail` – 360°-Sicht einer Beraterin (Auszahlungen, Verkäufe, Artikel)
- `POST /api/commissions/add-latest` – neuesten Zahllauf hinzufügen
- `POST /api/commissions/remove` – Zahllauf aus Historie entfernen
- `GET /api/help` – diese Hilfe-Dokumentation
- `GET /api/backup/status` – Zustand des Sicherungsauftrags und Liste der Archive
- `POST /api/backup/export` – Sicherung im Hintergrund erstellen
- `GET /api/backup/download` – erstelltes Archiv herunterladen (nur Dateiname, kein Pfad)
- `POST /api/backup/import/upload` – Archiv hochladen und Manifest zur Vorschau lesen
- `POST /api/backup/import/apply` – Import ausführen (verlangt `confirm=IMPORTIEREN`)

## 7) Frontend-Funktionen (JavaScript, gruppiert)

### API/Initialisierung
- `apiGet`, `apiPost`, `apiFetch`, `readApiJson`, `fetchExecutables`, `init`

### Meldungen & Fehlerprotokoll
- `showNotice`, `hideNotice`, `reportClientError`, `refreshErrorBadge`, `scheduleErrorBadgeRefresh`
- `openErrorLog`, `loadErrorLog`, `copyErrorLog`, `downloadErrorLog`, `clearErrorLog`

### UI-Struktur & Navigation
- `createTabAndPanel`, `createAnalyticsTabAndPanel`, `activateTab`, `renderSideNav`, `wireTabSwitching`, `wireMainSectionTabs`

### Tabelle
- `rowKey`, `formatEuroAmount`, `filteredRows`, `renderTable`, `wireSortingAndFiltering`

### Zahlläufe
- `removeCommissionFromHistory`, `sortCommissionsChronologically`, `sortCommissionsNewestFirst`, `renderCommissionHistoryEditor`, `updateCommissionSelect`, `addLatestCommission`

### Versandmodus/Settings
- `applyAdvisorRecipientUiState`, `loadSettings`, `saveSettings`, `getEmailRecipientMode`, `shouldSendEmails`, `buildSettingsSnapshot`

### Exporte & Details
- `exportSelectedInvoiceDetailsPdfs`, `loadInvoiceDetailsPdf`, `pickFolder`, `wireFolderPickerFallback`
- `openInvoicePreview`, `postInvoiceDetailsExport`, `postInvoiceDetailsExportConfirmed` (Rückfrage zu Altbuchungen)

### Analytics
- `drawBarChart`, `renderAnalytics`, `wireAnalyticsDateRange`, `fetchAnalyticsData`
- 360°-Sicht: `fetchAdvisor360`, `renderAdvisor360`, `renderAdvisorSales`, `advisorSaleDetailHtml`

### Polling
- `pollOnce`, `togglePolling`, `setPollingButtonState`

### Datensicherung & Umzug
- `loadBackupStatus`, `renderBackupJob`, `renderBackupArchives`, `startBackupPoll`, `stopBackupPoll`
- `startBackupExport`, `checkBackupArchive`, `applyBackupImport`, `formatBytes`

### Version
- `loadVersion`, `loadVersionHistory`, `wireVersionPopup`

## 8) Backend-Funktionsgruppen (Java)

- HTTP-Handler pro Endpunkt (Polling, Settings, Exporte, Analytics, Version, Zahlläufe)
- Hilfsfunktionen für:
  - Dateiformatierung
  - PDF-Erzeugung
  - IBAN-Prüfung
  - Config-/UI-Settings-Persistenz
  - API-Aufrufe gegen GoAffPro
  - E-Mail-Versand
  - Datensicherung und Import (`BackupService`: Archiv packen, Manifest lesen, Pfade umschreiben, Bestand ersetzen)
  - Fehlerprotokoll (`ErrorLog`: Datei im Datenverzeichnis, Zusammenfassen von Wiederholungen, Schwärzen von Zugangsdaten)

---

Wenn Sie neue Funktionen hinzufügen, bitte diese Datei ebenfalls aktualisieren.


## 9) Beraterinnen-Detailansicht in der Tabelle

- In der Spalte **Affiliate-Name** kann auf den Namen gefahren werden (Mouseover).
- Es erscheint eine visuelle Infokarte (Sprechblasen-Stil) mit persönlichen Stammdaten:
  - Name
  - Anschrift
  - E-Mail
  - Telefon
  - Firma
  - Steuernummer
  - IBAN/BIC/Kontoinhaber

## 10) Reiter „Validierung"

- Enthält einen Button **„Stammdaten laden / neu laden“**.
- Jeder Klick leert die Tabelle und lädt die Daten neu vom Backend.
- Die Daten basieren auf GoAffPro-Endpoint `affiliates` mit erweitertem Feldsatz.
- Angezeigt werden nur praxisrelevante Felder:
  - ID
  - Name
  - E-Mail
  - Telefon
  - Adresse
  - Land
  - Geburtsdatum
  - USt-Nr.
  - IBAN
  - Kontoinhaber
  - IBAN korrekt
  - Vertrag
  - Status
  - Aktion (Erinnerung senden, fehlende Felder, Versandhistorie)

### Spalte „Vertrag"

- Häkchen (Filterwert „Ja“) bedeutet: an der Beraterin hängt ein Dokument, dessen **Titel oder
  Dateiname** das Wort „Vertrag" enthält (Groß-/Kleinschreibung egal). Der Titel des gefundenen
  Dokuments erscheint beim Darüberfahren.
- Kreuz (Filterwert „Nein“) bedeutet: kein solches Dokument gefunden.
- **?** bedeutet: die Dokumente konnten nicht abgerufen werden. Das ist ausdrücklich **nicht**
  dasselbe wie „kein Vertrag" — in diesem Fall wird auch keine Erinnerung wegen des Vertrags
  ausgelöst.
- Der unterschriebene SERVICEPARTNER-Vertrag wird von GoAffPro als `contract_<id>.pdf` mit dem
  Titel „VEMMiNA Beratervertrag" abgelegt. Das Wort „Vertrag" steckt also nur im Titel, nicht im
  Dateinamen — deshalb werden beide Felder geprüft.
- Ein fehlender Vertrag zählt als fehlendes Feld und erscheint damit in der Erinnerungsmail.

### Spalte „Adresse"

- Die Anschrift wird aus den GoAffPro-Feldern Adresszeile 1 und 2, PLZ, Ort, Bundesland und Land
  zusammengesetzt, so wie GoAffPro sie über die Schnittstelle ausliefert.
- Geprüft wird jeder Teil einzeln. Fehlen Straße, PLZ oder Ort, ist die Zelle rot. Beim Darüberfahren
  steht, was fehlt, z. B. „Straße und Hausnummer“. Dieser Teil erscheint auch unter „Felder fehlen“
  und damit in der Erinnerungsmail.
- Fehlt die Anschrift ganz, heißt das fehlende Feld wie bisher „Adresse“.
- Steht die PLZ zusätzlich im Ortsfeld (z. B. „6890 Lustenau“), erscheint sie nur einmal.
- **Wenn GoAffPro im Admin-Bereich eine Straße zeigt, die hier fehlt:** Die Tabelle zeigt, was die
  GoAffPro-Schnittstelle liefert, ohne Zwischenspeicher. Nach „Stammdaten laden / neu laden“ steht
  dort also der aktuelle Stand. Fehlt die Straße dann immer noch, ist sie in GoAffPro nicht
  gespeichert. Dann die Straße in GoAffPro (erneut) eintragen, speichern und hier neu laden.

### Spalten „IBAN“ und „IBAN korrekt"

- Die IBAN wird auf Syntax und Prüfsumme geprüft (siehe Abschnitt 2). Ist sie fehlerhaft, ist die
  IBAN-Zelle rot; beim Darüberfahren steht der Grund, z. B. „Die IBAN hat eine falsche Prüfsumme
  (vermutlich ein Tippfehler oder Zahlendreher)“.
- Eine fehlerhafte IBAN zählt unter „Felder fehlen“ als **„Korrekte IBAN (die hinterlegte IBAN …)“**
  und steht so auch in der Erinnerungsmail. „Erinnerung senden“ geht damit auch dann, wenn sonst
  nichts fehlt. Die IBAN selbst steht nicht in der Mail.
- Leerzeichen in der IBAN sind kein Fehler, auch geschützte Leerzeichen nicht. In die E-Rechnung
  (XML) wird die IBAN ohne Leerzeichen geschrieben, im PDF steht sie wie hinterlegt.


## 11) Dynamischer Hilfe-Bereich

- Der Hilfe-Reiter zeigt die Dokumentation als aufklappbare Bereiche (Accordion).
- Die ersten Abschnitte sind initial geöffnet, weitere können bei Bedarf aufgeklappt werden.
- Grundlage bleibt die Datei `docs/HILFE.md`, die über `GET /api/help` geladen wird.

## 12) Validierungsfilter

Im Reiter **Validierung** gibt es vier Wege, die Tabelle einzugrenzen:

**Suchfelder „Name suchen“ und „E-Mail suchen“** — filtern beim Tippen. Groß-/Kleinschreibung und
Umlaut-Schreibweise sind egal: „dorr“, „doerr“ und „dörr“ finden alle „Dörr“. Mehrere Wörter müssen
alle vorkommen, die Reihenfolge ist egal („eva knorr“ findet „Benjamin und Eva Knorr“). Beide Felder
lassen sich kombinieren; das ✕ im Feld leert es wieder.

**Schnellfilter „nur Approved"** — der Schalter neben dem Lade-Button blendet alle Beraterinnen
aus, deren Status nicht `approved` ist. Er ist **standardmäßig aktiv**; zum Anzeigen aller
Datensätze (auch `blocked`, `invited`, `pending`) einfach abwählen. Die Statuszeile zeigt „X von Y
Datensätzen angezeigt", solange gefiltert wird. Greift der Filter auf keine Zeile, erscheint statt
einer leeren Tabelle der Hinweis „Keine Datensätze mit diesem Filter."

**Schnellfilter „nur unvollständige"** — blendet mit einem Klick alle Beraterinnen aus, bei denen
alle geprüften Angaben in Ordnung sind. Übrig bleiben die, bei denen in der Spalte **Aktion**
„… Felder fehlen" steht statt „vollständig". Geprüft wird dasselbe wie für die Erinnerungsmail:
Name, E-Mail, Telefon, Anschrift (Straße, PLZ, Ort), Land, Geburtsdatum, Steuernummer oder
USt-ID, Zahlmethode, IBAN samt Prüfsumme, Kontoinhaber und der unterschriebene Vertrag.
Standardmäßig aus. Zusammen mit „nur Approved" zeigt die Tabelle alle aktiven Beraterinnen, bei
denen noch etwas fehlt.

**Spaltenfilter und Sortierung (wie in Excel)** — gilt für jede Spalte und jede Tabelle:

- **Linksklick** auf einen Spaltenkopf sortiert auf- bzw. absteigend.
- **Rechtsklick** auf einen Spaltenkopf öffnet ein Filtermenü mit allen vorkommenden Werten und
  ihrer Häufigkeit, inklusive „Alle" / „Keine" / „Filter löschen".
- Spaltenbreiten lassen sich am rechten Rand des Spaltenkopfs ziehen.
- **Abgekürzte Zellen** (Text endet mit „…“) zeigen beim Darüberfahren den vollständigen Text.
  Das gilt auch für abgekürzte Spaltenköpfe. Eine Erklärung zur Zelle (z. B. was an der Anschrift
  fehlt) steht direkt darunter.

Alle Wege sind kombinierbar und wirken direkt auf die geladene Tabelle.

## 13) Speichern-Button (Änderungsstatus)

- Wenn keine Änderungen an den Einstellungen erkannt wurden, ist der Speichern-Button heller dargestellt.
- Sobald eine Änderung erkannt wird, wird der Button kräftiger hervorgehoben.
- Die Erkennung basiert auf einem Snapshot-Vergleich der relevanten Einstellungsfelder.

## 14) Datensicherung und Umzug

Zu finden im Reiter **Sync** ganz unten unter **Datensicherung & Umzug**.

### Sicherung erstellen

Ein Klick auf **Sicherung erstellen** packt den gesamten Bestand in eine ZIP-Datei:
Einstellungen und Vorlagen, das Versandprotokoll, die Belegnummern-Zähler, die
GoAffPro-Datenbank mit allen synchronisierten Datensätzen, die heruntergeladenen
Dateien sowie sämtliche erzeugten Belege (PDF, JSON, ZUGFeRD).

Der Vorgang läuft im Hintergrund; eine Fortschrittskarte zeigt den aktuellen Schritt.
Ist er fertig, erscheint das Archiv in der Liste darunter und lässt sich herunterladen.
Die drei neuesten Archive bleiben auf dem Server liegen, ältere werden entfernt.

**Zugangsdaten einschließen** (Haken, standardmäßig aus): API-Key, SMTP-Passwort und
SMTP-Benutzername werden nur mitgesichert, wenn dieser Haken gesetzt ist. Für einen
Umzug ist er nötig; für eine reine Datensicherung nicht. Ist er gesetzt, trägt die
Datei den Zusatz `_mit-zugangsdaten` im Namen.

Läuft gerade ein Sync, wird die Sicherung abgelehnt. Der Knopf **Sync pausieren** steht
im selben Reiter weiter oben. Er hält auch einen laufenden Sync an, und zwar nach der
gerade geladenen Seite bzw. Datei; bis dahin zeigt der Status „Wird angehalten“. Steht er
auf „Pausiert“, ist die Sicherung möglich.

### Sicherung einspielen

1. Datei auswählen und **Archiv prüfen** klicken. Das Archiv wird hochgeladen, aber
   noch nichts verändert.
2. Die Vorschau zeigt Erstellungsdatum, Quellumgebung, Belegnummern-Stand, Anzahl der
   Datensätze und Belegordner sowie ob Zugangsdaten enthalten sind.
3. Zum Bestätigen `IMPORTIEREN` in das Feld tippen und **Import starten**.

**Der Import ersetzt den gesamten bisherigen Bestand.** Vorher wird davon automatisch
eine Sicherung angelegt (`pre-import_*.zip`, immer mit Zugangsdaten), die in der
Archivliste erscheint. Nach dem Import lädt sich die Oberfläche selbst neu; ein
Neustart der Anwendung ist nicht nötig.

### Was beim Import angepasst wird

Alle gespeicherten Dateipfade sind absolut. Beim Import werden sie auf die
Zielumgebung umgeschrieben — das funktioniert auch zwischen Windows und Linux. Betroffen
sind die Dateiverweise der synchronisierten Datensätze und die vier Pfadfelder je
Eintrag im Versandprotokoll.

Lässt sich eine Datei nicht zuordnen oder fehlt sie, wird der Verweis **geleert** statt
auf einen falschen Pfad gesetzt: Der nächste Sync lädt sie dann sauber nach, und die
Bestandsanzeige bleibt ehrlich. Der Bericht nennt beide Zahlen (etwa „25 von 25
zugeordnet, 0 fehlend").

Die Export- und Datenverzeichnisse werden **nicht** aus dem Archiv übernommen, sondern
auf die Werte der Zielumgebung gesetzt.

### Belegnummern-Zähler

Die Zähler stehen ausschließlich in `config.properties` — **nicht** in der Datenbank.
Ein Import übernimmt sie aus dem Archiv, weil das für einen Umzug der richtige Fall
ist. Läuft die alte Umgebung danach weiter, vergeben beide dieselben Nummern; die alte
Instanz gehört dann abgeschaltet.

### Sicherheit

Die Anwendung kennt keine Anmeldung — auch diese Funktionen nicht. Wer den Port
erreicht, kann den kompletten Datenbestand herunterladen oder überschreiben. Das
Archiv enthält Bankverbindungen, Steuernummern und Umsätze aller Beraterinnen und ist
unverschlüsselt; es gehört nicht in E-Mail-Anhänge oder Cloud-Ordner.

Belegdownloads sind auf den Exportordner begrenzt. Liegt eine Datei außerhalb, meldet
der Server *„Die Datei liegt außerhalb des Exportordners."* — der Beleg muss dann neu
erzeugt werden.

## 15) Auswertungen: Beraterinnenebene (360°)

Beraterin wählen und **360° laden**. Die Sicht holt ihre Daten live aus GoAffPro; der Sync
spielt dafür keine Rolle.

- **Auszahlungen:** Zahlläufe der Beraterin im gewählten Zeitraum (ab dem gewählten Zahllauf).
- **Verkäufe:** alle Bestellungen mit Bestelldatum im Zeitraum – auch offene und noch nicht
  ausgezahlte, also unabhängig von den Zahlläufen.
  - **Direkt** sind ihre eigenen Verkäufe.
  - **Team** sind Verkäufe ihrer Teampartnerinnen, für die sie eine Team-Provision (Reward)
    erhält. Als Provision steht dort ihre Team-Provision, nicht die Provision der Partnerin.
  - Je Verkauf stehen Status, „ausgezahlt in …“ bzw. „nicht ausgezahlt“, „Eigenbestellung“
    (die Kundin ist die Beraterin selbst) und „Neukundin“.
  - Ein Klick auf den Pfeil klappt die **Aufschlüsselung** auf, wie die Bestellansicht in
    GoAffPro: Positionen mit Menge, Netto, Rabatt, MwSt und Provision mit Satz, darunter
    Bestellwert, BMG, Rabatt, Versand, MwSt, Anpassung und Provision. Versand ergibt sich aus
    Bestellwert minus Positionen, die Anpassung aus Provision minus Summe der Positionen.
  - Abgelehnte oder stornierte Verkäufe stehen grau in der Liste und zählen nicht mit.
- **Kennzahlen:** Direkte Verkäufe, Team-Verkäufe und verkaufte Einheiten zählen Bestellungen
  bzw. Artikel (abzüglich Retouren), nicht Buchungen. Bei den Provisionen steht dabei, wie viel
  davon noch nicht ausgezahlt ist.
- **Verkaufte Artikel:** dieselben Verkäufe nach Artikel zusammengefasst, Umsatz netto.

**Kundendaten:** Die Verkäufe zeigen Name und E-Mail der Kundin im Klartext – wie GoAffPro
selbst. Telefon und Adresse werden nicht übernommen.

## 16) Fehlerprotokoll

Fehler standen bisher nur in der Konsole des Containers oder kurz in einer Statuszeile. Jetzt
sammelt sie das **Fehlerprotokoll**: Kopfzeile → **Einstellungen** → ganz unten.

- **Was hineinkommt:**
  - jede Fehlerantwort des Servers, z. B. eine abgelehnte Vorschau oder ein Abbruch beim Erzeugen
    einer Gutschrift, mit Bereich, Anfrage und – bei Programmfehlern – dem Stacktrace,
  - Fehler im Browser (JavaScript-Fehler, unlesbare Server-Antworten),
  - abgebrochene Zeitpläne (stündlicher/nächtlicher Sync, Wochenmail an Führungskräfte),
    abgebrochene Sync-Läufe, Datensicherung und Import,
  - eine fehlgeschlagene Mail an die Buchhaltung.
- Eine **Rückfrage**, etwa zu Altbuchungen, ist kein Fehler und steht nicht im Protokoll.
- **Gleiche Fehler** innerhalb von 10 Minuten werden zusammengefasst („3× bis …“).
- **Kopfzeile:** Gibt es Einträge, die noch niemand angesehen hat, erscheint dort ein roter Knopf
  „1 neuer Fehler“. Ein Klick öffnet das Protokoll; danach verschwindet der Knopf.
- **Kopieren** legt den Text in die Zwischenablage: Version, System und alle Einträge mit
  technischen Details, dazu im Anhang die jüngsten Sync-Fehler (höchstens 10, höchstens 7 Tage
  alt). Diesen Text bei einem Problem mit einer kurzen Beschreibung weitergeben. Ohne https
  klappt das automatische Kopieren nicht immer; dann steht der Text in einem Feld zum Markieren,
  oder man nimmt **Herunterladen** (Textdatei).
- **Leeren** löscht alle Einträge (mit Rückfrage). Ältere Sync-Fehler erscheinen danach auch nicht
  mehr im Anhang.
- **Speicherort:** `fehlerprotokoll.json` im Datenverzeichnis (`GOAFFPRO_SYNC_DATA_PATH`, im
  Container `/app/data`). Höchstens 500 Einträge. Die Datei gehört bewusst nicht zur
  Datensicherung, sie beschreibt nur die eigene Umgebung.
- **Datenschutz:** API-Schlüssel und SMTP-Passwort werden vor dem Speichern geschwärzt, ebenso
  Parameter wie `apiKey` oder `token` in Anfragen. Namen, E-Mail-Adressen und Beträge aus
  Fehlermeldungen bleiben stehen – vor dem Weitergeben bitte bedenken.
