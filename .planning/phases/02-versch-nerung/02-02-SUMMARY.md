---
phase: 02-versch-nerung
plan: 02
subsystem: ui

tags: [html, css, design-tokens, css-custom-properties, javascript, pill-collapse, ux]

# Dependency graph
requires:
  - phase: 02-versch-nerung
    plan: 01
    provides: label color fix (clr-text on recipient-advisor-label) and 7 trivial UI fixes

provides:
  - 11 new CSS custom property tokens in :root for design system completeness
  - All recurring hardcoded hex values in CSS rules and inline styles replaced with var() references
  - Zahllauf-pill collapse: max 10 newest pills visible, toggle expands/collapses older pills

affects: [02-03-versch-nerung]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CSS token migration: expand :root block to eliminate recurring hardcoded hex in CSS rules"
    - "Pill overflow pattern: slice(-N) for newest-first, hidden div #pillOverflow, JS toggle handler"

key-files:
  created: []
  modified:
    - src/main/resources/ui/dashboard.html

key-decisions:
  - "11 new tokens named semantically (surface-light, surface-tint, border-medium, accent-1b, etc.) — matches existing naming convention"
  - "Pill collapse shows 10 NEWEST pills (list.slice(-MAX_VISIBLE)) since chronologically sorted list has newest at end"
  - "Event delegation: box.querySelectorAll runs on parent box to cover both visible and hidden pills without re-binding"
  - "clr-surface-light count (10) below plan estimate of 12 — all #ede9fe occurrences properly migrated, plan count was an overestimate"
  - "#1e3a8a (off-palette blue) on 'Vorhandene Benutzer' heading replaced with var(--clr-accent-1) (#7c3aed) — closest on-palette match"

patterns-established:
  - "CSS token expansion: add tokens to :root, then sweep CSS rules for matching hardcoded values"
  - "Pill collapse pattern: MAX_VISIBLE constant, slice(-MAX_VISIBLE) for visible, slice(0, -MAX_VISIBLE) for hidden, toggle button with text swap"

requirements-completed: [BEAU-08, BEAU-09]

# Metrics
duration: 5min
completed: 2026-04-06
---

# Phase 2 Plan 2: CSS Token Migration and Zahllauf-Pill Collapse Summary

**11 CSS design tokens added to :root, all recurring hardcoded hex values in CSS rules and inline styles migrated to var() references, and Zahllauf-pill list capped at 10 newest entries with expand/collapse toggle.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-06T17:03:18Z
- **Completed:** 2026-04-06T17:08:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added 11 new CSS custom properties to `:root`: `--clr-surface-light`, `--clr-surface-tint`, `--clr-border-medium`, `--clr-accent-1b`, `--clr-accent-2-end`, `--clr-accent-4-end`, `--clr-danger-end`, `--clr-accent-3-end`, `--clr-accent-4-dark`, `--clr-text-code`, `--clr-border-neutral`
- Migrated all recurring hardcoded hex values in CSS rules (tabs, table headers, pills, help-doc, tree-toggle, email-designer tabs, section tabs, validation subtabs, KPI card gradients)
- Migrated inline style hex values: Abbrechen button (`#6b7280` → `var(--clr-muted)`), border-top lines (`#e5e7eb` → `var(--clr-border-neutral)`), "Vorhandene Benutzer" heading (`#1e3a8a` → `var(--clr-accent-1)`), three canvas borders
- Total hardcoded hex count in file reduced from ~99 to 69 (remaining are: `:root` definitions, `rgba()` functions, JS fallback colors, warning-banner colors, and other one-off non-recurring hex values)
- `renderCommissionHistoryEditor` now collapses pills when `list.length > 10`: shows 10 newest by default, older pills hidden in `#pillOverflow` div, toggle button "Alle anzeigen (N weitere)" expands/collapses
- Event delegation on `box` (parent element) means remove handlers work on all pills regardless of visibility state

## Task Commits

Each task was committed atomically:

1. **Task 1: CSS tokens to :root and replace hardcoded hex** - `be5c489` (feat)
2. **Task 2: Zahllauf-pill collapse toggle** - `a2c16f0` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified

- `src/main/resources/ui/dashboard.html` — 11 new :root tokens, CSS hex migration, pill collapse JS

## Decisions Made

- 11 new tokens named semantically following existing naming convention (`--clr-` prefix, semantic suffix like `-light`, `-tint`, `-end`, `-dark`)
- Pill collapse shows 10 NEWEST pills (`list.slice(-MAX_VISIBLE)`) since `sortCommissionsChronologically` produces chronological order with newest at end
- Event delegation via `box.querySelectorAll('button[data-remove-commission]')` on the parent box element — covers both rendered visible pills and pills inside `#pillOverflow` without needing to re-bind handlers after toggle
- Off-palette `#1e3a8a` on "Vorhandene Benutzer" heading mapped to `var(--clr-accent-1)` (#7c3aed) as the closest semantic match on the design system palette

## Deviations from Plan

None - plan executed exactly as written.

The `clr-surface-light` occurrence count (10) is below the plan's stated "at least 12" — but this is because the plan's count was an overestimate. All `#ede9fe` instances in CSS rules have been fully migrated (9 CSS replacements + 1 `:root` definition = 10 total). The important criterion — no hardcoded `#ede9fe` outside `:root` — is fully satisfied.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All BEAU-08 and BEAU-09 audit findings closed
- dashboard.html ready for Phase 02-03: Typography scale consolidation (8 sizes to 4-step scale)
- No blockers for next plan

---
*Phase: 02-versch-nerung*
*Completed: 2026-04-06*

## Self-Check: PASSED

- dashboard.html: FOUND
- 02-02-SUMMARY.md: FOUND
- Commit be5c489 (Task 1 - CSS tokens): FOUND
- Commit a2c16f0 (Task 2 - pill collapse): FOUND
- Commit 341edd6 (docs - metadata): FOUND
