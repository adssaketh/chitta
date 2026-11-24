package com.mansaketh.chitta.shots

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Works the app instead of looking at it: add an entry through Quick Add and find it in the Ledger. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", shadows = [ShadowWorkManagerForTests::class])
class FlowTest : ShotBase() {
    @Test fun addEntryAndSeeItInLedger() {
        prepare()
        tap("Add", 0)
        shot("40_flow_open")
        tap("Title"); type("Flow test lunch")
        shot("41_flow_titled")
        tap("Groceries")
        tap("9", -1); tap("9", -1)
        shot("42_flow_amount")
        tap("Save ₹99")
        shot("43_flow_saved")
        tap("Ledger")
        shot("44_flow_ledger")
    }
}
