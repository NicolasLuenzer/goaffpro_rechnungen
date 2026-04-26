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

Die App speichert ihre Daten in **zwei Docker Named Volumes**:

| Volume | Inhalt |
|--------|--------|
| `goaffpro-config` | `config.properties` (App-Einstellungen, Templates) |
| `goaffpro-exports` | PDF-Exporte, `goaffpro_users.enc` |

Beim ersten Start sind die Volumes leer — die App nutzt Defaults und persistiert UI-Aenderungen automatisch in `goaffpro-config`.

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

Folgende Daten sollten regelmaessig gesichert werden:

| Quelle | Inhalt |
|--------|--------|
| `.env` (oder Portainer Env-Vars) | API-Keys, Passwoerter (Secrets) |
| Docker Volume `goaffpro-config` | App-Einstellungen, SMTP-Host, Templates |
| Docker Volume `goaffpro-exports` | PDF-Exporte, Rechnungen, `goaffpro_users.enc` |

Volumes auf dem Host sichern:

```bash
# Volume-Inhalte anzeigen
docker run --rm -v goaffpro-config:/data alpine ls -la /data
docker run --rm -v goaffpro-exports:/data alpine ls -la /data

# Backup als tar.gz
docker run --rm \
  -v goaffpro-config:/source/config \
  -v goaffpro-exports:/source/exports \
  -v $(pwd):/backup \
  alpine tar -czf /backup/goaffpro_backup_$(date +%Y%m%d).tar.gz -C /source .
```

Auf Synology lassen sich Named Volumes ueber **Container Manager > Container > Details > Speicher** einsehen.

## Verzeichnisstruktur auf dem Server

```
/volume1/docker/goaffpro_rechnungen/
├── .env                         # Secrets (nur fuer CLI-Deployment)
├── .env.example                 # Template fuer .env
├── docker-compose.yml           # Container-Konfiguration
├── Dockerfile                   # Multi-Stage Build
├── pom.xml                      # Maven Build
├── src/                         # Quellcode
└── docs/                        # Hilfe-Dokumentation
```

Daten liegen in Docker Volumes (verwaltet von Docker, nicht im Stack-Verzeichnis).

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
