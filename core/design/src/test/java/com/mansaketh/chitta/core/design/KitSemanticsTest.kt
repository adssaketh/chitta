package com.mansaketh.chitta.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.kit.BadgedIconAction
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.CheckRow
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.DateSheet
import com.mansaketh.chitta.core.design.kit.FilterChip
import com.mansaketh.chitta.core.design.kit.MessageBar
import com.mansaketh.chitta.core.design.kit.MessageKind
import com.mansaketh.chitta.core.design.kit.MonthStepper
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.Segmented
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.TickRow
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 11a, definition of done: TalkBack reads every new control with the right role and label, and each one still works when tapped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class KitSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { ChittaTheme(mode = ThemeMode.Paper) { content() } }

    private fun roleOf(node: SemanticsNodeInteraction): Role? = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

    @Test fun switchRowIsASwitchAndTheWholeRowToggles() {
        val on = mutableStateOf(false)
        show { SwitchRow("App lock", on.value, { on.value = it }, sub = "Fingerprint or PIN on open") }
        assertEquals(Role.Switch, roleOf(rule.onNodeWithText("App lock")))
        rule.onNodeWithText("Fingerprint or PIN on open").performClick()
        assertTrue(on.value)
        rule.onNodeWithText("App lock").performClick()
        assertFalse(on.value)
    }

    @Test fun tickRowIsARadioButtonAndReportsItsState() {
        val picked = mutableStateOf("Paper")
        show {
            Column {
                TickRow("Paper", picked.value == "Paper", { picked.value = "Paper" })
                TickRow("Ink", picked.value == "Ink", { picked.value = "Ink" })
            }
        }
        assertEquals(Role.RadioButton, roleOf(rule.onNodeWithText("Ink")))
        assertEquals(true, rule.onNodeWithText("Paper").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithText("Ink").performClick()
        assertEquals("Ink", picked.value)
    }

    @Test fun checkRowIsACheckbox() {
        val ticked = mutableStateOf(false)
        show { CheckRow("HDFC savings", ticked.value, { ticked.value = it }, sub = "Ready") }
        assertEquals(Role.Checkbox, roleOf(rule.onNodeWithText("HDFC savings")))
        rule.onNodeWithText("HDFC savings").performClick()
        assertTrue(ticked.value)
    }

    @Test fun navRowIsAButton() {
        var opened = false
        show { NavRow("Accounts", { opened = true }, icon = ChittaIcons.Wallet, value = "6") }
        assertEquals(Role.Button, roleOf(rule.onNodeWithText("Accounts")))
        rule.onNodeWithText("Accounts").performClick()
        assertTrue(opened)
    }

    @Test fun badgedIconButtonLabelCarriesTheCount() {
        var clicks = 0
        show {
            Column {
                BadgedIconAction(ChittaIcons.Bell, "Bank messages", 4, { clicks++ })
                BadgedIconAction(ChittaIcons.Gear, "Other messages", 120, {})
            }
        }
        rule.onNodeWithContentDescription("Bank messages, 4").performClick()
        assertEquals(1, clicks)
        rule.onNodeWithContentDescription("Other messages, more than 99").assertExistsCompat()
    }

    @Test fun filterChipReadsRemoveFilterAndRemoves() {
        var removed = false
        show { FilterChip("Food & dining", { removed = true }) }
        rule.onNodeWithContentDescription("Remove filter: Food & dining").performClick()
        assertTrue(removed)
    }

    @Test fun segmentedPartsAreRadioButtons() {
        val chosen = mutableStateOf(0)
        show { Segmented(listOf("Bars", "Donut"), chosen.value, { chosen.value = it }) }
        assertEquals(Role.RadioButton, roleOf(rule.onNodeWithText("Donut")))
        rule.onNodeWithText("Donut").performClick()
        assertEquals(1, chosen.value)
    }

    @Test fun monthStepperHasBothArrowsLabelledAndHonoursTheEnds() {
        var earlier = 0
        var later = 0
        show { MonthStepper("November 2026", { earlier++ }, { later++ }, canLater = false) }
        rule.onNodeWithContentDescription("Earlier month").performClick()
        rule.onNodeWithContentDescription("Later month").performClick()
        assertEquals(1, earlier)
        assertEquals(0, later)
        rule.onNodeWithText("November 2026").assertExistsCompat()
    }

    @Test fun buttonsAreButtonsAndDangerHasItsWords() {
        var saved = 0
        var deleted = 0
        show {
            Column {
                ChittaButton("Save", { saved++ })
                ChittaButton("Delete entry", { deleted++ }, kind = ButtonKind.Danger)
            }
        }
        assertEquals(Role.Button, roleOf(rule.onNodeWithText("Save")))
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Delete entry").performClick()
        assertEquals(1, saved)
        assertEquals(1, deleted)
    }

    @Test fun errorMessageBarCanBeClosedAndRetried() {
        var retried = false
        var closed = false
        show { MessageBar("Backup failed. Nothing was lost.", kind = MessageKind.Error, actionLabel = "Retry", onAction = { retried = true }, onClose = { closed = true }) }
        rule.onNodeWithText("Retry").performClick()
        rule.onNodeWithContentDescription("Close message").performClick()
        assertTrue(retried)
        assertTrue(closed)
    }

    @Test fun saveMessageBarShowsUndo() {
        var undone = false
        show { MessageBar("Saved Swiggy, ₹486", actionLabel = "Undo", onAction = { undone = true }) }
        rule.onNodeWithText("Undo").performClick()
        assertTrue(undone)
    }

    @Test fun dateSheetReturnsTheTappedDay() {
        var used: LocalDate? = null
        show {
            DateSheet(
                initial = LocalDate.of(2026, 11, 10),
                today = LocalDate.of(2026, 11, 12),
                firstDayOfWeek = DayOfWeek.MONDAY,
                onUse = { used = it },
            )
        }
        rule.onNodeWithText("Use 10 Nov").assertExistsCompat()
        rule.onNodeWithContentDescription("Saturday 14 November 2026").performClick()
        rule.onNodeWithText("Use 14 Nov").performClick()
        assertEquals(LocalDate.of(2026, 11, 14), used)
    }

    @Test fun dateSheetQuickPicksYesterday() {
        var used: LocalDate? = null
        show {
            DateSheet(
                initial = LocalDate.of(2026, 11, 10),
                today = LocalDate.of(2026, 11, 12),
                firstDayOfWeek = DayOfWeek.SUNDAY,
                onUse = { used = it },
            )
        }
        rule.onNodeWithText("Yesterday").performClick()
        rule.onNodeWithText("Use 11 Nov").performClick()
        assertEquals(LocalDate.of(2026, 11, 11), used)
    }

    @Test fun dateSheetMonthStepMovesToTheEarlierMonth() {
        var used: LocalDate? = null
        show {
            DateSheet(
                initial = LocalDate.of(2026, 11, 10),
                today = LocalDate.of(2026, 11, 12),
                firstDayOfWeek = DayOfWeek.SUNDAY,
                onUse = { used = it },
            )
        }
        rule.onNodeWithContentDescription("Earlier month").performClick()
        rule.onNodeWithText("October 2026").assertExistsCompat()
        rule.onNodeWithContentDescription("Wednesday 14 October 2026").performClick()
        rule.onNodeWithText("Use 14 Oct").performClick()
        assertEquals(LocalDate.of(2026, 10, 14), used)
    }

    private fun SemanticsNodeInteraction.assertExistsCompat() {
        fetchSemanticsNode() // throws if the node is missing
    }
}
