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

## 2. Konfiguration vorbereiten

```bash
mkdir -p config docker-data/exports
```

Erstelle die Datei `config/config.properties` mit den benoetigten Einstellungen:

```properties
# GoAffPro API
goaffproApiKey=<DEIN_API_KEY>

# SMTP E-Mail-Versand
smtpHost=smtp.mandrillapp.com
smtpPort=587
smtpUser=<DEIN_SMTP_USER>
smtpPassword=<DEIN_SMTP_PASSWORT>
smtpTls=true

# ERPNext API (optional)
erpNextApiKey=<DEIN_ERPNEXT_KEY>
erpNextApiSecret=<DEIN_ERPNEXT_SECRET>

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

> **Wichtig:** Die Datei `config/config.properties` enthaelt sensible Zugangsdaten und wird NICHT ins Git-Repository aufgenommen.

## 3. Container bauen und starten

```bash
docker-compose up --build -d
```

Der erste Build dauert einige Minuten (Maven-Dependencies werden heruntergeladen).

## 4. Zugriff testen

Die App ist erreichbar unter:

```
http://<SERVER-IP>:8090
```

Logs pruefen:

```bash
docker-compose logs -f goaffpro
```

## 5. Updates einspielen

```bash
cd /volume1/docker/goaffpro_rechnungen
git pull
docker-compose up --build -d
```

Der Multi-Stage-Build nutzt Docker-Layer-Caching — nur geaenderte Teile werden neu gebaut.

## 6. Container verwalten

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

## 7. Backup

Folgende Dateien/Verzeichnisse sollten regelmaessig gesichert werden:

| Pfad | Inhalt |
|------|--------|
| `config/config.properties` | API-Keys, SMTP-Zugangsdaten, App-Einstellungen |
| `docker-data/exports/` | PDF-Exporte, Rechnungen, `goaffpro_users.enc` (verschluesselte Benutzer) |

```bash
# Beispiel: Backup erstellen
tar -czf backup_goaffpro_$(date +%Y%m%d).tar.gz config/ docker-data/
```

## Verzeichnisstruktur auf dem Server

```
/volume1/docker/goaffpro_rechnungen/
├── config/
│   └── config.properties        # Konfiguration mit Zugangsdaten
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
