package com.mansaketh.chitta.core.design

import androidx.compose.ui.graphics.Color
import com.mansaketh.chitta.core.design.tokens.ChittaColors
import com.mansaketh.chitta.core.design.tokens.Palette
import com.mansaketh.chitta.core.design.tokens.contrast
import kotlin.math.pow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WCAG 2.2 contrast, computed from the token hex values themselves (not typed in), so a palette
 * change that breaks AA fails the build. Text needs 4.5:1; graphics and input borders need 3:1.
 * The Ink figures also match the table in Paddhu-Pusthakam-dark-mode.pdf.
 */
class ContrastTest {
    private fun lum(argb: Long): Double {
        fun ch(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun ratio(a: Long, b: Long): Double {
        val la = lum(a)
        val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun atLeast(min: Double, fg: Long, bg: Long, what: String) {
        val r = ratio(fg, bg)
        assertTrue(r >= min, "$what is %.2f:1, needs $min".format(r))
    }

    @Test fun paperTextPairs() = with(Palette) {
        atLeast(4.5, PAPER_INK, PAPER_BACKGROUND, "ink on page")
        atLeast(4.5, PAPER_INK, PAPER_SURFACE, "ink on sheet")
        atLeast(4.5, PAPER_MUTED, PAPER_BACKGROUND, "muted on page")
        atLeast(4.5, PAPER_MUTED, PAPER_SURFACE, "muted on sheet")
        atLeast(4.5, PAPER_WARNING_TEXT, PAPER_BACKGROUND, "warning text on page")
        atLeast(4.5, PAPER_WARNING_TEXT, PAPER_SURFACE, "warning text on sheet")
        atLeast(4.5, PAPER_GREEN, PAPER_BACKGROUND, "income green on page")
        atLeast(4.5, PAPER_GREEN, PAPER_SURFACE, "income green on sheet")
        atLeast(4.5, PAPER_ON_SINDOOR, PAPER_SINDOOR, "text on the red button")
        atLeast(4.5, PAPER_BACKGROUND, PAPER_INK, "page colour on an ink button or Undo bar")
    }

    @Test fun inkTextPairs() = with(Palette) {
        atLeast(4.5, INK_INK, INK_BACKGROUND, "ink on page")
        atLeast(4.5, INK_INK, INK_SURFACE, "ink on sheet")
        atLeast(4.5, INK_MUTED, INK_BACKGROUND, "muted on page")
        atLeast(4.5, INK_MUTED, INK_SURFACE, "muted on sheet")
        atLeast(4.5, INK_WARNING_TEXT, INK_BACKGROUND, "warning text on page")
        atLeast(4.5, INK_WARNING_TEXT, INK_SURFACE, "warning text on sheet")
        atLeast(4.5, INK_GREEN, INK_BACKGROUND, "income green on page")
        atLeast(4.5, INK_GREEN, INK_SURFACE, "income green on sheet")
        atLeast(4.5, INK_ON_SINDOOR, INK_SINDOOR, "dark text on the red button")
        atLeast(4.5, INK_BACKGROUND, INK_INK, "page colour on a light button or Undo bar")
    }

    @Test fun inkMatchesTheDarkModePdf() = with(Palette) {
        // dark PDF, "Tokens and contrast": 15.3, 14.1, 6.9, 6.4, 6.1, 6.1, 8.2, 8.2
        assertEquals(15.3, ratio(INK_INK, INK_BACKGROUND), 0.05)
        assertEquals(14.1, ratio(INK_INK, INK_SURFACE), 0.05)
        assertEquals(6.9, ratio(INK_MUTED, INK_BACKGROUND), 0.05)
        assertEquals(6.4, ratio(INK_MUTED, INK_SURFACE), 0.05)
        assertEquals(6.1, ratio(INK_SINDOOR, INK_BACKGROUND), 0.05)
        assertEquals(6.1, ratio(INK_BACKGROUND, INK_SINDOOR), 0.05)
        assertEquals(8.2, ratio(INK_WARNING_TEXT, INK_BACKGROUND), 0.05)
        assertEquals(8.2, ratio(INK_GREEN, INK_BACKGROUND), 0.05)
    }

    @Test fun graphicsAndFieldBordersNeedThreeToOne() = with(Palette) {
        atLeast(3.0, PAPER_MARK, PAPER_BACKGROUND, "Paper mid-grey (field border, ring bills) on page")
        atLeast(3.0, PAPER_MARK, PAPER_SURFACE, "Paper mid-grey on a sheet")
        atLeast(3.0, INK_MARK, INK_BACKGROUND, "Ink mid-grey on page")
        atLeast(3.0, INK_MARK, INK_SURFACE, "Ink mid-grey on a sheet")
        atLeast(3.0, PAPER_SINDOOR, PAPER_BACKGROUND, "Paper accent mark on page")
        atLeast(3.0, INK_SINDOOR, INK_BACKGROUND, "Ink accent mark on page")
    }

    @Test fun categoryHuesAreVisibleInBothThemes() {
        val ink = ChittaColors.Ink.category
        Palette.CATEGORY_PAPER.forEachIndexed { i, hex ->
            atLeast(3.0, hex, Palette.PAPER_BACKGROUND, "${Palette.CATEGORY_NAMES[i]} on Paper")
            atLeast(3.0, ink[i].toArgbLong(), Palette.INK_BACKGROUND, "${Palette.CATEGORY_NAMES[i]} on Ink")
        }
        assertEquals(12, Palette.CATEGORY_PAPER.size)
    }

    @Test fun customAccentKeepsTextReadable() {
        // A pale accent: the text on it and the red-as-text colour must still pass.
        listOf(Color(0xFFFFC857), Color(0xFF2F6F73), Color(0xFF6B4062)).forEach { accent ->
            listOf(ChittaColors.Paper, ChittaColors.Ink).forEach { base ->
                val themed = base.withAccent(accent)
                assertTrue(contrast(themed.onSindoor, themed.sindoor) >= 4.5f, "text on accent $accent")
                assertTrue(contrast(themed.warningText, themed.background) >= 4.5f, "warning text for accent $accent")
            }
        }
    }

    private fun Color.toArgbLong(): Long {
        fun c(v: Float) = Math.round(v * 255).toLong()
        return (0xFFL shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }
}
