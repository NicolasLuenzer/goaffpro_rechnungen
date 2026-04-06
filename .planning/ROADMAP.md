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

### Phase 1: Responsive Layout und Sidebar-Collapse

**Goal:** Erste responsive Breakpoint-Schicht hinzufuegen: Sidebar auf Mobile verstecken und per Hamburger-Button als Drawer einblenden, Input-Overflow fixen, Layout vertikal stacken. Kein Email-Designer-Responsive (D-01).
**Requirements**: RESP-01-media-queries, RESP-02-input-overflow, RESP-03-layout-stacking, RESP-04-sidebar-hide, RESP-05-hamburger-toggle, RESP-06-sidebar-drawer
**Depends on:** Phase 0
**Plans:** 2 plans

Plans:
- [ ] 01-01-PLAN.md — CSS @media breakpoint: sidebar hide, input fix, layout stacking
- [ ] 01-02-PLAN.md — Hamburger button HTML + JS toggle + sidebar drawer overlay
