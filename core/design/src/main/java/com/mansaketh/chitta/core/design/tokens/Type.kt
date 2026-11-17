package com.mansaketh.chitta.core.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mansaketh.chitta.core.design.R

/**
 * Three roles, bundled offline (master section 3, final design page 2):
 *  - Amounts and headlines: Fraunces (variable, optical size follows the text size).
 *  - Interface text: Hanken Grotesk.
 *  - Ledger data, stamps and codes: IBM Plex Mono.
 *
 * Fraunces has no tabular-figure feature, so digits are lined up by AmountText, which gives every
 * digit the same slot width. Hanken Grotesk has no rupee glyph, so every rupee sign is drawn in
 * the amount font (see rupeeAware). Both findings are in DECISIONS.md.
 */
object ChittaFonts {
    /** Fraunces at a weight and a text size. SOFT and WONK are off: the plain, upright cut. */
    fun fraunces(weight: Int, size: TextUnit): FontFamily = FontFamily(
        Font(
            resId = R.font.fraunces_variable,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.opticalSizing(size),
                FontVariation.Setting("SOFT", 0f),
                FontVariation.Setting("WONK", 0f),
            ),
        )
    )

    val Hanken = FontFamily(
        Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
        Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    )

    val PlexMono = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    )
}

/**
 * Text styles. Nothing below 13 sp except stamps (11 sp mono). Section titles are never heavier than
 * weight 500. Sizes are sp so text can grow to 200%; components use minimum heights, never fixed ones.
 */
@Immutable
class ChittaType internal constructor() {
    // Amounts and headlines (Fraunces)
    val amountHero = amountStyle(48.sp, 52.sp, 400)
    val amountLarge = amountStyle(32.sp, 36.sp, 400)
    val amount = amountStyle(24.sp, 28.sp, 500)
    val amountRow = amountStyle(18.sp, 24.sp, 500)
    val amountSmall = amountStyle(16.sp, 24.sp, 500)

    /** The two figures under Safe to spend on Home (Budget left, Range): same face as the hero, about 24 sp, regular weight (PDF p.13). */
    val amountTile = amountStyle(24.sp, 28.sp, 400)
    val headline = amountStyle(28.sp, 32.sp, 500)
    /** First run title (PDF p.10): larger than the Lock name, measured off the page. */
    val displayTitle = amountStyle(36.sp, 40.sp, 500)
    val dayNumber = amountStyle(24.sp, 28.sp, 500)

    // Interface text (Hanken Grotesk)
    val title = hanken(18.sp, 24.sp, FontWeight.Medium)
    val body = hanken(16.sp, 24.sp, FontWeight.Normal)
    val bodyMedium = hanken(16.sp, 24.sp, FontWeight.Medium)
    val label = hanken(13.sp, 16.sp, FontWeight.Normal)
    val labelMedium = hanken(13.sp, 16.sp, FontWeight.Medium)
    val button = hanken(16.sp, 24.sp, FontWeight.Medium)

    // Ledger data, stamps and codes (IBM Plex Mono)
    val mono = TextStyle(fontFamily = ChittaFonts.PlexMono, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val stamp = TextStyle(
        fontFamily = ChittaFonts.PlexMono, fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.em,
    )

    // Roles. Screens pick a style by role, never by size. These are names for the styles above:
    // no size or weight changes.
    val screenTitle get() = headline
    val barTitle get() = title
    val sectionHeading get() = title
    val link = hanken(13.sp, 16.sp, FontWeight.Medium).copy(textDecoration = TextDecoration.Underline)
    val heroNumber get() = amountHero
    val figure get() = amountLarge

    /** The next sizes down from [figure], tried in order when a figure does not fit its column. */
    val figureSteps: List<TextStyle> get() = listOf(amount, amountRow)
    val rowTitle get() = body
    val rowMeta get() = label
    val rowAmount get() = amountRow

    // v8 kit roles (D277). New names, so screens that still use the old roles do not move until 11b.
    /** Section heading, Hanken 15 medium; its trailing note is 14 muted. */
    val sectionTitle = hanken(15.sp, 20.sp, FontWeight.Medium)
    val sectionNote = hanken(14.sp, 20.sp, FontWeight.Normal)

    /** Sheet title and empty-state title: Fraunces 22 medium. */
    val sheetTitle = amountStyle(22.sp, 28.sp, 500)

    /** Row meta line, 14 muted; chip text, 14. */
    val rowMeta14 = hanken(14.sp, 20.sp, FontWeight.Normal)
    val chip = hanken(14.sp, 20.sp, FontWeight.Normal)
    val chipSelected = hanken(14.sp, 20.sp, FontWeight.Medium)

    /** Month stepper label: Fraunces 19 medium. */
    val stepperLabel = amountStyle(19.sp, 24.sp, 500)

    /** Count badge: Plex Mono 11 medium (like a stamp, the one place below 13 sp besides stamps). */
    val badge = TextStyle(fontFamily = ChittaFonts.PlexMono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

    /** The margin date of an entry row: day in Fraunces 20, weekday in Plex 13 (use [mono]). */
    val marginDay = amountStyle(20.sp, 24.sp, 500)

    /** A figure inside a sheet: 16 (the screens use [rowAmount], 18). */
    val rowAmountSheet get() = amountSmall

    /** Empty-state body, 15 muted. */
    val emptyBody = hanken(15.sp, 24.sp, FontWeight.Normal)

    /** The font a rupee sign is drawn in wherever it sits inside interface text. */
    val rupeeFamily: FontFamily = ChittaFonts.fraunces(500, 16.sp)

    private fun amountStyle(size: TextUnit, line: TextUnit, weight: Int) = TextStyle(
        fontFamily = ChittaFonts.fraunces(weight, size),
        fontSize = size,
        lineHeight = line,
        fontWeight = FontWeight(weight),
    )

    private fun hanken(size: TextUnit, line: TextUnit, weight: FontWeight) = TextStyle(
        fontFamily = ChittaFonts.Hanken,
        fontSize = size,
        lineHeight = line,
        fontWeight = weight,
    )
}
