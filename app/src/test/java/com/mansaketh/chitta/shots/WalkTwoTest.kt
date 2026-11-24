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
        tap("Loans"); tap("Kiran"); shot("22_loan_detail"); back()
        tap("Bills"); tap("Electricity"); shot("23_bill_sheet"); tap("Close")
        tap("More", -1)
        tap("Rules"); shot("24_rules"); back()
        tap("Household"); shot("25_household"); back()
        tap("Mutual funds"); shot("26_mutual_funds"); back()
        tap("Settings"); tap("Backup and restore"); shot("27_settings_backup"); tap("Google Drive backup"); shot("28_drive_backup"); back()
        tap("Delete everything"); shot("29_delete_confirm")
    }
}
