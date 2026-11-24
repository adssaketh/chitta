package com.mansaketh.chitta.shots

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Walk 1: Ledger, entry detail, Quick Add, Plan tabs, Insights tabs, Accounts, Categories, Settings, Customise. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", shadows = [ShadowWorkManagerForTests::class])
class WalkOneTest : ShotBase() {
    @Test fun walk() {
        prepare()
        shot("01_home")
        tap("is worked out", 0); shot("02_home_info"); tap("Close")
        // Quick Add
        tap("Add", 0); shot("03_quick_add"); back()
        // Ledger
        tap("Ledger"); shot("04_ledger")
        tap("Search"); type("cat:food amt>200"); shot("05_ledger_filtered"); tap("Close search")
        tap("Dmart"); shot("06_entry_detail"); back()
        // Plan tabs
        tap("Plan")
        for ((i, t) in listOf("Budgets", "Goals", "Loans", "Bills", "Calendar", "Sinking funds").withIndex()) { tap(t); shot("07_plan_${i}_${t.lowercase().replace(' ', '_')}") }
        // Insights tabs
        tap("Insights")
        for ((i, t) in listOf("Current", "History", "Net worth", "Review").withIndex()) { tap(t); shot("08_insights_${i}_${t.lowercase().replace(' ', '_')}") }
        // More shortcuts
        tap("More", -1); tap("Accounts"); shot("09_accounts"); back()
        tap("Categories"); shot("10_categories"); back()
        tap("Customise Home and tabs"); shot("11_customise"); back()
        tap("Settings"); shot("12_settings"); back()
        tap("Bring over your book"); shot("13_bring_over"); back()
        // bell
        tap("Home"); tap("Bank messages"); shot("14_bell_intake")
    }
}
