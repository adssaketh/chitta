# Changelog

## Phase 0: audit (13 Nov 2026)
- Added `docs/phase-0-audit.md`: feature inventory against master section 7, conflicts, Drift to Room map, risks, recommendations for the two open decisions.
- Added `DECISIONS.md` with decisions D1 to D11, all approved.
- No code. No build.

## Phase 1: design check (13 Nov 2026)
- Added docs/phase-1-design-check.md: placement map and 22 Ink screens.
- Added D12 to D14 to DECISIONS.md.
- No code. No build.

## Phase 2: design system (13 Nov 2026) - 0.2.0-phase2
- New project: `:app`, `:core:design`, `:core:money`.
- `:core:money`: `Paise`, Indian grouping, safe to spend, per day, range, ideal pace, pace status, forecast, limit day, budget ring (50 ticks), gauge segments. 25 unit tests using the master section 12 examples.
- `:core:design`: Paper/Ink/System themes, optional accent, hide-amounts; colour, type and size tokens; bundled fonts; 39 Phosphor icons; components (amounts, stamps, buttons, field, keypad, ledger rows, charts, nav, top bar, undo bar, empty/error/loading states).
- Component gallery (debug only), 26 screenshot tests (Paper and Ink, 200% text), contrast and type-rule tests.
- GitHub Actions workflow `build-apk.yml`; `.gitignore`.
- Decisions D15 to D25. Light warning-text and mark-grey colours changed from the PDF for contrast (D19).
- No feature screens.
