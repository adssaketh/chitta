# Phase 1: design check

Date: 13 Nov 2026. Branch: `phase-1-design-check`. No app code.

Sources: master prompt, `Paddhu-Pusthakam-final-design.pdf` (31 screens, v7), `Paddhu-Pusthakam-dark-mode.pdf` (10 screens), `claude/phase-0-audit.md`, decisions D1 to D11.

## Result
- 31 light screens compared with the Phase 0 inventory. Features with no screen, and where each goes, are in the table below (placement only, nothing new drawn).
- Dark PDF covers 10 screens (light 01, 04, 05, 06, 07, 13, 14, 19, 20, 30). The other 21 are drawn in Ink in the web page, plus the lock screen redrawn (conflict C1).
- Every dark text pair re-computed from hex: ink 15.3, muted 6.9 (6.4 on surface), dark text on red 6.1, warning 8.2 (7.5 on surface), green 8.2 (7.6 on surface). All pass 4.5:1 and match the dark PDF.

## Placement map

| Feature | Proposed home | Reuses |
|---|---|---|
| Fixed deposits | Accounts → “Assets” list beside Mutual funds, same row style; detail pushes like Mutual funds. Reminders appear on the Bill calendar. Net worth toggle sits on the Accounts total. | Accounts (p.25), Mutual funds (p.26) |
| Card dues from SMS | Shown as the card’s outstanding on Accounts (the card drawn on p.25). The payment message is not an entry: Import review lists it as one line “Updates card dues only”. | Accounts, Import review (p.28) |
| Instalment loans | Plan → Loans, a row marked “Instalment”; detail sheet lists the schedule, GST on interest and fee, and what is still owed. Net worth subtracts the balance. | Loans (p.21), Pay a bill (p.23) |
| Shared loans and deposits | “Who can see this” sheet from the loan or deposit detail, built like Choose what to share. Linked from Household → Your sharing. | Household (p.31), Choose what to share (p.33) |
| PhonePe import | More → Import & export → PhonePe (screenshot, PDF or CSV). Ends in Import review; nothing is saved before you confirm. | More (p.30), Import review |
| CSV import and mapping | Same entry point as PhonePe. Column mapping is one list screen of rows with a picker each, then Import review. | Import review |
| Categories and rules editor | More → Categories & rules (row already drawn). Two tabs: Categories (reorder, archive, merge) and Rules. A rule shows a preview of matches before it applies to history. Notification-scan templates become rules. | More (p.30), Ledger filtered (p.13) for the match list |
| Pinned entries | A “Pinned” strip as the first row of Quick Add; add one by long-pressing a saved entry. Manage under Settings → Transactions. Long-press on + opens the extra-add menu. | Quick Add (p.11) |
| Month review | A one-line row at the top of Insights on the 1st, opening one calm screen. No badges. | Insights (p.24) |
| Activity log and restore deleted | Backup page gets a “Recently deleted and changed” list under Restore points; each entry’s own history stays on Entry detail. | Backup (p.36), Entry detail (p.15) |
| Transfers, balance correction, correct total | Long-press on + (extra-add menu) and the Accounts actions row. | Accounts, Quick Add |
| Exchange rates and overrides | Accounts → currency line opens a rates sheet; per-entry override under “More options” in Quick Add. | Accounts, Quick Add |
| Credit-card limit, due date, utilisation | Card setup sheet from the card on Accounts; due date also feeds the Bill calendar. | Accounts, Bill calendar (p.22) |
| Subscriptions and upcoming list | Plan → Bills gets a Calendar / List switch; the list shows the total and monthly average. Detected subscriptions appear as one suggestion row. | Bill calendar |
| Sinking funds, rollover | Sinking funds are a goal type on Plan → Goals. Rollover is a switch in the budget’s edit sheet. | Goals (p.20), Budget detail (p.19) |
| Heatmap, customisable home and tabs | Insights section and Settings → Appearance → Home layout and tabs. | Insights, Settings (p.35) |
| Receipt capture and attachments | Camera icon in Quick Add and on Entry detail; files stay on the phone (D11). | Entry detail |
| Home widgets, shortcuts, Quick Settings tile | Settings → Appearance → Widgets and shortcuts. | Settings |
| Notification scanning, app links, reminders | Settings → Automation and Notifications groups. | Settings |
| Lock settings, delete all data, hide amounts | Settings → Security and Data; hide amounts is the eye icon already on Home. | Settings, Home (p.9) |
| Archive an account | Account edit sheet, opened by long-press on Accounts. | Accounts |
| Bill splitter | Dropped by your decision. Recorded in DECISIONS.md as D12. Settle up covers shared household entries. | none |

## Ink screens drawn (light page in brackets)

- Lock, redrawn for the phone prompt (C1 · replaces light p.6 and dark p.4)
- Bring over your book (Light p.7 · screen 02/31)
- Dry run (Light p.8 · screen 03/31)
- Ledger, filtered (Light p.13 · screen 08/31)
- Ledger, first use (Light p.14 · screen 09/31)
- Entry detail (Light p.15 · screen 10/31)
- Split, not balanced (Light p.16 · screen 11/31)
- Delete (Light p.17 · screen 12/31)
- Goals (Light p.20 · screen 15/31)
- Loans (Light p.21 · screen 16/31)
- Bill calendar (Light p.22 · screen 17/31)
- Pay a bill (Light p.23 · screen 18/31)
- Mutual funds (Light p.26 · screen 21/31)
- Import holdings (Light p.27 · screen 22/31)
- Import review (Light p.28 · screen 23/31)
- Settle up (Light p.29 · screen 24/31)
- More (Light p.30 · screen 25/31)
- Household (Light p.31 · screen 26/31)
- Invite sheet (Light p.32 · screen 27/31)
- Choose what to share (Light p.33 · screen 28/31)
- Who spent how much (Light p.34 · screen 29/31)
- Backup (Light p.36 · screen 31/31)

## Decisions needing your confirmation
- D12: Bill splitter dropped (your answer). Settle up covers shared entries.
- D13: Lock redrawn around the phone's system prompt; no in-app PIN (C1, follows master).
- D14: Placement map above is the plan; undrawn screens are drawn in their build phase, from existing parts.

## Not verified
- Page images were read as small renders; fine details (exact spacing) were not measured.
- Fonts: preview used web fonts; the ₹ glyph check for Fraunces, Hanken Grotesk and IBM Plex Mono is still open for Phase 2.
- Some placements rest on screen names only (for example the Insights month-review row); confirm when that screen is built.
