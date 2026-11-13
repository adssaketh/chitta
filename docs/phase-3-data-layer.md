# Phase 3: data layer

Branch: `phase-3-data-layer`. No screens.

## Modules
- `:core:model` (pure Kotlin): enums with stored codes; `Book` and its row classes.
- `:core:money`: adds `paiseFromDriftDouble`, `paiseFromDecimal`.
- `:core:importer` (pure Kotlin): `PaddhuImporter.read(BackupSource)` returns `Refused(reason)` or `Ready(book, report)`. Writes nothing.
- `:core:database` (Android, Room v1): 13 tables, DAOs, `BookRepository.writeFresh/readAll`, `AndroidBackupSource` (read-only), account/category/transaction repositories.

## Import flow (used by the Phase 4 screens)
1. Copy the picked file into app cache. 2. `AndroidBackupSource(file)` and `PaddhuImporter().read(...)`. 3. Show the `Report` (counts, rounded amounts, per-account conversion check, items not carried over, issues). 4. On approval, `BookRepository.writeFresh(book)`: one transaction, refuses a non-empty database.

## Checked on a real backup (101 entries)
Schema 48; 3 accounts, all INR, 2 decimals; dates are integer epoch seconds; amounts REAL, all whole paise; every `type` NULL.

## Not done or not verified
- The importer has not run on the real file itself, only on a fixture with the real column layout. First real run comes with the Phase 4 screen.
- Account sums are plain sums. Paddhu's balance rules (paid, upcoming, lent, borrowed) are decided in Phase 4 against Paddhu's on-screen balances.
- Undo of an import for 30 days, the restore point before import, and household re-check are Phase 4/7 work.
- Ping review queue and inbox are files on the old phone, not in the backup (Phase 0 note).
- No Room migration yet (version 1). Schema JSON is committed for the first one.
