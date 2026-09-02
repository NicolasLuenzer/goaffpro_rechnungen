# Hilfe- und Funktionsdokumentation (deutsch)

Diese Dokumentation beschreibt die wichtigsten Funktionen der Anwendung **VEMMiNA Assistent**.

Die Navigation ist bewusst flach aufgebaut: **Rechnungsservice**, **Validierungen** und **Auswertungen** stehen direkt in der Sidebar. Die **Hilfe** ist über den Button im Kopfbereich erreichbar. Eine zusätzliche VEMMiNA-Obergruppe wird nicht mehr angezeigt.

## 1) Hauptfunktionen im Arbeitsbereich

- **Polling starten/stoppen**
  - Ruft neue Zahlungen aus GoAffPro ab.
  - Aktualisiert die Tabelle und die Zahllauf-Historie.
- **Zahllauf-Auswahl**
  - Dropdown zeigt Zahlläufe, neuester oben.
  - Beeinflusst den Startpunkt (`sinceId`) für das Polling.
- **Neusten Zahllauf hinzufügen**
  - Ermittelt den neuesten Zahllauf aus GoAffPro und ergänzt ihn in die Historie.
- **Rechnungsdetails für selektierte Zeilen exportieren**
  - Erstellt Rechnungsdetails-PDFs (und JSON) für markierte Zeilen.
  - Kann optional E-Mails versenden.
- **Filterfeld**
  - Filtert die Tabelle nach Name, Land, Steuernummer, Belegdatum usw.

## 2) Tabellenfunktionen

- **Sortierung per Spaltenkopf** (auf/absteigend).
- **Mehrfachauswahl per Checkboxen**.
- **Spalte „IBAN vorhanden“**
  - ✅ wenn eine IBAN vorhanden ist, sonst ❌.
- **Spalte „IBAN korrekt“**
  - ✅ wenn IBAN formal gültig (Mod-97), sonst ❌.
- **Zeilenwarnung (leicht rot)**
  - Wird gesetzt, wenn
    - Name fehlt,
    - IBAN fehlt,
    - oder IBAN ungültig ist.

## 3) Einstellungen

- **GoAffPro API-Key**
- **Export-Zielordner**
- **E-Mail-Konfiguration**
  - Kontakt-E-Mail
  - SMTP Host/Port/Benutzer/Passwort
  - Versand aktivieren/deaktivieren
  - Versandziel: Kontakt-E-Mail oder Beraterinnen-E-Mail
- **Zahllauf-Historie bearbeiten**
  - Zahlläufe können entfernt werden.
- **Speichern-Feedback**
  - Meldet, ob tatsächlich Änderungen erkannt wurden oder nicht.

## 4) Exportlogik

- Pro Exportlauf wird automatisch ein Unterordner erzeugt:
  - `export_<belegdatum>_<hoechste-payment-id>_<beraterinnenname>`
- Dort werden die generierten Dateien (PDF/JSON) abgelegt.

## 4a) Altfälle bis 31.12.2025 (Gutschrift oder Rechnung)

Die Dokumentart wird **automatisch aus dem Provisionsdatum** abgeleitet — es gibt bewusst keinen
manuellen Schalter:

- Transaktionen **vor** dem Stichtag (Standard: 01.01.2026, Europe/Berlin) → **Rechnung**
  gegen die **VEMMiNA Qualitäts- Haushaltsprodukte GmbH**, eigener Nummernkreis `RE-JJJJ-NNNN`,
  ZUGFeRD-TypeCode 380, ohne §-14-Gutschrifts- und Widerspruchshinweise.
- Transaktionen **ab** dem Stichtag → **Gutschrift** gegen die **S+R Linear Technology GmbH**,
  Nummernkreis `GS-JJJJ-NNNN`, TypeCode 389, mit §-14-Hinweisen (unverändertes Verhalten).

Grundlage ist `payment.transactions[].created_at`. Fehlen alle Transaktionsdaten, entscheidet
das Zahllauf-Datum; fehlt auch das, wird als sicherer Rückfall eine Gutschrift erstellt.

**Gemischte Zahlläufe** (Transaktionen vor *und* ab dem Stichtag) werden **nicht automatisch**
entschieden: Der Server antwortet mit `409 MIXED_PERIOD`, es wird **kein Beleg erzeugt und keine
Belegnummer verbraucht**. Die Oberfläche zeigt die Aufteilung nach Anzahl und Summe und lässt
bewusst wählen. Diese manuelle Entscheidung wird im Versandprotokoll mit ⚠ vermerkt.

Einstellungen dafür: Gruppe „Altfälle bis 31.12.2025“ (Stichtag, Anschrift, USt-IdNr. und
Steuernummer der Alt-Gesellschaft). Die Rechnungsvorlagen sind im E-Mail-Designer als
„Rechnung PDF-Ansicht (Altfälle)“ und „Rechnungsmail (Altfälle)“ getrennt pflegbar, damit die
Gutschriftsvorlagen unberührt bleiben.

Rechnungsnummer und Rechnungsdatum tragen den **Ausstellungstag** (der Zähler setzt pro Jahr
zurück; eine Rückdatierung würde doppelte Nummern erzeugen). Leistungszeitraum und
Auszahlungsdatum werden im Dokument separat ausgewiesen.

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
- `POST /api/provisionen-goaffpro/invoice-details-pdf` – Rechnungsdetails-PDF + JSON
- `GET /api/version` – aktuelle Version
- `GET /api/version/history` – letzte Versionen
- `POST /api/analytics/fetch` – Auswertungsdaten
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
- `apiGet`, `apiPost`, `fetchExecutables`, `init`

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

### Analytics
- `drawBarChart`, `renderAnalytics`, `wireAnalyticsDateRange`, `fetchAnalyticsData`

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
- Angezeigt werden nur praxisrelevante Felder, u. a.:
  - ID
  - Name
  - E-Mail
  - Telefon
  - Adresse
  - Land
  - Steuernummer
  - Zahlmethode
  - IBAN
  - IBAN korrekt
  - Status


## 11) Dynamischer Hilfe-Bereich

- Der Hilfe-Reiter zeigt die Dokumentation als aufklappbare Bereiche (Accordion).
- Die ersten Abschnitte sind initial geöffnet, weitere können bei Bedarf aufgeklappt werden.
- Grundlage bleibt die Datei `docs/HILFE.md`, die über `GET /api/help` geladen wird.

## 12) Validierungsfilter

Im Reiter **Validierung** stehen schnelle Filter zur Verfügung (für alle relevanten Spalten jeweils mit/ohne):

- Name (mit/ohne)
- E-Mail (mit/ohne)
- Telefon (mit/ohne)
- Adresse (mit/ohne)
- Land (mit/ohne)
- Geburtsdatum (mit/ohne)
- Steuernummer (mit/ohne)
- Zahlmethode (mit/ohne)
- IBAN (mit/ohne)
- IBAN korrekt / ungültig
- Status (mit/ohne)
- zusätzlicher Status-Filter (Dropdown)

Die Filter sind kombinierbar und wirken direkt auf die geladene Tabelle.

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
im selben Reiter weiter oben.

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
