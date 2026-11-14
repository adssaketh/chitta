# Changelog

## Home fix (14 Nov 2026) - on `main`
- With the cash check leading, Home keeps the budget ring, per day, range and month rule under it, headed with the budget name (D187). "1 day" instead of "1 days".

## Release rename to Paddhu Pusthakam 6.0.0 (14 Nov 2026) - on `main`
- The app is now Paddhu Pusthakam: application ID `com.mansaketh.paddhupusthakam` (the old Flutter app's ID), label, launcher name, lock screen title, messages and backup file names. Version name 6.0.0, version code 600 (old app: 5.4.15, code 449). The release is signed with the Paddhu keystore, so it installs over the old app as an update.
- Old launcher icon files (adaptive and monochrome) copied from the Paddhu repo.
- New Drive backups are named `paddhu-pusthakam-<ms>.db`, local backup files `paddhu-pusthakam-backup-<date>.db`, CSV exports `paddhu-pusthakam-entries-<date>.csv`. Older `chitta-` Drive backups still list and restore.
- Ping's names already matched (`com.mansaketh.paddhupusthakam.ShareReceiverActivity`, inbox authority `com.mansaketh.paddhupusthakam.pinginbox`), so Ping needs no change.
- Not changed on purpose: Kotlin package `com.mansaketh.chitta`, `chitta://` links, the background-add action name, database and preference file names, repo name.

## Phase 9a: Google sign-in and Drive backup (14 Nov 2026) - on `main`

- Settings > Backup and restore > **Google Drive backup**: sign in with Google (Credential Manager), then **Back up now**, an automatic daily backup (switch, on by default once signed in), the list of backups in Drive, Restore (same "Replace your book?" confirmation and restore point as a backup file) and Delete one from Drive. Sign out forgets the account on this phone and leaves Drive alone.
- Backups go to the app's hidden Drive folder only (scope `drive.appdata`), as `chitta-<time>.db`. The newest 20 are kept; older ones are removed only after the new one has arrived with the right size. Files in that folder that are not Chitta backups are listed, never deleted, and can be tried as a Paddhu book through the existing preview.
- No Drive library: five small HTTPS calls. New libraries: Credential Manager 1.6.0, googleid 1.2.1, play-services-auth 22.0.0 (all latest stable).
- `:core:domain`: `CloudBackups` (naming, keep 20, due once a day, "days since"). 13 new tests. App: `DriveJson` tests (5).
- Not in 9a: cross-device sync, receipt photos in backups, the household (9b), the release rename (9c).
- Decisions D171 to D180. Nothing has run on a phone. Sign-in works only on the release-signed APK once `com.mansaketh.chitta` is registered in Firebase project `cashew-saketh`.

## Phase 8j: widgets, Quick Settings tile and shortcuts (14 Nov 2026) - approved, on `main`

- Two home-screen widgets (Glance 1.2.0, latest stable): **Quick add** (Expense and Income buttons, open the add sheet through the existing `chitta://add?type=` link; nothing is saved) and **Safe to spend** (the figure Home leads with, per day and days left, and spent today).
- The spend widget masks every amount when hide-amounts is on or the lock is on. Hide-amounts is now remembered across restarts so the widget can read it (D166).
- Quick Settings tile "Add expense". Launcher shortcuts: Add expense, Add income, Ledger (`chitta://open?tab=ledger`, ignored if the Ledger tab was swapped out of the bar).
- `:core:domain`: `HomeFigures.top` (the top-of-Home steps, now shared by Home and the widget), `WidgetFigures` and `WidgetLines` (the widget's numbers and words, masked or not). 12 new tests worked by hand.
- Decisions D165 to D170. Nothing has run on a phone.

## Phase 8i: gold, EPF and PPF (14 Nov 2026) - on branch `phase-8i-assets`
- Accounts > "Gold, EPF and PPF": add assets by name and type what each is worth with a date. Every update is kept as history; Update value, History, Rename or edit note, Delete (10-second Undo each).
- Net worth: Gold, EPF and PPF lines, each with its own switch (on by default); the 12-month chart counts the value you had typed by each month. STALE after 35 days (EPF, PPF) or 60 days (gold).
- `ManualAssets` in `:core:domain`; `ManualAsset`, `AssetValue`, `AssetKind` in `:core:model`. Schema 8 with tested migration: `manual_asset` and `asset_value`.
- Decisions D158 to D164. Tests: 11 new in `Phase8iTest` worked by hand (416 in `:core:domain` pass locally); 4 new database and migration tests that CI runs (they need Robolectric).
- Written without a local Android build; CI compiles the app. No new libraries.

## Phase 8h: receipt capture (14 Nov 2026) - on branch `phase-8h-receipts`
- Entry detail gets a "Receipt" row: take a photo (phone camera app) or choose one from the gallery; one photo per entry; view it full screen with pinch to zoom; read the text again; remove it (10-second Undo).
- `ReceiptParser` in `:core:intake` (plain Kotlin) reads the amount, date and shop name from the photo's text. ML Kit reads the text on the phone (already in the app). The result is only suggested: each of amount, date and title has its own "Use this" button.
- Photos live in the app's private storage, one file per entry, outside backups and restore points. No database change. Photos of deleted entries are removed after 30 days; Delete everything removes all photos.
- Decisions D151 to D157. Tests: 17 new in `ReceiptParserTest` worked by hand; 107 in `:core:intake` pass locally.
- Written without a local Android build; CI compiles the app. No new libraries.

## Phase 8g: subscription detection (14 Nov 2026) - on `main`
- `Subscriptions` in `:core:domain`: finds payees charged 3 times in a row on a weekly, monthly, every-3-months or yearly rhythm, amounts within 10%, still live (not long overdue), and not already a repeating bill or dismissed.
- Plan > Bills: one row "N possible subscriptions" opens a sheet; tap one to add it as a subscription (first due date today or later, reminders on) or say it is not one (remembered, with Undo; "Not subscriptions (n)" brings it back).
- No database change; dismissals are kept in the app settings on the phone (sync in Phase 9).
- Decisions D144 to D150. Tests: 18 new in `Phase8gTest` worked by hand (monthly, quarterly, yearly, weekly, 31st anchor, 10% edge, cancelled, tracked, dismissed); 405 in `:core:domain` pass locally.
- Written without a local Android build; CI compiles the app.

## Phase 8f: month in review and smart search (14 Nov 2026) - on `main`
- Month in review: `MonthReviews` in `:core:domain` (totals as the All Spending Summary, the month before for comparison, top 3 categories up and down by at least ₹500, budgets hit or missed). Insights gets a Review tab with arrows between finished months; Home shows "<month> in review" on the 1st to the 3rd and opens the same screen. No notification, no badges.
- Smart search in the Ledger: `SearchSyntax` reads `amt`, `cat`, `acc`, `type`, `note`, `last`, `from`, `to`, with a minus to leave matches out. Plain words search as before. Anything that cannot be used is listed as "Not used: ..." and the rest still applies. A one-line hint shows when the box is open and empty.
- No database change.
- Decisions D135 to D143. Tests: 50 new in `Phase8fTest` (parser, applying filters to entries, review figures worked by hand); 387 in `:core:domain` pass locally.
- Written without a local Android build; CI compiles the app.

## Phase 8e: rollover budgets, sinking funds, Zerodha ledger (14 Nov 2026) - on `main`
- Rollover: a per-budget switch (repeating spending budgets only, off by default) carries unspent or overspent money into the next period. `Rollover` in `:core:domain`; `BudgetSummary.carry`; the budget screen says what carried in; the earlier-periods list shows each period's own limit.
- Sinking funds: Plan > Sinking funds. A total, a start, a due date and an optional category that pays the bill. `SinkingFunds` spreads the total over the calendar months (shares add up to the paisa), reserves this month's share off Safe to spend and the cash check, and adds back spending the fund already set aside. Home's "How this is worked out" shows both lines.
- Schema 7 with tested migration: `budget.rollover` and the `sinking_fund` table.
- Zerodha Console ledger (.xlsx or .csv): `XlsxReader` (no library) and `ZerodhaLedger` in `:core:intake`. The CSV import now accepts it: opening balance, every row's running balance and the closing balance are checked (refused with the row number if not); the review list then works as for any CSV, with the opening balance as the first row.
- Decisions D124 to D134. Tests: 8 rollover, 13 sinking funds, 9 ledger, 3 xlsx (389 in the plain-Kotlin modules I could run).
- Written without a local Android build; CI compiles and tests the rest.

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
