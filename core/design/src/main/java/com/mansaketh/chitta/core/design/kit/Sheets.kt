package com.mansaketh.chitta.core.design.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.IconAction
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.icons.KitIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.Motion
import com.mansaketh.chitta.core.design.theme.rememberMotionEnabled
import com.mansaketh.chitta.core.design.tokens.Dimens

/*
 * Sheets and groups (v8 kit, PDF pages 16 and 36).
 */

/**
 * The head of every sheet (v8 §4.19): the handle (36 x 4, rule colour), 6 dp under it the title in Fraunces 22 medium, and an optional Close
 * button at the end. It draws its own handle, so a sheet that uses it is hosted by `SheetSurface(handle = false)`.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(Dimens.SheetHandleSizeW)
                .height(Dimens.SheetHandleSizeH)
                .clip(RoundedCornerShape(Dimens.SheetHandleRadius))
                .background(c.rule),
        )
        Spacer(Modifier.height(Dimens.SheetHandleGap))
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = ChittaTheme.type.sheetTitle,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onClose != null) IconAction(ChittaIcons.Close, "Close", onClose)
        }
    }
}

/**
 * The header row of a group that opens and closes (v8 §4.22, replaces the header in the old `ExpandGroup`): leading icon (22), the title (16 medium),
 * a muted caret up or down (18), 20 dp above and 12 dp below the line, and a hairline. The whole row is the tap target and TalkBack hears
 * "Expanded" or "Collapsed".
 */
@Composable
fun GroupHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTarget)
                .pressClickable(role = Role.Button, onClick = onClick)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(top = Dimens.GroupTop, bottom = Dimens.GroupBottom),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(Dimens.IconRowLeading))
                Spacer(Modifier.width(Dimens.Space3))
            }
            Text(
                title,
                style = ChittaTheme.type.bodyMedium,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Dimens.TitleAmountGap))
            Icon(
                if (expanded) KitIcons.CaretUp else ChittaIcons.CaretDown,
                contentDescription = null,
                tint = c.inkMuted,
                modifier = Modifier.size(Dimens.IconCaret),
            )
        }
        HairlineRule()
    }
}

/**
 * A group of rows that opens and closes, built on [GroupHeader] (v8 §4.22): no frame, a hairline under the header, the rows below it. It keeps its own
 * open state across rotation, eases open in 250 ms (jumps when animations are off) and ticks lightly on every change.
 */
@Composable
fun ExpandGroup(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(modifier.fillMaxWidth()) {
        GroupHeader(title, expanded, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            expanded = !expanded
        }, icon = icon)
        if (motion) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(Motion.expandTween<IntSize>()) + fadeIn(Motion.expandTween<Float>()),
                exit = shrinkVertically(Motion.expandTween<IntSize>()) + fadeOut(Motion.expandTween<Float>()),
            ) { Column(content = content) }
        } else if (expanded) {
            Column(content = content)
        }
    }
}
