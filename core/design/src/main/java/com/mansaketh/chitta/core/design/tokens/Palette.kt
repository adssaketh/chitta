package com.mansaketh.chitta.core.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Raw hex values. Paper = light theme, Ink = dark theme.
 * Source: master prompt section 3, Paddhu-Pusthakam-final-design.pdf (Paper) and
 * Paddhu-Pusthakam-dark-mode.pdf (Ink). Values marked NEW are Phase 2 additions, see DECISIONS.md.
 * Kept as plain Longs so the contrast tests can read them without Compose.
 */
object Palette {
    // Paper (light)
    const val PAPER_BACKGROUND = 0xFFF5F1E8
    const val PAPER_SURFACE = 0xFFFBF8F2
    const val PAPER_INK = 0xFF1B1A17
    const val PAPER_MUTED = 0xFF5E5A52
    const val PAPER_RULE = 0xFFDDD6C8
    const val PAPER_SINDOOR = 0xFFD9381E
    const val PAPER_GREEN = 0xFF1F6F4A

    /** NEW: white on #D9381E is 4.63:1; the page colour on it is only 4.11:1. */
    const val PAPER_ON_SINDOOR = 0xFFFFFFFF

    /** NEW: #D9381E as text is 4.11:1 on paper, below AA. This darker red is 4.69:1 on paper, 4.99:1 on a sheet. */
    const val PAPER_WARNING_TEXT = 0xFFC9331B

    /** NEW: mid-grey for graphics (ring "bills" ticks, field borders). 3.18:1 on paper, above the 3:1 for graphics. */
    const val PAPER_MARK = 0xFF8C877B

    // Ink (dark)
    const val INK_BACKGROUND = 0xFF121110
    const val INK_SURFACE = 0xFF1C1A18
    const val INK_INK = 0xFFEDE7DA
    const val INK_MUTED = 0xFFA39C8E
    const val INK_RULE = 0xFF2E2B27
    const val INK_SINDOOR = 0xFFFF5A3C
    const val INK_WARNING_TEXT = 0xFFFF8A73
    const val INK_GREEN = 0xFF4FBF8A

    /** Dark rule 2: dark text on the red accent, because white reaches only 3.1:1. */
    const val INK_ON_SINDOOR = INK_BACKGROUND

    /** NEW: mid-grey for graphics on Ink. 4.06:1 on the Ink background. */
    const val INK_MARK = 0xFF7A7468

    /**
     * NEW: twelve earthy category hues for Paper (master section 3 asks for "a 12-hue earthy set
     * derived from these tokens"). Ink uses each lightened 15% (dark PDF rule 5).
     */
    val CATEGORY_PAPER = longArrayOf(
        0xFFA97A0B, // turmeric
        0xFFB5472F, // brick
        0xFF8E3B46, // madder
        0xFF6B4062, // plum
        0xFF3F4F7A, // indigo
        0xFF2F6F73, // teal
        0xFF5B7A3A, // moss
        0xFF7A7727, // olive
        0xFF9C8458, // sand
        0xFFA5654A, // clay
        0xFF5F6B73, // slate
        0xFF6A5646, // cocoa
    )
    val CATEGORY_NAMES = listOf(
        "Turmeric", "Brick", "Madder", "Plum", "Indigo", "Teal",
        "Moss", "Olive", "Sand", "Clay", "Slate", "Cocoa",
    )
}

/** Theme colours. Use through ChittaTheme.colors, never raw hex in screens. */
@Immutable
data class ChittaColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val rule: Color,
    /** Mid-grey for graphics and input borders (not text). */
    val mark: Color,
    /** Accent fill: the primary add button and the one thing on a screen that needs attention. */
    val sindoor: Color,
    val onSindoor: Color,
    /** Red as text. Lighter than the fill on Ink, darker on Paper, so both pass AA. */
    val warningText: Color,
    /** Income and money in. */
    val green: Color,
    val scrim: Color,
    val category: List<Color>,
) {
    /**
     * A custom accent (Cashew's accent picker, kept by the master prompt). The fill is the chosen colour; the
     * text on it and the red-as-text colour are adjusted so they still pass 4.5:1.
     */
    fun withAccent(accent: Color): ChittaColors = copy(
        sindoor = accent,
        onSindoor = listOf(Color.White, background, Color(Palette.PAPER_INK)).maxBy { contrast(it, accent) },
        warningText = ensureContrast(accent, background, 4.5f, towards = ink),
    )

    companion object {
        val Paper = ChittaColors(
            isDark = false,
            background = Color(Palette.PAPER_BACKGROUND),
            surface = Color(Palette.PAPER_SURFACE),
            ink = Color(Palette.PAPER_INK),
            inkMuted = Color(Palette.PAPER_MUTED),
            rule = Color(Palette.PAPER_RULE),
            mark = Color(Palette.PAPER_MARK),
            sindoor = Color(Palette.PAPER_SINDOOR),
            onSindoor = Color(Palette.PAPER_ON_SINDOOR),
            warningText = Color(Palette.PAPER_WARNING_TEXT),
            green = Color(Palette.PAPER_GREEN),
            scrim = Color(Palette.PAPER_INK).copy(alpha = 0.40f),
            category = Palette.CATEGORY_PAPER.map { Color(it) },
        )

        val Ink = ChittaColors(
            isDark = true,
            background = Color(Palette.INK_BACKGROUND),
            surface = Color(Palette.INK_SURFACE),
            ink = Color(Palette.INK_INK),
            inkMuted = Color(Palette.INK_MUTED),
            rule = Color(Palette.INK_RULE),
            mark = Color(Palette.INK_MARK),
            sindoor = Color(Palette.INK_SINDOOR),
            onSindoor = Color(Palette.INK_ON_SINDOOR),
            warningText = Color(Palette.INK_WARNING_TEXT),
            green = Color(Palette.INK_GREEN),
            // "a sheet is #1C1A18 over a near-black scrim. No pure black or pure white anywhere."
            scrim = Color(0xFF0A0908).copy(alpha = 0.72f),
            category = Palette.CATEGORY_PAPER.map { Color(it).lighten(0.15f) },
        )
    }
}

private fun Color.lighten(amount: Float): Color =
    Color(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount, alpha)

/** WCAG 2.2 contrast ratio between two opaque colours. */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Moves [color] toward [towards] in 5% steps until it reaches [minRatio] against [background]. */
fun ensureContrast(color: Color, background: Color, minRatio: Float, towards: Color): Color {
    var c = color.compositeOver(background)
    var step = 0
    while (contrast(c, background) < minRatio && step < 20) {
        step++
        val t = 0.05f * step
        c = Color(
            color.red + (towards.red - color.red) * t,
            color.green + (towards.green - color.green) * t,
            color.blue + (towards.blue - color.blue) * t,
        )
    }
    return c
}
