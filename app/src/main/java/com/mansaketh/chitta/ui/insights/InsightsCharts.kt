package com.mansaketh.chitta.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.HeatCell
import com.mansaketh.chitta.core.domain.HistoryPoint
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Money out per bucket as plain bars, with the average as a dashed line. The bucket that is still filling is drawn in the one accent
 * colour, with an outline up to where it ends at today's pace (master section 3, design page 24). Past buckets are a quiet grey.
 * Values are in the description for TalkBack; labels sit under the bars, every [labelEvery] bars so they never collide.
 */
@Composable
fun FlowBars(
    points: List<HistoryPoint>,
    label: (HistoryPoint) -> String,
    average: Paise?,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
    height: Dp = 140.dp,
) {
    val c = ChittaTheme.colors
    if (points.isEmpty()) return
    val top = maxOf(
        points.maxOf { it.moneyOut.value }, points.maxOfOrNull { it.projectedOut?.value ?: 0L } ?: 0L, average?.value ?: 0L, 1L,
    ).toFloat()
    val spoken = points.joinToString(". ") { "${label(it)}, ${IndianFormat.spoken(it.moneyOut)} out" } +
        (average?.let { ". Average ${IndianFormat.spoken(it)}" } ?: "")
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = spoken }) {
            val gap = 8.dp.toPx()
            val barW = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(2.dp.toPx())
            val baseY = size.height
            fun y(v: Long) = baseY - v / top * (size.height - 4.dp.toPx())
            points.forEachIndexed { i, p ->
                val x = i * (barW + gap)
                val outTop = y(p.moneyOut.value)
                val projected = p.projectedOut
                if (projected != null && projected.value > p.moneyOut.value) {
                    drawRect(c.mark, Offset(x, y(projected.value)), Size(barW, baseY - y(projected.value)), style = Stroke(1.dp.toPx()))
                }
                drawRect(if (p.isCurrent) c.sindoor else c.mark, Offset(x, outTop), Size(barW, (baseY - outTop).coerceAtLeast(1.dp.toPx())))
            }
            if (average != null && average.value > 0) {
                drawLine(
                    c.ink, Offset(0f, y(average.value)), Offset(size.width, y(average.value)), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Box(Modifier.height(Dimens.Gap2))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEachIndexed { i, p ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelEvery == 0 || i == points.lastIndex && labelEvery == 1) {
                        Text(label(p), style = ChittaTheme.type.label, color = if (p.isCurrent) c.ink else c.inkMuted, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * A single ink line through [values] (paise), with a dot on the last one. When the values cross zero a hairline marks zero. Used for the
 * running total on History and for net worth over time.
 */
@Composable
fun TrendLine(
    values: List<Long>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val c = ChittaTheme.colors
    if (values.size < 2) return
    val lo = minOf(values.min(), 0L)
    val hi = maxOf(values.max(), 0L).let { if (it == lo) lo + 1 else it }
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }) {
        val pad = 6.dp.toPx()
        fun x(i: Int) = pad + i.toFloat() / (values.size - 1) * (size.width - 2 * pad)
        fun y(v: Long) = pad + (hi - v).toFloat() / (hi - lo).toFloat() * (size.height - 2 * pad)
        if (lo < 0) drawLine(c.rule, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        for (i in 1 until values.size) {
            drawLine(c.ink, Offset(x(i - 1), y(values[i - 1])), Offset(x(i), y(values[i])), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(c.ink, radius = 4.dp.toPx(), center = Offset(x(values.lastIndex), y(values.last())))
    }
}

private val LEVEL_ALPHA = floatArrayOf(0f, 0.15f, 0.35f, 0.70f, 0.92f)

/**
 * The month as weeks of day cells, shaded in four steps of ink by how much was spent (design: ink and quiet greys only, no colour ramp).
 * Days after today are drawn empty. Each cell has the date and the amount for TalkBack.
 */
@Composable
fun HeatGrid(
    weeks: List<List<HeatCell>>,
    firstDay: DayOfWeek,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                val d = firstDay.plus(i.toLong())
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = ChittaTheme.type.label, color = c.inkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    val date = cell.date
                    if (date == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val level = cell.level.coerceIn(0, 4)
                        val dense = level >= 3
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(c.rule.copy(alpha = 0.5f))
                                .background(c.ink.copy(alpha = LEVEL_ALPHA[level]))
                                .semantics { contentDescription = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, " + if (cell.spent.value > 0) "spent ${IndianFormat.spoken(cell.spent)}" else "nothing spent" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(date.dayOfMonth.toString(), style = ChittaTheme.type.label, color = if (dense) c.background else c.ink, modifier = Modifier.padding(0.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The optional flat donut (master section 3): ring segments in the earthy category set with a small gap between them, the total in
 * the middle, never 3D or a gradient. The legend sits right under it and carries the names, so colour is never the only cue.
 */
@Composable
fun DonutChart(slices: List<com.mansaketh.chitta.core.domain.DonutSlice>, total: Paise, description: String, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    val sum = slices.sumOf { it.amount.value }.coerceAtLeast(1L)
    Box(modifier.fillMaxWidth().aspectRatio(1.6f).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(168.dp).aspectRatio(1f)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val gapDeg = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.amount.value / sum
                drawArc(
                    color = if (s.categoryId == null) c.inkMuted else c.category[i % c.category.size],
                    startAngle = start + gapDeg / 2, sweepAngle = (sweep - gapDeg).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        com.mansaketh.chitta.core.design.components.AmountText(total, style = ChittaTheme.type.title)
    }
}
