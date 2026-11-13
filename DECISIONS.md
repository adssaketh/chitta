# Decisions

Status: **decided** once you approved (13 Nov 2026), unless marked otherwise.

| # | Date | Decision | Why | Status |
|---|---|---|---|---|
| D1 | 13 Nov 2026 | Chitta lives in its own repo `adssaketh/chitta`, one branch per phase. **Changed in Phase 2:** the repo is public (you chose this); master says private. | Master section 9; the Paddhu repo is public and Flutter. | decided, conflict noted |
| D2 | 13 Nov 2026 | Where master prompt and design PDF disagree, master wins and the conflict is reported. | Project instructions. | decided |
| D3 | 13 Nov 2026 | No database encryption (SQLCipher) for now; revisit as encrypted backup files in Phase 7. | APK size target, migration and Drive backup complexity; Android file encryption plus app lock already cover a lost phone. Not yet verified against SQLCipher's current size. | decided |
| D4 | 13 Nov 2026 | Range averages this period; "Not enough data yet" before day 7. | Matches PDF p.3 and master section 12. | decided |
| D5 | 13 Nov 2026 | Lock uses the system biometric/device-credential prompt; no in-app PIN (overrides PDF p.6). | Master section 9. | decided |
| D6 | 13 Nov 2026 | Importer reads backup `.sqlite` files with `user_version` 33 to 48 only; older files get "update Paddhu first". | Schema files in `drift_schemas/` start at v33. | decided |
| D7 | 13 Nov 2026 | Drop confetti on goals. | Master section 4 bans gamification. | decided |
| D8 | 13 Nov 2026 | Money is stored as integer paise for every account. **Assumption:** no account uses a decimals setting other than 2. Re-check against a real backup in Phase 3. | Master section 2, rule 4. | decided, to verify |
| D9 | 13 Nov 2026 | Build pinned transactions and restore points (Data Recovery) as new work. Drop tags, Cashew "Intelligence" and the broadcast receiver. | Not in the Paddhu source; master lists them as existing. | decided |
| D10 | 13 Nov 2026 | Drop Gmail email scanning and Firestore shared budgets (household replaces them). Keep notification scanning. | Gmail is outside the network list in master section 2. | decided |
| D11 | 13 Nov 2026 | Attachments stay on the phone only; no Drive upload. | Master section 2, rule 2. | decided |
| D12 | 13 Nov 2026 | Drop the bill splitter. | Settle up covers shared household entries. | decided |
| D13 | 13 Nov 2026 | Lock uses the phone's system prompt; no in-app PIN. | Master section 9 over design PDF p.6 (C1). | decided |
| D14 | 13 Nov 2026 | Undrawn screens are drawn in their build phase, from existing parts. | Phase 1 is placement only. | decided |
| D15 | 13 Nov 2026 | Three modules: `:app`, `:core:design` (Android), `:core:money` (pure Kotlin). | Money rules run as fast JVM tests with no Android; design can't touch them by accident. | decided |
| D16 | 13 Nov 2026 | Fonts: Hanken Grotesk (interface), Fraunces variable (amounts), IBM Plex Mono (stamps, codes). All OFL, bundled, no network. | Master fonts. | decided |
| D17 | 13 Nov 2026 | Digits are drawn in equal-width slots (`TabularText`), not via font features. | Fraunces has no tabular figures, so columns would not line up. | decided |
| D18 | 13 Nov 2026 | Every ₹ is drawn in Fraunces, even inside Hanken sentences (`rupeeAware`). | Hanken Grotesk has no ₹ glyph; it would fall back to a random system font. | decided |
| D19 | 13 Nov 2026 | Light-theme tokens changed: warning text `#C9331B`, mark grey `#8C877B` (dark `#7A7468`), on-sindoor white. | The PDF values failed WCAG contrast (text 4.5:1, field borders 3:1). Tests now guard this. | decided, tell designer |
| D20 | 13 Nov 2026 | Icons: Phosphor Regular (MIT), generated into `ChittaIcons.kt` by `tools/gen_icons.py`. 39 icons. | Master choice; no runtime icon library, so APK stays small. Phosphor's npm package was last published March 2024 (v2.1.1). | decided |
| D21 | 13 Nov 2026 | Stable-only versions: Kotlin 2.4.21, AGP 9.5.1, Gradle 9.9.1, Compose BOM 2026.09.00, minSdk 26, compileSdk 37. No Material 3 Expressive alpha. | Project rule on alphas. Compose 1.12 requires compileSdk 37. | decided |
| D22 | 13 Nov 2026 | Screenshot tests use Roborazzi 1.79.0 + Robolectric 4.17, not Paparazzi. | Paparazzi 1.3.5 fails under AGP 9. | decided |
| D23 | 13 Nov 2026 | Component gallery is debug-only; release shows a plain placeholder. | Keeps it out of the shipped app. | decided |
| D24 | 13 Nov 2026 | At 150% text and above, ledger rows put the amount under the title; stamps never wrap. | Verified at 200% in snapshots; stamps were clipped before. | decided |
| D25 | 13 Nov 2026 | No Gradle wrapper committed; CI pins Gradle 9.9.1 via `setup-gradle`. | No wrapper could be generated here. Add one from Termux later if wanted. | decided |
