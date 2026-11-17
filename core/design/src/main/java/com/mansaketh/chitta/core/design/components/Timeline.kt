package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens

/**
 * One step of a timeline (Entry history, activity lists): a thin rail on the left with a dot, a medium-weight [title] with the muted
 * [meta] (usually the time) under it, then [content] (details, actions). The rail is a [Dimens.Hairline] line joining the dots; it
 * starts at the dot on the [first] item and stops at the dot on the [last]. The newest step's dot is ink, older ones the mark grey.
 * Rows are [Dimens.RowPadV] top and bottom, so a timeline keeps the same rhythm as every other list (D268).
 */
@Composable
fun TimelineItem(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    /** A quiet word after the time on the same line ("12 Nov, 7:05 pm · Restore", PDF p.20). */
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = ChittaTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(Dimens.Space6).fillMaxHeight()) {
            val x = size.width / 2f
            // The dot sits level with the title's first line: RowPadV plus half of a 24 dp line.
            val dotY = (Dimens.RowPadV + Dimens.Space3).toPx()
            val line = Dimens.Hairline.toPx()
            if (!first) drawLine(c.rule, Offset(x, 0f), Offset(x, dotY), strokeWidth = line)
            if (!last) drawLine(c.rule, Offset(x, dotY), Offset(x, size.height), strokeWidth = line)
            drawCircle(if (first) c.ink else c.mark, radius = Dimens.Dot.toPx() / 2f, center = Offset(x, dotY))
        }
        Column(Modifier.weight(1f).padding(start = Dimens.InlineGap, top = Dimens.RowPadV, bottom = Dimens.RowPadV)) {
            Text(title, style = ChittaTheme.type.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (action != null && onAction != null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("$meta · ", style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 1)
                    Text(
                        action, style = ChittaTheme.type.rowMeta.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = c.inkMuted,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget).pressClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction).wrapContentHeight(androidx.compose.ui.Alignment.CenterVertically),
                    )
                }
            } else Text(meta, style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}
