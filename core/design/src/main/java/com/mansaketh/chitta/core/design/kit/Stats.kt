package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens

/** One figure and its label for [StatGrid], e.g. "3,218" and "entries". */
data class Stat(val value: String, val label: String)

/**
 * A grid of figures (PDF p.11, "Bring over your book"): [columns] equal columns [Dimens.StatColumnGap] apart, each cell a hairline on top,
 * the figure in Fraunces 24 and a muted label under it. The last row of a grid that does not fill it leaves its cells empty.
 * Each cell reads as one phrase to TalkBack ("6 accounts").
 */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier, columns: Int = 3) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        stats.chunked(columns).forEach { rowStats ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.StatColumnGap)) {
                for (i in 0 until columns) {
                    val s = rowStats.getOrNull(i)
                    Column(Modifier.weight(1f)) {
                        if (s != null) {
                            HairlineRule()
                            Column(
                                Modifier
                                    .padding(top = Dimens.StatPadTop, bottom = Dimens.StatPadBottom)
                                    .semantics(mergeDescendants = true) { contentDescription = "${s.value} ${s.label}" },
                            ) {
                                Text(s.value, style = ChittaTheme.type.amountTile, color = c.ink, maxLines = 1, overflow = TextOverflow.Clip)
                                Text(s.label, style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A line with a result at the end (PDF p.12, "Dry run"): ink title, muted [sub], and a tick in the green when [ok], or a cross in the warning
 * colour when it is not. Not tappable and no caret. A rule closes the row unless [rule] is false; screens that run their rules edge to edge
 * pass false and draw a [HairlineRule] outside their padding, as First run does.
 */
@Composable
fun ResultRow(title: String, sub: String, ok: Boolean, modifier: Modifier = Modifier, rule: Boolean = true) {
    val c = ChittaTheme.colors
    val verdict = if (ok) "matches" else "does not match"
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinTwoLine)
                .padding(vertical = Dimens.RowPadV)
                .semantics(mergeDescendants = true) { contentDescription = "$title, $sub, $verdict" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.rupeeAware(), style = ChittaTheme.type.bodyMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = if (ok) c.inkMuted else c.warningText, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Dimens.TitleAmountGap))
            Icon(
                if (ok) ChittaIcons.Check else ChittaIcons.Close,
                contentDescription = null,
                tint = if (ok) c.green else c.warningText,
                modifier = Modifier.size(Dimens.IconTick).clearAndSetSemantics { },
            )
        }
        if (rule) HairlineRule()
    }
}
