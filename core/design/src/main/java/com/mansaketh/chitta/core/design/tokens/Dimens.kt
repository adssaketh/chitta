package com.mansaketh.chitta.core.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Layout tokens. Every value is a multiple of 4 dp (master section 3).
 *
 * Screens use the role names (SectionGap, HeadingGap, RowPadV ...), never a raw step or a literal dp.
 * The old Gap names stay as aliases until the last screen has moved to roles.
 */
object Dimens {
    val Grid = 4.dp

    // Spacing scale, 4 dp base.
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space12 = 48.dp

    // Aliases of the old names.
    val Gap1 = Space1
    val Gap2 = Space2
    val Gap3 = Space3
    val Gap4 = Space4
    val Gap6 = Space6
    val Gap8 = Space8
    val Gap9 = 36.dp

    /** Left and right of every screen and sheet. */
    val Margin = Space6
    val ScreenMargin = Margin

    /** Above every section heading (design PDF p.2: 32 to 36 dp). */
    val SectionGap = Space8

    /** Heading to its rule or first content; tab rule to first content. */
    val HeadingGap = Space3

    /** Top and bottom inside every list row, so rows land at 56 and 68 dp. */
    val RowPadV = Space3

    /** List rows are 56 to 68 dp; these are minimums so text at 200% can grow them. */
    val RowMin = 56.dp
    val RowTall = 68.dp

    /** Ledger rows: three lines are common, so they start a little taller. */
    val RowLedger = 64.dp

    /** Icon to text, label to value, amount to stamp. */
    val InlineGap = Space2

    /** Between two figures side by side, and the least gap between a row's title and its amount. */
    val FigureGap = Space6
    val TitleAmountGap = Space4

    /** Inside sheets, fields and the undo bar. */
    val CardInner = Space4

    /** Screen title row. */
    val TitleBarMin = 56.dp

    val ButtonHeight = 56.dp
    val TouchTarget = 48.dp
    val IconSize = 24.dp

    /** The add button: 56 dp, 24 dp from the edges. */
    val FabSize = 56.dp
    val FabMargin = Space6

    /** End of a scroll on screens that show the add button: 56 + 24 + 16. */
    val FabInset = 96.dp

    /** End of a scroll on screens without it. */
    val EndInset = Space8

    /** Scrolling sheet bodies are capped at this share of the screen height; the content scrolls inside. */
    const val SheetMaxFraction = 0.8f

    /** The 2 dp x 24 dp mark above the selected tab, and the least height of the bottom bar. */
    val MarkerWidth = 24.dp
    val MarkerHeight = 2.dp
    val NavBarMin = 64.dp

    /** Left margin column that holds dates, like a ledger page. */
    val MarginColumn = 56.dp

    val Hairline = 1.dp

    /** Outline drawn around a focused row or button. */
    val FocusRing = 2.dp

    /** Small icon beside a label (tabs, carets). */
    val IconSmall = 16.dp

    /** A category glyph: a soft circle with the icon inside. v8 (D279): 40 dp circle, 20 dp icon. */
    val GlyphCircle = 40.dp
    val GlyphIcon = 20.dp

    /** Line weight of a drawn status mark (calendar marks). */
    val MarkStroke = 1.5.dp

    /** The coloured dot before a chip label. */
    val Dot = 8.dp

    /** Height of a horizontal ledger bar. */
    val BarHeight = 8.dp

    /** The grab handle at the top of a sheet. */
    val SheetHandleWidth = 32.dp
    val SheetHandleHeight = 4.dp

    /** App logo on the lock screen and in More; the Home budget ring and the net-worth trend line. */
    val LogoSmall = 64.dp
    val LogoLarge = 80.dp
    val RingSmall = 120.dp
    val TrendHeight = 80.dp

    /** One day cell of the bill calendar. */
    val CalendarCell = 52.dp

    /** The width of a quiet Close button over a full-screen picture. */
    val CloseWidth = 120.dp

    /** The widest a label may grow in a label-and-value row before it wraps. */
    val LabelMaxWidth = 200.dp

    /** A tag's side padding; its text line supplies the height. */
    val TagPadH = Space2

    // ---- v8 kit (Phase 11a, D277 to D283). Components in `kit` read only these. ----

    val Space5 = 20.dp

    /** Corners (D278): 4 tags, tick boxes and stamps; 12 every control; 28 sheet tops. */
    val CornerTag = 4.dp
    val CornerControl = 12.dp
    val CornerSheet = 28.dp

    /** Switch (D280: ink, never sindoor). */
    val SwitchWidth = 46.dp
    val SwitchHeight = 28.dp
    val SwitchKnob = 19.dp
    val SwitchBorder = 1.5.dp

    /** Tick box (pick many) and the tick at the end of a pick-one row. */
    val TickBoxSize = 22.dp
    val TickBoxBorder = 1.5.dp

    /** Count badge: least size, ring in the page colour, and where it sits on a 48 dp icon button. */
    val BadgeMin = 20.dp
    val BadgeRing = 2.dp
    val BadgeTop = 5.dp
    val BadgeEnd = 3.dp
    val BadgePadH = Space1

    val ButtonHeightSmall = 48.dp
    val ChipHeight = 40.dp
    val FilterChipHeight = 36.dp
    val ChipBorderSelected = 1.5.dp

    /** Segmented switch: 3 dp padding, 34 dp parts. The part radius (12 - 3 = 9) is the inner radius of the 12 dp outline. */
    val SegmentPad = 3.dp
    val SegmentHeight = 34.dp
    val SegmentRadius = 9.dp
    val SegmentPadH = Space4

    val TopBarMainHeight = 76.dp
    val TopBarDetailHeight = 64.dp

    /** Sheet handle: 36 x 4 dp, rule colour, 6 dp below. The 2 dp radius is half its height. */
    val SheetHandleSizeW = 36.dp
    val SheetHandleSizeH = 4.dp
    val SheetHandleRadius = 2.dp
    val SheetHandleGap = 6.dp

    val MessageBarMinHeight = 56.dp
    val MessageBarInset = 16.dp
    val MessageBarBottom = 88.dp

    val RowMinOneLine = 56.dp
    val RowMinTwoLine = 68.dp

    /** Icon sizes (v8 kit). */
    val IconTopBar = 24.dp
    val IconMainTitle = 26.dp
    val IconRowLeading = 22.dp
    val IconCaret = 18.dp
    val IconChip = 18.dp
    val IconFilterX = 14.dp
    val IconEmpty = 40.dp
    val IconTick = 22.dp

    /** Gauge bar: 6 dp tall; 3 dp between 10 segments, 2 dp between 20. */
    val GaugeHeight = 6.dp
    val GaugeGap10 = 3.dp
    val GaugeGap20 = 2.dp

    /** Group header (settings, accounts): space above and below the title row. */
    val GroupTop = Space5
    val GroupBottom = Space3

    /** Date sheet: 46 dp rows, 40 dp day circles, 1.5 dp ring for today. */
    val DateRow = 46.dp
    val DayCircle = 40.dp
    val TodayRing = 1.5.dp

    /** The margin column of an entry row that shows a date. */
    val EntryMarginColumn = MarginColumn

    /** Inside a Note: all four sides. */
    val NoteInner = Space4

    /** A pressed entry row or chip keeps its hit area at least this tall even when it draws smaller. */
    val HitMin = TouchTarget
}

object Shapes {
    /** Controls: 12 dp (D278). */
    val Control = RoundedCornerShape(Dimens.CornerControl)

    /** Bottom-sheet top corners: 28 dp. */
    val Sheet = RoundedCornerShape(topStart = Dimens.CornerSheet, topEnd = Dimens.CornerSheet)

    /** Tags (Split, Possible duplicate) and tick boxes: hairline outline, 4 dp corners, never a pill. */
    val Tag = RoundedCornerShape(Dimens.CornerTag)

    /** The selected part inside a Segmented switch. */
    val SegmentPart = RoundedCornerShape(Dimens.SegmentRadius)

    /** Stamps are outlined labels, not pills. */
    val Stamp = RoundedCornerShape(4.dp)
}
