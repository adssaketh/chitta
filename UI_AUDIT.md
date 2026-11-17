# UI audit: spacing, rhythm, consistency

Phase: UI polish pass. Branch `phase-ui-polish`. Written 14 Nov 2026 against `main` at 2cd743b.
Status: the first polish pass (`phase-ui-polish`, merged 14 Nov 2026) built the tokens, `Section`, `ListRow` and the end-padding tokens. See "UI polish 2" at the bottom for the second pass.

## How this was done

- Toolkit: Jetpack Compose (Kotlin), no XML layouts.
- Screens: `app/src/main/java/com/mansaketh/chitta/ui/**` (36 files with layout code).
- Shared components: `core/design/src/main/java/com/mansaketh/chitta/core/design/components/` (`Layout.kt`, `Ledger.kt`, `Charts.kt`, `AmountText.kt`, `Buttons.kt`, `Stamp.kt`, `Field.kt`, `Keypad.kt`) and `ui/Common.kt`.
- Tokens: `core/design/.../tokens/Dimens.kt` (spacing), `Type.kt` (text styles), `Palette.kt` (colours).
- Read line by line: Home, Ledger, Insights (Current tab), More, app root, every shared component, tokens.
- Checked by search across all files, not read line by line: Plan, Accounts, Categories, Customise, Settings, Quick Add, Review, Net worth, History, Mutual funds, Assets, Rules, Intake, Imports, Cloud backup, Entry detail and the sheets. Their rows below come from counts of literal sizes, bottom paddings, heading styles and `maxLines`. They get a line-by-line read when each is implemented.
- Design PDF pages 9 (Home), 12 (Ledger), 24 (Insights), 30 (More) were opened as images. Dark PDF pages were listed by title only, not opened yet.
- Nothing was run on a phone or in a snapshot test for this audit. "Seen in screenshot" means your screenshots.

## What the numbers say

| Measure | Found |
| --- | --- |
| Literal `dp` values in screen files | 87, of which 33 are bottom paddings and 13 are sheet max heights |
| Bottom padding at the end of a scroll | Three values for one job: 120 dp (11 places), 140 dp (9), 160 dp (10). None is a token. The add button needs 56 + 24 = 80 dp |
| Screens that pad for the add button but never show it | Settings, Restore points, Customise, Cloud backup, Categories, Rules, Automation, Mutual funds, Assets, Imports, Intake, Entry detail, History, Budget/Goal/Loan detail, Month review. 120 to 160 dp of blank at the end of each |
| Row vertical padding | 8 dp in 39 places, 12 dp in 21, 16 dp in 6 |
| Gaps between blocks | 8 dp ×70, 16 dp ×57, 12 dp ×55, 32 dp ×32: picked by eye per screen, no role names |
| Section headings | `SectionHeader` used 41 times; 24 of those have no rule under them, 17 do. 67 more headings are plain `Text` in `title` or `headline` style |
| Title lines limited with `maxLines` | 12 places in total. Ledger rows, balance rows, budget rows and most list rows have no limit |
| Main-screen titles | Ledger, Plan, Insights, More: serif 28 sp with an icon. Home: sans 18 sp in a top bar |

## Conflicts between the sources

| # | Master prompt | Design PDF | Code today | Proposed |
| --- | --- | --- | --- | --- |
| C1 | Does not say what Home's title is | p.9: no "Home" title. The top line is the budget selector, with card and eye icons | Sans "Home" with an icon; budget selector is a second line under it | Follow the PDF: selector in the top line, no "Home" word (question 1) |
| C2 | Silent | p.12, 24, 30: serif title, no icon beside it | Serif title with the tab icon beside it | Follow the PDF: drop the icon (question 1) |
| C3 | Ring has "the safe percentage in the middle" | p.9: "46%" with the word "safe" under it | Percent only | Follow the PDF: add "safe" (answers the 8% question) |
| C4 | Section 3: date in a fixed left margin column | p.12: the day is a header line ("Thu 12 Nov"), no margin column, no category glyph | Margin column and glyph | Master wins, no change. It is why titles get only about 130 dp and wrap |
| C5 | Silent | p.30: a status line under "More", rows in groups (Money, Data, App) with a status value on each | Flat list of 5 rows, no status | Follow the PDF with what exists today (question 6) |
| C6 | Add button "floats bottom-right on every main screen" | p.9: it floats over empty space | It floats over content while you scroll | Padding cannot fix mid-scroll overlap; see question 4 |
| C7 | Silent | p.9: "6 days short" | "15 days short of the month end" (wraps to two lines) | Follow the PDF wording |

## Findings

Severity: **High** = text clipped, overlapped or hidden. **Med** = uneven rhythm that is easy to see. **Low** = tidy-up.

### Home (`ui/home/HomeScreen.kt`, PDF p.9 light, dark PDF "02 / 10 Home")

| # | Problem | Cause in code | Fix | Sev |
| --- | --- | --- | --- | --- |
| H1 | Big gap under "How this is worked out" (your item 1) | Not a collapsed expander. The link is a 48 dp touch box, then a 32 dp section gap, then the rule: about 60 dp of air under a 13 sp line | Keep the 48 dp touch area but let it overlap the gap; rule sits one section gap under the hero block | Med |
| H2 | Empty space right of the hero ₹8,402 | By design the ring sits beside the hero (p.9). When the cash check leads, the ring moved under it with the budget name (D187), so the right side is empty | Leave empty (the ring belongs to the budget, not to the cash figure). Tighten the block so it reads as one unit. Question 2 offers an alternative | Low |
| H3 | Gaps differ between every section (item 2) | Seven different recipes: 12 dp before the ring, 32 dp after it, 12 dp before the month rule, 4 dp under it, 32 dp + 48 dp header before each section, 56 dp Total row then 32 + 48 before Recent entries | One section gap (32), one heading-to-content gap (12), one row padding (12), from tokens | Med |
| H4 | Tight before "Groceries budget", huge before "Balances" | 12 dp above the budget name; 32 dp + a 48 dp tall header above Balances | Both become: section gap, heading, heading gap | Med |
| H5 | Big gap between a heading and its rule | `SectionHeader` is 48 dp tall with the text centred, so 12 dp of air sits under every heading even with no action beside it | Heading is as tall as its text; only a heading with an action keeps the 48 dp touch height | Med |
| H6 | Five heading looks (item 3) | "Cash check" = `label` muted; "Groceries budget" and "How this is worked out" and "Customise Home" = `labelMedium` ink; "Balances" = `title` | Two roles only: section heading and label. Links get the underlined action style so they stop looking like headings | Med |
| H7 | "Spent" label not on the number's baseline (item 7) | Row is centre-aligned, and `AmountText` is drawn on a canvas, so it reports no baseline. Nothing can baseline-align with any amount in the app | Make `TabularText` report its first baseline, then align label, amount and stamp on it | Med |
| H8 | Too much air between the Spent row and the chart | 12 dp gap + 8 dp inside the chart + the chart's own headroom above the forecast line | Gap from token; trim the chart's inner top pad to 4 dp | Low |
| H9 | "Range" column: warning wraps to two lines, columns end at different heights | Long copy in a half-width column | PDF wording "15 days short" (C7) | Med |
| H10 | "Limit ~16 Nov" under the add button (items 5, 6) | Mid-scroll overlap. End padding is 160 dp, which is enough at the end of the list | See question 4 | High |
| H11 | Blank area under "Customise Home" (item 9) | On purpose, not broken: it is the last line, followed by the 160 dp end padding. The link repeats the gear icon in the top bar | End padding becomes the 96 dp token. Keep the link, styled as a link | Low |
| H12 | Per day / Range figures can clip on a narrow phone | Two half-width columns of 32 sp serif. An amount is a fixed-size canvas: it cannot wrap or shrink, so it overflows its column | Amounts step down one size when they do not fit (see S3) | High |
| H13 | Balance rows: long account names can run into the amount | No `maxLines`, no gap between name and amount | 1 line with ellipsis, 16 dp minimum gap | Med |

### Ledger (`ui/ledger/LedgerScreen.kt`, `components/Ledger.kt`, PDF p.12 and 13 light, dark "05 / 10")

| # | Problem | Cause in code | Fix | Sev |
| --- | --- | --- | --- | --- |
| L1 | "November 2026" sits hard under the tab rule, with a large gap before In/Out | Month text is top-aligned in a 56 dp box: 0 dp above it, about 24 dp of dead space under it | 12 dp above, 8 dp to the In/Out line, 12 dp to the rule | Med |
| L2 | First row touches the header rule; the header covers rows | The sticky header is meant to cover rows as they scroll under. The real fault is no gap between the rule and the first day | 8 dp after the header rule | Med |
| L3 | "In" label looks clipped | Not found in code: label and amount sit in a plain row inside the 24 dp margin. Most likely your red line. To be confirmed in a 360 dp snapshot | Verify; fix only if the snapshot shows it | Low |
| L4 | Row heights vary (item 11) | Title has no `maxLines`; with the date column and glyph it has about 130 dp, so most real titles wrap. The category line wraps too | Title and category line limited (question 3); row min height 64 dp | High |
| L5 | Day number not on the title's baseline | Date column has a fixed 12 dp top pad; the first row is centred in its own height, which changes with wrapping | Align the day number's baseline with the first title's baseline | Med |
| L6 | Last row's amount hidden by the add button (item 6) | Same as H10 | Question 4 | High |
| L7 | "Transfer from Mansa" and "Transfer to Saketh" icons differ | **Not a styling fault.** Neither row is a transfer in the app's sense. Both are ordinary entries in categories with those names, so each shows its category's icon and colour. Real transfers (paired entries) all use one icon. See bug B2 | No visual change until B2 is decided | Info |
| L8 | Search hint, "Filtered" line and notices use 4, 8 and 0 dp paddings | Ad hoc | Tokens | Low |

### Insights (`ui/insights/InsightsScreen.kt`, `InsightsCharts.kt`, `ReviewScreen.kt`, PDF p.24 light, dark "08 / 10")

| # | Problem | Cause in code | Fix | Sev |
| --- | --- | --- | --- | --- |
| I1 | "₹1,35,449" nearly touches "-103%" (item 5) | Same as H12: fixed-size amount in a half-width column. One more digit, a 360 dp phone or 130% text makes it overlap | S3, and stack the two figures one above the other when both do not fit | High |
| I2 | "-103%" uses a hyphen; amounts use a true minus | Plain string | Use the same minus sign as amounts | Low |
| I3 | Footnote under the add button | Same as H10 | Question 4 | High |
| I4 | Range and Accounts rows start hard under the tab rule | No gap after the tabs | Heading gap after the tab rule, on every tabbed screen | Low |
| I5 | Title row padding differs from Ledger and More | Insights 12 dp, More 12 dp, Ledger 0 dp, Plan own value | One screen-title component with fixed padding | Med |
| I6 | Heatmap: day 1 swamps the scale (item 8) | Level = share of the month's biggest day, in four equal steps. One big day pushes every other day into the lightest step | Scale to the 90th percentile of days with spending; days above it take the darkest step. This is a display scale in `Insights.heat` with unit tests, not a money rule, but it is domain code: needs your yes (question 5) | Med |
| I7 | Heatmap: future days look like zero-spend days | Both are level 0. The code comment says future days are drawn empty; they are not | Future days: outline only, muted number. Zero-spend past days: the light fill | Med |
| I8 | Heatmap has no legend | None drawn | One line under the grid: "Less", five swatches, "More", plus "outlined days are still to come" | Low |
| I9 | Red dot on day 28 | The grid draws no dot. It matches the pink touch markers elsewhere | None | Info |
| I10 | Heatmap gaps are literal 4 dp; donut is a literal 168 dp | Literals | Tokens | Low |

### More (`ui/AppRoot.kt` `MoreScreen`, PDF p.30 light; no dark page)

| # | Problem | Cause in code | Fix | Sev |
| --- | --- | --- | --- | --- |
| M1 | Five rows then blank (item 10) | Flat list, no grouping, no status | Follow p.30: one status line under the title, rows in labelled groups, a status value where one exists today. Version in a small footer line. No filler (question 6) | Med |
| M2 | Rows are 56 dp with 8 dp padding; label is muted grey though it is the row's main text | `ValueRow` was made for label/value pairs | A navigation row: ink label, optional muted value, caret | Low |

### Shared components and all other screens

| # | Where | Problem | Fix | Sev |
| --- | --- | --- | --- | --- |
| S1 | 30 scroll containers in 20 files | End padding 120/140/160 dp, with or without an add button | `Dimens.FabInset` (96) on the main tabs that show the button; `Dimens.EndInset` (32) everywhere else | Med |
| S2 | `Layout.kt` `SectionHeader` | 48 dp tall always; rule under it added by hand in 17 of 41 uses | One `Section` component: gap, heading, heading gap, optional rule. Used on every screen | Med |
| S3 | `AmountText.kt` | Canvas with a fixed size: cannot shrink or wrap, reports no baseline | Report the baseline; take a list of fallback styles and use the first that fits the width | High |
| S4 | `Layout.kt` `ChittaTopBar` and `ScreenTitle` | Two title looks for main tabs (C1, C2); pushed screens use sans 18 sp, which is fine and stays | One main-tab title; pushed screens keep the back-arrow bar | Med |
| S5 | `Common.kt` `ValueRow`, `ChoiceSheet` rows; Home balance, coming-up, goal, loan rows; Insights `TotalRow`; Plan rows | Each builds its own row: 56 or 68 dp, 8 or 12 dp padding, rule inside or outside | One `ListRow` (title, optional meta, optional trailing amount or value) with token padding | Med |
| S6 | `Common.kt` `TextTabs`, bottom bar | Literal 2 dp × 24 dp marker, 64 dp bar height | Tokens (`Dimens.Marker`, `Dimens.NavBarMin`) | Low |
| S7 | Sheets (`PlanSheets`, `QuickAddSheet`, `FundsUi`, `IconPickerSheet`, `Common`) | Max heights 340, 360, 380, 420, 460, 560, 640 dp as literals. On a short screen at 130% text a 640 dp cap can pass the screen height | Cap as a share of screen height (one token), content scrolls inside | Med |
| S8 | `MutualFundsScreen.kt` | 14 hand-made headings in `title` or `headline` style | `Section` | Med |
| S9 | Plan (`PlanScreen`, `PlanDetails`, `PlanSheets`, `PlanCommon`, `FundsUi`) | 19 literal sizes, 9 hand-made headings, row titles without `maxLines` | Tokens, `Section`, `ListRow` | Med |
| S10 | Accounts, Categories, Rules, Automation, Assets | 140 dp end padding with no add button; hand-made headings | S1, S2 | Low |
| S11 | Settings, Restore points, Customise, Cloud backup | 160 dp end padding with no add button; `SectionHeader` followed by a label with no gap token | S1, S2 | Low |
| S12 | Entry detail, History, Split and Receipt sheets | 120 dp end padding; 8 hand-made headings | S1, S2 | Low |
| S13 | Imports, CSV import, Intake | 120/140/160 dp end paddings; literal 8 dp list paddings | S1 | Low |
| S14 | `Layout.kt` `EmptyState`, `ErrorState` | Vertical padding is the section gap on both sides, so an empty screen starts 32 dp lower than a full one | Heading gap above, section gap below | Low |
| S15 | Undo bar (`AppRoot.kt`) | Sits a literal 72 dp above the bottom, and under the add button's corner | Token; when the bar shows, it sits above the add button line | Med |
| S16 | Snapshot tests | 26 component snapshots at 393 dp wide and at 200% text. None at 360 dp, none at 130%, none of whole screens | Add 360 dp and 130% runs for the changed components and for Home, Ledger, Insights and More built from sample data | Med |

## Possible bugs: reported, not fixed

| # | What you saw | What the code does | Verdict |
| --- | --- | --- | --- |
| B1 | Net total ₹68,778.77 with no minus | Net = money in − money out (`Balances.kt` `Flow.net`). Negative amounts are formatted with a true minus before the ₹ (`IndianFormat.rupees`), the same path that draws "−₹544.93" on Home | The code draws the minus. In your screenshot the red circle crosses exactly where it would be. Not confirmed as a bug; please look at the screen without the mark-up. A snapshot of this row with a negative figure will be added either way |
| B2 | Money out ₹1,35,449.41 looks too large | Home's ₹6,470 is the Groceries budget only; Insights counts every category, so they are not the same figure. But: an entry is left out as a transfer only when it is one half of a pair made in this app, or a balance correction. Your Ledger shows "Transfer from Mansa" (+₹1,000) and "Transfer to Saketh" (₹1,000) as ordinary entries in categories with those names. Entries like these count as money in **and** money out | **Likely a real problem with the imported book**: transfers between your and Mansa's accounts that the old app stored as category entries inflate both totals by the same amount. The footnote is then not true for your data. Net total is not affected. Size unknown without the data. Needs its own decision (a money rule, so not in this phase) |
| B3 | Ring says 8% | It is Safe to spend as a share of the budget: ₹529.91 of ₹7,000 = 7.6%, shown as 8% (`Segments.kt`). So it is what is left, not what is used | Number is right, label is missing. Fix is C3: the word "safe" under it, as on p.9 |

## Proposed tokens

The design PDF (p.2) and master section 3 already fix: screen margin 24, between sections 32 to 36, list rows 56 to 68, buttons 56, body 16 sp, smallest text 13 sp, 4 dp grid. The values below keep all of those.

### Spacing scale (4 dp base)

| Token | dp | Today |
| --- | --- | --- |
| `Space1` | 4 | `Gap1` |
| `Space2` | 8 | `Gap2` |
| `Space3` | 12 | `Gap3` |
| `Space4` | 16 | `Gap4` |
| `Space6` | 24 | `Gap6` |
| `Space8` | 32 | `Gap8` |
| `Space12` | 48 | new |

`Gap9` (36 dp) is removed if nothing uses it after the pass. The old `Gap` names stay as aliases until the last screen is moved, then go.

### Roles (screens use these names, never raw steps)

| Role token | Value | Used for | Source |
| --- | --- | --- | --- |
| `ScreenMargin` | 24 | Left and right of every screen and sheet | PDF p.2 |
| `SectionGap` | 32 | Above every section heading | PDF p.2 |
| `HeadingGap` | 12 | Heading to its rule or first content; tab rule to first content | new |
| `RowPadV` | 12 | Top and bottom inside every list row | new (rows land at 56 and 68) |
| `RowMin` / `RowTall` | 56 / 68 | One-line and two-line rows; Ledger row 64 | PDF p.2 |
| `InlineGap` | 8 | Icon to text, label to value, amount to stamp | existing habit |
| `FigureGap` | 24 | Between two figures side by side | existing |
| `CardInner` | 16 | Inside sheets, fields and the undo bar | existing |
| `TitleBarMin` | 56 | Screen title row | existing |
| `FabSize` / `FabMargin` | 56 / 24 | Add button | existing |
| `FabInset` | 96 | End of scroll on screens with the add button (56 + 24 + 16) | replaces 120/140/160 |
| `EndInset` | 32 | End of scroll on screens without it | replaces 120/140/160 |
| `SheetMaxFraction` | 0.8 of screen height | Scrolling sheet bodies | replaces 7 literals |

### Typography roles (one place: `Type.kt`)

| Role | Style | Today's name | Used for |
| --- | --- | --- | --- |
| `screenTitle` | Fraunces 28/32, 500 | `headline` | Main-tab titles, month name in Ledger |
| `barTitle` | Hanken 18/24, 500 | `title` | Pushed screens with a back arrow, sheet titles |
| `sectionHeading` | Hanken 18/24, 500 | `title` | Every section heading |
| `label` | Hanken 13/16, 400, muted | `label` | Labels above figures, captions, footnotes |
| `link` | Hanken 13/16, 500, underlined | `labelMedium` + underline | "How this is worked out", "See all", "Customise Home" |
| `heroNumber` | Fraunces 48/52, 400 | `amountHero` | The one big figure |
| `figure` | Fraunces 32/36, 400 (steps down to 24/28) | `amountLarge` | Per day, Range, Spent, Saved |
| `rowTitle` | Hanken 16/24, 400 | `body` | Row titles |
| `rowMeta` | Hanken 13/16, 400, muted | `label` | Second line of a row |
| `rowAmount` | Fraunces 18/24, 500 | `amountRow` | Amounts in rows |

No size or weight changes: these are names for what exists, so every screen picks by role. No new library is needed for any of this.

## Order of work after approval (small commits)

1. Tokens and type roles; `Section`, `ListRow`, main-tab title, `AmountText` baseline and fit. Component snapshots updated.
2. Home. 3. Ledger. 4. Insights and heatmap. 5. More. 6. Plan. 7. Accounts, Categories, Customise, Settings. 8. Quick Add, Review, Net worth, History, the rest. 9. Sheets and empty states.
Each step: 360 dp and 130% snapshots in Paper and Ink, tests green, PDF page named.


## UI polish 2 (15 Nov 2026, branch `phase-ui-polish-2`)

Brief: spacing from tokens, quiet hierarchy, expandable rows, press feedback, theming check, cleanup. Decisions D263 to D267.

Done
- S1 end padding: already done in pass 1 (no 120/140/160 dp left; the 96 dp token is `FabInset`).
- Spacing literals: 57 left at the start; layout ones moved to `Dimens` (D267). Chart drawing constants stay.
- Press, tint and focus feedback in shared components (D265); haptic tick on expand.
- Tags (D263), expandable groups in Settings (D264), gallery section and snapshots for all three.

Not done, and why
- Ledger rows expanding in place: changes the Ledger tap; you chose Settings groups.
- Timeline-style Entry history rows, Home / Insights / More per-screen rhythm pass: not started. The first pass already set their spacing; a second look needs a phone check of the new rows first.
- Whole-screen snapshots (D246): still component-level only; this chat cannot build, so a refactor of the big screens would be untested.
- About 25 older `clickable` rows still use the default feedback.
- Unused composables and duplicate components: none found while editing; no full sweep was made.
- True black and accent picker: proposals only (D266). Contrast would need new Palette values and tests; the dark PDF forbids pure black.
- Not built or run here: no Android SDK in this workspace, so nothing was compiled or tested locally. CI is the first compile and re-records the snapshots.

### UI polish 2, part 2 (branch `phase-ui-polish-3`)
- Done: timeline rows for Entry history (D268); press feedback on the remaining rows (D269).
- Home, Insights, More: checked, no change (D270). More groups and status line (C5, M1) still undecided.
- Still not done: Ledger rows expanding, whole-screen snapshots (D246), True black and accent (D266), a sweep for unused composables.

## UI polish 3 (15 Nov 2026, branch `phase-ui-polish-4`)
Done: all Settings sections are ExpandGroups (D271); FootNote component; SectionHeader gap rule (D272); Accounts button row and currency case (D273).
Not checked on a phone yet. Open: whole-screen snapshots (D246); Quick Add "INR" comparison (money logic); More's groups (D270).

## Phase 11a: the v8 kit (16 Nov 2026, branch `phase-11a-kit`)
Scope: tokens, D277 to D283, the shared components, the gallery and screenshot tests. No screen changed. Reference: `Paddhu-Pusthakam-final-design-v8.pdf`, pages 3 to 5 (kit), 16 (date sheet), 36 (group headers).

How the kit stays out of the screens' way until 11b
- The new components live in `com.mansaketh.chitta.core.design.kit`, under the names the Phase 11 prompt uses (`Section`, `NavRow`, `ValueRow`, `ChittaButton`, `EmptyState`, `ExpandGroup`, `GaugeBar`). The old components of the same name stay in `core.design.components` and the app. A screen moves by changing its import in 11b, so no existing screen drew differently in 11a.
- `ChittaButton` (kit) has no forced full width and four kinds (Primary sindoor, Secondary, Link, Danger). The old `ChittaButton` (components) keeps its full width and its Primary / Attention / Quiet styles; its 142 call sites move in 11b.
- Where the v8 value differs from the old one, the old one keeps its name: `GlyphCircleLegacy` (32 dp), the old `SheetHandleWidth`, the old `sectionHeading` and `rowMeta` type roles, `Shapes.Stamp` (2 dp). New roles have new names (`sectionTitle`, `rowMeta14`, `sheetTitle`, `stepperLabel`, `badge`, `marginDay`).

Checked against the PDF (kit pages)
- Page 3: main bar with bell + badge and gear; detail bar with icon actions and bottom hairline; Section with trailing "See all"; NavRow with icon, sub, value and caret; SwitchRow (ink, both states); TickRow; CheckRow.
- Page 4: EntryRow with glyph, meta and tag; text field and error (existing `ChittaField`, unchanged); Primary, ButtonPair, Link + Danger; CategoryChip (selected and not); FilterChip + "Clear all".
- Page 5: Segmented; MonthStepper; badge 4 and 99+; stamps and tags; gauges (10, 20, over); MessageBar (save, error); EmptyState; EmptyLine.
- Page 16: DateSheet. Page 36: GroupHeader / ExpandGroup (icon, title, caret up or down, hairline).

Known gaps, so 11b to 11e do not assume otherwise
- Touch targets: chips (40, 36) and day cells (46 rows) keep a hit area of at least their spec'd height by a 48 dp outer box, except the Segmented parts, which are 34 dp tall because the prompt spec'd 34 dp parts in a 3 dp padded outline.
- The 10 s timeout for saves is a constant (`MESSAGE_BAR_SAVE_MILLIS`); the timer itself stays in `UndoCenter` and moves in 11b.
- `EntryRow` has no select mode. Multi-select in the Ledger is `CheckRow` per the prompt; how it looks beside the glyph is decided in 11c.
- `DateSheet` takes the first day of the week as a parameter; it is read from the Customise setting at the call site in 11b.
- Screenshots of the new sections are recorded by CI (`[record snapshots]` in the commit message) and need a look on the first run before merging.


## Phase 11b: call sites (16 Nov 2026, branch `phase-11b-callsites`)
Checked after the swap
- Old components left in `core.design.components`: none of SectionHeader, Section, ScreenTitle, ChittaTopBar, EmptyState, ErrorState, ButtonStyle, ChittaButton, ExpandGroup, UndoBar, GlyphCircleLegacy.
- Text glyphs ▾ ● ▲ ✓ – ○ in screens: none left.
Left on purpose (not in the 11b list)
- Literal dp in chart drawing (`InsightsCharts`, `BudgetRing`), tab-swipe distances and the Household radio ring (`HouseholdScreen`): same as D267.
- Quick Add keeps its own top (tabs, no title) with the old handle: the v8 sheet header needs a title and the prompt gives it none.
- Ledger rows (`LedgerRow` inside `DayGroup`, with its own selection circle) and Home's Coming up rows: not yet on `EntryRow` / `CheckRow`. The prompt lists the swap under the shared components but gives no 11b step for it, and `LedgerRow` carries long-press, tags, stamps and the 200% layout, so it needs your go-ahead. Open question in the 11b report.

## Phase 11c: daily screens (16 Nov 2026, branch `phase-11c-daily`)
Matched to the PDF: Home p.13, Safe to spend p.14 (Note only), Quick Add p.15, Ledger filtered p.18, Entry p.20, Split p.21, Delete p.22.
Not built (not in app)
- Ledger filtered: the "5 entries / total" line under the chips (the app has no filtered total); Home p.13 "Over pace" stamp placement kept as today.
- Entry p.20: the mono "Expense · from a bank message" line and the History list inline (History stays a row that opens its own screen).
- Bell list p.23: the "Ready" / "Needs a look" groups, counts and "Fix selected" (not in app).
Left as is, needs your decision
- Bell list rows keep their own tap (opens the row sheet to change account or category), with the shared tick box; `CheckRow` would make a tap only tick, which removes editing.
- Date sheet, Saved with Undo, Ledger first use, Delete layout: already on the kit from 11b / earlier in 11c.
Not verified on a phone: everything. Not compiled locally.

## Phase 11d: plan and review screens (16 Nov 2026, branch `phase-11d-plan-review`)
Matched to the PDF: Budgets p.24, Budget detail p.25 (Delete only), Goals p.26, Loans p.27, Bills p.28, Bill calendar p.29, Pay a bill p.30, Insights p.31, Accounts p.32, Mutual funds p.33 (unchanged), Import holdings p.34.
Not built (not in app)
- Budget detail p.25: the Spent / Forecast pair, the legend under the chart and the "Limits by category / Rollover / Alerts / History" rows. The app's detail screen has its own sections and stays as it is.
- Goals p.26: the month-savings note. Loans p.27: the "Owed to you / You owe" totals and the green "owed to you" amount.
- Bills p.28: the "Coming up" total beside the heading and "Show 6 paid". Calendar p.29: the day's bills inline under the grid (the app opens a sheet).
- Pay a bill p.30: the Full / Minimum / Other amount choice and the "Paid from" / "Paid on" rows. Insights p.31: "Compared with" line stays as the app words it.
- Accounts p.32: the Net worth heading and glyph circles on account rows; account groups for spending and cards. Mutual funds p.33: "Your funds" heading, the Invested / Gain pair.
Copy kept
- Titles and labels as the app words them ("Budget", "Show n more", "Reminders and auto-pay", "Choose file"). New headings only where the prompt asks for headed groups on Accounts.
Left as is
- Sinking funds tab and the other detail screens' stacked buttons (not in the 11d list), except Delete, which is the Danger button.
- Stale `DatePickerDialog` imports remain in `EntryDetailScreen` and `QuickAddSheet`; the 11e audit removes them.
Not verified on a phone: everything. Not compiled locally.

## Screen 05 Home (16 Nov 2026, branch `ui-05-home`, PDF p.13, exact match rule)
Checklist (Paper and Ink). Built to the page; none of it verified on a phone, and nothing compiled locally.
1. Top bar: tab icon, title, bell with count badge, gear. Built. The hide-amounts eye is also there (your answer), so it differs from the page.
2. "November budget" line: built; the caret shows only with more than one budget (your answer).
3. "Safe to spend" with info button: built.
4. Hero amount: built (steps down a size when it does not fit beside the ring).
5. "About X a day for N days": built.
6. Ring beside the hero, 100 dp: built from the page's measure.
7. Swatch-only legend: built.
8. Budget left and Range tiles with an ink rule on top: built.
9. "X spent" and the ink-outlined stamp: built.
10. Month rule, Today and Limit lines: built; the drawing is my reading of the page (D287 item 1).
11. Coming up with the total due: built.
12. Rounded-square add button: built; the 16 dp corner is measured.
13. Bottom nav with a calendar Plan icon: built.
Not drawn on the page, kept as the app has them: the "in review" row, the chosen sections, "Customise Home", the cash-check layout, the over-limit and no-budget states, the Estimate stamp, and the "Your cash is lower" line.
Known leftovers: the "How Safe to spend is worked out" sheet is much taller than p.14 (Screen 06); the add button floats over that sheet (Screen 06); Customise shows the calendar icon for both Plan and the heatmap.

## Screen 06 Safe to spend, explained (PDF p.14), 16 Nov 2026
Built, not compiled locally and not verified on a phone. Branch `ui-06-safe-to-spend-explained`. Your screenshot of the old sheet showed: nearly full height, add button over the sheet, bottom bar under it, paise in amounts, hairline above the total, no Close, no subtitle, no sub-lines.
1. Home behind a dimmed scrim: built (scrim from `BottomOverlay`); not verified on a phone.
2. Sheet about 60% tall, handle, 28 dp top corners: built as content height capped at 80%; not verified on a phone.
3. Two-line Fraunces title with Close: built.
4. Subtitle with "As of": built.
5. Three two-line rows with sub-lines and "–": built.
6. Solid ink rule above the total: built.
7. Total row with "÷ 18 days = ₹1,528 a day": built.
8. Cash check note with title and the account names: built.
9. Ink: same tokens; not verified on a phone.
10. Add button hidden and bottom bar covered while a sheet is open: built for every sheet.
Known leftover: Customise shows the calendar icon for both Plan and the heatmap (screen 29).

## Screen 07 Quick Add (PDF p.15), 16 Nov 2026, branch `ui-07-quick-add`, exact match rule

Checklist (Paper and Ink). "Built" means written to the page; nothing is marked "matches" because it has not been checked on a phone.
| # | Element on p.15 | State |
| --- | --- | --- |
| 1 | Handle only, no title, no close button | Built |
| 2 | Expense / Income / Transfer at the top left (was squeezed to "Expense | Inco") | Built, bug fixed |
| 3 | Pin and Split icons at the top right | Built; always drawn, dimmed until usable; glyphs end 12 dp from the screen edge (PDF: touching it) |
| 4 | Small muted ₹, hero figure, caret (sindoor Paper, ink Ink) | Built |
| 5 | "Safe to spend after this: ₹…" | Built (new); expense only |
| 6 | Title field, no label, 48 dp | Built |
| 7 | Category chips with coloured icons, selected 1.5 dp ink, no "Category" label, run under the screen edge | Built |
| 8 | Summary line "account · date · who sees it" and "More options" | Built |
| 9 | Hairline, then keypad without tiles, muted operators | Built |
| 10 | Save in sindoor with the amount ("Save ₹486"), brighter in Ink | Built |
| 11 | No scrolling at the default text size; sheet about 74% of the screen | Built as content height (cap 80%); about 40 dp taller than the page because of the keyboard link |
Known leftovers: the Home page behind the sheet still shows the hide-amounts eye (kept on Screen 05); the PDF draws no empty, foreign-currency, transfer or large-text state, so those follow the same styles by judgement; Customise shows the calendar icon for both Plan and the heatmap.


## Screen 10 Ledger filtered (PDF p.18), 16 Nov 2026, branch `ui-10-ledger-filtered`, exact match rule
Checklist (Paper and Ink). "Built" means written to the page; nothing is marked "matches" because nothing was compiled or checked on a phone.
| # | Element on p.18 | State |
| --- | --- | --- |
| 1 | Book icon and "Ledger" | Unchanged |
| 2 | Search field: icon, mono query, x, rule border, surface fill | Built (border was the darker mark colour) |
| 3 | "Search tips" small, muted, underlined, right | Built |
| 4 | Chips: Food & dining, Over ₹200, November, then "Clear all" | Built (typed filters and the month chip are new) |
| 5 | "5 entries" and the total, hairline under | Built (new) |
| 6 | Margin date, glyph circle, title, account line, Split tag, amount | Built; second line is the account alone while narrowed |
| 7 | No day totals, hairline under every row | Built |
| 8 | Add button, bottom bar | Unchanged |
Known leftovers: "Search tips" and "Clear all" keep a 48 dp tap area, so the gap above the chips may be a little taller than the page; the rule border on the field has less contrast than the old mark border; multi-month results and the empty state are not drawn on the page and follow the same styles by judgement.

## Screens 01 Lock and 02 First run (PDF p.9 and p.10), 17 Nov 2026, branch `ui-01-02-lock-firstrun`, exact match rule
Checklist (Paper and Ink). "Built" means written to the page; nothing is marked "matches" because nothing was compiled or checked on a phone.
| # | Element | State |
| --- | --- | --- |
| 1 | Lock: round logo, "Paddhu Pusthakam", "Enter your PIN" | Built (logo was a rounded square, text was "Your book is locked.") |
| 2 | Four dots, two filled | Built (new) |
| 3 | Red wrong-PIN line with tries left | Built (new) |
| 4 | Pad 1-9, 0, delete, no tiles | Built (new) |
| 5 | "Forgot PIN?" underlined, "Use fingerprint" button | Built (new); fingerprint hidden when none is enrolled |
| 6 | First run: round logo, large name, one line | Built |
| 7 | Two rows with icon, title, second line, caret, full-width hairlines | Built (were two buttons) |
| 8 | "Everything stays on this phone." | Built (replaces the Start fresh note) |
Known leftovers: logo sizes, title size, row spacing and the pad's row height are measured off the page images, not stated by it; the page draws no disabled-key look, wait countdown, choose-a-PIN or mismatch state, which follow the same styles by judgement.

## Screens 03 Bring over your book and 04 Dry run (PDF p.11 and p.12), 17 Nov 2026, branch `ui-03-04-bring-over-dry-run`, exact match rule
Checklist (Paper and Ink). "Built" means written to the page; nothing is marked "matches" because nothing was compiled or checked on a phone.
| # | Element | State |
| --- | --- | --- |
| 1 | Top bar: back, title, subtitle "Step N of 3 · nothing saved yet", hairline | Built (subtitle new) |
| 2 | File block: "From your Paddhu backup", file name in mono, "2.4 MB · Jan 2024 to Nov 2026" | Built (new) |
| 3 | Grid of six figures, hairline on top of each, Fraunces 24 and muted label | Built (new `StatGrid`) |
| 4 | "Needs your decision" with count, inset rule, rows with sub-line and caret, edge-to-edge rules | Built (new); only real findings, no receipt or "AI add" rows |
| 5 | "Start fresh" and "Run dry run" pinned, equal width | Built (were stacked in the scroll); Cancel instead of Start fresh outside First run |
| 6 | Dry run: headline, "Compared with Paddhu at 8:51 pm", full-width rule | Built (new) |
| 7 | Four result rows with a green tick, edge-to-edge rules | Built (new `ResultRow`); cross and warning colour when a total differs (not drawn) |
| 8 | Note box about the restore point | Built (existing `Note`) |
| 9 | "Cancel" and "Import N entries" pinned | Built (new); Import disabled on a mismatch |
Known leftovers: row height (page about 62 dp, kit 68), button height (page about 50 dp, kit 56), the label-to-file-name gap and the gaps around the grid are read off zoomed page images, not stated by it; Cancel on p.12 is drawn narrower than Import but kept equal; the Ink page was only looked at as an image.
