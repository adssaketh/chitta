# Phase 0 audit: Paddhu Pusthakam source vs the master prompt

Date: 13 Nov 2026. No code was written. Nothing was built or run.

## 1. What I read, and what I did not

**Read:** `budget/lib/database/tables.dart` (tables, enums, converters, migrations list), `budget/firestore.rules`, `budget/docs/HOUSEHOLD.md`, `PADDHU_DB_SETUP.md`, `contract/ping-paddhu/SPEC.md`, `budget/pubspec.yaml`, `AndroidManifest.xml`, `build.gradle`, `.github/workflows/build-apk.yml`, the header comments of every struct file for household, Ping, known accounts, card dues, fixed deposits, loans and settings merge, `autoSmsPolicy.dart` in full, and both design PDFs (text of every page).

**Not done (say so rather than guess):**
- I did not run any Dart test or the app. No Flutter toolchain here.
- I did not read the bodies of most pages and parsers (PhonePe, SMS parser, billSplitter, sync client). Where a row below says "by file name", that is all I checked.
- I have not opened a real Paddhu backup file. Date storage format (Drift's default is integer seconds, but check) must be confirmed on one.
- I have not looked at the PDF page images, only their text.

**Source snapshot:** repo `adssaketh/Paddhu-Pusthakam`, commit `d1847f8` ("Ping integration (6c)"), app version 5.4.15+449, Drift schema version **48**, Flutter 3.19.6 in CI, minSdk 23, compileSdk 34, applicationId `com.mansaketh.paddhupusthakam`.

## 2. Conflicts to decide (master prompt wins unless you say otherwise)

| # | What disagrees | What I will follow |
|---|---|---|
| C1 | Master section 9 says lock = BiometricPrompt with device-credential fallback (pattern/PIN of the phone). Design PDF p.6 draws an in-app PIN with "tries left" and "Forgot PIN?". | Master. I will redraw p.6 around the system prompt and drop the in-app PIN. **Please confirm.** |
| C2 | Master section 7 lists pinned transactions, tags, Cashew "Intelligence" and "Data Recovery" as Cashew features to keep. None exists in the Paddhu source (see section 3). | Master says add/confirm; I list them as "not found" and treat them as new work or drop. See Q4. |
| C3 | Master section 2 lists the only allowed network calls. The source also uses the **Gmail API** (`autoTransactionsPageEmail.dart`) and **Drive file scope for attachments** (`uploadAttachment.dart`). | Master. Both need your decision (Q5). |
| C4 | Master section 7 says accounts and categories can be archived. `Wallets` and `Categories` have no archive column (only `Budgets` and `Objectives` do). | Treat as new work. Low priority. |
| C5 | `PADDHU_DB_SETUP.md` describes `users/{uid}` with sub-collections for accounts, settings, loans, deposits, pending and learned corrections. The code only reads and writes the `users/{uid}` profile document (householdId, displayName, inviteCode; `householdService.dart`). I found none of the sub-collections, and no `balances` collection call in the Dart code (only the rule helpers). Settings merge goes through Drive sync, and the Ping queue is files on the phone. | Rebuild what the code does, not what the doc says. Verify in Phase 7. |

Numbers check: I recomputed every worked example in PDF pages 3 and 4 against master section 12. All match (27,502; 1,528; 12 days; 24,000; 66,300; 1,00,752; 4,83,539; 15,171; 3,140; split 613.34 + 613.33 + 613.33). They become the unit-test vectors.

## 3. Feature inventory against master section 7

Evidence: **file** = I read it; **name** = I only saw a matching file, class or setting; **none** = searched, not found.

### Confirmed (present in the source)
| Item | Evidence |
|---|---|
| Accounts: name, colour, icon, currency, order, per-account decimals, ownerUid | file (`Wallets`) |
| Transfers as paired entries | file (`pairedTransactionFk`) |
| Balance correction (as a budget filter) | file (`includeBalanceCorrection`) |
| Exchange rates and overrides | name (`exchangeRatesPage`, `currencyFunctions`) |
| Categories with subcategories, colour, icon, emoji, income flag | file |
| Transaction types upcoming, subscription, repetitive, lent (credit), borrowed (debt); paid, skipped, next-occurrence flag | file |
| Exclude from budget, link to goal or loan | file (`budgetFksExclude`, `objectiveFk`, `objectiveLoanFk`) |
| Titles memory (with exact-match flag) | file (`AssociatedTitles`) |
| Budgets: filters, category limits, pinned, archived, absolute limit, repeat period | file |
| Goals and long-term loans | file (`Objectives`, type goal or loan, `income` = lent) |
| Heatmap, customisable home, upcoming and subscriptions pages | name |
| Biometric lock | name (`local_auth`, `USE_BIOMETRIC`) |
| Google sign-in, Drive app-data backup, 20 backups kept, auto backup, device sync, `.sql`/`.sqlite` file import and export | file/name (`accountAndBackup`, `syncClient`, `importDB`, `exportDB`, `backupLimit: 20`) |
| CSV import and export | name |
| App links | name (`app_links`) |
| Notification scanning with templates | name (`notification_listener_service`, `ScannerTemplates` table) |
| Reminders and due notifications | name |
| Home widgets (Plus, Transfer, Net worth, Net worth plus) and launcher shortcuts | name (Kotlin providers, `quick_actions`) |
| Activity log | name (`activityPage`, `DeleteLogs`) |

### Fork-only features (master section 7 list), checked
| Item | Evidence |
|---|---|
| Household: sign-in, owner/member, 8-char invite, up to 8, hand-over, remove | file (`firestore.rules`, HOUSEHOLD.md, `household*.dart`) |
| Per-entry sharing (`isPrivate`, `householdSyncedAt`), share-existing choice | file |
| Placeholder account per member (`hh_<uid>`), balances hidden unless owner allows | file (`householdWallets.dart`) |
| Everyone / Mine, Who spent how much, diagnostics | name |
| Shared loans and fixed deposits (`sharedItems`, visibleTo up to 7, membersCanEdit) | file |
| Fixed deposits: model, quarterly compounding, "FD booked" SMS reader | file (header), 358 lines |
| Credit-card dues from SMS; payment SMS not saved | file (header) |
| Instalment loans: reducing balance, EMI, 18% GST on interest and fee | file (header), 266 lines |
| Ping: share sheet, auto-send inbox, date-range file, de-dup, auto-save policy, View SMS in Ping, logos (24 h cache) | file (SPEC, `autoSmsPolicy`) |
| Known accounts (one wallet per bank, cards combined) | file (hard-coded in `knownAccounts.dart`) |
| PhonePe import: screenshot (ML Kit), PDF, CSV | name |
| Everything unlocked | file (`featureGates.dart`) |
| Settings merge: newest wins, loans/deposits item by item, device-only keys never synced | file |
| Bill splitter | name (`billSplitter.dart`, ~1,300 lines, own `SplitPerson` model) |

### Not found in the source
Pinned transactions (no setting, table or string); tags (only commented-out `labelFks`); Cashew "Intelligence" add-with-AI; Data Recovery restore points; rollover, sinking funds, rules engine, split transactions (the splitter page is people-splitting, not category-splitting), credit limit and utilisation, mutual funds, hide-amounts mode, edit history (only delete logs), account and category archive, an Android broadcast receiver for adding transactions (the manifest only has notification receivers), per-account credit-card statement cycle. The ones in section 8 were always new work. The ones in section 7 (pinned, tags, Intelligence, Data Recovery, broadcast receiver, card cycle) are a gap between the master list and this build; confirm in Q4.

### In the source but not in section 7 (added to the list)
1. **Shared budgets through Firestore** (`shareBudget.dart`, `Budgets.shared*` columns, `Transactions.sharedKey`, `transactionOwnerEmail`, `sharedStatus`): an upstream Cashew feature that predates the household. Q5.
2. **Gmail email scanning** (`autoTransactionsPageEmail`, `addEmailTemplate`, Gmail API scope). Q5.
3. **Scanner templates table** (`ScannerTemplates`): rules for notification/email parsing. Folds into the rules engine.
4. **Attachments uploaded to Drive** (`uploadAttachment.dart`, `drive.file` scope). Q5.
5. **Ping review queue and inbox are files** on the phone (`files/ping_inbox/`, `files/ping_review/`), not database rows and not in a backup. Migration note.
6. **Confetti** on goals (`objectivePage`, `confetti` package). Banned by section 4; drop.
7. Upstream extras seen by name only: onboarding page, debug page, detailed changelog page, rating popup. Not carried over unless you say.

## 4. Drift to Room map

All ids are `TEXT` uuids; keep them as `TEXT` primary keys in Room so Firestore ids, Ping notes and paired-transaction links survive.

| Drift table | Room entity | Notes |
|---|---|---|
| `Wallets` | `account` | `amount` columns elsewhere are `REAL`; see money below. `currencyFormat` kept as text. `homePageWidgetDisplay` (JSON of enum ordinals) becomes a TypeConverter. New nullable columns later: credit limit, statement and due dates, "for spending" flag. |
| `Transactions` | `transaction` | `amount REAL` to `Long` paise plus currency code plus stored rate. `type`, `reoccurrence` are enum **ordinals**; map explicitly, never by name. `budgetFksExclude` JSON list to a join table or converter. Keep `pairedTransactionFk`, `objectiveFk`, `objectiveLoanFk`, `ownerUid`, `isPrivate`, `householdSyncedAt`, `methodAdded`. Upstream `shared*` columns: drop if Q5 says drop. |
| `Categories` | `category` | `mainCategoryPk` null = main, set = subcategory. |
| `CategoryBudgetLimits` | `category_budget_limit` | `amount` to paise. |
| `AssociatedTitles` | `title_memory` | Keep order and `isExactMatch`. |
| `Budgets` | `budget` | Five JSON string-list columns (wallets, categories, exclude, member filters, shared members) and one enum-ordinal list (`budgetTransactionFilters`). Use join tables for wallets and categories; converter for the rest. |
| `Objectives` | `objective` | `type` goal or loan; `income` means lent when loan. |
| `ScannerTemplates` | `scan_template` | Feeds the rules engine, or dropped with notification scanning (Q5). |
| `DeleteLogs` | `activity_log` | Becomes part of edit history. |
| `AppSettings` (one JSON blob) | `setting` rows plus `loan_terms`, `fixed_deposit`, `card_due` tables | The blob holds `loanTerms`, `fixedDeposits`, `cardDues`, `fdReminderCount`, `settingsModified`, household keys. These are the fork's data; without parsing the blob, loans, deposits and dues are lost. Device-only keys are not imported. |
| (none) | `transaction_version`, `transaction_split`, `rule`, `sinking_fund`, `mf_holding`, ... | New in later phases. |

**Money conversion (the main migration risk).** Drift stores `amount` as `REAL` (a double). Plan: read each value, convert with `BigDecimal.valueOf(double)` (shortest decimal form, not the binary expansion), scale to the account's decimals with HALF_EVEN, store the integer. The dry run flags any value that moves by more than half a minor unit and compares every balance and total with Paddhu's own figures. Rounding rule is part of Q2.

**Dates.** No `build.yaml`, so Drift probably stores `DateTime` as integer seconds. Confirm on a sample backup before writing the importer.

**Schema range.** `drift_schemas/` holds v33 to v48. The importer should read `PRAGMA user_version` and accept 33 to 48 only; older files get a clear "update Paddhu first" message. Q3.

## 5. Risks

1. **Double to paise** can shift totals by a paisa; mitigated by the dry run, but non-2-decimal accounts (decimals up to 12) need a decision (Q2).
2. **Settings blob** holds loans, deposits and card dues: easy to lose silently. Importer must have a test per key.
3. **Ping and the Firebase sign-in are pinned** to `com.mansaketh.paddhupusthakam`, its receiver, the inbox authority and the signing certificate. Chitta's id `com.mansaketh.chitta` cannot talk to Ping until Phase 9. Plan the test route (Q6).
4. **Firebase and Google sign-in for a new app id.** The Chitta app id and its SHA-1 must be registered in project `cashew-saketh`, and Credential Manager needs the web client id. Also unverified by me: whether the Drive app-data folder is shared between two Android client ids in one Google Cloud project. Check the Drive API docs on appDataFolder before promising Chitta can read Paddhu's cloud backups. Until then, migration is by backup **file**.
5. **Two Firebase projects appear in `firebase_options.dart`:** android `cashew-saketh`, web `budget-app-flutter` (upstream's). Web is irrelevant; confirm `google-services.json` is the `cashew-saketh` one.
6. **Public repo.** `Paddhu-Pusthakam` is public and contains `knownAccounts.dart` (your account and card names) and Firebase options. Not secrets, but personal. Chitta's repo must be private.
7. **Household in Firestore:** `firestore.rules` cannot count members (8 limit is app-side) and sharedItems has known gaps listed in HOUSEHOLD.md. The Kotlin client must reproduce the same document shapes exactly, or the two apps will fight over one household.
8. **Widgets live under `com.example.budget`** in Kotlin while the app id is `com.mansaketh.paddhupusthakam`. Harmless today; just do not copy the package name.
9. **Untested by me:** the existing Dart tests (Ping golden vectors, household, loans, card dues, FDs, auto-save, settings merge). They are the best reference vectors for the Kotlin tests. Ping vectors need `TZ=Asia/Kolkata`.

## 6. Decisions recommended for the two open items

- **Database encryption:** recommend **no SQLCipher for now**. Android encrypts app storage by default, the app has a biometric/device lock, and Drive backups are already private to your account. SQLCipher adds APK size against the 20 MB target and complicates Room migration tests and the Paddhu import. Revisit in Phase 7 as encrypted *backup files* instead. I have not verified SQLCipher's current size or Room support; check before any reversal.
- **Range window:** recommend **this period** (what PDF p.3 and master section 12 already show), with "Not enough data yet" before day 7. A last-30-days option can be a setting later.

## 7. Phase 1 handover

Screens in the PDF not yet in the feature map: fixed deposits, card dues, instalment loans, PhonePe import, shared loans, categories and rules editor, pinned entries, CSV mapping, month review (all listed on PDF p.37 as "not drawn yet"), plus the Ink theme pages beyond dark p.12. Also no PDF home yet for: bill splitter, scanner templates, activity log, exchange rates, widgets settings, Bill-splitter decision.
