# Plan: MT940 Kontoauszug-Import im AS-Bereich

## Ziel
- MT940-Dateien pro Bankkonto in die App hochladen.
- Alle Buchungszeilen tabellarisch anzeigen.
- Bereits importierte Buchungen dauerhaft speichern.
- Bei erneutem Upload nur **neue** (dazugekommene) Transaktionen ergänzen (inkrementeller Import).

---

## 1) UI/UX-Konzept (Bereich AS)

### Neuer Bereich in AS
Im bestehenden Bereich **AS** einen neuen Unterbereich ergänzen:
- Titel: `Banktransaktionen (MT940)`
- Komponenten:
  1. **Bankkonto-Auswahl** (Dropdown)
  2. **Neues Bankkonto anlegen** (Name, IBAN/Kontonummer, optional BIC, Bankname)
  3. **Datei-Upload** (`.sta`, `.mt940`, `.txt`)
  4. **Import-Button** (z. B. `MT940 importieren`)
  5. **Statusbox** mit Ergebnissen:
     - Datei gelesen: X Zeilen
     - Neu importiert: Y
     - Duplikate übersprungen: Z
  6. **Tabelle der Banktransaktionen** mit Filter/Sortierung

### Tabellenspalten (MVP)
- Buchungsdatum
- Wertstellungsdatum
- Betrag
- Währung
- Gegenkonto / Name
- Verwendungszweck
- Transaktionscode (falls vorhanden)
- Referenz / EndToEnd / Bankreferenz
- Saldo nach Buchung (falls vorhanden)
- Importzeitpunkt
- Eindeutiger Fingerprint (nur technisch, optional versteckt)

---

## 2) Backend-Architektur

## Neue API-Endpunkte (Vorschlag)
1. `GET /api/as/bank-accounts`
   - Liefert alle konfigurierten Bankkonten.

2. `POST /api/as/bank-accounts`
   - Legt ein neues Bankkonto an.
   - Request: `name`, `ibanOrAccountNo`, `bic?`, `bankName?`, `currency?`

3. `POST /api/as/mt940/import`
   - Multipart Upload + `bankAccountId`
   - Liest MT940, transformiert in Transaktionen, dedupliziert und speichert nur neue.
   - Response mit Importstatistik.

4. `GET /api/as/bank-transactions?bankAccountId=...&from=...&to=...&q=...`
   - Liefert gespeicherte Transaktionen für Tabelle.

5. Optional: `GET /api/as/mt940/import-history?bankAccountId=...`
   - Zeigt Importläufe (Dateiname, Zeit, neu/duplikat).

---

## 3) Datenhaltung / Persistenz

## Datenspeicher
Analog zu bestehenden Settings-Mechaniken in der App:
- Persistenz in Datei (JSON) im Settings-Verzeichnis, z. B.
  - `bank_accounts.json`
  - `bank_transactions.json`
  - optional `bank_import_runs.json`

(Später kann man auf SQLite wechseln, für MVP reicht JSON.)

## Datenmodell (Vorschlag)
### BankAccount
- `id` (UUID)
- `name`
- `ibanOrAccountNo`
- `bic`
- `bankName`
- `currency`
- `createdAt`

### BankTransaction
- `id` (UUID)
- `bankAccountId`
- `bookingDate`
- `valueDate`
- `amount`
- `currency`
- `counterparty`
- `purpose`
- `reference`
- `bankReference`
- `transactionCode`
- `balanceAfter`
- `rawLineHash` / `fingerprint` (unique pro Konto)
- `importedAt`
- `sourceFileName`

### ImportRun (optional)
- `id`
- `bankAccountId`
- `fileName`
- `importedAt`
- `totalParsed`
- `inserted`
- `duplicates`
- `errors`

---

## 4) Deduplikationsstrategie (wichtig)

Ziel: Bei erneutem Hochladen derselben/überlappender MT940-Datei keine Dubletten.

## Fingerprint je Buchung
Fingerprint aus normalisierten Feldern bilden, z. B.:
- `bankAccountId`
- `bookingDate`
- `valueDate`
- `amount`
- `currency`
- `counterparty` (normalisiert)
- `purpose` (normalisiert, whitespace reduziert)
- `reference/bankReference`

Dann SHA-256 über die zusammengesetzte Zeichenkette.

Regel:
- Wenn Fingerprint bereits für dieses Konto existiert => Duplikat, nicht erneut speichern.
- Sonst speichern.

Damit funktionieren:
- erneuter Import derselben Datei
- Import mit Überschneidung (z. B. Bank liefert immer letzte 90 Tage)

---

## 5) MT940 Parsing-Strategie

## Parser-Baustein
Neuen Service einführen, z. B. `Mt940Parser`:
- Input: Dateiinhalt (String)
- Output: Liste normalisierter `BankTransactionCandidate`

Zu parsen (MVP):
- `:20:` Referenz
- `:25:` Konto
- `:28C:` Auszugsnummer
- `:60F:` Anfangssaldo
- `:61:` Umsatzzahlung (Kernzeile)
- `:86:` Verwendungszweck / Gegenpartei
- `:62F:` Endsaldo

## Fehlerbehandlung
- Parser robust gegen unvollständige Felder.
- Import teilweise erfolgreich erlauben:
  - valide Zeilen speichern
  - fehlerhafte Zeilen zählen und im Response melden.

---

## 6) Frontend-Flow

1. Nutzer wählt Bankkonto (oder legt neu an).
2. Nutzer lädt MT940-Datei hoch.
3. Klick auf `MT940 importieren`.
4. Backend antwortet mit Statistik.
5. Tabelle wird automatisch neu geladen.
6. Sortierung/Filter wie in bestehenden Tabellen (Excel-artig) wiederverwenden.

---

## 7) Sicherheit & Validierung

- Nur eingeloggte User mit Bereich **AS** (oder Admin) dürfen Endpunkte nutzen.
- Dateigröße limitieren (z. B. 10 MB).
- Dateityp prüfen (MIME + Endung, best effort).
- Keine Speicherung der Rohdatei zwingend nötig; optional für Audit.

---

## 8) Rollout in Iterationen

## Phase 1 (MVP)
- Ein Konto
- Upload + Parse + Persist + Dedupe
- Tabelle anzeigen

## Phase 2
- Mehrere Bankkonten
- Importhistorie
- Bessere Suche/Filter

## Phase 3
- Matching mit ERPNext/Belegen
- Kennzeichnung „zugeordnet / offen“
- Export (CSV/XLSX)

---

## 9) Akzeptanzkriterien

- Upload einer MT940-Datei erzeugt sichtbare Transaktionen in Tabelle.
- Zweiter Upload derselben Datei erzeugt **0 neue** Buchungen.
- Upload einer erweiterten Datei fügt nur neue Buchungen hinzu.
- Daten bleiben nach Neustart erhalten.
- Pro Bankkonto getrennte Sicht auf Transaktionen.

---

## 10) Konkrete nächste technische Tasks

1. Datenmodelle `BankAccount`, `BankTransaction`, `ImportRun` anlegen.
2. JSON-Persistenz-Helfer im bestehenden Settings-Verzeichnis ergänzen.
3. `Mt940Parser` implementieren + Unit-Test-Dateien mit Beispiel-MT940.
4. Neue AS-API-Endpunkte in `WebUiServer` registrieren.
5. AS-UI um Upload, Kontoauswahl und Tabelle erweitern.
6. Dedupe via Fingerprint + Importstatistik implementieren.
7. E2E manuell testen mit 2 MT940-Dateien (gleich + erweitert).

