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
}

object Shapes {
    /** Controls: 12 to 14 dp. */
    val Control = RoundedCornerShape(12.dp)

    /** Bottom-sheet top corners. */
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Stamps are outlined labels, not pills. */
    val Stamp = RoundedCornerShape(2.dp)
}
