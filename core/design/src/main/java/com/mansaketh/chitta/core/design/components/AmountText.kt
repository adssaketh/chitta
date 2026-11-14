package com.mansaketh.chitta.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise

/** How an amount is signed. Expenses are plain ink; income is green with a plus (master section 3). */
enum class AmountTone { Expense, Income, Neutral }

private const val MASK = "₹ ••••"

/**
 * An amount in the serif, with Indian grouping and every digit in a slot of the same width, so a
 * column of amounts lines up on its right edge. (Fraunces has no tabular figures; this is how the
 * "tabular lining figures" rule is met. See DECISIONS.md.)
 *
 * [fallbackStyles] are smaller styles tried in order when the amount does not fit the width it is
 * given, so a long figure steps down instead of running over its neighbour.
 *
 * Hide-amounts mode masks the figure. TalkBack reads "rupees 1200", not the grouped text.
 */
@Composable
fun AmountText(
    amount: Paise,
    modifier: Modifier = Modifier,
    style: TextStyle = ChittaTheme.type.amountRow,
    color: Color = ChittaTheme.colors.ink,
    tone: AmountTone = AmountTone.Neutral,
    alwaysShowPaise: Boolean = false,
    roundToRupee: Boolean = false,
    fallbackStyles: List<TextStyle> = emptyList(),
) {
    val hidden = ChittaTheme.hideAmounts
    val body = when {
        roundToRupee -> IndianFormat.rupeesRounded(amount)
        else -> IndianFormat.rupees(amount, alwaysShowPaise = alwaysShowPaise)
    }
    val text = if (tone == AmountTone.Income && !amount.isNegative) "+$body" else body
    val tint = if (tone == AmountTone.Income) ChittaTheme.colors.green else color
    val spoken = if (hidden) "amount hidden" else IndianFormat.spoken(amount)
    TabularText(
        text = if (hidden) MASK else text,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        style = style,
        color = tint,
        fallbackStyles = fallbackStyles,
    )
}

/** Live display of the amount being typed on the keypad, grouped as you type. */
@Composable
fun AmountEntryText(
    raw: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ChittaTheme.type.amountHero,
    color: Color = ChittaTheme.colors.ink,
) {
    val shown = IndianFormat.RUPEE + IndianFormat.entry(raw.ifEmpty { "0" })
    TabularText(
        text = shown,
        modifier = modifier.clearAndSetSemantics { contentDescription = "amount $raw" },
        style = style,
        color = color,
    )
}

/** What the last measure pass decided; the draw pass reads it. Plain fields, not snapshot state. */
private class TabularPlan {
    var glyphs: List<TextLayoutResult> = emptyList()
    var cells: IntArray = IntArray(0)
    var baseline: Float = 0f
}

/**
 * Draws [text] with every ASCII digit centred in a slot as wide as the widest digit of the style used.
 * Other characters (comma, point, rupee sign, plus, minus) keep their own width.
 *
 * The first style in [style] + [fallbackStyles] whose width fits the width on offer is used; if none
 * fits, the last one is. The text reports its first baseline, so a label, an amount and a stamp in one
 * row can all sit on the same line (`Modifier.alignByBaseline()`).
 */
@Composable
fun TabularText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
    fallbackStyles: List<TextStyle> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val plan = remember { TabularPlan() }
    val styles = remember(style, fallbackStyles) { listOf(style) + fallbackStyles }
    Layout(
        content = {},
        modifier = modifier.drawBehind {
            var x = 0f
            plan.glyphs.forEachIndexed { i, g ->
                val cell = plan.cells[i]
                drawText(
                    g,
                    color = color,
                    topLeft = Offset(x + (cell - g.size.width) / 2f, plan.baseline - g.firstBaseline),
                )
                x += cell
            }
        },
    ) { _, constraints ->
        var chosen: Pair<List<TextLayoutResult>, IntArray>? = null
        for ((index, candidate) in styles.withIndex()) {
            val measured = measureTabular(measurer, text, candidate)
            chosen = measured
            val fits = !constraints.hasBoundedWidth || measured.second.sum() <= constraints.maxWidth
            if (fits || index == styles.lastIndex) break
        }
        val (glyphs, cells) = chosen ?: (emptyList<TextLayoutResult>() to IntArray(0))
        val baseline = glyphs.maxOfOrNull { it.firstBaseline } ?: 0f
        val height = glyphs.maxOfOrNull { (baseline - it.firstBaseline) + it.size.height } ?: 0f
        plan.glyphs = glyphs
        plan.cells = cells
        plan.baseline = baseline
        val width = cells.sum().coerceIn(constraints.minWidth, constraints.maxWidth.coerceAtLeast(constraints.minWidth))
        layout(
            width = width,
            height = height.toInt().coerceIn(constraints.minHeight, constraints.maxHeight.coerceAtLeast(constraints.minHeight)),
            alignmentLines = mapOf(FirstBaseline to baseline.toInt()),
        ) {}
    }
}

private fun measureTabular(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
): Pair<List<TextLayoutResult>, IntArray> {
    val slot = ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
    val glyphs = text.map { measurer.measure(it.toString(), style) }
    val cells = IntArray(glyphs.size) { i -> if (text[i] in '0'..'9') slot else glyphs[i].size.width }
    return glyphs to cells
}
