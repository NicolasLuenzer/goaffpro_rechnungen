# Phase 2: Verschönerung — Research

**Researched:** 2026-04-06
**Domain:** Vanilla JS SPA CSS/HTML beautification — single monolith file (dashboard.html, 3022 lines post Phase 1)
**Confidence:** HIGH — all findings are based on direct code inspection of dashboard.html

---

## Summary

Phase 2 targets the remaining UI/UX audit findings after Phase 1 fixed responsive layout and sidebar collapse. The work is entirely within `src/main/resources/ui/dashboard.html` — a single-file Vanilla JS SPA. No build system, no bundler, no component framework. All CSS lives in the `<style>` block (lines 11–362), all HTML and JS follow inline.

The file now has 3022 lines (Phase 1 added ~179 lines of CSS+JS). The 11 remaining issues fall into three risk tiers:

- **Low-risk / high-visibility:** Favicon, login-form wrapper, password field type, "Beraterinnen-E-Mail" red color, date-layout in Auswertungen, chart empty-state text, color-picker target size. These are small, isolated, and easily verified.
- **Medium-risk / medium-impact:** Zahllauf-pill collapse toggle (JS state change needed), button semantic system (CSS class rename across ~20 button elements), typography scale reduction (CSS token change + find-replace on font-size values).
- **Higher-risk / architectural:** CSS token migration for hardcoded hex values (risk: missing one occurrence). The Settings overlay as full-page-replace pattern is out of scope per audit priority (L effort, Mittel impact — defer to Phase 3 if ever).

**Primary recommendation:** Execute in three sequential plans — (1) trivial fixes (favicon, form wrapper, password types, label color, date layout, chart text, color-picker), (2) CSS token cleanup + button system, (3) Zahllauf-pill collapse + typography scale. Keep each plan tightly scoped to prevent regression in a 3022-line monolith.

---

## Project Constraints (from CLAUDE.md)

No CLAUDE.md present in repository root. No project-level coding conventions are enforced beyond what is visible in the existing code:

- Single file: all changes go to `src/main/resources/ui/dashboard.html`
- No build step — plain CSS + HTML + vanilla JS
- Existing token system (`:root` lines 11–32) is the established convention — new tokens must go into that block
- No external CSS frameworks (no Tailwind, no Bootstrap)

---

## Standard Stack

### Core — what already exists
| Asset | Location | Purpose |
|-------|----------|---------|
| CSS custom properties | `:root` lines 11–32 | Design token system — 10 colors, 3 radii, 3 shadows |
| Google Fonts: Inter | `<head>` line 8–9 | Typography — weights 400/500/600/700 loaded |
| Vanilla JS | `<script>` tag line 515 | All interactivity inline |
| Canvas 2D API | `drawBarChart()` line 2280 | Chart rendering — no library, hand-rolled |

### No external dependencies to add
Phase 2 uses no new libraries. All fixes are pure CSS / HTML / vanilla JS.

---

## Architecture Patterns

### Established Patterns in the File

**CSS token usage:** New colors must be added to the `:root` block and referenced via `var(--name)`. Do not add hardcoded hex values — this is what Phase 2 is specifically fixing.

**Button variants — current state:**
```css
/* Global default — green gradient (accent-4) */
button { background: linear-gradient(135deg, var(--clr-accent-4), #0d9488); ... }
/* .secondary — teal gradient (accent-2) */
button.secondary { background: linear-gradient(135deg, var(--clr-accent-2), #0e7490); }
/* .danger — red gradient */
button.danger { background: linear-gradient(135deg, var(--clr-danger), #b91c1c); }
/* .header-btn — glass/frosted on header */
.header-btn { background: rgba(255,255,255,.15) !important; ... }
/* .save-btn-idle / .save-btn-dirty — purple save states */
```

**Pill render pattern:**
```javascript
// renderCommissionHistoryEditor() — line 1673
box.innerHTML = list.map(value => {
  const label = (historyLabels && historyLabels[value]) || value;
  return `<span class="pill">${label}<button type="button" data-remove-commission="${value}">x</button></span>`;
}).join('');
```
Modifying this function is the only change needed for pill collapse. The `box` is `#commissionHistoryEditor` div (`.pill-list`). The function is called from 5 locations: `renderCommissionHistoryEditor()` calls from `removeCommissionFromHistory`, `addLatestCommission`, `rebuildCommissionHistoryFromPayments`, `loadSettings` (×2).

**Settings overlay pattern — current (full page replace):**
```javascript
function openSettingsOverlay() {
  document.getElementById('mainLayout').style.display = 'none';
  document.getElementById('settingsOverlay').style.display = 'block';
}
```
The overlay HTML is at lines 398–503. This is the "Einstellungen sind Vollseiten-Ersatz" issue. Audit rates it as `Mittel` priority / `L` effort. Recommend deferring to Phase 3 — it's not required for Phase 2's beautification goals and carries significant risk.

**Canvas chart rendering — current empty state:**
```javascript
// drawBarChart() line 2292-2296
if (!labels || labels.length === 0) {
  ctx.fillStyle = '#6b7280';
  ctx.font = '14px Arial';
  ctx.fillText('Keine Daten', 20, 30);
  return;
}
```
The canvas elements (lines 862, 869, 871) render a small "Keine Daten" text in the top-left corner of a large empty white rectangle. Fix: add a centered, descriptive placeholder text (e.g., "Bitte Zeitraum wählen und „Daten ziehen" klicken").

---

## Issue-by-Issue Code Findings

### Issue 1: Favicon 404
**Current state:** No `<link rel="icon">` in `<head>`. No `favicon.ico` in `/src/main/resources/ui/`. The Java backend serves `dashboard.html` from that path; the browser requests `/favicon.ico` at the root.
**Fix options:**
- Option A (preferred): Add `<link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🧾</text></svg>">` to `<head>` — zero file dependency.
- Option B: Add a real `favicon.ico` file to the static serving path (requires understanding the Java server's static file serving configuration — riskier without backend context).
**Recommendation:** Option A — inline SVG data URI favicon. No files to add, no backend configuration change.
**Confidence:** HIGH (standard browser behavior)

### Issue 2: Login form `<form>` wrapper
**Current state (lines 505–513):**
```html
<div id="loginOverlay" class="login-overlay">
  <div class="login-card">
    <h3 style="margin-top:0">Anmeldung</h3>
    <div class="toolbar"><label>Benutzername</label><input id="loginUsername" type="text" .../></div>
    <div class="toolbar"><label>Passwort</label><input id="loginPassword" type="password" .../></div>
    <div class="toolbar"><button id="loginBtn" type="button">Login</button>...</div>
  </div>
</div>
```
No `<form>` element wraps the inputs. The password field is `type="password"` already. The browser console shows "Password field is not contained in a form (3×)" — this means the login password plus two additional password fields in the settings overlay trigger this warning.
**Fix:** Wrap the login-card content in `<form onsubmit="return false;" autocomplete="on">`. Add `autocomplete="current-password"` to `#loginPassword` and `autocomplete="username"` to `#loginUsername`. Also add `autocomplete` to SMTP password and ERPNext API secret fields.
**Affected lines:** 505–513 (login), 421 (smtpPassword), 440 (erpnextApiSecret).
**Risk:** Low — `type="button"` on loginBtn means no accidental form submission. The `onsubmit="return false;"` guard ensures keyboard-Enter doesn't double-submit.

### Issue 3: API-Key/Password as plaintext
**Current state:**
- `#goaffproAPIKey` (line 692): `type="text"` — should be `type="password"`
- `#erpnextApiKey` (line 436): `type="text"` — should be `type="password"`
- `#smtpPassword` (line 421): already `type="password"` — OK
- `#erpnextApiSecret` (line 440): already `type="password"` — OK
**Fix:** Change `type="text"` to `type="password"` for `#goaffproAPIKey` and `#erpnextApiKey`. Optionally add a show/hide toggle button (eye icon) using a `<button>` that toggles between `type="password"` and `type="text"`.
**Risk:** Low. JS code reads `.value` — `type` attribute does not affect value retrieval. The show/hide toggle is pure JS with no side effects.

### Issue 4: "Beraterinnen-E-Mail" label in red
**Current state (line 107, line 655):**
```css
.recipient-advisor-label { color: #b91c1c; font-weight: 700; }
```
```html
<label class="recipient-advisor-label"><input ... /> Versand: an Beraterinnen-E-Mail</label>
```
The red color `#b91c1c` is `--clr-danger` shade — communicates error/warning instead of a selectable option.
**Fix:** Change `.recipient-advisor-label` color from `#b91c1c` to `var(--clr-text)` (normal text color). The warning context is already provided by `#advisorMailWarning` (`.warning-banner`) which shows only when advisor mode is active. The label does not need to be red — the banner provides the warning.
**Risk:** Minimal. CSS class change, one selector.

### Issue 5: Zahllauf-Pills collapse toggle
**Current state:** `renderCommissionHistoryEditor()` renders all pills unconditionally. The count can be ~50 pills. The `.pill-list` div has no overflow control.
**Current HTML structure:** `<div id="commissionHistoryEditor" class="pill-list"></div>`
**Fix:** In `renderCommissionHistoryEditor()`, if `list.length > 10`:
1. Render first 10 pills normally
2. Render remaining pills in a `<div id="pillOverflow" style="display:none">...</div>`
3. Render a toggle button: `<button type="button" class="secondary" id="pillToggleBtn">Alle anzeigen (${list.length - 10} weitere)</button>`
4. Wire a click handler on the toggle button to show/hide `#pillOverflow` and update button text
**Risk:** Medium. Only one function is modified (`renderCommissionHistoryEditor`). The toggle is purely display — no data is lost. The remove-button click handler wires on all pills (both visible and hidden), so we must ensure `querySelectorAll` covers both containers. Fix: run `querySelectorAll` on `box` (the parent), not on the individual containers.
**Important:** The 5 call sites that call `renderCommissionHistoryEditor` all pass `(history, historyLabels)`. No signature change needed — only the function body changes.

### Issue 6: Typography scale reduction
**Current state — 8 font sizes in use:**
| Size | Where used |
|------|-----------|
| 10px | `.side-nav-section-label` |
| 11px | `.side-nav-chevron` (implicitly via font-size:11px) |
| 12px | `.pill`, `.tree-toggle`, `.advisor-hover-card`, `.settings-hint`, table cell special rules, canvas chart |
| 13px | `.tab`, `.section-tab`, `.validation-subtab`, `.email-designer-tab`, `.side-nav-dept-badge`, `.files`, `.side-nav-item`, `.status` paragraph, `.help-doc`, `.version-popup li`, `.table-filter-menu h4`, `.table-filter-options label`, `.side-nav-dept-name` |
| 14px | global `input`, global `button`, `.status` div, `h2` body text (via body default) |
| 15px | `.settings-group h3` |
| 20px | modal heading (inline style line 400: `font-size:20px`) |
| 26px | `h1` line 37 |

**Recommended 4-step scale (from audit):**
| Token | Size | Currently |
|-------|------|-----------|
| `--fs-sm` | 12px | Replaces 10px, 11px, 12px |
| `--fs-body` | 14px | Replaces 13px, 14px |
| `--fs-sub` | 18px | Replaces 15px, 20px (step up) |
| `--fs-heading` | 24px | Replaces 26px (step down) |

**Risk assessment:** MEDIUM-HIGH. This affects many components visually. The biggest risk is at the small end: collapsing 10px/11px/12px all to 12px is safe. Collapsing 13px and 14px to 14px is safe. The 15px setting group heading moving to 18px is a step up (currently between 14px and 20px). The 26px h1 moving to 24px is barely visible. The 20px modal heading moving to 18px is consistent.
**However:** The side-nav-section-label at 10px is a deliberate design choice for the muted uppercase label style — bumping it to 12px may make it look too prominent. This is a judgment call.
**Recommendation:** Implement the token system but apply conservatively — use 12px for 10px/11px/12px elements, 14px for 13px/14px, keep 18px for settings h3 and modal headings, use 24px for h1. Add the tokens to `:root` but apply only the unambiguous merges.

### Issue 7: Hardcoded hex values — CSS token migration
**Hardcoded values inventory (from audit + code inspection):**

| Hardcoded value | Occurrences | Proposed token | Lines |
|----------------|-------------|----------------|-------|
| `#ede9fe` | 5+ | `--clr-surface-light` | 60, 92, 95, 102, 123, 146, 153, 181, 205, 207 |
| `#ddd6fe` | 2 | `--clr-border-medium` | 92, 93, 163 |
| `#c4b5fd` | 3 | `--clr-border-strong` (already exists!) | 102 (pill border) |
| `#faf8ff` | 4 | `--clr-surface-tint` | 70, 77, 94, 118, 129, 173 |
| `#4f46e5` | 3 | `--clr-accent-1b` | 61, 124, 141, 147, 182, 214 |
| `#0d9488` | 1 | derive from `--clr-accent-2` gradient end | 81 |
| `#0e7490` | 2 | derive from `--clr-accent-2` gradient end | 83, 215 |
| `#b91c1c` | 2 | derive from `--clr-danger` shade | 85, 107 |
| `#b45309` | 1 | derive from `--clr-accent-3` shade | 216 |
| `#047857` | 1 | derive from `--clr-accent-4` shade | 217 |
| `#1e3a8a` | 1 | off-palette — replace with `var(--clr-accent-1)` | 484 (inline style) |
| `#6b7280` | 2 | `var(--clr-muted)` (already exists!) | 471 (inline style on Abbrechen btn) |
| `#0f172a` | 1 | near-black, use `var(--clr-text)` | 118 |

**Note:** `#c4b5fd` appears both as `var(--clr-border-strong)` (the existing token value) and hardcoded inline. This means some hardcodes can be replaced by tokens that already exist — just missing the `var()` reference.

**Risk:** MEDIUM. The main risk is missing an occurrence. Recommend a systematic grep-based approach: find all hex patterns, replace in order from most-used to least-used, verify in browser after each group.

**Key insight:** `#ede9fe`, `#faf8ff`, `#4f46e5`, `#ddd6fe` appear most frequently (table rows, tab hovers, pill background, settings background) — tokenizing these 4 values covers the majority of hardcoded uses.

### Issue 8: Date layout in Auswertungen (2-line wrap)
**Current state (lines 837–840):**
```html
<label for="analyticsFromDate">von:</label>
<input id="analyticsFromDate" type="date" style="min-width:170px" />
<label for="analyticsToDate">bis:</label>
<input id="analyticsToDate" type="date" style="min-width:170px" />
```
These are inside a `.toolbar` (which is `display:flex; flex-wrap:wrap`). The preceding items are two `<select>` elements (Zahllauf and Zeitraum), both with `min-width`. The total width exceeds the panel width, causing the date range to wrap to line 2.
**Fix:** Wrap `<label>von:</label><input…/><label>bis:</label><input…/>` in a `<div style="display:flex;align-items:center;gap:8px;flex-shrink:0;">`. This treats the from/to date range as a single non-wrapping unit within the flex toolbar. Optionally reduce `min-width` of date inputs from `170px` to `140px`.
**Risk:** Low. CSS/HTML only. No JS interaction.

### Issue 9: Chart empty states — descriptive text
**Current state (line 2292–2296):** `ctx.fillText('Keine Daten', 20, 30)` — top-left corner, small, easy to miss.
**Fix in `drawBarChart()`:**
```javascript
if (!labels || labels.length === 0) {
  ctx.fillStyle = '#9ca3af';
  ctx.font = '14px Inter, system-ui, sans-serif';
  const msg = 'Keine Daten – bitte Zeitraum wählen und „Daten ziehen" klicken';
  const x = Math.floor(width / 2 - ctx.measureText(msg).width / 2);
  const y = Math.floor(height / 2);
  ctx.fillText(msg, Math.max(10, x), y);
  return;
}
```
There are 3 canvas elements: `analyticsCountryChart`, `analyticsAmountChart`, `analyticsTxChart`. All use `drawBarChart()`. The fix in one function covers all three.
**Risk:** Minimal — isolated to `drawBarChart()`, no side effects.

### Issue 10: Color-picker target size (WCAG 2.5.5)
**Current state (lines 776–779):**
```html
<label style="display:flex;align-items:center;gap:6px;">Textfarbe <input id="emailTextColor" type="color" value="#1f2937"/></label>
<label style="display:flex;align-items:center;gap:6px;">Highlight <input id="emailHighlightColor" type="color" value="#fff59d"/></label>
```
Native `<input type="color">` renders as a small square (~28×28px) in most browsers. WCAG 2.5.5 recommends min 44×44px.
**Fix:** Add CSS rule for `input[type="color"]`: `width: 44px; height: 44px; padding: 2px; cursor: pointer; border-radius: var(--radius-sm); border: 1.5px solid var(--clr-border-strong);`
**Risk:** Minimal. Isolated CSS rule. Does not affect value reading.

### Issue 11: Login button text ("Login" → "Anmelden")
**Current state (line 510):** `<button id="loginBtn" type="button">Login</button>`
**Fix:** Change to "Anmelden" (German UX convention per audit Pillar 1).
**Risk:** Minimal — text-only change. No JS targets this button by text content.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Font loading | Custom font loader | Already loaded via Google Fonts in `<head>` | Already present |
| CSS variables | Inline JS-managed theme | `:root` CSS custom properties | Already established |
| Toggle visibility | Framework show/hide | `style.display = 'none'` / `'block'` | Established pattern in this codebase |
| Type scale | External type library | Native CSS `font-size` with tokens | No build step |
| Inline SVG favicon | External icon service | Data URI `<link rel="icon" href="data:...">` | No file dependency |

---

## Common Pitfalls

### Pitfall 1: Hardcoded inline styles that escape CSS class changes
**What goes wrong:** Changing a CSS class won't affect inline `style="..."` attributes. For example, line 471 has `style="background:#6b7280;"` on the Abbrechen button — changing the `button` default CSS won't fix this.
**Why it happens:** The settings overlay was built with inline styles instead of utility classes.
**How to avoid:** Before each fix, grep for the hardcoded value appearing in both CSS rules AND inline style attributes. Lines 340 (audit reference), 399, 400, 484, 471 all have relevant inline styles.
**Warning signs:** After a CSS class change the visual still shows the old color in the browser.

### Pitfall 2: `renderCommissionHistoryEditor` click handler scoping
**What goes wrong:** If the pill-toggle adds pills to a child div `#pillOverflow`, a `querySelectorAll('[data-remove-commission]')` scoped to `box` still finds them — good. But if coded as `document.querySelectorAll` (wrong scope) or scoped to only the first child div — broken.
**How to avoid:** In the refactored function, always query `box.querySelectorAll('[data-remove-commission]')` after the full `box.innerHTML` is written, including both visible and hidden pills.

### Pitfall 3: CSS specificity battles from `!important` on existing rules
**What goes wrong:** Some existing rules use `!important` (e.g., `.save-btn-idle`, `.save-btn-dirty`, the hamburger responsive override, `#mainContent padding-left`). New CSS tokens must not create unintended specificity conflicts.
**How to avoid:** When adding font-size tokens, don't add `!important`. The existing cascade should handle ordering correctly. Check specificity before adding new rules.

### Pitfall 4: Typography scale change breaks sidebar label at 10px
**What goes wrong:** `.side-nav-section-label` at 10px is intentionally tiny — the uppercase muted label style (`letter-spacing:.1em; text-transform:uppercase`). Bumping to 12px may look out of proportion.
**How to avoid:** Treat `.side-nav-section-label` as a special case — keep its `font-size: 10px` or use a dedicated `--fs-nav-label: 11px` token separate from `--fs-sm`. Don't blindly collapse all sub-12px to 12px.

### Pitfall 5: The inline `#e5e7eb` border in settings overlay
**What goes wrong:** Lines 474, 482 have `border-top: 1px solid #e5e7eb` as inline styles. This color is not in the token system. When doing the token migration sweep, this may be overlooked.
**How to avoid:** Include `#e5e7eb` in the grep sweep — this is a Tailwind gray-200 equivalent that should map to `var(--clr-border)` (`#e0e7ff`) or a new `--clr-border-neutral` token.

---

## Prioritization Matrix

| Issue | Impact | Risk | Effort | Recommended Plan |
|-------|--------|------|--------|------------------|
| Favicon 404 | Medium | Minimal | S | Plan 02-01 |
| Login `<form>` wrapper | Medium | Low | S | Plan 02-01 |
| GoAffPro/ERPNext API-Key as `type=password` | Medium | Low | S | Plan 02-01 |
| "Beraterinnen-E-Mail" red color | High | Minimal | S | Plan 02-01 |
| Date layout in Auswertungen | Medium | Low | S | Plan 02-01 |
| Chart empty state text | Low | Minimal | S | Plan 02-01 |
| Color-picker target size | Low | Minimal | S | Plan 02-01 |
| Login button "Anmelden" | Low | Minimal | S | Plan 02-01 |
| Zahllauf-pill collapse | High | Medium | M | Plan 02-02 |
| Hardcoded hex → CSS tokens | Medium | Medium | M | Plan 02-02 |
| Typography scale (4-step) | Medium | Medium | M | Plan 02-03 |
| Settings as full-page replace | Low | High | L | DEFER to Phase 3 |

---

## Architecture Patterns

### Recommended Plan Split

**Plan 02-01: Trivial fixes** (all S-effort, low-risk)
- Favicon inline SVG
- Login `<form>` wrapper + autocomplete attributes
- GoAffPro + ERPNext API-Key change to `type="password"`
- `.recipient-advisor-label` color fix
- Analytics date range: wrap in non-breaking flex group
- `drawBarChart()` empty state: centered descriptive text
- `input[type="color"]` sizing to 44×44px
- Login button text "Login" → "Anmelden"

**Plan 02-02: CSS system cleanup** (M-effort, medium-risk)
- Tokenize 4 most-used hardcoded hex values: `#ede9fe`, `#faf8ff`, `#4f46e5`, `#ddd6fe`
- Replace remaining `#1e3a8a` and `#6b7280` inline styles with existing tokens
- Replace gradient endpoints `#0d9488`, `#0e7490`, `#b91c1c`, `#b45309`, `#047857` with derived tokens
- Zahllauf-pill collapse: max 10 visible + toggle button in `renderCommissionHistoryEditor()`

**Plan 02-03: Typography scale** (M-effort, medium-risk)
- Add `--fs-sm`, `--fs-body`, `--fs-sub`, `--fs-heading` tokens to `:root`
- Apply across CSS rules — merge adjacent sizes, keep `.side-nav-section-label` at its current size (exception)

### Anti-Patterns to Avoid
- **Adding new hardcoded hex values** while fixing others — defeats the purpose of token migration
- **Wrapping pills in a separate HTML container** that isn't accessible to the existing `querySelectorAll('[data-remove-commission]')` — breaks the remove-pill event handlers
- **Changing `input type`** for `#smtpPassword` or `#erpnextApiSecret` (already `type="password"`) — no-op, don't touch what works
- **Modal refactor for settings overlay** in Phase 2 — out of scope, high risk, rated L effort

---

## Environment Availability

Step 2.6: SKIPPED — Phase 2 is pure CSS/HTML/JS changes with no external tool dependencies. Only `dashboard.html` is modified. No build tools, no package managers, no external services required.

---

## Validation Architecture

`workflow.nyquist_validation` key is absent from `.planning/config.json` — treated as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | None detected — no test files, no jest.config.*, no package.json |
| Config file | None |
| Quick run command | Manual browser check (no automated test runner) |
| Full suite command | Manual browser check |

This project has no automated test infrastructure. Verification is manual — open `http://localhost:8080` in a browser, visually confirm each fix. The verifier agent should use Playwright for automated visual checks where feasible.

### Phase Requirements → Test Map
| ID | Behavior | Test Type | Automated Command | File Exists? |
|----|----------|-----------|-------------------|-------------|
| BEAU-01 | Favicon appears in browser tab | smoke | Playwright: check no 404 on /favicon.ico | N/A |
| BEAU-02 | Login form enables password manager (in `<form>`) | manual | Browser DevTools: no "Password field not in form" warning | N/A |
| BEAU-03 | API keys hidden by default (type=password) | smoke | Playwright: check `input#goaffproAPIKey` type attribute | N/A |
| BEAU-04 | Beraterinnen-E-Mail label not red | smoke | Playwright: check computed color of `.recipient-advisor-label` | N/A |
| BEAU-05 | Date range stays on one line at 1440px | smoke | Playwright: screenshot of Auswertungen at 1440px viewport | N/A |
| BEAU-06 | Chart empty state shows centered text | manual | Visual check in browser before fetching data | N/A |
| BEAU-07 | Color picker min 44×44px | smoke | Playwright: check offsetWidth/offsetHeight of `#emailTextColor` | N/A |
| BEAU-08 | Max 10 pills visible, toggle shows rest | smoke | Playwright: mock 15 pills, count `.pill` elements visible | N/A |
| BEAU-09 | No hardcoded hex values in `:root`-excluded CSS | automated | `grep -E '#[0-9a-fA-F]{3,6}' dashboard.html` — count decreases | N/A |
| BEAU-10 | Typography uses ≤4 distinct font sizes | automated | `grep -oE 'font-size:\s*[0-9]+px' dashboard.html \| sort -u` | N/A |

### Wave 0 Gaps
None — no test framework needed. All verification is browser-based (manual + Playwright screenshots). The verifier agent can check attributes and computed styles programmatically.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Hardcoded hex throughout CSS | CSS custom properties (`:root` tokens) | Phase 2 completes the migration started in the original design |
| 8 font sizes (10px–26px) | 4-step scale via CSS tokens | Eliminates visual noise, improves hierarchy |
| All pills always visible | Progressive disclosure (10 + toggle) | Reduces cognitive load on settings page |
| `<input type="text">` for secrets | `<input type="password">` | Security hygiene |
| Browser native confirm() | (deferred) | Not in Phase 2 scope |

---

## Open Questions

1. **Favicon format and content**
   - What we know: No favicon exists; browser shows 404
   - What's unclear: Whether the Java backend serves static files from the `/ui/` directory at root `/favicon.ico` or only serves `dashboard.html` at a specific route
   - Recommendation: Use inline data URI `<link rel="icon">` in `<head>` — this works regardless of backend routing. If the backend doesn't serve the file at `/favicon.ico`, the data URI approach is the only reliable fix without backend changes.

2. **GoAffPro API-Key show/hide toggle**
   - What we know: Changing to `type="password"` hides the value; usability concern is that admins may need to copy the key
   - What's unclear: Whether a show/hide toggle (eye icon button) is in scope
   - Recommendation: Add a minimal show/hide toggle button next to each API key field. ~5 lines of JS per field. Low risk. Improves usability.

3. **Pill collapse: real-world pill count**
   - What we know: Audit observed ~50 pills. The sort function `sortCommissionsChronologically` orders them.
   - What's unclear: Whether showing the 10 most recent or 10 oldest is correct. Since pills are "Editierbare Zahlläufe (chronologisch)", the 10 most recent (newest-first) are most actionable.
   - Recommendation: Show 10 newest (tail of the chronologically sorted list), collapse the rest. The toggle shows older ones.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `src/main/resources/ui/dashboard.html` (3022 lines) — all CSS, HTML, and JS findings are from this file
- `.planning/ui-reviews/UI-REVIEW.md` — static audit findings
- `.planning/ui-reviews/UX-LIVE-REVIEW.md` — live audit findings with screenshots

### Secondary (MEDIUM confidence)
- WCAG 2.5.5 minimum target size 44×44px — well-established accessibility standard

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Issue inventory: HIGH — all from direct code inspection
- Fix approaches: HIGH — all are standard HTML/CSS/JS patterns
- Risk ratings: MEDIUM — based on code reading; actual regression risk depends on browser rendering
- Typography scale merge: MEDIUM — the 10px/11px sidebar label behavior post-merge requires visual confirmation

**Research date:** 2026-04-06
**Valid until:** Stable — dashboard.html changes only when plans execute. Re-read the file before each plan is written.
