---
phase: 02-versch-nerung
plan: 01
subsystem: ui

tags: [html, css, favicon, accessibility, wcag, form, password, canvas, chart]

# Dependency graph
requires:
  - phase: 01-responsive-layout-und-sidebar-collapse
    provides: Responsive sidebar and hamburger toggle foundation

provides:
  - SVG favicon via data URI (no /favicon.ico 404)
  - Login form with autocomplete, for-labels, and "Anmelden" button text
  - GoAffPro and ERPNext API-Key inputs masked as type=password
  - Beraterinnen-E-Mail label using neutral var(--clr-text) color
  - Analytics date range wrapped in flex-shrink:0 group for single-line layout
  - drawBarChart empty state with centered Inter-font descriptive German text
  - input[type="color"] CSS rule enforcing 44x44px WCAG 2.5.5 target size

affects: [02-02-versch-nerung, 02-03-versch-nerung]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "data URI SVG favicon for zero-dependency browser tab icon"
    - "form wrapper with autocomplete attributes for browser credential saving"
    - "Canvas ctx.measureText for centered empty-state text"
    - "input[type=color] CSS rule for WCAG target size"

key-files:
  created: []
  modified:
    - src/main/resources/ui/dashboard.html

key-decisions:
  - "Use data URI SVG emoji favicon instead of separate .ico file — zero dependencies, renders in all modern browsers"
  - "adminUsersBox kept outside the <form> — it is an admin section unrelated to login credentials"
  - "Date input min-width reduced from 170px to 140px to fit the flex-shrink wrapper without overflow"
  - "Chart empty-state message uses unicode escapes for special German characters to avoid encoding issues in JS strings"

patterns-established:
  - "Canvas empty state: use measureText to center text horizontally and height/2 for vertical centering"
  - "CSS-first WCAG target sizing: input[type='color'] rule in global CSS rather than inline styles"

requirements-completed: [BEAU-01, BEAU-02, BEAU-03, BEAU-04, BEAU-05, BEAU-06, BEAU-07]

# Metrics
duration: 15min
completed: 2026-04-06
---

# Phase 2 Plan 1: 7 Trivial UI Fixes Summary

**SVG favicon, login-form with autocomplete, API-key password masking, neutral label color, flex-locked date range, centered chart empty state, and 44x44px color pickers — all applied to dashboard.html in two atomic commits.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-06T17:00:00Z
- **Completed:** 2026-04-06T17:15:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added SVG emoji favicon via data URI in `<head>` — browser tab now shows a chart icon with no 404 for /favicon.ico
- Wrapped login inputs in `<form autocomplete="on">` with for/autocomplete attributes and changed button text to "Anmelden" (German UX convention)
- Masked GoAffPro and ERPNext API-Key inputs as type="password" — secrets no longer visible in plain text
- Changed .recipient-advisor-label from error-red (#b91c1c) to var(--clr-text) — label no longer implies an error state
- Wrapped analytics date range in `flex-shrink:0` div — stays on one line at 1440px without wrapping
- Improved drawBarChart empty state: centered text, Inter font, descriptive German instruction instead of top-left "Keine Daten"
- Added `input[type="color"]` CSS rule: 44x44px dimensions satisfying WCAG 2.5.5 target size

## Task Commits

Each task was committed atomically:

1. **Task 1: Favicon, login form wrapper, API-key masking, login button text** - `a24f1ca` (feat)
2. **Task 2: Beraterinnen-E-Mail label color, date layout, chart empty states, color picker sizing** - `d88d268` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified

- `src/main/resources/ui/dashboard.html` - All 7 UI/UX fixes applied (favicon, login form, API masking, label color, date layout, chart empty state, color picker sizing)

## Decisions Made

- Used data URI SVG emoji favicon — no separate asset file needed, works in all modern browsers
- `adminUsersBox` kept outside the `<form>` element — it is an admin user management section unrelated to login credentials
- Date input min-width reduced from 170px to 140px to fit properly within the new flex-shrink:0 wrapper
- Chart empty-state text uses unicode escape sequences (\u2013, \u00e4, \u201e, \u201c) for German characters to ensure encoding safety in the JS string

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 7 BEAU-0x audit findings closed
- dashboard.html ready for Phase 02-02: CSS token migration and Zahllauf-pill collapse
- No blockers for next plan

---
*Phase: 02-versch-nerung*
*Completed: 2026-04-06*
