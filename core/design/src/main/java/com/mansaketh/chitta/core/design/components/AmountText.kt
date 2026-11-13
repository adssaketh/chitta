package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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

/**
 * Draws [text] with every ASCII digit centred in a slot as wide as the widest digit of [style].
 * Other characters (comma, point, rupee sign, plus, minus) keep their own width.
 */
@Composable
fun TabularText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slot = remember(style, density) {
        ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
    }
    val glyphs = remember(text, style, density) { text.map { measurer.measure(it.toString(), style) } }
    val widths = remember(glyphs, slot) {
        glyphs.mapIndexed { i, g -> if (text[i] in '0'..'9') slot else g.size.width }
    }
    val totalWidth = widths.sum()
    val height = glyphs.maxOfOrNull { it.size.height } ?: 0
    Canvas(modifier.size(with(density) { totalWidth.toDp() }, with(density) { height.toDp() })) {
        var x = 0f
        glyphs.forEachIndexed { i, g ->
            val cell = widths[i]
            drawText(g, color = color, topLeft = Offset(x + (cell - g.size.width) / 2f, 0f))
            x += cell
        }
    }
}
