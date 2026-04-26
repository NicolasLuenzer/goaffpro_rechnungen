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

## 8. Backup

Folgende Daten sollten regelmaessig gesichert werden:

| Quelle | Inhalt |
|--------|--------|
| `.env` (oder Portainer Env-Vars) | API-Keys, Passwoerter (Secrets) |
| Docker Volume `goaffpro-config` | App-Einstellungen, SMTP-Host, Templates |
| `/volume1/docker/goaffpro_rechnungen/export/` | PDF-Exporte, Rechnungen |

Backup als tar.gz:

```bash
docker run --rm \
  -v goaffpro-config:/source/config \
  -v /volume1/docker/goaffpro_rechnungen/export:/source/exports \
  -v $(pwd):/backup \
  alpine tar -czf /backup/goaffpro_backup_$(date +%Y%m%d).tar.gz -C /source .
```

Das `goaffpro-config`-Volume ist im Synology Container Manager unter **Container > Details > Speicher** einsehbar. Die PDFs sind direkt ueber die Synology File Station unter `docker/goaffpro_rechnungen/export/` zugreifbar.

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

Die `config.properties` liegt in einem Docker Named Volume (`goaffpro-config`).

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
