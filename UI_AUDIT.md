# UI audit: spacing, rhythm, consistency

Phase: UI polish pass. Branch `phase-ui-polish`. Written 14 Nov 2026 against `main` at 2cd743b.
Status: **audit and proposal only. No screen code has changed.** Waiting for approval.

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
