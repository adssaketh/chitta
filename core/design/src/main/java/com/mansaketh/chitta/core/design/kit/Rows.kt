package com.mansaketh.chitta.core.design.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.AmountText
import com.mansaketh.chitta.core.design.components.AmountTone
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.components.StampTone
import com.mansaketh.chitta.core.design.components.TagChip
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.components.pressCombinedClickable
import com.mansaketh.chitta.core.design.components.pressFeedback
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.Motion
import com.mansaketh.chitta.core.design.theme.rememberMotionEnabled
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes
import com.mansaketh.chitta.core.money.Paise

/*
 * Rows (v8 kit, PDF pages 3 and 4). One version of each, used on every screen:
 *  - a row that opens something has an ink title and a caret (NavRow, ValueRow with a click);
 *  - on/off is always a switch (SwitchRow); one choice gets a tick (TickRow); many choices get tick boxes (CheckRow);
 *  - a list of entries is always EntryRow.
 * Row heights are minimums (56 dp, 68 dp with a second line), so text at 200% grows the row instead of clipping.
 */

/**
 * The only way to show a heading (v8 §4.4): [Dimens.SectionGap] above (or [Dimens.HeadingGap] when [first]), the title in Hanken 15 medium,
 * an optional muted [note] at the end (a figure like "₹18,458 due", or "See all" when [onNote] is given), [Dimens.HeadingGap] and a hairline,
 * then the content. A note that can be tapped keeps a 48 dp row; its own padding supplies the air under the text.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    note: String? = null,
    onNote: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = ChittaTheme.colors
    val tappable = note != null && onNote != null
    Column(modifier.fillMaxWidth().padding(top = if (first) Dimens.HeadingGap else Dimens.SectionGap)) {
        Row(
            Modifier.fillMaxWidth().then(if (tappable) Modifier.heightIn(min = Dimens.TouchTarget) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.rupeeAware(),
                style = ChittaTheme.type.sectionTitle,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (note != null) {
                Text(
                    note.rupeeAware(),
                    style = ChittaTheme.type.sectionNote,
                    color = c.inkMuted,
                    modifier = Modifier
                        .padding(start = Dimens.TitleAmountGap)
                        .then(
                            if (tappable) {
                                Modifier
                                    .heightIn(min = Dimens.TouchTarget)
                                    .pressClickable(role = Role.Button, onClick = onNote!!)
                                    .padding(horizontal = Dimens.InlineGap)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            } else Modifier,
                        ),
                )
            }
        }
        if (!tappable) Spacer(Modifier.size(Dimens.HeadingGap))
        HairlineRule()
        content()
    }
}

/** A row that opens another screen or sheet: optional leading icon (22), ink title (medium), optional muted sub line, optional muted value, caret (18). */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sub: String? = null,
    value: String? = null,
    rule: Boolean = true,
    /** A destructive choice (Delete everything): title and icon in warning text, never filled. */
    danger: Boolean = false,
) {
    val c = ChittaTheme.colors
    val tint = if (danger) c.warningText else c.ink
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (sub == null) Dimens.RowMinOneLine else Dimens.RowMinTwoLine)
                .pressClickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.IconRowLeading))
                Spacer(Modifier.width(Dimens.Space3))
            }
            Column(Modifier.weight(1f)) {
                Text(title.rupeeAware(), style = ChittaTheme.type.bodyMedium, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) {
                Spacer(Modifier.width(Dimens.TitleAmountGap))
                Text(
                    value.rupeeAware(),
                    style = ChittaTheme.type.body,
                    color = c.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = Dimens.LabelMaxWidth),
                )
            }
            Spacer(Modifier.width(Dimens.Space2))
            Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconCaret))
        }
        if (rule) HairlineRule()
    }
}

/**
 * A label and its value: muted label at the start, ink value at the end. Read-only when [onClick] is null; with a click it opens a picker
 * and shows the caret (a row with no caret does not open anything).
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    rule: Boolean = true,
    /** A green tick after the value, for a check that passed (the Bring over dry run). TalkBack hears [matchLabel] as the state. */
    matched: Boolean = false,
    matchLabel: String = "Matches",
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinOneLine)
                .then(if (onClick != null) Modifier.pressClickable(role = Role.Button, onClick = onClick) else Modifier)
                .then(if (matched) Modifier.semantics(mergeDescendants = true) { stateDescription = matchLabel } else Modifier)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label.rupeeAware(), style = ChittaTheme.type.body, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = Dimens.LabelMaxWidth))
            Text(
                value.rupeeAware(),
                style = ChittaTheme.type.body,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = Dimens.TitleAmountGap),
            )
            if (matched) {
                Spacer(Modifier.width(Dimens.Space2))
                Icon(ChittaIcons.Check, contentDescription = null, tint = c.green, modifier = Modifier.size(Dimens.IconTick))
            }
            if (onClick != null) {
                Spacer(Modifier.width(Dimens.Space2))
                Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconCaret))
            }
        }
        if (rule) HairlineRule()
    }
}

/**
 * The switch itself, drawn only (the row around it carries the role and the state). Ink, never sindoor (D280): on = ink track with a page-coloured
 * knob at the end; off = a mark-grey outline with a mark-grey knob at the start. 46 x 28 dp, 19 dp knob, 1.5 dp border. The knob slides in 250 ms,
 * or jumps when animations are off.
 */
@Composable
fun ChittaSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    val motion = rememberMotionEnabled()
    val pad = (Dimens.SwitchHeight - Dimens.SwitchKnob) / 2
    val travel = Dimens.SwitchWidth - Dimens.SwitchKnob - pad * 2
    val x: Dp by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = if (motion) Motion.expandTween<Dp>() else snap<Dp>(),
        label = "knob",
    )
    Box(
        modifier
            .size(Dimens.SwitchWidth, Dimens.SwitchHeight)
            .clip(CircleShape)
            .background(if (checked) c.ink else Color.Transparent)
            .border(Dimens.SwitchBorder, if (checked) c.ink else c.mark, CircleShape)
            .padding(pad),
    ) {
        Box(Modifier.offset(x = x).size(Dimens.SwitchKnob).clip(CircleShape).background(if (checked) c.background else c.mark))
    }
}

/** An on/off choice: [title], optional muted [sub], the switch at the end. The whole row toggles and TalkBack calls it a switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    rule: Boolean = true,
) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (sub == null) Dimens.RowMinOneLine else Dimens.RowMinTwoLine)
                .pressFeedback(source)
                .toggleable(value = checked, interactionSource = source, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.rupeeAware(), style = ChittaTheme.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Dimens.TitleAmountGap))
            ChittaSwitch(checked)
        }
        if (rule) HairlineRule()
    }
}

/** Pick one: the title, and an ink tick (22) at the end of the chosen row. The room for the tick is always kept, so rows do not shift. */
@Composable
fun TickRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    rule: Boolean = true,
) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (sub == null) Dimens.RowMinOneLine else Dimens.RowMinTwoLine)
                .pressFeedback(source)
                .selectable(selected = selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.rupeeAware(), style = ChittaTheme.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Dimens.TitleAmountGap))
            Box(Modifier.size(Dimens.IconTick), contentAlignment = Alignment.Center) {
                if (selected) Icon(ChittaIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(Dimens.IconTick))
            }
        }
        if (rule) HairlineRule()
    }
}

/** The tick box, drawn only: 22 dp, 4 dp corners, 1.5 dp border; ticked = ink fill with a page-coloured tick. */
@Composable
fun TickBox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Box(
        modifier
            .size(Dimens.TickBoxSize)
            .clip(Shapes.Tag)
            .background(if (checked) c.ink else Color.Transparent)
            .border(Dimens.TickBoxBorder, if (checked) c.ink else c.inkMuted, Shapes.Tag),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(ChittaIcons.Check, contentDescription = null, tint = c.background, modifier = Modifier.size(Dimens.IconSmall))
    }
}

/**
 * Pick many: a tick box at the start, the [title], an optional muted [sub] (a reason, a category), an optional amount at the end.
 * Replaces "Chosen", Intake's `TickBox` and the Ledger multi-select circle. TalkBack calls it a checkbox.
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    amount: Paise? = null,
    amountTone: AmountTone = AmountTone.Expense,
    rule: Boolean = true,
) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (sub == null) Dimens.RowMinOneLine else Dimens.RowMinTwoLine)
                .pressFeedback(source)
                .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TickBox(checked)
            Spacer(Modifier.width(Dimens.Space4))
            Column(Modifier.weight(1f)) {
                Text(title.rupeeAware(), style = ChittaTheme.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (amount != null) {
                Spacer(Modifier.width(Dimens.TitleAmountGap))
                AmountText(amount, style = ChittaTheme.type.amountSmall, tone = amountTone)
            }
        }
        if (rule) HairlineRule()
    }
}

/** A category glyph: a 40 dp circle in the category colour at 16%, the icon (20) in the full colour (D279). Decoration; the row carries the words. */
@Composable
fun Glyph(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(Dimens.GlyphCircle).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimens.GlyphIcon))
    }
}

/**
 * Every list of entries (Ledger, Home "Coming up", Loans, bills): an optional margin date (day in Fraunces 20, weekday in Plex 13), the 40 dp
 * [Glyph], the [title] (medium) with a muted [meta] line, an optional [tag] ([TagChip], on its own line) and [stamp], and the amount in Fraunces 18
 * (income green with a "+"). [reserveMargin] keeps the date column's room on the rows of a day that do not show the date. From 150% text the
 * amount moves under the title, so a stamp keeps its width. Unlike the glyph, nothing here is a separate tap target: the whole row is one button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    title: String,
    meta: String,
    glyph: ImageVector,
    glyphColor: Color,
    amount: Paise,
    modifier: Modifier = Modifier,
    tone: AmountTone = AmountTone.Expense,
    marginDay: Int? = null,
    weekday: String? = null,
    reserveMargin: Boolean = marginDay != null,
    tag: String? = null,
    stamp: String? = null,
    stampTone: StampTone = StampTone.Neutral,
    /** More flags after [tag] (a row can be Split and a Possible duplicate at once); drawn on the same line, wrapping if needed. */
    moreTags: List<String> = emptyList(),
    /** In multi-select: null = not selecting; otherwise a tick box ([TickBox]) before the glyph, ticked or not. */
    selectMark: Boolean? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    rule: Boolean = true,
) {
    val c = ChittaTheme.colors
    val stacked = LocalDensity.current.fontScale >= 1.5f
    val textColumn: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(title.rupeeAware(), style = ChittaTheme.type.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap), verticalAlignment = Alignment.CenterVertically) {
                Text(meta.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (stamp != null) Stamp(stamp, tone = stampTone)
            }
            val allTags = listOfNotNull(tag) + moreTags
            if (allTags.isNotEmpty()) {
                Row(Modifier.padding(top = Dimens.Space1), horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) { allTags.forEach { TagChip(it) } }
            }
        }
    }
    val leadMark: @Composable () -> Unit = {
        if (selectMark != null) {
            TickBox(selectMark, Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(Dimens.Space3))
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinTwoLine)
                .pressCombinedClickable(role = if (selectMark != null) Role.Checkbox else Role.Button, onLongClick = onLongClick, onClick = onClick)
                .then(if (selectMark != null) Modifier.semantics { selected = selectMark } else Modifier)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reserveMargin) {
                Column(
                    Modifier
                        .width(Dimens.EntryMarginColumn)
                        .align(Alignment.Top)
                        .clearAndSetSemantics { if (marginDay != null) contentDescription = "${weekday.orEmpty()} $marginDay".trim() },
                ) {
                    if (marginDay != null) {
                        Text(marginDay.toString(), style = ChittaTheme.type.marginDay, color = c.ink)
                        if (weekday != null) Text(weekday, style = ChittaTheme.type.mono, color = c.inkMuted)
                    }
                }
            }
            if (stacked) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        leadMark()
                        Glyph(glyph, glyphColor)
                        textColumn(Modifier.weight(1f).padding(start = Dimens.Space3))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.Space1), horizontalArrangement = Arrangement.End) {
                        AmountText(amount, style = ChittaTheme.type.amountRow, tone = tone)
                    }
                }
            } else {
                leadMark()
                Glyph(glyph, glyphColor)
                textColumn(Modifier.weight(1f).padding(start = Dimens.Space3, end = Dimens.TitleAmountGap))
                AmountText(amount, style = ChittaTheme.type.amountRow, tone = tone, fallbackStyles = listOf(ChittaTheme.type.amountSmall))
            }
        }
        if (rule) HairlineRule()
    }
}
