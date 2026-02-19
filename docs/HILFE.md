# Hilfe- und Funktionsdokumentation (deutsch)

Diese Dokumentation beschreibt die wichtigsten Funktionen der Anwendung **VEMMiNA Provisionssystem**.

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

## 7) Frontend-Funktionen (JavaScript, gruppiert)

### API/Initialisierung
- `apiGet`, `apiPost`, `fetchExecutables`, `init`

### UI-Struktur & Navigation
- `createTabAndPanel`, `createAnalyticsTabAndPanel`, `activateTab`, `wireTabSwitching`, `wireMainSectionTabs`

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
