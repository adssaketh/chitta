package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
        val topPad = 4.dp.toPx()
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
    /** Fill the ring in a circle, clockwise from the top, each time it first appears (Home). Off in snapshots. */
    animate: Boolean = false,
) {
    if (ticks == null) return
    val c = ChittaTheme.colors
    // With the system "Remove animations" on, the ring simply appears full.
    val run = animate && com.mansaketh.chitta.core.design.theme.rememberMotionEnabled()
    val fill = remember { Animatable(if (run) 0f else 1f) }
    LaunchedEffect(run) { if (run) fill.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) else fill.snapTo(1f) }
    val spoken = if (ticks.over) "Budget ring: over the budget"
    else "Budget ring: ${ticks.safePercent} percent is safe to spend"
    Box(modifier.size(diameter).clearAndSetSemantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val tickLen = 12.dp.toPx()
            val tickW = 3.dp.toPx()
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val reached = fill.value * RingTicks.TOTAL
            repeat(RingTicks.TOTAL) { i ->
                val color: Color = when {
                    i + 1 > reached -> c.rule // not reached yet by the sweep
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ticks.over) "over" else "${ticks.safePercent}%",
                style = ChittaTheme.type.amount,
                color = if (ticks.over) c.warningText else c.ink,
            )
            // The ring shows what is left, not what is used: say so (design p.9).
            if (!ticks.over) Text("safe", style = ChittaTheme.type.label, color = c.inkMuted)
        }
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
        Text(label, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f).alignByBaseline())
        AmountText(amount, style = ChittaTheme.type.amountSmall, modifier = Modifier.alignByBaseline())
    }
}

/**
 * The v8 month rule (final design p.13): one thin rule along the days of the period, so the whole month is one glance.
 *  - A tick per day along the rule.
 *  - A solid ink bar for money spent, measured in days of budget: spent / limit of the rule's length.
 *  - A short ink line across the rule at today, and a dashed line under it that ends at today (the ideal pace is "today's share of the limit").
 *    A solid bar that runs past the today line means spending is ahead of pace.
 *  - A dotted run from the end of the bar to the end of the period (from day 7 only, when a forecast exists).
 *  - A sindoor tick on the day the forecast reaches the limit.
 * The numbers all come from [BudgetFigures]; this only draws them. Mapping decision: D287.
 */
@Composable
fun MonthRuleThin(
    daysInPeriod: Int,
    dayOfPeriod: Int,
    spent: Paise,
    limit: Paise,
    limitDay: Int?,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    val days = daysInPeriod.coerceAtLeast(1)
    val today = dayOfPeriod.coerceIn(0, days)
    val spentShare = if (limit.value > 0) (spent.value.toFloat() / limit.value).coerceIn(0f, 1f) else 0f
    val forecast = BudgetFigures.forecast(spent, today, days)
    val ideal = BudgetFigures.idealSpend(limit, today, days)
    val spoken = buildString {
        append("Spent ${IndianFormat.spoken(spent)} by day $today of $days. ")
        if (ideal != null) append("Ideal pace ${IndianFormat.spoken(ideal)}. ")
        if (forecast != null) append("At this rate ${IndianFormat.spoken(forecast)} by the end. ")
        if (limitDay != null) append("The limit is reached on day $limitDay.")
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.MonthRuleHeight)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val baseY = 11.dp.toPx()
        val w = size.width
        fun x(day: Int) = day.toFloat() / days * w
        val barEnd = spentShare * w
        val todayX = x(today)

        // beyond the bar: dotted when there is a forecast, else a plain hairline
        if (barEnd < w) {
            if (forecast != null) {
                drawLine(
                    c.ink, Offset(barEnd, baseY), Offset(w, baseY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 6.dp.toPx())),
                )
            } else {
                drawLine(c.rule, Offset(barEnd, baseY), Offset(w, baseY), strokeWidth = 1.dp.toPx())
            }
        }
        // money spent: the solid bar
        if (barEnd > 0f) drawLine(c.ink, Offset(0f, baseY), Offset(barEnd, baseY), strokeWidth = 5.dp.toPx())
        // the ideal pace: dashed, under the bar, ending at today
        if (ideal != null && today > 0) {
            drawLine(
                c.mark, Offset(0f, baseY + 10.dp.toPx()), Offset(todayX, baseY + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        // a tick per day; on the solid bar it cuts through in the page colour
        for (d in 0..days) {
            val tx = x(d)
            drawLine(if (tx <= barEnd) c.background else c.ink, Offset(tx, baseY - 3.dp.toPx()), Offset(tx, baseY + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        // today
        drawLine(c.ink, Offset(todayX, baseY - 9.dp.toPx()), Offset(todayX, baseY + 12.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        // where the forecast reaches the limit: the one red mark
        if (limitDay != null) {
            drawLine(c.sindoor, Offset(x(limitDay), baseY - 9.dp.toPx()), Offset(x(limitDay), baseY + 9.dp.toPx()), strokeWidth = 3.dp.toPx())
        }
    }
}

/** The swatch-only legend beside the Home ring (final design p.13): a square per part, no amounts. Wraps at large text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingSwatchLegend(modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
    ) {
        SwatchItem("Spent", c.ink)
        SwatchItem("Bills", c.mark)
        SwatchItem("Safe to spend", c.rule)
    }
}

@Composable
private fun SwatchItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Dimens.LegendSwatch).background(color))
        Spacer(Modifier.width(Dimens.InlineGap))
        Text(label, style = ChittaTheme.type.rowMeta14, color = ChittaTheme.colors.inkMuted)
    }
}
