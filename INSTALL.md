# GoAffPro Rechnungen — Installations-Anleitung

## Voraussetzungen

- Docker und Docker Compose auf dem Ziel-Server installiert
- Git installiert (zum Klonen des Repositories)
- Netzwerkzugang zum Server auf Port 8090

## 1. Repository klonen

```bash
cd /volume1/docker
git clone https://github.com/NicolasLuenzer/goaffpro_rechnungen.git
cd goaffpro_rechnungen
```

## 2. Secrets bereitstellen

Es gibt zwei Wege, je nach Deployment-Methode:

### Variante A: CLI / SSH (mit `.env`-Datei)

Kopiere `.env.example` zu `.env` und trage die echten Zugangsdaten ein:

```bash
cp .env.example .env
nano .env
```

Inhalt der `.env`:

```env
GOAFFPRO_API_KEY=<DEIN_GOAFFPRO_KEY>
SMTP_PASSWORD=<DEIN_SMTP_PASSWORT>
```

### Variante B: Portainer (Stack via Git Repository)

Im Stack-Erstellungsformular von Portainer **nach unten scrollen** zum Abschnitt **"Environment variables"**. Dort die 2 Variablen einzeln eintragen:

| Name | Wert |
|------|------|
| `GOAFFPRO_API_KEY` | dein Key |
| `SMTP_PASSWORD` | dein Passwort |

Portainer injiziert diese in den Container.

> **Wichtig:** `.env` enthaelt sensible Zugangsdaten und wird NICHT ins Git-Repository aufgenommen.

> **Sicherheitshinweis:** Die App hat **keinen Login** — wer die URL erreicht, hat vollen Zugriff. Nur in vertrauenswuerdigen, internen Netzwerken betreiben.

## 3. Konfiguration (optional)

Die App speichert ihre Daten an zwei Orten:

| Pfad | Typ | Inhalt |
|------|-----|--------|
| `goaffpro-config` | Docker Named Volume | `config.properties` (App-Einstellungen, Templates) |
| `/volume1/docker/goaffpro_rechnungen/export/` | Host Bind-Mount | PDF-Exporte (vom NAS-Filesystem zugreifbar) |

> **Wichtig:** Das Host-Verzeichnis `/volume1/docker/goaffpro_rechnungen/export/` muss vor dem Stack-Start existieren. Per SSH anlegen:
> ```bash
> mkdir -p /volume1/docker/goaffpro_rechnungen/export
> ```

Beim ersten Start sind beide leer — die App nutzt Defaults und persistiert UI-Aenderungen automatisch in `goaffpro-config`. PDF-Exporte landen direkt im Host-Verzeichnis und sind ueber die Synology File Station zugreifbar.

Wenn du die Konfiguration **vorbefuellen** willst:

```bash
# In den laufenden Container einsteigen
docker-compose exec goaffpro sh
# Datei anlegen
cat > /app/config/config.properties << 'EOF'
smtpHost=smtp.mandrillapp.com
# ... (siehe Beispiel unten)
EOF
exit
docker-compose restart goaffpro
```

Beispielinhalt (alles optional — Felder koennen auch via UI gesetzt werden):

```properties
# SMTP E-Mail-Versand (Passwort kommt aus .env)
smtpHost=smtp.mandrillapp.com
smtpPort=587
smtpUsername=<DEIN_SMTP_USER>
smtpTls=true

# Pfad fuer PDF-Exporte (Container-intern, nicht aendern)
pdfExportPath=/app/exports

# E-Mail Absender
contactEmail=<DEINE_EMAIL>
emailBcc=<DEINE_BCC_EMAIL>

# Features
eInvoiceEnabled=true
sendEmailsEnabled=true
eInvoiceAttachAndStoreEnabled=true
```

> **Hinweis:** API-Keys und Passwoerter (`goaffproAPIKey`, `smtpPassword`) sollten NICHT in `config.properties` stehen — sie werden ueber `.env` (oder Portainer Env-Vars) gesetzt. Wenn die Env-Variable gesetzt ist, hat sie Vorrang vor dem File-Wert.

## 4. Container bauen und starten

```bash
docker-compose up --build -d
```

Der erste Build dauert einige Minuten (Maven-Dependencies werden heruntergeladen).

## 5. Zugriff testen

Die App ist erreichbar unter:

```
http://<SERVER-IP>:8090
```

Logs pruefen:

```bash
docker-compose logs -f goaffpro
```

## 6. Updates einspielen

```bash
cd /volume1/docker/goaffpro_rechnungen
git pull
docker-compose up --build -d
```

Der Multi-Stage-Build nutzt Docker-Layer-Caching — nur geaenderte Teile werden neu gebaut.

## 7. Container verwalten

```bash
# Status pruefen
docker-compose ps

# Stoppen
docker-compose down

# Neustarten
docker-compose restart goaffpro

# Logs anzeigen
docker-compose logs -f goaffpro
```

## 8. Datensicherung

### Variante A (empfohlen): Sicherung ueber die Oberflaeche

Die App bringt eine vollstaendige Sicherung mit. Sie steht im Reiter **Sync** ganz unten
unter **Datensicherung & Umzug**.

Ein Klick auf **Sicherung erstellen** packt in eine einzige ZIP-Datei:

| Inhalt | Herkunft |
|--------|----------|
| Einstellungen, Vorlagen, Versandprotokoll, **Belegnummern-Zaehler** | `config.properties` (Volume `goaffpro-config`) |
| UI-Einstellungen | `goaffpro_ui_settings.properties` (Exportordner) |
| GoAffPro-Datenbank (alle synchronisierten Datensaetze) | `goaffpro_sync.sqlite` (Volume `goaffpro-data`) |
| Heruntergeladene Dateien/Assets | `goaffpro_files/` (Volume `goaffpro-data`) |
| Alle erzeugten Belege (PDF, JSON, ZUGFeRD) | Exportordner |

Die Datenbank wird per `VACUUM INTO` als konsistenter Schnappschuss gezogen — ein
laufender Zugriff kann die Sicherung also nicht zerreissen. Waehrend ein Sync laeuft,
wird die Sicherung mit einem Hinweis abgelehnt; den Sync vorher ueber
**Sync pausieren** anhalten.

**Zugangsdaten:** Standardmaessig werden `goaffproAPIKey`, `smtpPassword` und
`smtpUsername` **nicht** mitgesichert. Fuer einen echten Umzug den Haken
**Zugangsdaten einschliessen** setzen — dann traegt die Datei den Zusatz
`_mit-zugangsdaten` im Namen und gehoert entsprechend behandelt. Der Inhalt der
`.env` reist nie mit.

Die drei neuesten Sicherungen bleiben unter `<Datenverzeichnis>/backups/` liegen und
sind dort erneut herunterladbar; aeltere werden automatisch entfernt.

### Variante B: Sicherung der Volumes

```bash
docker run --rm \
  -v goaffpro-config:/source/config \
  -v goaffpro-data:/source/data \
  -v /volume1/docker/goaffpro_rechnungen/export:/source/exports \
  -v $(pwd):/backup \
  alpine tar -czf /backup/goaffpro_backup_$(date +%Y%m%d).tar.gz -C /source .
```

Das Volume `goaffpro-data` **muss mit**: darin liegen die SQLite-Datenbank und die
heruntergeladenen Dateien. Die Volumes sind im Synology Container Manager unter
**Container > Details > Speicher** einsehbar, die PDFs direkt ueber die File Station
unter `docker/goaffpro_rechnungen/export/`.

Zusaetzlich sichern: die **`.env`** bzw. die Portainer-Umgebungsvariablen. Sie sind in
keiner der beiden Varianten enthalten.

## 9. Umzug auf eine andere Umgebung

Der Umzug funktioniert in beide Richtungen zwischen Windows und Linux; alle
gespeicherten Pfade werden beim Import automatisch auf die Zielumgebung umgeschrieben.

1. **Quelle:** Sync pausieren, dann im Reiter **Sync** unter *Datensicherung & Umzug*
   den Haken **Zugangsdaten einschliessen** setzen und **Sicherung erstellen** klicken.
   Nach Abschluss die ZIP-Datei herunterladen.
2. **Ziel:** Die App dort nach dieser Anleitung installieren und einmal starten.
3. Im Ziel unter *Datensicherung & Umzug* die Datei auswaehlen und **Archiv pruefen**
   klicken. Die Vorschau nennt Erstellungsdatum, Quellumgebung, Zaehlerstand, Anzahl
   Datensaetze und Belegordner. Diese Zahlen mit der Quelle vergleichen.
4. `IMPORTIEREN` in das Bestaetigungsfeld tippen und **Import starten**.

**Der Import ersetzt den gesamten Datenbestand der Zielumgebung.** Vorher legt die App
davon automatisch eine Sicherung unter `<Datenverzeichnis>/backups/pre-import_*.zip`
an (immer inklusive Zugangsdaten, sie verlaesst die Maschine nicht).

Nach dem Import:

- Ein Neustart ist **nicht** noetig, die Oberflaeche laedt sich selbst neu.
- Der Bericht nennt, wie viele Dateien zugeordnet werden konnten
  (z. B. „25 von 25"). Nicht auffindbare Dateien werden bewusst auf *leer* gesetzt
  statt auf einen falschen Pfad — der naechste Sync laedt sie sauber nach.
- **Die Belegnummern-Zaehler werden uebernommen.** Fuer einen Umzug ist das richtig.
  Laeuft die alte Umgebung weiter, vergeben beide dieselben Nummern — dann die alte
  Instanz abschalten.
- Kontrolle: Der Zaehler ist im Archiv ohne Entpacken pruefbar:
  ```bash
  unzip -p goaffpro-backup_*.zip manifest.json
  ```

## 10. Sicherheitshinweise

**Die App kennt keine Anmeldung.** Jeder, der den Port erreicht, kann alle Daten sehen
und aendern. Mit der Sicherungsfunktion gilt zusaetzlich: Er kann den kompletten
Datenbestand als eine Datei herunterladen und ihn ueberschreiben. Die App gehoert
deshalb ausschliesslich in ein vertrauenswuerdiges internes Netz — nicht ins Internet,
kein Port-Forwarding, keine Reverse-Proxy-Veroeffentlichung ohne vorgelagerte
Authentifizierung.

**Das Sicherungsarchiv ist schutzbeduerftig.** Es enthaelt Bankverbindungen (IBAN),
Steuernummern, Adressen und Umsaetze aller Beraterinnen sowie — bei gesetztem Haken —
API-Key und SMTP-Passwort. Es ist unverschluesselt. Nicht per E-Mail versenden, nicht
in einen Cloud-Ordner legen und nach dem Umzug von Zwischenstationen loeschen.

**Fremde Dateien im Exportordner reisen mit.** Liegen dort Dateien, die nicht von
dieser App stammen (beobachtet: `bank_accounts.json`, `bank_transactions.json`,
`goaffpro_users.enc`), werden sie mitgesichert. Das Manifest weist sie unter
`stats.exportLooseFiles` aus.

**Belegdownloads sind auf den Exportordner begrenzt.** Faellt eine Datei ausserhalb,
antwortet der Server mit `403` und der Meldung *„Die Datei liegt ausserhalb des
Exportordners."* Bei Altfaellen aus einer frueheren Installation kann das auftreten —
der Beleg liegt dann nicht mehr am hinterlegten Ort und muss neu erzeugt werden.

## Verzeichnisstruktur auf dem Server

```
/volume1/docker/goaffpro_rechnungen/
├── .env                         # Secrets (nur fuer CLI-Deployment)
├── .env.example                 # Template fuer .env
├── docker-compose.yml           # Container-Konfiguration
├── Dockerfile                   # Multi-Stage Build
├── pom.xml                      # Maven Build
├── src/                         # Quellcode
├── docs/                        # Hilfe-Dokumentation
└── export/                      # PDF-Exporte (vom Container beschrieben)
```

Zwei Docker Named Volumes liegen ausserhalb dieses Verzeichnisses:

| Volume | Container-Pfad | Inhalt |
|--------|----------------|--------|
| `goaffpro-config` | `/app/config` | `config.properties` — Einstellungen, Vorlagen, Versandprotokoll, **Belegnummern-Zaehler** |
| `goaffpro-data` | `/app/data` | `goaffpro_sync.sqlite`, `goaffpro_files/` (Assets), `backups/` (Sicherungsarchive) |

Der Ordner `export/` ist als Bind-Mount auf `/app/exports` eingebunden und enthaelt
neben den Belegordnern auch `goaffpro_ui_settings.properties`.

## Troubleshooting

**Container startet nicht:**
```bash
docker-compose logs goaffpro
```
Haeufige Ursachen: `config.properties` fehlt oder ist nicht lesbar.

**Port 8090 belegt:**
```bash
docker-compose down
docker-compose up -d
```

**Config-Aenderungen uebernehmen:**
Die App liest `config.properties` beim Start. Nach Aenderungen:
```bash
docker-compose restart goaffpro
```

**Sicherung oder Import wird abgelehnt („Wartungsmodus" / laufender Sync):**
Beide Vorgaenge sperren sich gegenseitig mit dem Sync aus. Im Reiter **Sync** oben auf
**Sync pausieren** klicken und den Vorgang wiederholen.

**Belegnummer beginnt nach einem Import wieder bei 1:**
Die Zaehler stehen ausschliesslich in `config.properties`, nicht in der Datenbank.
Im Archiv pruefen mit `unzip -p goaffpro-backup_*.zip manifest.json` — fehlt dort der
Block `counters`, stammt das Archiv aus einer Umgebung ohne vergebene Nummern. Den
Stand dann direkt setzen (Container gestoppt):
```bash
docker-compose down
docker run --rm -v goaffpro-config:/config alpine \
  sh -c 'printf "\ngutschriftCounter=394\ngutschriftCounterYear=2026\n" >> /config/config.properties'
docker-compose up -d
```
Der Wert ist die zuletzt **tatsaechlich vergebene** Nummer (bei `GS-2026-0394` also
`394`); die naechste Gutschrift erhaelt dann `GS-2026-0395`.
