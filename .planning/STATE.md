---
gsd_state_version: 1.0
milestone: v1.3.2
milestone_name: Post-Deployment Maintenance
status: maintenance
stopped_at: Firmenname im Nachweis-PDF-Hinweis per Einstellung konfigurierbar gemacht
last_updated: "2026-05-05T00:00:00.000Z"
last_activity: 2026-05-05
progress:
  total_phases: 2
  completed_phases: 2
  total_plans: 5
  completed_plans: 5
  percent: 100
---

# Project State

## Project Reference

**Projekt:** S+R Assistent (GoAffPro Rechnungsservice)
**Technologie:** Java 22 + Vanilla JS SPA (dashboard.html, 51.987 Zeilen)
**Aktueller Fokus:** UI & UX Verbesserungen auf Basis Audit-Ergebnisse

## Current Position

Milestone v1.0 abgeschlossen — laufender Betrieb auf Synology/Portainer.
Aktuelle Tätigkeit: punktuelle Bugfixes nach Deployment-Beobachtungen.

Phase: 02 (versch-nerung) — DONE
Plan: 3 of 3
Status: Phase complete — verified
Last activity: 2026-04-26 (SMTP-Konfig-Fix, Tag 1.3.1)

Progress: [██████████] 100%

## Post-Milestone Maintenance Log

- **2026-05-05 — Nachweis-Firmenname konfigurierbar (Tag 1.3.2):** Der Hinweistext im Provisions-PDF enthielt den Firmennamen „S+R Linear Technology GmbH" hart eincodiert. Neue Einstellung `nachweisFirmenname` (Config-Key) ermöglicht, diesen Wert in den UI-Einstellungen zu ändern. Änderungen: neues Textfeld in der Settings-Seite (Abschnitt „Provisionsnachweis PDF"), GET/POST `/api/settings` um den Key erweitert, `createInvoiceDetailsPdf()` liest den Wert aus der Config. Fallback bleibt „S+R Linear Technology GmbH".

- **2026-04-26 — SMTP-Konfig-Fix (Tag 1.3.1):** Im laufenden Container kam beim PDF-/E-Mail-Versand `[SMTP] Konfiguration unvollständig (host=MISSING, username=MISSING, password=ok)`. Ursache: `mergeUiSettingsIntoConfig` ([WebUiServer.java:3815](src/main/java/WebUiServer.java#L3815)) verwendete `Objects.toString(value, default)`, was nur bei `null` auf den Default fällt — leere Strings aus `goaffpro_ui_settings.properties` überschrieben dadurch gültige Werte aus `config.properties`. Fix: `if (!ui.isEmpty())`-Pattern für alle SMTP-Felder (analog zu `goaffproAPIKey`, `lastImportedComission`). Zusätzlich `GOAFFPRO_SMTP_HOST` / `_USERNAME` / `_PORT` / `_TLS` in `docker-compose.yml` und `.env.example` durchgereicht — `resolveSmtpConfig` las sie zwar bereits, sie kamen aber nicht durch den Container.
- **Deploy-Hinweis Portainer:** Env-Vars müssen in den Stack-Settings (Tab „Environment variables") hinterlegt werden — `.env` wirkt nur lokal, nicht beim Git-basierten Deploy.

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: —

## Accumulated Context

### Audit-Ergebnisse

Beide Reviews liegen in `.planning/ui-reviews/`:

- `UI-REVIEW.md` — Statischer Code-Audit (16/24)
- `UX-LIVE-REVIEW.md` — Playwright Live-Audit (13/24)

### Top-Prioritäten (aus Audit)

1. 🔴 Mobile Layout kaputt — Sidebar überlappt bei <768px
2. 🔴 Kein Button-System — 4 Farben ohne Semantik
3. 🟠 "Beraterinnen-E-Mail"-Label rot statt neutral
4. 🟠 ~50 Zahllauf-Pills ohne Pagination
5. 🟡 Einstellungen als Seitenersatz statt Modal/Drawer

### Roadmap Evolution

- Projekt initialisiert: 2026-04-06 (manuelles Setup nach Audit)
- Phase 1 hinzugefügt: Responsive Layout und Sidebar-Collapse
- Phase 2 hinzugefügt: Verschönerung

### Blockers/Concerns

Keine.

## Session Continuity

Last session: 2026-05-05T00:00:00.000Z
Stopped at: Nachweis-Firmenname konfigurierbar gemacht, Tag 1.3.2
Resume file: None
