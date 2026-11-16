package com.mansaketh.chitta.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.Motion
import com.mansaketh.chitta.core.design.theme.rememberMotionEnabled
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/**
 * A group of rows that opens and closes (D264). The whole header row is the tap target (at least [Dimens.RowTall] tall); the caret turns
 * 180 degrees and the content eases open in about 250 ms ([Motion]). With animations off it simply appears. A light tick on every
 * change. The group keeps its own open state across rotation ([initiallyExpanded] is only the first value).
 *
 * The frame is a hairline outline, not a card: no fill, no shadow. Content rows are inset by [Dimens.CardInner] so their rules never
 * double up with the outline.
 */
@Composable
fun ExpandGroup(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ChittaTheme.colors
    val motion = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motion) Motion.expandTween<Float>() else snap<Float>(),
        label = "caret",
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Dimens.HeadingGap)
            .clip(Shapes.Control)
            .border(Dimens.Hairline, c.rule, Shapes.Control),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowTall)
                .pressClickable(role = Role.Button, shape = Shapes.Control) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    expanded = !expanded
                }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = Dimens.CardInner, vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(Dimens.GlyphCircleLegacy).clip(CircleShape).background(c.ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.GlyphIcon))
                }
                Box(Modifier.width(Dimens.Gap3))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = ChittaTheme.type.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (summary != null) Text(summary, style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.width(Dimens.TitleAmountGap))
            Icon(ChittaIcons.CaretDown, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall).rotate(turn))
        }
        if (motion) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(Motion.expandTween<IntSize>()) + fadeIn(Motion.expandTween<Float>()),
                exit = shrinkVertically(Motion.expandTween<IntSize>()) + fadeOut(Motion.expandTween<Float>()),
            ) {
                Column(Modifier.padding(horizontal = Dimens.CardInner)) { HairlineRule(); content() }
            }
        } else if (expanded) {
            Column(Modifier.padding(horizontal = Dimens.CardInner)) { HairlineRule(); content() }
        }
    }
}
