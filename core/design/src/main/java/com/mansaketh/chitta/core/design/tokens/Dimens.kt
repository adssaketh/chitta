package com.mansaketh.chitta.core.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Layout tokens. Every value is a multiple of 4 dp (master section 3). */
object Dimens {
    val Grid = 4.dp
    val Gap1 = 4.dp
    val Gap2 = 8.dp
    val Gap3 = 12.dp
    val Gap4 = 16.dp
    val Gap6 = 24.dp
    val Gap8 = 32.dp
    val Gap9 = 36.dp

    /** Screen margins. */
    val Margin = 24.dp

    /** Space between sections: 32 to 36 dp. */
    val SectionGap = 32.dp

    /** List rows are 56 to 68 dp; these are minimums so text at 200% can grow them. */
    val RowMin = 56.dp
    val RowTall = 68.dp
    val ButtonHeight = 56.dp
    val TouchTarget = 48.dp
    val IconSize = 24.dp

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
