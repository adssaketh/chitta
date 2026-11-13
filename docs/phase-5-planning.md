# Phase 5: planning

Branch `phase-5-planning`. Screens follow the final design: Budget detail p.19, Goals p.20, Loans p.21, Bill calendar p.22, Pay a bill p.23. The Budgets list page was not checked.

## What was built
- Plan tab: Budgets, Goals, Loans, Bills, Calendar. A bell in the header opens Bills and reminders.
- Rules live in `:core:domain` (Periods, Bills, Budgets, Goals, Loans, BillCalendar, Reminders) with 71 new tests. Storage lives in `PlanRepository`; entries still go through `LedgerRepository` so every change has a history row and an Undo.
- Database v3: `anchorDay` on entries. The v1 to v2 to v3 migration is tested against the committed schema files.
- Reminders: `ReminderWorker` posts today's reminders, then queues the next run. Two channels: Due bills, Log reminder.

## Check on your phone
1. Open Plan, Budgets. Compare each imported budget's spent figure with Paddhu's screen (D46).
2. Compare balances on Accounts with Paddhu (D45 changes lent and borrowed).
3. Pay a monthly bill dated the 31st, then look at the next one (D50).
4. Switch on a due-bill reminder, allow notifications, and set the time two minutes ahead.
5. Deny notifications in Android settings and open the bell: the plain note and the link should show.

## Open
- D47: "added only" budgets count like normal ones.
- Shared loans (Phase 7), fixed deposits, rollover, sinking funds and card dues (Phase 8) are not here.
