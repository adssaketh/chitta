# Decisions

Status: **decided** once you approved (13 Nov 2026), unless marked otherwise.

| # | Date | Decision | Why | Status |
|---|---|---|---|---|
| D1 | 13 Nov 2026 | Chitta lives in its own private repo `adssaketh/Chitta`, one branch per phase. | Master section 9; the Paddhu repo is public and Flutter. | decided |
| D2 | 13 Nov 2026 | Where master prompt and design PDF disagree, master wins and the conflict is reported. | Project instructions. | decided |
| D3 | 13 Nov 2026 | No database encryption (SQLCipher) for now; revisit as encrypted backup files in Phase 7. | APK size target, migration and Drive backup complexity; Android file encryption plus app lock already cover a lost phone. Not yet verified against SQLCipher's current size. | decided |
| D4 | 13 Nov 2026 | Range averages this period; "Not enough data yet" before day 7. | Matches PDF p.3 and master section 12. | decided |
| D5 | 13 Nov 2026 | Lock uses the system biometric/device-credential prompt; no in-app PIN (overrides PDF p.6). | Master section 9. | decided |
| D6 | 13 Nov 2026 | Importer reads backup `.sqlite` files with `user_version` 33 to 48 only; older files get "update Paddhu first". | Schema files in `drift_schemas/` start at v33. | decided |
| D7 | 13 Nov 2026 | Drop confetti on goals. | Master section 4 bans gamification. | decided |
| D8 | 13 Nov 2026 | Money is stored as integer paise for every account. **Assumption:** you did not name any account with a decimals setting other than 2. Re-check against a real backup in Phase 3. | Master section 2, rule 4. | decided, to verify |
| D9 | 13 Nov 2026 | Build pinned transactions and restore points (Data Recovery) as new work. Drop tags, Cashew "Intelligence" and the broadcast receiver. | They are not in the Paddhu source; master lists them as existing. | decided |
| D10 | 13 Nov 2026 | Drop Gmail email scanning and Firestore shared budgets (household replaces them). Keep notification scanning. | Gmail is outside the network list in master section 2. | decided |
| D11 | 13 Nov 2026 | Attachments stay on the phone only; no Drive upload. | Master section 2, rule 2. | decided |
| D12 | 13 Nov 2026 | Drop the bill splitter. | Settle up covers shared household entries. | decided |
| D13 | 13 Nov 2026 | Lock uses the phone's system prompt; no in-app PIN. | Master section 9 over design PDF p.6 (C1). | decided |
| D14 | 13 Nov 2026 | Undrawn screens are drawn in their build phase, from existing parts. | Phase 1 is placement only. | decided |
