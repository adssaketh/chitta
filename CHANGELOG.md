# Changelog

## Phase 11b: every screen on the kit (16 Nov 2026) - on branch `phase-11b-callsites`
- Look only. Every screen now uses the v8 kit from 11a; no feature, setting or number changed.
- Buttons: one `ChittaButton` with weights (red Primary, outlined Secondary, Link, Danger). Top bars: Home, Ledger, Plan, Insights and More use `TopBarMain`; every other screen `TopBarDetail`. One icon map for tabs, More and Home sections (`ui/FeatureIcons.kt`). Sections, rows, empty states, chips, month steppers and the gauge are the kit versions.
- Four conversions, as the prompt lists: "(off)" on a rule is a switch; "Chosen" is a tick; "Earlier / Later" are arrow buttons; "Up / Down" in Customise are arrow buttons. On / Off rows (backup, cash check, sharing, Animations and others) are switches.
- Sheets: every sheet starts with the v8 sheet header. Dates are picked in the new date sheet (Entry detail, Quick Add and every Plan, Assets, Funds, Deposits, Insights date row). Undo is the new message bar (10 seconds).
- Calendar marks and instalment ticks are drawn marks, not text glyphs; Home's budget picker has a drawn caret.
- Removed: the old section header, top bar, empty / error state, button, expand group, undo bar, and the 32 dp glyph circle. Stamps are 4 dp.
- Tests: kit logic tests updated; snapshots re-recorded by GitHub Actions (`[record snapshots]`).

## Phase 11a: the v8 kit (16 Nov 2026) - on branch `phase-11a-kit`
- Nothing on any screen changes. This phase adds the building blocks that 11b to 11e will swap in, plus the gallery and screenshot tests for them.
- New package `core/design/kit` with the 22 shared components of the Phase 11 prompt (section 4): TopBarMain, TopBarDetail, CountBadge (and BadgedIconAction), Section, NavRow, ValueRow, SwitchRow (and ChittaSwitch), TickRow, CheckRow (and TickBox), EntryRow (and Glyph), ChittaButton with four kinds and ButtonPair, CategoryChip, FilterChip, Segmented, MonthStepper, GaugeBar, MessageBar (and MessageBarSlot), EmptyState and EmptyLine, SheetHeader, DateSheet, Note, GroupHeader and ExpandGroup.
- New tokens in `Dimens`, `Type` and `Palette` (including `warningOnInverse`), `CaretLeft` and `CaretUp` icons, and decisions D277 to D283.
- `Dimens.GlyphCircle` is 40 dp now; the old 32 dp is `GlyphCircleLegacy` and the old rows use it until 11b. `SheetSurface` takes `handle = false` for sheets that use `SheetHeader`.
- Component gallery: five new "Kit" sections. Screenshot tests in Paper and Ink at 393 dp, at 200% text, and at 360 dp with 130% text (30 new images). New unit tests for the tokens, the badge, the date grid and the contrast of the message-bar icon; new semantics tests for roles and labels.
- Not built or run here: this chat has no Android SDK, so nothing was compiled or tested locally. GitHub Actions is the first compile; the commit message asks it to record the new screenshots.

## Phase 10d: statement files (16 Nov 2026)
- Mutual funds > Import: the CAMS / MFCentral summary PDF is read again (the real file prints the NAV date and folio number with no space, which the reader did not expect).
- An NPS statement that is only pictures (a printed or screenshot PDF) is now read: the pages are drawn and read on the phone. The figures are checked against the statement's own total before anything is offered; scheme names are shown for you to check.
- New: Holdings report (Excel, .xlsx) with Fund Name, ISIN, Total Units, Invested Value, Current Value and Folio Number. Checked against its Total Invested and Total Current values; brings the ISIN of each fund.
- Any other error while reading a file now shows a message instead of closing the app.

## Phase 10c: Federal accounts (16 Nov 2026)
- Built-in list: the Federal Signet Rupay card (••9368) is marked operating and goes into "Saketh - Federal". The three Federal savings accounts (••3269, ••4365, ••6299) stay closed, as you confirmed.

## Phase 10b: known accounts built in (16 Nov 2026)
- Your 42 accounts and cards from the old app (Scapia, Axis, SBI, HDFC, ICICI, Federal, Yes, pension rows, closed ones) now come with the app, so bank messages are recognised on the first run without loading a file. A file you load still replaces them.

## Phase 10: bell and smarter auto-add (16 Nov 2026)
- Home: a bell next to the gear shows how many bank messages are waiting, and opens the same review list as Settings > Bank messages and UPI.
- Bank messages from Ping are now added automatically even when part of them is a guess: an unknown card or account is made from the SMS (for example "Scapia card"), and a message with no merchant or no known category goes into your "Other" category. The Check tag on the entry (Ledger and entry page) says it was a guess; changing the entry or tapping "Looks right" removes it.
- Still waiting in the bell: closed accounts, pension / provident fund rows, card bill payments, likely duplicates, UPI pages, amounts not read with certainty, and messages that do not say which account they are.
- Not changed: the "AI" is still rules plus the categories you chose before; nothing leaves the phone (D274 is the decision note).
- Undo after an automatic save removes the entries and puts the messages back in the bell; an account made by that save stays (empty).

## UI polish 3 (15 Nov 2026) - on branch `phase-ui-polish-4`
- Settings: Security, Household and Data are now groups like Look and feel, so every section opens and closes the same way. Only Look and feel starts open.
- Accounts: Move money and Exchange rates sit side by side (before, the second button was squeezed out and left a tall empty gap). The total balance now counts accounts whose currency was saved as "inr" (it showed 0.00), and the code shows in capitals.
- Even spacing: every section heading has the same gap above it (Accounts, Plan details, Shared with you, Mutual funds). The grey notes under lists are one shared style.
- Not changed: Quick Add compares the account currency to "INR" exactly. If an imported account is lower case there, the Currency row can show a rate it does not need. Money logic, so left for your decision.

## UI polish 2, part 2 (15 Nov 2026) - on branch `phase-ui-polish-3`
- Entry history is a timeline: a thin rail with a dot per version, the action in bold with the time under it, then the entry summary, what changed, and Restore.
- Every remaining tappable row now shows the pressed state and focus outline (Home, Insights, Plan, Accounts, Categories, Mutual funds, Assets, Household, Customise, Intake, CSV import, icon picker, Quick Add).
- Home, Insights and More: no spacing change needed after the first pass (D270).

## UI polish 2 (15 Nov 2026) - on branch `phase-ui-polish-2`
- Tappable rows and buttons now show a pressed state (faint tint, 0.98 scale on a spring) and a visible focus outline. The scale is off when animations are off. Buttons, icon buttons, shared list rows, Ledger rows, choice sheets, chips, tabs and the Who-spent arrows are covered.
- Ledger: "Split" and "Possible duplicate" are small outlined tags on their own line under the category, instead of words in the grey line.
- Settings: Look and feel (open), Backup and restore, Import and export and Help are groups that open and close, with a turning caret and an eased open. Security, Household and Data stay as they are.
- Spacing: remaining literal dp values in screens and shared components now come from Dimens.
- Component gallery: new "Rows, tags and groups" section, with snapshots at 393 dp, 200% text and 360 dp / 130%.
- Not done: see UI_AUDIT.md, "UI polish 2".

## Phase 9c part 2, step 2: sync between phones (15 Nov 2026) - on branch `phase-9c-sync-2`
- Settings > Backup and restore > "Sync between phones": Sync now, a status line (Up to date / N waiting / Syncing), last synced, waiting count, last error.
- Each row of the book is one Firestore document under `users/{uid}/book`. A round pulls what changed since the cursor, applies it (newer stamp wins), then pushes this phone's changes. Runs a few seconds after an edit, when the app comes to the front, and hourly in the background (WorkManager).
- Before sending, the server's time is checked; a phone clock more than 2 minutes off stops the sync with a message. Delete markers older than 180 days are cleared from the cloud once a day.
- A phone that has not synced for 180 days is asked: Merge, or Replace with the cloud (a restore point is made first).
- A restore, or "Delete everything", makes the cloud follow this phone: the phone starts over as a new device and every cloud row it lacks gets a delete marker.
- Edit history (`entry_version`) is not synced, by your choice. No schema change.
- Tests: new SyncTest cases (clock limit, own-rows rule, status words) and SyncStoreTest cases (read a row, newer/older cloud row, delete markers, unknown and missing columns, device-only settings, replace, start over).

## Phase 9c part 2, step 1: sync groundwork (15 Nov 2026) - on branch `phase-9c-sync`
- Schema 10: two small tables, `sync_change` (which rows changed here and when, with delete markers) and `sync_state` (this phone's sync id and cursor). Nothing existing is changed or dropped.
- SQLite triggers on 18 tables note every insert, edit and delete, whichever code made it (screens, importer, household sync, restore). They are put on each time the database opens.
- Pure merge rules in `:core:domain` (`Sync.kt`): newest stamp wins for a whole row, a tie goes to the larger device id, a delete is a stamp, device-only settings never sync, household copies are not yours to sync.
- Nothing is sent anywhere yet and no screen changes. Step 2 adds the Firestore service, the background job and Settings > Sync.

## Phase 9c step 2: lock icon, motion, accessibility, performance and anti-slop audits (15 Nov 2026) - on branch `phase-9c-audits`
- Lock screen: the launcher icon now sits above the name (80 dp), replacing the plain text-only mark. More uses the same drawing.
- Motion: the tab slide, the add button and the Home ring fill now jump to their end state when the phone's "Remove animations" is on, and follow the setting while the app is open. Nothing else in the app animates.
- TalkBack: six tappable rows (categories, accounts, assets, mutual funds) now announce as buttons.
- Performance: `LargeBookTest` builds 10,000 entries over 30 months and times Ledger grouping and search, the Insights summary and category totals, and the duplicate scan, with loose limits. Timings are printed in the CI log. Scroll speed and cold start still need your phone.
- Snapshots: a 360 dp, 130% text run of the shared components in both themes (`NarrowPhoneSnapshotTest`).
- Anti-slop audit: no gradients, blur, shadows, emoji, banned fonts or heavy weights found in the code. Uppercase text only in `Stamp`.
- Settings > Look and feel > Animations On/Off: your own switch, apart from the phone's setting. Off (or the phone's Remove animations) turns off every animation in the app.
- Sheets now rise on a spring with no bounce and the backdrop fades in (one place: `BottomOverlay`). Odometer-rolling totals were not wanted.
- Not done: whole-app screen snapshots. Screens read their view models directly, so drawing one needs a stateless version of each first (see D246).

## Phase 9c step 1: transfers inside categories (B2) and the Net total check (B1) (15 Nov 2026) - on branch `phase-9c`
- Categories > a category > "Counts as a transfer: On/Off". When on, its entries (and its subcategories' entries) still change balances but no longer count as spending or income. They are left out of money in and out, the Net total, the Ledger's day totals, category bars, the Spending and Income tabs, Who spent how much, widgets and budgets.
- Categories named "Transfer from ..." or "Transfer to ..." are switched on when the app updates its database (schema 8 to 9) and when a Paddhu backup is imported. Check Categories and turn off any that are not transfers.
- Net total itself does not change: a transfer out and a transfer in cancel. Money in and money out fall by the same amount.
- Not changed: subscription suggestions, duplicate checks, sinking funds and rules still see these entries as ordinary.
- B1: the code draws a minus for a negative Net total (tested). Please look at the Insights Net total without mark-up.
- Tests: `TransferCategoriesTest` (10), `MigrationTest` (schema 9), importer pre-mark test. Domain tests run locally; the rest run in CI.

## Phase 9b step 5b: Members edit shared items (15 Nov 2026) - on branch `phase-9b-step5b`
- When an owner turns on "Let them edit the details", the people they share with get an "Edit details" button on the shared deposit or loan. Deposit: bank, amount, rate, dates, interest added, bank maturity amount, FD number, note. Loan: name and a note.
- The edit goes to the server at once (it needs the connection; offline it says nothing was saved). If somebody changed the item first, it is refused and says so.
- The owner's phone applies the newest change to the real deposit or loan within a few seconds. Accounts and the loan amount stay as the owner has them; a bad edit is refused and the owner's copy goes back up.
- "Last edited by X" shows on the owner's deposit and loan detail and on members' sheets. If somebody else changes an item after your edit, your sheet says so.
- Shared items still never count in a member's Net worth, loan totals or reminders. Only the owner can delete an item or change who sees it.
- The loan note now shows on the owner's loan detail when there is one.
- Not yet run on a phone; CI compile and tests pending (the chat workspace cannot build the app).

## Phase 9b step 5a: Shared loans and fixed deposits (15 Nov 2026) - on `main`
- Fixed deposit detail and instalment loan detail get a "Who sees it" row (only when you are in a household). Pick up to 7 people; nobody else sees the item. "Make private again" removes it for everyone.
- "Let them edit the details" is stored and sent (the old app honours it). People using this app can only look for now; editing from here is step 5b.
- Accounts > Fixed deposits and Plan > Loans each get a "Shared with you" list, view only, with a detail sheet. Shared items are never in your Net worth, loan totals, reminders or the instalment ticks.
- A shared loan shows "Paid so far" as the owner's phone worked it out (ticked instalments plus payments recorded as entries there), as of the owner's last sync.
- Same Firestore documents as the old app (`sharedItems`); the published rules are unchanged. Works without choosing what to share for entries.
- Only instalment loans can be shared (a loan without terms has nothing a viewer could see), as in the old app.
- Not yet run on a phone; CI compile and tests pending.

## Phase 9b step 4: Who spent how much and Diagnostics (15 Nov 2026) - on `main`
- Settings > Household > "Who spent how much" (PDF p.34): the household total for a month and each member's spending with a 20-segment bar and their share. Shared entries only; private and personal entries, transfers, balance corrections and loans are left out; a refund reduces what its member spent. Arrows beside the month name switch months (not in the PDF).
- Settings > Household > "Diagnostics": what this phone holds against what the server counts (shared entries, your Mine-only entries, members), with "Check again" and a note when entries are waiting to go up.
- Settle up (PDF p.29) is not built: the owner dropped it (D220). The "Settle up" link on p.34 is left out for the same reason. No shares, no "who owes whom".
- Not yet run on a phone. The server counts use Firestore count queries and have not been tried against the live rules.

## Phase 9b step 3: Sharing entries (15 Nov 2026) - on `main`
- After you create or join a household, "Choose what to share" (PDF p.33): share your existing entries, or only new ones. "Not now" keeps everything on the phone. Settings > Household > "Your sharing" (PDF p.31) shows Up to date / N waiting / Needs attention, with Send now and "Share my earlier entries".
- New entries are shared by default. "Who sees it" in Quick Add and on the entry detail switches one to "Mine only".
- Ledger: an Everyone / Mine switch appears once sharing is on. Everyone shows the shared ledger (members' entries are labelled with their name as the account); Mine shows everything you own.
- Other members' entries are read-only; delete only if the owner allows it. Bulk edit and delete skip them and tell you.
- Entries made offline wait and go up when the phone is online again. Categories are shared (last edit wins, as in the old app).
- Home, Insights, budgets and net worth are unchanged and personal.
- Not in this step: "Who spent how much" (step 4), settle-up and shared loans/deposits (step 5). Not yet run on a phone; CI compile and tests pending.

## Icons on tabs and Home sections, logo on More (15 Nov 2026) - on branch `phase-ui-polish-2`
- Insights tabs (Current, History, Net worth, Review) and Ledger tabs (All, Spending, Income, Upcoming) each have a small icon before the label. The strips can scroll sideways when the icons make them wider than the screen; swiping on them does not change the main tab.
- Customise > Home sections: an icon before each section name (Balances, Recent entries, Budgets, Net worth, Goals, Loans, Spending by category, Spending heatmap).
- More: the app logo (the launcher icon) and the name sit quietly at the bottom, in the blank space. The list scrolls above it.
- All icons come from the existing Phosphor set; none were added.

## Phase 9b step 2: Household (15 Nov 2026) - on branch `phase-9b-household`
- Settings > Household: sign in to the household with the same Google account as Drive backup (one extra tap on a phone that signed in during 9a), then create a household, join with an 8-character code, or leave.
- Household page (PDF p.31): household name, members "2 of 8" with an OWNER stamp, "Invite someone", "Show account balances to members" (owner only), "Leave this household".
- Owner taps another member: make owner (one atomic write, your invite code is cancelled), let them delete others' entries or not, remove. Tap the household name to rename it. Tap your own row to change your name.
- Invite sheet (PDF p.32): one active code, tap to copy, share, make a new code (cancels the old one), stop people joining. Shows how many places are left.
- Same Firestore data as the old Flutter Paddhu (users, invites, households, members), so a household made there opens here. The published `firestore.rules` are used unchanged.
- Leaving or joining again deletes the loans and fixed deposits you had shared (the old app may have made some); entries on the phone are never touched.
- Not in this step: sharing entries, Everyone/Mine, Who spent how much, settle-up, shared loans and deposits (steps 3 to 5). "Your sharing" and "Who spent how much" from PDF p.31 are not drawn yet.
- Libraries: Firebase BoM 35.2.0 (firebase-auth, firebase-firestore). No google-services plugin.
- 17 new domain tests (`HouseholdTest`).

## Swipe fix, vibration strength, ledger scroll ticks (14 Nov 2026) - on branch `phase-ui-polish`
- Tab swipe: once the finger has clearly moved sideways the movement is taken before the list sees it, so a diagonal swipe no longer turns into a scroll and fails. The Plan tab strip still scrolls on its own.
- Settings > Look and feel > Vibration strength: Off, Light, Medium (default), Strong. Uses the phone's vibrator directly (VIBRATE permission) with a set strength; phones whose motor cannot vary strength use the normal system tap. The old on/off choice carries over (Off stays Off).
- Ledger: a small tick as each day passes the top while you scroll.

## Tab swipe reliability and shared label/value rows (14 Nov 2026) - on branch `phase-ui-polish`
- Swiping between tabs now watches the touch before lists and rows react, decides left/right by which way the finger moves more, and also accepts a short quick flick (24 dp at speed) as well as a 56 dp drag. Before, a slightly diagonal swipe was often taken by the list and lost, so about one in five needed a second try. A tab strip that scrolls sideways still wins over the swipe.
- The label/value row used in Quick Add, Settings and the detail screens uses the shared row spacing (12 dp above and below, 16 dp between label and value); long values are cut after two lines instead of squeezing the label.

## Settings: theme, vibrations, diagnostics (14 Nov 2026) - on branch `phase-ui-polish`
- Settings > Look and feel: Theme (follow the phone, Paper, Ink; now remembered) and a Vibrations switch that turns off every tap, keypad and swipe buzz.
- Settings > Help > Diagnostics: shows errors the app recorded (crashes, failed Drive backup, receipt clean-up) with the app and Android version; Share sends it as text, Clear empties it.

## UI polish steps 7 to 9: the other screens and sheets (14 Nov 2026) - on branch `phase-ui-polish`
- End of scroll: lists that can raise an Undo or hold actions (Accounts, Categories, Rules, Entry detail, Assets, Mutual funds, Plan details) end 96 dp from the bottom; forms and settings pages (Settings, Customise, Cloud backup, Imports, Intake, Review, Automation) end 32 dp from the bottom. The old 120, 140 and 160 dp gaps are gone.
- Sheets: list and form heights are a share of the screen height instead of fixed dp, so they no longer get cut off on short phones or with big text.
- Undo bar sits exactly above the bottom bar (64 dp bar + 8 dp).

## UI polish steps 5 and 6: More and Plan (14 Nov 2026) - on branch `phase-ui-polish`
- More: each row is a standard list row with an ink label and a caret, 56 dp tall, and the page scrolls on short screens or big text.
- Plan: rows use the shared spacing (12 dp above and below, 16 dp before the amount, long titles cut with an ellipsis, long grey lines wrap to three); section headings get 32 dp above them; the three buttons at the bottom of Bills now have 8 dp between them; the list ends 96 dp from the bottom so the add button never covers the last row.

## Keypad "=" key (14 Nov 2026) - on branch `phase-ui-polish`
- An "=" key appears under the keypad once the amount holds a sum (120+45); tapping it replaces the sum with the total (165). It stays hidden for a plain amount. Rounds the same way as Save (to whole paise, half-even). Unit-tested.

## Quick Add: phone keyboard option (14 Nov 2026) - on branch `phase-ui-polish`
- A link under the keypad, "Use the phone keyboard", swaps the on-screen keypad for the normal keyboard (type 120+45 and it still adds up; - * / work as signs). "Use the keypad" swaps back. The keypad is unchanged and stays the default. Keypad keys no longer buzz twice.

## Vibrations and ring fill (14 Nov 2026) - on branch `phase-ui-polish`, not yet felt on a phone
- Light haptics: bottom-bar tab change and a swipe between tabs (tick), add button (tap, and a firmer one on long-press), a long-press on a Ledger row (starts selecting), every keypad key. The save button already had its confirm buzz. They follow the phone's own touch-feedback setting.
- Home: the budget ring fills circularly, clockwise from the top, in about 0.9 s every time Home is opened or returned to (not on a data refresh). The snapshot tests keep the ring still.

## UI polish pass: step 4, Insights and heatmap (14 Nov 2026) - on branch `phase-ui-polish`, not yet seen on a phone
- Insights: a 12 dp gap under the tab rule on all four tabs; Spent steps down a size so "₹1,35,449" no longer touches "-103%"; the percentage uses a true minus; the gaps around "Totals" are the section rhythm; end padding 96 dp.
- Heatmap (Insights and Home): the shade scale is the 90th percentile of days with spending, so one big day no longer turns every other day the lightest step (display scale in `Heatmap.month`, new unit test). Days still to come are outlined with a muted number; days with nothing spent keep the light fill. A legend (Less, five steps, More) sits under the grid.

## UI polish pass: step 3, Ledger (14 Nov 2026) - on branch `phase-ui-polish`, not yet seen on a phone
- Month header: 12 dp under the tab rule, 8 dp to In/Out, 12 dp to the rule, 8 dp before the first day. In/Out labels sit on the amounts' baseline. The 56 dp box that left about 24 dp of dead space under the month name is gone.
- Rows: title and category line are one line each with an ellipsis (the category line no longer wraps), 12 dp padding, 64 dp minimum, 16 dp before the amount. The day number sits on the first title's baseline. Amounts step down a size if they do not fit.
- End padding 96 dp (was 160); search hint and filter notes use token gaps.

## Tab switching animation (14 Nov 2026) - on branch `phase-ui-polish`, not yet seen on a phone
- Switching main tabs slides the new tab in from the side you moved towards (about 1/8 of the screen) while it fades in, and the old one fades out: about 0.25 s, direction follows the bottom-bar order. The bottom bar stays still.
- Swiping sideways on a main tab moves to the next or previous tab (72 dp drag). A screen part that scrolls sideways, such as a tab strip, takes the drag first.
- The ledger page-turn idea was shown as a mockup and dropped.

## UI polish pass: step 2, Home (14 Nov 2026) - on branch `phase-ui-polish`, not yet seen on a phone
- Home: one gap rhythm from tokens (32 above each section heading, 12 heading to rule, 12 dp row padding). The 60 dp of air under "How this is worked out" is gone (the link keeps a 48 dp touch height; the rule sits right under it). "Groceries budget" is a normal section heading. "Customise Home" is styled as a link.
- Balances, Coming up and Total use `ListRow`: names are one line with an ellipsis and at least 16 dp before the amount. The Total row no longer leaves about 100 dp before "Recent entries".
- Per day and Range: figures step down a size when they do not fit; the Range note reads "15 days short" (one line). The Spent label, amount and stamp share one baseline, as do the ring legend labels and amounts. The month rule's inner top padding is 4 dp.
- The ring says "safe" under its percent (answers the "8%" question: it is what is left).
- End of the page is 96 dp (`FabInset`), was 160.
- The add button hides while any main screen scrolls down and returns when it scrolls up, so it no longer covers the last row (Home, Ledger, Insights footnote). One change in `AppRoot`.

## UI polish pass: step 1, shared spacing and components (14 Nov 2026) - on branch `phase-ui-polish`, not yet built or checked on a phone
- `Dimens`: spacing scale (`Space1` to `Space12`) and role tokens (`HeadingGap` 12, `RowPadV` 12, `RowLedger` 64, `InlineGap`, `FigureGap`, `TitleAmountGap`, `TitleBarMin`, `FabInset` 96, `EndInset` 32, `SheetMaxFraction`, `NavBarMin`, marker size). Old `Gap` names are aliases.
- `Type`: role names (`screenTitle`, `sectionHeading`, `link`, `figure`, `figureSteps`, `rowTitle`, `rowMeta`, `rowAmount`...). No size or weight changed.
- `SectionHeader` is now as tall as its text, with 12 dp under it; only a heading with an action keeps the 48 dp touch height. This removes the big gap between headings and their rules on every screen at once. New `Section` (gap, heading, optional rule) and `ListRow` (one-line title and meta limited with an ellipsis, 12 dp padding, 16 dp before the amount).
- `AmountText` / `TabularText` report their first baseline (so a label, an amount and a stamp can share one line) and take `fallbackStyles`: a figure steps down a size when it does not fit its column.
- Main-tab titles keep their icons. `ScreenTitle` is now a 56 dp row; the extra vertical padding on Plan, Insights and More is removed so all four titles sit on the same line.
- Not done yet: screens do not use `Section`, `ListRow` or the fallback styles yet (steps 2 to 9). Component snapshots need re-recording.

## UI polish pass: audit (14 Nov 2026) - on branch `phase-ui-polish`, awaiting approval
- Added `UI_AUDIT.md`: every screen's spacing, heading and clipping problems, the conflicts with the design PDF, three possible bugs (reported, not fixed) and the proposed spacing and type tokens. No screen code changed.

## Category icons (14 Nov 2026) - on `main`, 6.0.2
- Imported categories now show an icon that fits (270 old picture names mapped to 169 new line icons, or a guess from the category name), instead of one tag for all. The icon picker lists them all and searches by the old names too (D188).

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
