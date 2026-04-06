# Phase 1: Responsive Layout und Sidebar-Collapse - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Fügt die erste responsive Breakpoint-Schicht zu `dashboard.html` hinzu. Konkret: Sidebar kollabierbar machen auf schmalen Viewports, die globale `min-width:420px`-Input-Regel für Mobile fixen, und das Haupt-Layout (`#mainLayout`) stacken lassen. Der Email-Designer bleibt in dieser Phase unverändert (Desktop-only).

Dies ist eine reine CSS/HTML-Phase — kein neues Feature, nur Viewport-Kompatibilität.

</domain>

<decisions>
## Implementation Decisions

### Email-Designer auf Mobile
- **D-01:** Email-Designer-Grid (`minmax(560px,1fr) minmax(420px,1fr)`) bleibt in Phase 1 **unverändert** — Desktop-only. Kein Responsive-Fix für den Email-Designer in dieser Phase.

### Claude's Discretion
Die folgenden Bereiche wurden nicht diskutiert — Claude entscheidet die Umsetzung basierend auf Audit-Ergebnissen und gängigen Patterns:

- **Sidebar-Collapse-Pattern:** Hamburger-Button mit Drawer/Overlay oder einfaches Ausblenden auf Mobile. Betrifft `#sideNav` (`.side-nav`, 220px).
- **Breakpoints:** Bei welcher Breite (Empfehlung aus Audit: `768px` für Mobile, `900px` für Tablet) wechselt das Layout.
- **Input-Breite:** Die globale Regel `input[type="text"] { min-width: 420px }` (Zeile 77) auf Mobile auf `width:100%; min-width:0` setzen.
- **Toolbar/Tab-Stacking:** `.toolbar` und `.tabs` auf Mobile zu `flex-direction:column` wechseln lassen.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Audit-Ergebnisse (Primärquelle für Issues)
- `.planning/ui-reviews/UI-REVIEW.md` — Statischer Code-Audit (Pillar 2: Visuals 2/4): listet konkret die fehlenden @media-Rules, die 420px-Input-Regel und Sidebar-Issues
- `.planning/ui-reviews/UX-LIVE-REVIEW.md` — Playwright Live-Audit: Mobile 0/4, konkreter Fix-Vorschlag für Sidebar (Zeile 30: `@media (max-width: 767px)`) und Screenshot-Dokumentation

### Zieldatei
- `src/main/resources/ui/dashboard.html` — Einzige Datei, die in Phase 1 geändert wird. Alle CSS-Änderungen gehen in den `<style>`-Block dieser Datei.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- CSS-Token-System (`:root`, Zeilen 12–32): Farbvariablen, Radii, Shadows — alle neuen Responsive-Rules sollten diese Tokens verwenden
- `.toolbar { display:flex; flex-wrap:wrap; }` (Zeile 67): Bereits `flex-wrap:wrap`, braucht nur `flex-direction:column` auf Mobile
- `.tabs { display:flex; flex-wrap:wrap; }` (Zeile 58): Gleiche Situation wie .toolbar

### Established Patterns
- Inline-Style auf `#mainLayout`: `display:flex;align-items:flex-start;gap:0;` (Zeile 258) — für Responsive muss entweder die Inline-Style überschrieben werden per @media, oder der Style auf eine CSS-Klasse migriert werden
- Sidebar JS: `#sideNav` wird dynamisch per JS gebaut (`buildSideNav()` ab Zeile 2516) — Hamburger-Toggle-Button muss im HTML ergänzt werden, Sidebar-State per JS (`classList.toggle`)

### Integration Points
- Zeile 258: `<div id="mainLayout" ...>` — Haupt-Layout-Container, hier wrappen oder @media ansetzen
- Zeile 259: `<nav id="sideNav" class="side-nav">` — Sidebar-Element
- Zeile 77: `input[type="text"], input[type="email"]... { min-width: 420px; }` — zu überschreibende Problemregel

</code_context>

<specifics>
## Specific Ideas

- Aus UX-Live-Audit (Zeile 30): `@media (max-width: 767px) { .sidebar { display: none; } .hamburger { display: block; } }` als Startpunkt
- Audit empfiehlt Breakpoint bei `<768px` für Mobile

</specifics>

<deferred>
## Deferred Ideas

- Email-Designer Responsive: Grid auf Single-Column für Mobile — verschoben auf spätere Phase
- Skeleton Screens für async Daten — aus UI-Audit, gehört in separate UX-Phase

</deferred>

---

*Phase: 01-responsive-layout-und-sidebar-collapse*
*Context gathered: 2026-04-06*
