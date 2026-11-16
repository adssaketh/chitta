package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/*
 * Chips and the segmented switch (v8 kit, PDF pages 4 and 5). Each draws at its spec height but keeps a 48 dp tap area (see HitBox).
 */

/**
 * A choice among categories or quick picks: 40 dp high, 1 dp rule border; the chosen one has a 1.5 dp ink border, the surface fill and medium text.
 * The leading category [icon] (18) is drawn in its [color]; it replaces the old square colour dot. Pick-one, so TalkBack calls it a radio button.
 * Without an [icon] it serves as a quick pick ("Today", "Yesterday").
 */
@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = ChittaTheme.colors.ink,
) {
    val c = ChittaTheme.colors
    HitBox(onClick = onClick, modifier = modifier, role = Role.RadioButton, selected = selected) {
        Row(
            Modifier
                .heightIn(min = Dimens.ChipHeight)
                .clip(Shapes.Control)
                .background(if (selected) c.surface else Color.Transparent)
                .border(if (selected) Dimens.ChipBorderSelected else Dimens.Hairline, if (selected) c.ink else c.rule, Shapes.Control)
                .padding(horizontal = Dimens.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimens.IconChip))
            Text(
                text,
                style = if (selected) ChittaTheme.type.chipSelected else ChittaTheme.type.chip,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A chip that opens a picker (Range, Accounts on Insights; PDF page 31): 40 dp high, 1 dp rule border, surface fill, the current choice in ink and
 * a muted caret (18). It is a button, not a pick-one choice, so TalkBack does not call it a radio button. The text shortens before the caret does.
 */
@Composable
fun PickerChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
) {
    val c = ChittaTheme.colors
    HitBox(onClick = onClick, modifier = modifier, role = Role.Button, onClickLabel = onClickLabel) {
        Row(
            Modifier
                .heightIn(min = Dimens.ChipHeight)
                .clip(Shapes.Control)
                .background(c.surface)
                .border(Dimens.Hairline, c.rule, Shapes.Control)
                .padding(horizontal = Dimens.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = ChittaTheme.type.chip, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Dimens.InlineGap))
            Icon(ChittaIcons.CaretDown, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconCaret))
        }
    }
}

/**
 * An active filter: 36 dp high, surface fill, 14 sp text, a muted x (14) at the end. Tapping removes it. TalkBack reads "Remove filter: [label]".
 */
@Composable
fun FilterChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    HitBox(
        onClick = onRemove,
        modifier = modifier.semantics { contentDescription = "Remove filter: $label" },
        role = Role.Button,
    ) {
        Row(
            Modifier
                .clearAndSetSemantics { }
                .heightIn(min = Dimens.FilterChipHeight)
                .clip(Shapes.Control)
                .background(c.surface)
                .border(Dimens.Hairline, c.rule, Shapes.Control)
                .padding(start = Dimens.Space3, end = Dimens.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = ChittaTheme.type.chip, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(Dimens.InlineGap))
            Icon(ChittaIcons.Close, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconFilterX))
        }
    }
}

/**
 * Two or three parts in a 1 dp rule outline with 12 dp corners and 3 dp padding; the chosen part has an ink fill and page-coloured text.
 * For Bars / Donut and Expense / Income / Transfer. It switches a view, not a screen: screen tabs stay [com.mansaketh.chitta.core.design.components.TextTabs] style.
 * Each part is a radio button for TalkBack.
 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 2..3) { "Segmented takes 2 or 3 parts" }
    val c = ChittaTheme.colors
    Box(modifier.heightIn(min = Dimens.HitMin), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .clip(Shapes.Control)
                .border(Dimens.Hairline, c.rule, Shapes.Control)
                .padding(Dimens.SegmentPad),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selectedIndex
                HitBox(onClick = { onSelect(i) }, role = Role.RadioButton, selected = on, shape = Shapes.SegmentPart, hitMin = Dimens.SegmentHeight) {
                    Box(
                        Modifier
                            .heightIn(min = Dimens.SegmentHeight)
                            .clip(Shapes.SegmentPart)
                            .background(if (on) c.ink else Color.Transparent)
                            .padding(horizontal = Dimens.SegmentPadH),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = if (on) ChittaTheme.type.chipSelected else ChittaTheme.type.chip, color = if (on) c.background else c.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}
