# Changelog

## Phase 4: core loop (13 Nov 2026) - 0.4.0-phase4
- New `:core:domain`: exact keypad calculator, balances, totals, Quick Add rules, transfers, balance corrections, ledger search and grouping, bulk edit, delete impact, edit diff, exchange conversion, category merge/convert/move. 39 unit tests.
- Database v2 (migration tested against the committed v1 schema): original amount/currency/rate on entries, archived flags, pinned entries, entry history, exchange rates and overrides.
- Hilt added. Screens: first-run chooser, Ledger (tabs, search, filters, multi-select, bulk edit, copy, share totals), Quick Add (expense, income, transfer, pinned strip, long-press + menu, currency and rate), entry detail with history and restore, Accounts (balances, correct balance, rates), Categories, import flow with preview and 30-day Undo import.
- Every save, edit and delete shows Undo for 10 seconds.
- Decisions D35 to D44. Two are assumptions to verify on your real backup (D35, D36). Navigation Compose skipped (D37).
- Home, Plan and Insights show a plain "not built yet" line.

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

## Phase 3: data layer (13 Nov 2026) - branch `phase-3-data-layer`
- `:core:model` (new, pure Kotlin): enums with explicit stored codes, and the `Book` data classes the importer produces and the database stores.
- `:core:money`: `paiseFromDriftDouble` and `paiseFromDecimal` (shortest decimal, then HALF_EVEN), with 7 tests. 32 money tests in total.
- `:core:importer` (new, pure Kotlin): reads a Paddhu backup (schema 33 to 48) through a `BackupSource`. Reads all ten Drift tables and the settings blob (loan terms, fixed deposits, card dues). Returns a `Report` (counts, rounded amounts, per-account conversion check, items not carried over, issues). Refuses older, newer or non-Paddhu files. 25 tests on a fixture built from the real v48 column layout.
- `:core:database` (new, Android): Room version 1 with 13 tables, DAOs, `BookRepository` (writes a book in one transaction, refuses a non-empty database, reads it back), account, category and transaction repositories, `AndroidBackupSource` (read-only). 8 Robolectric tests, schema exported to `core/database/schemas/1.json`.
- Checked on a real backup (101 entries, 3 accounts): schema 48, all INR with 2 decimals, dates are epoch seconds, every amount is whole paise, every `type` is NULL.
- CI runs the new tests. Decisions D26 to D34.
- No screens. No importer screen yet (Phase 4).
