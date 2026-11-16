package com.mansaketh.chitta.ui.add

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens

/*
 * Pieces of the Quick Add sheet that are laid out in ways the shared kit does not cover (PDF p.15). Only this sheet uses them.
 */

/** Lets a child reach [start] and [end] into the side margins: the width it is given grows by both, and it is placed [start] to the left. */
internal fun Modifier.bleed(start: Dp, end: Dp = start): Modifier = layout { measurable, constraints ->
    val s = start.roundToPx()
    val e = end.roundToPx()
    val width = constraints.maxWidth + s + e
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-s, 0) }
}

/** Reports [by] less height than the child has and puts the child half of that higher, so a 48 dp tap area can sit as tightly as the drawing shows it. */
internal fun Modifier.tightVertical(by: Dp): Modifier = layout { measurable, constraints ->
    val cut = by.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - cut).coerceAtLeast(0)) { placeable.place(0, -cut / 2) }
}

/**
 * The top row: the type switch at the start, Pin and Split at the end (PDF p.15). The icons are measured first and keep their 48 dp. The switch
 * is tried with its side padding at 16 dp (as drawn), then 12, then 8, and the first that fits the space left is used, so all three parts always
 * show on one line, even on a 360 dp phone. Only if text is so large that even 8 does not fit does the switch scroll sideways.
 * The icons reach [Dimens.TopActionsBleed] into the end margin; the parent must be wide enough to hold that bleed (the form area is).
 */
@Composable
internal fun QuickAddTopRow(type: @Composable (Dp) -> Unit, actions: @Composable () -> Unit, modifier: Modifier = Modifier) {
    SubcomposeLayout(modifier) { constraints ->
        val total = constraints.maxWidth + Dimens.TopActionsBleed.roundToPx()
        val actionsPlaced = subcompose("actions") { Row(verticalAlignment = Alignment.CenterVertically) { actions() } }.first().measure(Constraints(maxWidth = total))
        val available = (total - actionsPlaced.width).coerceAtLeast(0)
        val steps = listOf(Dimens.SegmentPadH, Dimens.Space3, Dimens.Space2)
        var typePlaced: Placeable? = null
        for ((i, pad) in steps.withIndex()) {
            val natural = subcompose("type$i") { Box { type(pad) } }.first().measure(Constraints())
            if (natural.width <= available) { typePlaced = natural; break }
        }
        val chosen = typePlaced ?: subcompose("typeFit") { Box(Modifier.horizontalScroll(rememberScrollState())) { type(steps.last()) } }.first().measure(Constraints(maxWidth = available))
        val height = maxOf(actionsPlaced.height, chosen.height)
        layout(constraints.maxWidth, height) {
            chosen.place(0, (height - chosen.height) / 2)
            actionsPlaced.place(total - actionsPlaced.width, (height - actionsPlaced.height) / 2)
        }
    }
}

/** One tappable piece of the summary line: [text] is what shows, [description] is what TalkBack says it opens. */
internal data class SummaryPart(val text: String, val description: String, val onClick: () -> Unit, val separator: String = " · ")

/**
 * "HDFC UPI · Today · Everyone" and, at the end, "More options" (PDF p.15). Each piece opens its own picker; the first piece shortens before the
 * others do. [onMore] is null once the extra rows are showing.
 */
@Composable
internal fun SummaryLine(parts: List<SummaryPart>, onMore: (() -> Unit)?, modifier: Modifier = Modifier, moreLabel: String = "Add a note") {
    val c = ChittaTheme.colors
    Row(modifier.heightIn(min = Dimens.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            parts.forEachIndexed { i, p ->
                if (i > 0) Text(p.separator, style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 1)
                Text(
                    p.text.rupeeAware(),
                    style = ChittaTheme.type.rowMeta14,
                    color = c.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = (if (i == 0) Modifier.weight(1f, fill = false) else Modifier)
                        .heightIn(min = Dimens.TouchTarget)
                        .pressClickable(role = Role.Button, onClickLabel = p.description, onClick = p.onClick)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        if (onMore != null) {
            Text(
                moreLabel,
                style = ChittaTheme.type.link,
                color = c.ink,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget).pressClickable(role = Role.Button, onClick = onMore).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}
