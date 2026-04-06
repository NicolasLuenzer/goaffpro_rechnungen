---
phase: 2
slug: versch-nerung
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-06
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None — no test runner detected; verification is browser-based |
| **Config file** | none |
| **Quick run command** | Manual browser check at `http://localhost:8080` |
| **Full suite command** | Manual browser check + Playwright attribute/style checks |
| **Estimated runtime** | ~5 minutes manual |

---

## Sampling Rate

- **After every task commit:** Visual check in browser at `http://localhost:8080`
- **After every plan wave:** Full browser check + grep-based code checks
- **Before `/gsd:verify-work`:** All Playwright smoke checks must pass
- **Max feedback latency:** ~5 minutes

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| BEAU-01 | 01 | 1 | Favicon | smoke | Playwright: no 404 on favicon | ❌ N/A | ⬜ pending |
| BEAU-02 | 01 | 1 | Login `<form>` wrap | manual | DevTools: no "Password field not in form" warning | ❌ N/A | ⬜ pending |
| BEAU-03 | 01 | 1 | API keys type=password | smoke | Playwright: check `input#goaffproAPIKey` type attr | ❌ N/A | ⬜ pending |
| BEAU-04 | 01 | 1 | Beraterinnen-E-Mail label color | smoke | Playwright: computed color of `.recipient-advisor-label` not red | ❌ N/A | ⬜ pending |
| BEAU-05 | 01 | 1 | Date range one-line layout | smoke | Playwright: screenshot Auswertungen at 1440px | ❌ N/A | ⬜ pending |
| BEAU-06 | 01 | 1 | Chart empty state text | manual | Visual check before fetching data | ❌ N/A | ⬜ pending |
| BEAU-07 | 01 | 1 | Color picker min 44×44px | smoke | Playwright: offsetWidth/offsetHeight of `#emailTextColor` ≥ 44 | ❌ N/A | ⬜ pending |
| BEAU-08 | 02 | 2 | Max 10 pills + toggle | smoke | Playwright: count visible `.pill` elements ≤ 10 | ❌ N/A | ⬜ pending |
| BEAU-09 | 02 | 2 | Hardcoded hex values reduced | automated | `grep -oE '#[0-9a-fA-F]{3,6}' dashboard.html \| wc -l` — count vs baseline | ❌ N/A | ⬜ pending |
| BEAU-10 | 03 | 3 | Typography ≤4 distinct sizes | automated | `grep -oE 'font-size:\s*[0-9]+px' dashboard.html \| sort -u \| wc -l` ≤ 4 | ❌ N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

None — no test framework needed. All verification is browser-based (manual + Playwright attribute/style checks). No stubs or fixtures required.

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Login `<form>` enables password manager | BEAU-02 | Browser DevTools warning check | Open DevTools console, confirm no "Password field not contained in a form" warning |
| Chart empty state shows text | BEAU-06 | Visual only, no Playwright assertion needed | Load Auswertungen tab without selecting date range; confirm descriptive text visible |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
