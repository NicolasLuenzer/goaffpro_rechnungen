---
phase: 02-versch-nerung
plan: 03
subsystem: ui

tags: [html, css, design-tokens, css-custom-properties, typography]

# Dependency graph
requires:
  - phase: 02-versch-nerung
    plan: 02
    provides: 11 CSS tokens in :root, hardcoded hex values migrated to var() references

provides:
  - 4 typography scale tokens in :root (--fs-sm, --fs-body, --fs-sub, --fs-heading)
  - All CSS font-size rules replaced with var(--fs-*) token references
  - Distinct font sizes reduced from 8 arbitrary values to a 4-step scale (12/14/18/24px)

affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Typography scale token pattern: --fs-sm/body/sub/heading maps to 12/14/18/24px scale"
    - "Inline styles use literal px values matching tokens (var() not usable in inline style attributes)"

key-files:
  created: []
  modified:
    - src/main/resources/ui/dashboard.html

key-decisions:
  - "side-nav-section-label keeps 10px (WCAG note: intentional ultra-small uppercase label, accepted exception)"
  - "hamburger-btn keeps 22px font-size (icon glyph size, not text typography)"
  - "settings h2 inline style: 20px -> 18px literal (var() not viable in inline style attribute)"
  - "JS-generated dept badge spans updated from 11px to 12px (matching --fs-sm scale)"
  - "settings-group h3 steps up from 15px to 18px (var(--fs-sub)) for clear body/section hierarchy"

patterns-established:
  - "Typography token sweep: add tokens to :root, grep all font-size occurrences, replace systematically top-to-bottom"

requirements-completed: [BEAU-10]

# Metrics
duration: 5min
completed: 2026-04-06
---

# Phase 2 Plan 3: Typography Scale Consolidation Summary

**4 typography scale tokens (--fs-sm/body/sub/heading = 12/14/18/24px) added to :root, consolidating 8 arbitrary font sizes down to a 5-value set with 35 var(--fs-*) token usages across all CSS rules.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-06T17:10:00Z
- **Completed:** 2026-04-06T17:15:44Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added 4 typography tokens to `:root`: `--fs-sm: 12px`, `--fs-body: 14px`, `--fs-sub: 18px`, `--fs-heading: 24px`
- Replaced all CSS rule font-size values with `var(--fs-*)` token references — 35 usages total
- Eliminated 8-way font-size sprawl: 10px, 11px, 12px, 13px, 14px, 15px, 20px, 26px -> consolidated to 5 distinct values (10px exception, 12px, 14px, 18px, 22px hamburger icon)
- Updated `@media (max-width: 767px)` responsive overrides to use tokens
- Updated inline styles in HTML (settings overlay h2, "Vorhandene Benutzer" strong, reload button)
- Fixed JS-generated dept badge spans: 11px -> 12px (matching --fs-sm)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add typography tokens to :root and replace all font-size values in CSS rules** - `44ce57b` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified

- `src/main/resources/ui/dashboard.html` — 4 new :root typography tokens, all CSS font-size rules tokenized

## Decisions Made

- `.side-nav-section-label` retained at 10px — this is the documented WCAG exception (intentional ultra-small uppercase label for section grouping in dark sidebar)
- `.hamburger-btn` retained at 22px — this is the hamburger icon glyph size (☰), not text typography, so it falls outside the typography scale
- Inline styles use literal px values (18px, 14px, 12px) matching the token values — `var()` is not viable in HTML inline `style` attributes for cross-browser compatibility
- `.settings-group h3` stepped up from 15px to `var(--fs-sub)` (18px) — creates clear visual hierarchy between body text (14px) and section headings (18px)
- JS-generated department badge spans had `font-size:11px` — updated to 12px to align with --fs-sm, eliminating the last non-scale size from dynamic content

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed 11px in JS-generated dept badge spans**
- **Found during:** Task 1 (verification check)
- **Issue:** Line 2669 had `font-size:11px` in a JS template literal generating inline styles for department badges. The plan addressed CSS `font-size:11px` in `.side-nav-chevron` but the JS-generated inline style was not listed explicitly.
- **Fix:** Changed to `font-size:12px` (matching --fs-sm scale), eliminating the last non-scale size
- **Files modified:** src/main/resources/ui/dashboard.html (line 2669)
- **Verification:** `grep -c "font-size:11px" src/main/resources/ui/dashboard.html` returns 0
- **Committed in:** 44ce57b (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug fix)
**Impact on plan:** Required to fully satisfy the "no 11px" acceptance criterion. The plan missed one JS inline style occurrence during planning.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All BEAU-10 audit findings closed
- Typography scale fully consolidated: 4-step token system in place
- Phase 2 (Verschönerung) is now complete — all 3 plans executed
- No blockers

---
*Phase: 02-versch-nerung*
*Completed: 2026-04-06*

## Self-Check: PASSED

- dashboard.html: FOUND
- 02-03-SUMMARY.md: FOUND
- Commit 44ce57b (Task 1 - typography tokens): FOUND
