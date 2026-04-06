# Roadmap: S+R Assistent — UI & UX Verbesserungen

## Overview

Auf Basis des UI-Audits (Stufe 1: statischer Code-Audit, Stufe 2: Playwright Live-Audit) werden die identifizierten UI/UX-Probleme in der `dashboard.html` schrittweise behoben. Ziel ist eine konsistente, zugängliche und responsive Oberfläche.

## Milestones

- 🚧 **v1.0 UI-Verbesserungen** — Phasen 1-N (in Planung)

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Geplante Milestone-Arbeit
- Decimal phases (1.1, 1.2): Dringende Einfügungen (markiert mit INSERTED)

## Phase Details

## Progress

**Execution Order:**
Phasen werden in numerischer Reihenfolge ausgeführt.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1     | 2/2            | Complete | 2026-04-06 |
| 2     | 0/3            | Planned | — |

### Phase 1: Responsive Layout und Sidebar-Collapse

**Goal:** Erste responsive Breakpoint-Schicht hinzufuegen: Sidebar auf Mobile verstecken und per Hamburger-Button als Drawer einblenden, Input-Overflow fixen, Layout vertikal stacken. Kein Email-Designer-Responsive (D-01).
**Requirements**: RESP-01-media-queries, RESP-02-input-overflow, RESP-03-layout-stacking, RESP-04-sidebar-hide, RESP-05-hamburger-toggle, RESP-06-sidebar-drawer
**Depends on:** Phase 0
**Plans:** 2/2 plans executed

Plans:
- [x] 01-01-PLAN.md — CSS @media breakpoint: sidebar hide, input fix, layout stacking
- [x] 01-02-PLAN.md — Hamburger button HTML + JS toggle + sidebar drawer overlay

### Phase 2: Verschönerung

**Goal:** UI-Beautification: 7 triviale Fixes (Favicon, Login-Form, API-Key-Masking, Label-Farbe, Datum-Layout, Chart-Leerstate, Color-Picker), CSS-Token-Migration für hardcoded Hex-Werte, Zahllauf-Pill-Collapse, und Typography-Scale-Konsolidierung (8 Sizes auf 4-Step-Scale).
**Requirements**: BEAU-01, BEAU-02, BEAU-03, BEAU-04, BEAU-05, BEAU-06, BEAU-07, BEAU-08, BEAU-09, BEAU-10
**Depends on:** Phase 1
**Plans:** 3 plans

Plans:
- [ ] 02-01-PLAN.md — 7 trivial fixes: favicon, login form, API-key masking, label color, date layout, chart empty states, color picker sizing
- [ ] 02-02-PLAN.md — CSS token migration (hardcoded hex to custom properties) + Zahllauf-pill collapse toggle
- [ ] 02-03-PLAN.md — Typography scale consolidation (8 sizes to 4-step scale via CSS tokens)
