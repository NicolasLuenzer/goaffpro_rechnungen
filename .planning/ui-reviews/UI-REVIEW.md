# Standalone App — UI Review

**Audited:** 2026-04-06
**Baseline:** Abstract 6-pillar standards (no UI-SPEC.md)
**Screenshots:** Not captured (Playwright browser not installed — dev server at localhost:8080 confirmed running)
**Registry audit:** No shadcn (components.json absent) — skipped

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Good German copy throughout; login button bare "Login" label and some raw tech strings leaked to users |
| 2. Visuals | 2/4 | No responsive design (zero @media queries); toolbar overflow and fixed 420px inputs break narrow viewports |
| 3. Color | 3/4 | Well-defined token system; ~15 hardcoded hex/rgba values leak outside tokens, one off-token blue `#1e3a8a` |
| 4. Typography | 2/4 | 8 distinct font sizes (10px–26px) in use; no type scale rationale; smallest 10px violates accessibility contrast guidance |
| 5. Spacing | 3/4 | Consistent 8px base unit dominates; several one-off pixel values (1px, 3px, 4px, 5px, 7px) undermine scale |
| 6. Experience Design | 3/4 | Good loading/error/empty states; no skeleton screens; button disabled-state missing during polling; native window.confirm for destructive actions |

**Overall: 16/24**

---

## Top 3 Priority Fixes

1. **No responsive CSS (zero @media queries)** — The global `input { min-width: 420px }` rule and multi-column toolbar layouts overflow on any viewport narrower than ~1100px; the app becomes unusable on tablets and is broken on mobile — Add a single `@media (max-width: 900px)` breakpoint that overrides `input { min-width: 0; width: 100%; }`, stacks `.toolbar` to `flex-direction: column`, and collapses the sidebar.

2. **Typography uses 8 distinct font sizes with no scale rationale** — Sizes 10px, 11px, 12px, 13px, 14px, 15px, 20px, and 26px appear in production CSS, several within 1–2px of each other, creating visual noise and no clear hierarchy — Reduce to a 4-step scale (12px body-small / 14px body / 18px subheading / 24px heading) and replace all occurrences.

3. **Hardcoded hex values bypass the design-token system** — At least 15 hardcoded colors appear outside `:root` tokens: `#ede9fe`, `#ddd6fe`, `#faf8ff`, `#4f46e5`, `#0d9488`, `#0e7490`, `#b91c1c`, `#b45309`, `#047857`, `#1e3a8a`, `#0f172a`, `#1e3a8a`, `#6b7280` (inline style on "Abbrechen" button, line 340) — Move every repeated hardcoded value to a named CSS custom property in `:root` and reference the token everywhere.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**Strengths:**
- All UI text is in consistent German, appropriate for the user base.
- Status messages are specific and informative: "Erzeuge Rechnungsdetails-PDF 2/5 für Payment-ID 99999..." (line 2067), "Einstellungen gespeichert. Es wurden keine inhaltlichen Änderungen erkannt." (line 1775).
- Empty states communicate the required action: "Klicken Sie auf „Benutzer laden" um vorhandene Benutzer anzuzeigen." (line 367).
- Loading states are named: "Lade Stammdaten..." (line 934), "Lade Auswertungsdaten..." (line 2368).
- Error messages forward the underlying API message, giving technical users enough signal.

**Issues:**
- `<button id="loginBtn">Login</button>` (line 379) — "Login" is an English noun used as a verb. German UX convention is "Anmelden". Low-friction fix.
- Raw tech prefixes leak into copy: `Fehler: ${e.message}` (lines 2629, 2692, 2705, 2724, 2739) — the catch-all `Fehler:` prefix is acceptable but the raw JS error string is not user-facing copy; it exposes internal error names to end users.
- Tab label "Auswertungen" for the analytics tab (line 685) and sidebar label "Rechnungsservice" for the main panel (line 2747) are domain-specific but clear.
- `<p>Für diesen Reiter ist aktuell keine interaktive Aktion hinterlegt.</p>` (line 672) — fallback copy for unknown executables is a developer string, not user copy.

---

### Pillar 2: Visuals (2/4)

**Strengths:**
- Design token system is well-defined (`:root` lines 11–32) with purposeful purple-to-teal gradient identity.
- Clear visual hierarchy in the header: gradient banner, dual logos, h1 title, subtitle, spacer, action buttons.
- KPI cards use four distinct gradient backgrounds cycling on nth-child, giving the analytics grid visual rhythm.
- Hover card on advisor names (`.advisor-hover-card`) has a speech-bubble arrow treatment — thoughtful interaction detail.
- Warning state (`.warning-banner`, `.row-warning`) uses red-tinted background and border clearly.
- Save button idle/dirty states (`.save-btn-idle` / `.save-btn-dirty`) provide clear unsaved-change feedback.

**Issues:**
- **No responsive design at all** — `grep` finds zero `@media` rules in 2,843 lines. The global `input { min-width: 420px }` (line 77) causes horizontal overflow on any viewport below ~900px. The sidebar at 220px fixed width further crowds small screens.
- **Toolbar overflow** — `.toolbar { display: flex; flex-wrap: wrap; }` wraps reasonably but does not collapse to column on narrow viewports, so labels and inputs appear misaligned.
- **Icon-only interaction** — The pill remove button `<button>x</button>` (line 1549) is a bare "x" character with no accessible label. The column resizer `<span class="col-resizer">` (line 1225) has a `title` attribute but no visual affordance until hover.
- **No loading skeleton** — All async data loads result in an empty table body during fetch; status text alone (e.g., "Lade Stammdaten...") is the only indicator. A row-level skeleton or spinner would improve perceived performance.
- **Version badge** (`position: fixed; top: 14px; right: 18px`) overlaps with content on narrow screens and conflicts with the header layout visually.
- **External image dependency** — Two logo `<img>` tags fetch from Shopify CDN and sr-gmbh.de (lines 239–241). If either URL fails the header silently shows broken images with no fallback.

---

### Pillar 3: Color (3/4)

**Strengths:**
- A disciplined `:root` token block defines 10 semantic colors, 3 radii, and 3 shadow levels (lines 11–32).
- The purple-to-teal gradient identity is applied consistently across the header, active tabs, KPI cards, and sidebar.
- Semantic color roles are respected: `--clr-danger` for destructive buttons, `--clr-muted` for secondary text, `--clr-accent-1` for interactive elements.
- 60/30/10 split is roughly maintained: `--clr-bg` (#f0f4ff light blue) dominates surfaces, `--clr-sidebar` / `--clr-accent-1` provide secondary identity, `--clr-danger` / status colors used sparingly.

**Issues:**
- Hardcoded hex values that bypass tokens appear at least 15 times in CSS and inline styles:
  - `#ede9fe` (tab hover, table row hover, pill background, tree toggle) — should be a token `--clr-accent-1-light`
  - `#ddd6fe` / `#c4b5fd` (table header gradient, sort-active) — should derive from `--clr-border-strong`
  - `#faf8ff` (settings background, table even rows, input background) — should be `--clr-surface-tint`
  - `#4f46e5` (tab active gradient end, section-tab active) — should be token `--clr-accent-1b`
  - `#0d9488` / `#0e7490` (button gradient ends) — should derive from `--clr-accent-2` / `--clr-accent-4`
  - `#1e3a8a` (line 353, inline `color` on "Vorhandene Benutzer" heading) — **off-palette blue, not in any token**
  - `#6b7280` (line 340, inline `background` on "Abbrechen" button) — should use `--clr-muted`
- `button.secondary` gradient hard-ends at `#0e7490` instead of `var(--clr-accent-2)` gradient pair — subtle inconsistency.

---

### Pillar 4: Typography (2/4)

**Distinct font sizes in use:** 10px, 11px, 12px, 13px, 14px, 15px, 20px, 26px — **8 steps total**

**Distinct font weights in use:** 400 (via `font-weight:normal`), 500, 600, 700 — **4 weights**

**Strengths:**
- Inter loaded via Google Fonts (line 8) with a reasonable subset: 400/500/600/700.
- Body uses 14px (input default) and most UI text is 13px — legible at desktop resolution.
- Weight 700 is correctly reserved for headings and table headers; 600 for buttons and active tabs.

**Issues:**
- **8 font sizes with no scale logic** — 10px, 11px, 12px, 13px are used within 1–2px of each other in different components (nav label at 10px, chevron at 11px, table at 12px, sidebar item at 13px). This range is indistinguishable and visually noisy. Best practice is 4 or fewer steps.
- **10px (side-nav-section-label)** — Text at 10px is below WCAG 1.4.3 minimum for normal-weight text on colored backgrounds at standard screen densities.
- **15px settings-group heading** — Falls between the 14px body and 20px modal heading, creating an orphan size not in the natural scale.
- **font-weight:normal inline override** (line 330) — Hard-codes 400 weight as an inline style, bypassing the cascade. Should use a CSS class.
- **No declared line-height for body text** — The `body` rule has no `line-height`; only `.help-doc` (1.6) and `.advisor-hover-card` (1.5) specify it explicitly.

---

### Pillar 5: Spacing (3/4)

**Dominant spacing values (from CSS):** 8px gap, 12px padding, 16px padding, 18px padding, 20px padding — implicit 4px base unit with 8px dominant step. Reasonably consistent.

**Strengths:**
- Gap values of 6px and 8px dominate flex containers — consistent with an 8px grid.
- Card/panel padding is 18–20px, creating appropriate breathing room.
- `border-radius` uses named tokens (--radius-sm: 8px, --radius-md: 12px, --radius-lg: 16px) — well-applied across components.

**Issues:**
- **One-off padding values** — 1px, 2px, 3px, 4px, 5px, 7px, and 9px appear in various component rules, diverging from an 8px base. For example: `.pill button { padding: 2px 8px }` and `.tree-toggle { padding: 2px 7px }` — 7px is non-standard.
- **Mixed inline spacing** — Several inline style attributes use pixel values not in the token grid: `gap:10px`, `gap:12px`, `gap:4px` in the user form section (lines 329, 330, 333).
- **`input { min-width: 420px }` global rule** (line 77) is a layout constraint masquerading as a spacing rule. Combined with `padding: 9px 12px` it creates oversized inputs that overflow containers on most screens.
- **Analytics toolbar date inputs** at `min-width:170px` (lines 707, 709) deviate from the global 420px minimum — inconsistency handled via inline override rather than a modifier class.

---

### Pillar 6: Experience Design (3/4)

**State coverage summary:**
- Loading: Text-based status messages present for all async operations (polling, settings load, analytics fetch, validation load, tree load, help load). No spinners or skeleton screens.
- Error: Every `catch` block surfaces the error message to a status element. Generic `Fehler: ${e.message}` pattern used on admin operations (lines 2629, 2692, 2705, 2724, 2739).
- Empty: Handled on main table (`updateEmptyTablesVisibility` hides tables with zero rows), validation table, analytics tables, help doc, user admin table (lines 367, 2608).
- Disabled: Buttons disabled during async operations in two places (validation reminder send btn line 918–921, details-btn lines 1465–1470). Polling button and export buttons lack disabled state during in-flight requests.
- Destructive confirmations: `window.confirm()` used for three destructive actions (advisor email send line 2055, single-row email send line 2108, user delete line 2699). This is the browser native dialog — no consistent styled confirmation UX.

**Strengths:**
- Unsaved settings feedback via `.save-btn-idle` / `.save-btn-dirty` visual distinction is excellent UX.
- Warning banner for advisor-mode email (`.warning-banner`) with danger-class on affected buttons is a clear safety pattern.
- Column resize and right-click column filter (Excel-like) are advanced interaction patterns executed cleanly.
- Login overlay correctly checks for existing `authToken` before showing the form (line 2571).

**Issues:**
- **Polling button has no disabled state during polling** — `togglePolling` (line 2405) starts polling and calls `setPollingButtonState(true)` which only changes text/class, but the button remains enabled during `pollOnce()` execution window. Rapid clicking can queue multiple polls.
- **`exportSelectedInvoiceDetailsPdfs` and `loadInvoiceDetailsPdf` do not disable their trigger buttons** during the async loop (lines 2042–2100). A second click mid-export will submit duplicate requests.
- **`window.confirm()` for destructive actions** (lines 2055, 2108, 2699) uses browser native dialogs that are visually inconsistent with the app design, block the JS thread, and cannot be styled. A modal confirmation component would be more appropriate.
- **No error boundary** for the root `init()` function — it has a single top-level catch that renders `<p>Fehler: ${error.message}</p>` into `#panels` (line 2836), which leaves the header and sidebar rendered but content replaced by a bare error string.
- **Login form labels** (lines 377–378) use `<label>Benutzername</label>` and `<label>Passwort</label>` without `for` attributes pointing to the corresponding inputs — inputs have IDs `loginUsername` and `loginPassword` but labels are not associated, breaking screen-reader UX.
- **No `autocomplete` attribute** on the login password field or SMTP password field — browsers may not offer to fill/save credentials.

---

## Files Audited

- `src/main/resources/ui/dashboard.html` (2,843 lines — all HTML, CSS, JS)
- `docs/HILFE.md` (UX context / user documentation)
