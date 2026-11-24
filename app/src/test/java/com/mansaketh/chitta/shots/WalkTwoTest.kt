package com.mansaketh.chitta.shots

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Walk 2: detail screens and sheets (budget, goal, loan, pay a bill, delete confirm, Rules, Household, Mutual funds). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", shadows = [ShadowWorkManagerForTests::class])
class WalkTwoTest : ShotBase() {
    @Test fun walk() {
        prepare()
        tap("Plan")
        tap("Budgets"); tap("November budget"); shot("20_budget_detail"); back()
        tap("Goals"); tap("Emergency fund"); shot("21_goal_detail"); back()
        tap("More", -1)
        tap("Accounts"); tap("Mutual funds"); shot("26_mutual_funds"); back(); back()
        tap("Settings")
        tap("Import and export"); tap("Rules"); shot("24_rules"); back()
        tap("Household", 0); tap("Household", -1); shot("25_household"); back()
        tap("Backup and restore"); shot("27_settings_backup"); tap("Google Drive backup"); shot("28_drive_backup"); back()
        tap("Data"); tap("Delete everything"); shot("29_delete_confirm")
    }

    @Test fun sheets() {
        prepare()
        tap("Plan"); tap("Loans"); tap("Kiran"); shot("22_loan_sheet")
    }

    @Test fun billSheet() {
        prepare()
        tap("Plan"); tap("Bills"); tap("Electricity"); shot("23_bill_sheet"); tap("Pay another amount or from another account"); shot("23b_pay_sheet")
    }

    @Test fun entryDelete() {
        prepare()
        tap("Ledger"); tap("Dmart"); tap("Delete entry"); shot("30_entry_delete")
    }

    @Test fun entrySplit() {
        prepare()
        tap("Ledger"); tap("Dmart"); tap("Split"); shot("31_entry_split")
    }
}
