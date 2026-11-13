package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.money.BudgetFigures
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.RingTicks
import com.mansaketh.chitta.core.money.Segments

/**
 * A 10- or 20-segment gauge for budgets, goals, loans and card utilisation. Ink when fine; the accent
 * only when over the limit. Unused segments are the rule colour (decoration, exempt from text contrast).
 * [description] should say what the gauge measures, for example "Food budget".
 */
@Composable
fun GaugeBar(
    gauge: Segments.Gauge,
    description: String,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    val fill = if (gauge.over) c.sindoor else c.ink
    val spoken = "$description: ${gauge.filled} of ${gauge.segments} segments" + if (gauge.over) ", over the limit" else ""
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (gauge.segments - 1)) / gauge.segments
        repeat(gauge.segments) { i ->
            drawRect(
                color = if (i < gauge.filled) fill else c.rule,
                topLeft = Offset(i * (segW + gap), 0f),
                size = androidx.compose.ui.geometry.Size(segW, size.height),
            )
        }
    }
}

/**
 * The month rule: one glanceable pace line for a budget period (master section 3). Days run left to
 * right along a baseline with a tick per day. Money runs up. Drawn in this order:
 *  - a solid ink line for money spent so far, and an ink dot for today,
 *  - a dashed line for the ideal pace (limit × days gone ÷ days in period),
 *  - a dotted line for the forecast (from day 7 only),
 *  - a hairline at the limit, and a sindoor mark where the forecast reaches it.
 * All numbers come from [BudgetFigures]; the component only draws them.
 *
 * @param cumulativeSpent money spent up to and including each day; its size is the number of days gone.
 */
@Composable
fun MonthRule(
    daysInPeriod: Int,
    cumulativeSpent: List<Paise>,
    limit: Paise,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    val elapsed = cumulativeSpent.size
    val spent = cumulativeSpent.lastOrNull() ?: Paise.ZERO
    val ideal = BudgetFigures.idealSpend(limit, elapsed, daysInPeriod)
    val forecast = BudgetFigures.forecast(spent, elapsed, daysInPeriod)
    val limitDay = BudgetFigures.limitDay(spent, limit, elapsed, daysInPeriod)
    val spoken = buildString {
        append("Spent ${IndianFormat.spoken(spent)} by day $elapsed of $daysInPeriod. ")
        if (ideal != null) append("Ideal pace ${IndianFormat.spoken(ideal)}. ")
        if (forecast != null) append("At this rate ${IndianFormat.spoken(forecast)} by the end. ")
        if (limitDay != null) append("The limit is reached on day $limitDay.")
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val topPad = 8.dp.toPx()
        val baseY = size.height - 12.dp.toPx()
        val plotH = baseY - topPad
        val top = maxOf(limit.value, forecast?.value ?: 0L, spent.value).coerceAtLeast(1L).toFloat()
        fun x(day: Float) = day / daysInPeriod * size.width
        fun y(p: Paise) = baseY - p.value / top * plotH
        val hair = 1.dp.toPx()

        // baseline and a tick per day
        drawLine(c.rule, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = hair)
        for (d in 0..daysInPeriod) {
            val tall = d % 5 == 0
            drawLine(c.rule, Offset(x(d.toFloat()), baseY), Offset(x(d.toFloat()), baseY + if (tall) 8.dp.toPx() else 4.dp.toPx()), strokeWidth = hair)
        }
        // the limit
        drawLine(c.mark, Offset(0f, y(limit)), Offset(size.width, y(limit)), strokeWidth = hair)
        // ideal pace: dashed, straight from 0 to the limit
        drawLine(
            c.mark, Offset(0f, baseY), Offset(size.width, y(limit)), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
        )
        // forecast: dotted, from today's spend to the end
        if (forecast != null) {
            drawLine(
                c.ink, Offset(x(elapsed.toFloat()), y(spent)), Offset(size.width, y(forecast)),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx())),
            )
        }
        // actual spend: solid ink
        if (elapsed > 0) {
            var prev = Offset(0f, baseY)
            cumulativeSpent.forEachIndexed { i, p ->
                val next = Offset(x((i + 1).toFloat()), y(p))
                drawLine(c.ink, prev, next, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                prev = next
            }
            drawCircle(c.ink, radius = 5.dp.toPx(), center = prev) // today
        }
        // where the forecast reaches the limit: the one red mark
        if (limitDay != null) {
            drawCircle(c.sindoor, radius = 6.dp.toPx(), center = Offset(x(limitDay.toFloat()), y(limit)))
        }
    }
}

/**
 * The Home budget ring: 50 ticks of 2% each. Ink for spent, mid-grey for bills still to come, the rule
 * colour for Safe to spend. The centre says the safe percentage, or "over" when spend plus bills pass the budget.
 * Hidden (nothing drawn) when [ticks] is null, which is when no budget is set.
 */
@Composable
fun BudgetRing(
    ticks: RingTicks?,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 120.dp,
) {
    if (ticks == null) return
    val c = ChittaTheme.colors
    val spoken = if (ticks.over) "Budget ring: over the budget"
    else "Budget ring: ${ticks.safePercent} percent is safe to spend"
    Box(modifier.size(diameter).clearAndSetSemantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val tickLen = 12.dp.toPx()
            val tickW = 3.dp.toPx()
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            repeat(RingTicks.TOTAL) { i ->
                val color: Color = when {
                    i < ticks.spent -> c.ink
                    i < ticks.spent + ticks.bills -> c.mark
                    else -> c.rule
                }
                rotate(degrees = i * 360f / RingTicks.TOTAL, pivot = center) {
                    drawLine(
                        color, Offset(center.x, center.y - r), Offset(center.x, center.y - r + tickLen),
                        strokeWidth = tickW, cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            if (ticks.over) "over" else "${ticks.safePercent}%",
            style = ChittaTheme.type.amount,
            color = if (ticks.over) c.warningText else c.ink,
        )
    }
}

/** Three-item legend under the ring. Its amounts are the same figures the ring draws. */
@Composable
fun RingLegend(
    spent: Paise,
    bills: Paise,
    safe: Paise,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        LegendRow("Spent", c.ink, spent)
        LegendRow("Bills to come", c.mark, bills)
        LegendRow("Safe to spend", c.rule, safe, outlined = true)
    }
}

@Composable
private fun LegendRow(label: String, swatch: Color, amount: Paise, outlined: Boolean = false) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 4.dp, height = 16.dp)) {
            drawRect(swatch)
            if (outlined) drawRect(c.mark, style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.width(Dimens.Gap3))
        Text(label, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f))
        AmountText(amount, style = ChittaTheme.type.amountSmall)
    }
}
