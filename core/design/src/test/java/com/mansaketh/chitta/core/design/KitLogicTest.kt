package com.mansaketh.chitta.core.design

import com.mansaketh.chitta.core.design.kit.MESSAGE_BAR_SAVE_MILLIS
import com.mansaketh.chitta.core.design.kit.badgeText
import com.mansaketh.chitta.core.design.kit.badgedLabel
import com.mansaketh.chitta.core.design.kit.monthCells
import com.mansaketh.chitta.core.design.kit.useDateLabel
import com.mansaketh.chitta.core.design.kit.weekdayOrder
import com.mansaketh.chitta.core.design.tokens.ChittaType
import com.mansaketh.chitta.core.design.tokens.Dimens
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 11a: the kit's plain logic and its tokens, checked against the v8 prompt's §3 table and §4 rules. */
class KitLogicTest {
    @Test fun badgeShowsOneToNinetyNineThenNinetyNinePlus() {
        assertNull(badgeText(0))
        assertNull(badgeText(-3))
        assertEquals("1", badgeText(1))
        assertEquals("99", badgeText(99))
        assertEquals("99+", badgeText(100))
        assertEquals("99+", badgeText(4_000))
    }

    @Test fun badgedButtonLabelIncludesTheCount() {
        assertEquals("Bank messages", badgedLabel("Bank messages", 0))
        assertEquals("Bank messages, 4", badgedLabel("Bank messages", 4))
        assertEquals("Bank messages, 99", badgedLabel("Bank messages", 99))
        assertEquals("Bank messages, more than 99", badgedLabel("Bank messages", 120))
    }

    @Test fun saveMessagesStayTenSeconds() {
        assertEquals(10_000L, MESSAGE_BAR_SAVE_MILLIS)
    }

    @Test fun dateButtonSaysUseAndTheDay() {
        assertEquals("Use 10 Nov", useDateLabel(LocalDate.of(2026, 11, 10)))
        assertEquals("Use 1 Jan", useDateLabel(LocalDate.of(2027, 1, 1)))
    }

    @Test fun weekdayRowStartsOnTheChosenDay() {
        assertEquals(DayOfWeek.MONDAY, weekdayOrder(DayOfWeek.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, weekdayOrder(DayOfWeek.MONDAY).last())
        assertEquals(DayOfWeek.SUNDAY, weekdayOrder(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, weekdayOrder(DayOfWeek.SUNDAY).last())
        assertEquals(7, weekdayOrder(DayOfWeek.WEDNESDAY).toSet().size)
    }

    @Test fun novemberTwentyTwentySixOnAMondayStartGridMatchesThePdf() {
        // PDF page 16: 1 Nov 2026 is a Sunday, so with Monday first it sits in the last column of the first row, under S.
        val cells = monthCells(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(42, cells.size) // six weeks: 6 empty cells, 30 days, 6 empty cells
        assertEquals(6, cells.indexOfFirst { it != null })
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), cells[35])
        assertEquals(6, cells.size / 7)
    }

    @Test fun gridIsAlwaysWholeWeeksWithEveryDayOnce() {
        for (first in DayOfWeek.entries) {
            for (month in 1..12) {
                val ym = YearMonth.of(2026, month)
                val cells = monthCells(ym, first)
                assertEquals(0, cells.size % 7, "$ym starting $first")
                assertEquals(ym.lengthOfMonth(), cells.count { it != null })
                val days = cells.filterNotNull()
                assertEquals((1..ym.lengthOfMonth()).toList(), days.map { it.dayOfMonth })
                assertEquals(first, cells.first()?.dayOfWeek ?: first, "first cell of $ym is a $first or empty")
            }
        }
    }

    @Test fun leapDayIsInTheGrid() {
        val cells = monthCells(YearMonth.of(2028, 2), DayOfWeek.SUNDAY)
        assertTrue(cells.contains(LocalDate.of(2028, 2, 29)))
        assertEquals(29, cells.count { it != null })
    }

    @Test fun tokensMatchTheV8Table() {
        assertEquals(4f, Dimens.CornerTag.value)
        assertEquals(12f, Dimens.CornerControl.value)
        assertEquals(28f, Dimens.CornerSheet.value)
        assertEquals(40f, Dimens.GlyphCircle.value)
        assertEquals(20f, Dimens.GlyphIcon.value)
        assertEquals(46f, Dimens.SwitchWidth.value)
        assertEquals(28f, Dimens.SwitchHeight.value)
        assertEquals(19f, Dimens.SwitchKnob.value)
        assertEquals(1.5f, Dimens.SwitchBorder.value)
        assertEquals(22f, Dimens.TickBoxSize.value)
        assertEquals(1.5f, Dimens.TickBoxBorder.value)
        assertEquals(20f, Dimens.BadgeMin.value)
        assertEquals(2f, Dimens.BadgeRing.value)
        assertEquals(5f, Dimens.BadgeTop.value)
        assertEquals(3f, Dimens.BadgeEnd.value)
        assertEquals(56f, Dimens.ButtonHeight.value)
        assertEquals(48f, Dimens.ButtonHeightSmall.value)
        assertEquals(40f, Dimens.ChipHeight.value)
        assertEquals(36f, Dimens.FilterChipHeight.value)
        assertEquals(3f, Dimens.SegmentPad.value)
        assertEquals(34f, Dimens.SegmentHeight.value)
        assertEquals(9f, Dimens.SegmentRadius.value)
        assertEquals(76f, Dimens.TopBarMainHeight.value)
        assertEquals(64f, Dimens.TopBarDetailHeight.value)
        assertEquals(36f, Dimens.SheetHandleSizeW.value)
        assertEquals(4f, Dimens.SheetHandleSizeH.value)
        assertEquals(2f, Dimens.SheetHandleRadius.value)
        assertEquals(6f, Dimens.SheetHandleGap.value)
        assertEquals(56f, Dimens.MessageBarMinHeight.value)
        assertEquals(16f, Dimens.MessageBarInset.value)
        assertEquals(88f, Dimens.MessageBarBottom.value)
        assertEquals(56f, Dimens.RowMinOneLine.value)
        assertEquals(68f, Dimens.RowMinTwoLine.value)
        assertEquals(24f, Dimens.IconTopBar.value)
        assertEquals(26f, Dimens.IconMainTitle.value)
        assertEquals(22f, Dimens.IconRowLeading.value)
        assertEquals(18f, Dimens.IconCaret.value)
        assertEquals(18f, Dimens.IconChip.value)
        assertEquals(14f, Dimens.IconFilterX.value)
        assertEquals(6f, Dimens.GaugeHeight.value)
        assertEquals(3f, Dimens.GaugeGap10.value)
        assertEquals(2f, Dimens.GaugeGap20.value)
        assertEquals(46f, Dimens.DateRow.value)
        assertEquals(40f, Dimens.DayCircle.value)
        assertEquals(1.5f, Dimens.TodayRing.value)
        assertEquals(20f, Dimens.GroupTop.value)
        assertEquals(12f, Dimens.GroupBottom.value)
        assertEquals(32f, Dimens.SectionGap.value)
        assertEquals(12f, Dimens.HeadingGap.value)
        assertEquals(96f, Dimens.FabInset.value)
        assertEquals(32f, Dimens.EndInset.value)
    }

    @Test fun v8TypeRolesFollowTheTypeRules() {
        val t = ChittaType()
        val sizes = mapOf(
            "sectionTitle" to (t.sectionTitle to 15f), "sectionNote" to (t.sectionNote to 14f), "sheetTitle" to (t.sheetTitle to 22f),
            "rowMeta14" to (t.rowMeta14 to 14f), "chip" to (t.chip to 14f), "stepperLabel" to (t.stepperLabel to 19f),
            "badge" to (t.badge to 11f), "marginDay" to (t.marginDay to 20f), "emptyBody" to (t.emptyBody to 15f),
        )
        sizes.forEach { (name, pair) ->
            val (style, size) = pair
            assertEquals(size, style.fontSize.value, "$name size")
            assertTrue((style.fontWeight?.weight ?: 400) <= 500, "$name is heavier than 500")
            assertEquals(0f, style.lineHeight.value % 4f, "$name line height is off the 4 grid")
            if (name != "badge") assertTrue(style.fontSize.value >= 13f, "$name is below 13 sp")
        }
    }
}
