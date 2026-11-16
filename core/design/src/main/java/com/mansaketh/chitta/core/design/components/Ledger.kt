package com.mansaketh.chitta.core.design.components

import com.mansaketh.chitta.core.design.kit.EntryRow

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise

/** UI-only description of one ledger line. The real transaction model arrives in Phase 3. */
data class LedgerEntry(
    val title: String,
    val category: String,
    val glyph: ImageVector,
    val categoryColor: Color,
    val amount: Paise,
    val tone: AmountTone = AmountTone.Expense,
    /** Plain word for a status stamp, for example "Upcoming". At most two stamps per screen. */
    val stamp: String? = null,
    val stampTone: StampTone = StampTone.Neutral,
    /** The entry's id, so a screen can tell which row was tapped. Not shown. */
    val id: String = "",
    /** In multi-select mode: ticked rows show an ink check in place of the category glyph. */
    val selected: Boolean = false,
    /** Flags such as "Split" or "Possible duplicate", drawn as [TagChip]s under the category line (D263). */
    val tags: List<String> = emptyList(),
)

/**
 * One day of the ledger: every entry is an [EntryRow] (v8 §4.10). The first row carries the date in the margin column (day number in Fraunces,
 * weekday under it); later rows of the day keep that room empty. The day's total sits under the last row, on the rule. While [selecting]
 * every row shows a tick box before its glyph.
 */
@Composable
fun LedgerDay(
    dayNumber: Int,
    weekday: String,
    entries: List<LedgerEntry>,
    dayTotal: Paise,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    /** False while the Ledger is filtered: the page (p.18) draws no day total, so every row keeps its hairline under it. */
    showDayTotal: Boolean = true,
    onEntryClick: (LedgerEntry) -> Unit = {},
    onEntryLongClick: (LedgerEntry) -> Unit = {},
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, e ->
            EntryRow(
                title = e.title, meta = e.category, glyph = e.glyph, glyphColor = e.categoryColor, amount = e.amount, tone = e.tone,
                marginDay = if (i == 0) dayNumber else null, weekday = if (i == 0) weekday else null, reserveMargin = true,
                tag = e.tags.firstOrNull(), moreTags = e.tags.drop(1),
                stamp = e.stamp, stampTone = e.stampTone,
                selectMark = if (selecting) e.selected else null,
                onClick = { onEntryClick(e) }, onLongClick = { onEntryLongClick(e) },
                rule = !showDayTotal || i != entries.lastIndex,
            )
        }
        if (showDayTotal) Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.Gap8).padding(start = Dimens.EntryMarginColumn, top = Dimens.Gap2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text("Day total", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(end = Dimens.Gap2))
            AmountText(dayTotal, style = ChittaTheme.type.amountSmall, color = c.inkMuted)
        }
        if (showDayTotal) {
            HairlineRule()
            Box(Modifier.height(Dimens.Gap2))
        }
    }
}

/**
 * Title, a small category glyph, optional stamp, amount right-aligned. At least 56 dp, taller as text grows.
 * From 150% text upward the amount moves under the title, so a stamp such as UPCOMING keeps its full width.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerRow(
    entry: LedgerEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val c = ChittaTheme.colors
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val stacked = LocalDensity.current.fontScale >= 1.5f
    val glyph: @Composable () -> Unit = {
        if (entry.selected) {
            Box(Modifier.size(Dimens.GlyphCircle).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                Icon(ChittaIcons.Check, contentDescription = "Selected", tint = c.background, modifier = Modifier.size(Dimens.GlyphIcon))
            }
        } else {
            Box(
                Modifier.size(Dimens.GlyphCircle).clip(CircleShape).background(entry.categoryColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.glyph, contentDescription = null, tint = entry.categoryColor, modifier = Modifier.size(Dimens.GlyphIcon))
            }
        }
    }
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            // One line each with an ellipsis, so every row keeps the same height; the full text is on the entry's own page.
            Text(entry.title, style = ChittaTheme.type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.category, style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (entry.stamp != null) Stamp(entry.stamp, tone = entry.stampTone)
            }
            // Tags sit on their own line so the category and stamp keep their room; only flagged rows are taller.
            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = Dimens.Space1),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
                ) { entry.tags.forEach { TagChip(it) } }
            }
        }
    }
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.RowLedger)
        .pressCombinedClickable(role = Role.Button, onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() }, onClick = onClick)
        .padding(vertical = Dimens.RowPadV)
    if (stacked) {
        Column(base, verticalArrangement = Arrangement.spacedBy(Dimens.Gap1)) {
            Row(verticalAlignment = Alignment.Top) {
                glyph()
                text(Modifier.weight(1f).padding(start = Dimens.Gap3))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AmountText(entry.amount, tone = entry.tone)
            }
        }
    } else {
        Row(base, verticalAlignment = Alignment.CenterVertically) {
            glyph()
            text(Modifier.weight(1f).padding(start = Dimens.Gap3, end = Dimens.TitleAmountGap))
            AmountText(entry.amount, tone = entry.tone, fallbackStyles = listOf(ChittaTheme.type.amountSmall))
        }
    }
}

/** A sticky month header: month name with money in and money out. */
@Composable
fun MonthHeader(
    month: String,
    moneyIn: Paise,
    moneyOut: Paise,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        // 12 dp under the tab rule, 8 dp to In and Out, 12 dp to the rule, 8 dp before the first day (the gap stays when rows scroll under).
        Text(
            month,
            style = ChittaTheme.type.screenTitle,
            color = c.ink,
            modifier = Modifier.padding(top = Dimens.HeadingGap),
        )
        // In at the start, Out at the end. FlowRow so at 200% text Out wraps under In instead of colliding.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = Dimens.InlineGap, bottom = Dimens.HeadingGap),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap2),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) {
                Text("In", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.alignByBaseline())
                AmountText(moneyIn, style = ChittaTheme.type.amountSmall, tone = AmountTone.Income, fallbackStyles = listOf(ChittaTheme.type.amountSmall), modifier = Modifier.alignByBaseline())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) {
                Text("Out", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.alignByBaseline())
                AmountText(moneyOut, style = ChittaTheme.type.amountSmall, modifier = Modifier.alignByBaseline())
            }
        }
        HairlineRule()
        Box(Modifier.height(Dimens.InlineGap))
    }
}

/** Horizontal ledger bar: label, amount, then a bar in ink. The default category breakdown (no donut). */
@Composable
fun LedgerBar(
    label: String,
    amount: Paise,
    fraction: Float,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    /** The category's colour as a round dot before the name (v8 PDF page 31). The bar itself stays ink. */
    dot: Color? = null,
) {
    val c = ChittaTheme.colors
    val shown = fraction.coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMin)
            .padding(vertical = Dimens.Gap2)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, ${IndianFormat.spoken(amount)}, ${(shown * 100).toInt()} percent of the largest"
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap2),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.size(Dimens.Space3).background(dot, CircleShape))
                    Spacer(Modifier.width(Dimens.Space2))
                }
                Text(label, style = ChittaTheme.type.body, color = c.ink)
            }
            AmountText(amount, style = ChittaTheme.type.amountSmall)
        }
        Box(Modifier.fillMaxWidth().height(Dimens.BarHeight).background(c.rule)) {
            Box(Modifier.fillMaxWidth(shown).height(Dimens.BarHeight).background(if (accent) c.sindoor else c.ink))
        }
    }
}
