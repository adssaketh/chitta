# Changelog

## Phase 8d: fund prices, SIP estimates, Net worth lines (14 Nov 2026) - on branch `phase-8d-prices`
- `:core:intake`: `AmfiNav` reads AMFI's NAV file by column name (refused if a column is missing or under 1,000 schemes); `FundMatchIndex` and `FundPrices` match funds to schemes by code, ISIN, then name, and offer a pick list when unsure. A trimmed real copy of the file is a test resource. 13 new tests.
- `:core:domain`: `Sip` (instalment dates, units, crediting, reset); fund value and gain now include the SIP estimate; `NetWorth` gains Mutual funds and NPS lines (today only, each with a switch). 11 new tests.
- Schema 6 with tested migration: SIP columns on `mutual_fund`. Fetched prices and SIP estimates are written by their own queries.
- App: `FundPriceService` (fetch, match, price, credit SIP, saved copy of the file), a daily WorkManager job and a refresh when the app opens; Mutual funds screen gets Refresh prices, Update prices daily, Count in Net worth, a scheme picker, SIP fields in the edit sheet, and "estimated" labels; NPS tab gets its Net worth switch; Net worth tab shows the two lines with their price dates.
- A statement import keeps the SIP and the scheme link and restarts the estimate; it then links and prices the new funds.
- Fix: Undo after editing an NPS scheme restored nothing and deleted the scheme.
- Decisions D118 to D123.
- Written without a local Android build (no Android SDK here); the plain-Kotlin modules were run locally (316 domain and 78 intake tests pass); CI compiles and tests the rest.

## Phase 8c: Mutual funds screen (13 Nov 2026) - on branch `phase-8c-mutual-funds`
- Scope: the screen first, then (files arrived) the statement import and an NPS tab. Still not built: AMFI price fetch, Zerodha Console reader, SIP estimates, the Net worth line.
- `:core:model`: `MutualFund`. `:core:domain`: `MutualFunds` (value = units x price, gain, totals, STALE after 35 days, ISIN shape) with 12 tests on hand-worked figures.
- Database schema 5 with tested migration from 4: `mutual_fund` (with folio and NPS group) and `nps_account` tables. `AssetsRepository` gets save, observe, delete with Undo.
- `:core:intake`: `CasSummary` reads a CAMS / KFintech / MFCentral "Consolidated Account Summary" PDF's text (holdings, cost, units, price, value; refused if the rows do not add to the statement's Total) and `CasImport` plans the import (only statement-sourced funds change; a same-named hand-typed fund is never counted twice). 7 tests on invented text.
- `:core:intake`: `NpsStatement` reads a CRA NPS Transaction Statement's text (summary line and Investment Details table: units, price, value, contributions; refused if values do not add to the statement's totals) and `NpsImport` plans the import (account created or updated, only statement schemes replaced). 7 tests on invented text. One file picker serves both statement types.
- NPS: own tab on the Mutual funds screen, never added into mutual funds. `NpsAccount`, `Nps` totals (gain for the whole account: value minus net contributions), 4 tests.
- App: PDF password asked only when needed, used once, never stored. Statement preview shows what changes before anything is saved, with Undo.
- App: Accounts > Mutual funds (PDF p.26: value, price date, gain with how many funds it covers, one row per fund with its source and STALE), fund detail, add or edit by hand, delete with Undo, Import holdings sheet (PDF p.27; only "By hand" works).
- Decisions D108 to D116.
- Written without a local build (no Android SDK here); CI compiles and runs every test.

## Phase 8b: fixed deposits, instalment loans in Net worth, card dues from SMS (13 Nov 2026) - on branch `phase-8b-deposits-dues`
- `:core:domain`: `FixedDeposits` (value today, at maturity, accrued interest, in paise; the bank's stated maturity amount wins), `CardDues` (owed per card, matching a card to an account, no double count with entries), `Cards.figures` now takes card messages, `NetWorth` takes deposits (off by default) and instalment-loan balances (on by default). 29 new tests.
- `:core:intake`: `FdSms`, the "FD booked" message reader (6 tests, including the Ping contract's FD sample).
- `:core:database`: `AssetsRepository` and DAO methods for deposits and card dues. No schema change (the tables date from schema 1). 3 new tests.
- App: Accounts has Fixed deposits (list, detail, add/edit, close, delete with Undo), two Net worth switches, and Cards from bank messages (owed per card, set what is owed). Ping intake adds a fully read FD and updates card dues in one go, under one Undo item; a message that cannot be read in full stays parked and nothing is guessed. The card cockpit and card reminders count the messages. FD maturity reminders 7 days ahead, the day before and on the day.
- Net worth tab shows the deposits row when counted and says what instalment loans mean.
- Decisions D100 to D107.

## Phase 8a, slice 3c: donut chart (13 Nov 2026) - on branch `phase-8a-donut`
- Optional flat donut on Insights > By category. `Donut` in `:core:domain` with 4 tests. D96. App links (3b) merged to `main`.

## Phase 8a, slice 3b: app links (13 Nov 2026) - on branch `phase-8a-links`
- `chitta://add?...` opens Quick Add pre-filled (never saves); notes for ignored parts. `AppLinks` in `:core:domain` with 4 tests. D93 to D95.

## Phase 8a: Must items, slices 1 and 2 (13 Nov 2026) - on branch `phase-8a-must`, CI green
- `:core:domain`: `Splits`, `Rules`, `Duplicates`, `Cards` with 28 tests (`Phase8aTest`).
- Database schema 4 with tested migration from 3: `splitGroupId` on entries, card fields on accounts, `entry_rule` table. History snapshots keep the split group.
- Screens: Ledger marks "Split" and "Possible duplicate"; Accounts > Credit card details (limit, statement and due day, minimum due, reminder days, outstanding, available, utilisation gauge); Settings > Rules (new, edit, turn off, delete with Undo, apply to history with a preview); rules suggest a category in Quick Add; Split on Entry detail with a live remainder.
- Decisions D85 to D92. Not yet: card reminder notification, rules on imports, app links, notification scanning, background receiver, icon search, donut.
- Written without a local build; CI compiled and ran every test (one smart-cast error found and fixed on the way).

## Phase 7b: Ping and share intake, PhonePe and other UPI apps (13 Nov 2026) - 0.8.0-phase7b - on `main`
- Ping: the share receiver (JSON text, JSON file, or a payment screenshot, statement PDF or CSV from any app), the inbox provider (caller must be `com.ping.sms` with the pinned certificate), and a date-range file picker. Messages Chitta is sure about are saved at once with Undo; the rest wait in a review list with a count in Settings.
- Review list: Ready / needs a look, change account or category, one category for every row without one, make an account from the bank's name, remove a message for good, View SMS in Ping (also on an entry made from a Ping SMS), Ping logos for senders (kept 24 hours).
- De-duplication: by message id and by the day-plus-text key already used by the old app; a payment that is already in the book as the other half (bank SMS or UPI page) only gets its details added to that entry's note.
- UPI payments: PhonePe screenshot, statement PDF and statement CSV, plus the old app's Google Pay, Paytm, BHIM, CRED and MobiKwik screenshot readers. Text is read on the phone (ML Kit 16.0.1 for pictures, pdfbox-android 2.0.27.0 for PDFs). A payment page is never saved without the review list.
- Known accounts are loaded from a file the person picks, so no account digits are published with the code.
- New pure module `:core:intake` (SMS and instrument parsers, Ping export reader, auto-save rule, planner, UPI parser, Ping contract) with 43 unit tests that use the contract's golden vectors and invented UPI text. No database change.
- Decisions D75 to D84. Moved to Phase 8: app links, notification scanning, the background receiver and fixed-deposit messages.

## Phase 7a: data safety (13 Nov 2026) - 0.7.0-phase7a - on `main`
- App lock (fingerprint, face or phone PIN) with timeout, and hide in recents. Locked screen composes nothing from the book.
- Data Recovery: automatic daily restore points, manual points, restore with a safety point first.
- Backup file export and import (full database copy, checked before use).
- CSV import: column mapping, date formats, saved templates, review list, undo. CSV export by account and range.
- Settings screen under More: Security, Backup and restore, Import and export, Data (delete all).
- Pure logic in `:core:domain` (Csv, CsvImport, CsvExport, DataSafety) with unit tests; database file helpers in `:core:database`. Android code is checked by CI.
- Decisions D67 to D74. Firebase work moved to Phase 9.

## Phase 6: insights and home (13 Nov 2026) - 0.6.0-phase6 - on `main`
- Home: Safe to spend (or the cash check when it is lower, or when you force it), budget ring, per-day, range, spent with a pace stamp, month rule, "How this is worked out" sheet, Coming up. Optional sections: Budgets, Balances, Recent entries, Net worth, Goals, Loans, Spending, Heatmap. Only Budgets and Recent are on to start.
- Insights: Current (range, accounts, Spent and Saved against the period before, totals, cash flow, category bars with drill-down, biggest changes), History (days, weeks, months, years; per period or running total; average of finished periods) and Net worth over 12 months.
- Customise: Home sections (on/off, order, accounts, days), four swappable bottom tabs plus "Opens first", More shortcut order, accounts for spending, always-show cash check, first day of the week. Press and hold the tab bar to open it.
- Device settings live in `UiPrefs` (D57). No database change.
- `:core:money` gains HeroFigures and CashCheck; `:core:domain` gains Insights, History, Heatmap, NetWorth, HomeFigures and Layouts. 170 pure tests pass locally; the Android code is checked by CI.
- Decisions D56 to D66.
- Not in this phase: interactive pie (D61), Month in review (Phase 8), fixed deposits, funds and other assets in net worth (Phase 8), card dues (Phase 7).

## Phase 5: planning (13 Nov 2026) - 0.5.0-phase5 - branch `phase-5-planning`
- Plan tab with five views: Budgets, Goals, Loans, Bills (upcoming and subscriptions) and Calendar.
- Budgets: pace graph, even-pace line, forecast from day 7, category goals (press and hold to edit), breakdown, earlier periods, create/edit with accounts, left-out categories and lent/borrowed switch.
- Goals: progress, monthly need, contributions. Loans: long-term loans (repay, lend more), one-time lent/borrowed (collect all or part; a part makes a long-term loan), instalment loans with a tickable schedule.
- Bills: overdue first, scheduled totals page, pay (also a different amount or account), skip, set a missing amount, edit, delete; Auto-pay overdue and paid-date setting. Bill calendar: tap a day to pay, skip or undo.
- "Repeat" row on entry detail. Reminders (WorkManager 2.12.0): due bills the day before and on the day, 9:00 am, and a log reminder at 9:00 pm; permission-denied note with a link to Android settings.
- Database v3 (adds `anchorDay`, migration tested from the committed v2 schema). New `PlanRepository`.
- `:core:domain` gains Periods, Bills, Budgets, Goals, Loans, BillCalendar and Reminders. 71 new unit tests (110 in domain).
- Decisions D45 to D55. D45 corrects D35. Items to verify on your phone are marked in DECISIONS.md.
- Not in this phase: fixed deposits (Phase 8), shared loans (Phase 7), rollover budgets and sinking funds (Phase 8), card-due reminders (Phase 8), Home and Safe to spend (Phase 6).

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

- Phase 8a: category icon search. Category menu has "Choose an icon" with a search box over 51 icons; the Ledger draws the category's icon instead of the plain tag. Unit tests for the search.

- Phase 8a: rules suggest categories on Ping, UPI and CSV import review rows (suggest only). Quick Add has a Split button.

- Phase 8a: Settings > Automation with a background add receiver that needs your secret and offers Undo. Unit tests. Notification scanning is not built (D99).
