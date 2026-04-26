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
ERPNEXT_API_KEY=<DEIN_ERPNEXT_KEY>
ERPNEXT_API_SECRET=<DEIN_ERPNEXT_SECRET>
SMTP_PASSWORD=<DEIN_SMTP_PASSWORT>
AUTH_SECRET=<langer-zufaelliger-string>
ADMIN_PASSWORD=<sicheres-admin-passwort>
```

### Variante B: Portainer (Stack via Git Repository)

Im Stack-Erstellungsformular von Portainer **nach unten scrollen** zum Abschnitt **"Environment variables"**. Dort die 6 Variablen einzeln eintragen:

| Name | Wert |
|------|------|
| `GOAFFPRO_API_KEY` | dein Key |
| `ERPNEXT_API_KEY` | dein Key |
| `ERPNEXT_API_SECRET` | dein Secret |
| `SMTP_PASSWORD` | dein Passwort |
| `AUTH_SECRET` | langer zufaelliger String |
| `ADMIN_PASSWORD` | sicheres Admin-Passwort |

Portainer injiziert diese in den Container — die `.env`-Datei wird dann nicht benoetigt (`env_file` ist optional).

> **Wichtig:** `.env` enthaelt sensible Zugangsdaten und wird NICHT ins Git-Repository aufgenommen.

## 3. Konfiguration (optional)

Beim ersten Start ohne `config.properties` nutzt die App Defaults — das Verzeichnis `config/` wird vom Docker-Bind-Mount automatisch angelegt. Settings, die du in der UI vornimmst, werden in `config/config.properties` persistiert.

Wenn du die Konfiguration **vorbefuellen** willst, lege die Datei manuell an:

```bash
mkdir -p config
nano config/config.properties
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

> **Hinweis:** API-Keys und Passwoerter (`goaffproAPIKey`, `smtpPassword`, `erpnextApiKey`, `erpnextApiSecret`, `authSecret`, `adminPassword`) sollten NICHT in `config.properties` stehen — sie werden ueber `.env` (oder Portainer Env-Vars) gesetzt. Wenn die Env-Variable gesetzt ist, hat sie Vorrang vor dem File-Wert.

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

## 8. Backup

Folgende Dateien/Verzeichnisse sollten regelmaessig gesichert werden:

| Pfad | Inhalt |
|------|--------|
| `.env` | API-Keys, Passwoerter (Secrets) |
| `config/config.properties` | App-Einstellungen, SMTP-Host, Templates |
| `docker-data/exports/` | PDF-Exporte, Rechnungen, `goaffpro_users.enc` (verschluesselte Benutzer) |

```bash
# Beispiel: Backup erstellen
tar -czf backup_goaffpro_$(date +%Y%m%d).tar.gz .env config/ docker-data/
```

## Verzeichnisstruktur auf dem Server

```
/volume1/docker/goaffpro_rechnungen/
├── .env                         # Secrets (API-Keys, Passwoerter)
├── .env.example                 # Template fuer .env
├── config/
│   └── config.properties        # App-Einstellungen (kein Secret)
├── docker-data/
│   └── exports/                 # PDF-Exporte + Benutzerdaten
├── docker-compose.yml           # Container-Konfiguration
├── Dockerfile                   # Multi-Stage Build
├── pom.xml                      # Maven Build
├── src/                         # Quellcode
└── docs/                        # Hilfe-Dokumentation
```

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
