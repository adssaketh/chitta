package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/** Ignores a second tap within [windowMillis]: save buttons cannot be double-tapped (master section 13). */
@Composable
fun rememberOnce(windowMillis: Long = 700, action: () -> Unit): () -> Unit {
    val last = remember { longArrayOf(0L) }
    return {
        val now = System.nanoTime() / 1_000_000
        if (now - last[0] >= windowMillis) {
            last[0] = now
            action()
        }
    }
}

/**
 * The accent add button, bottom-right on every main screen. Tap adds a transaction; long-press opens the
 * extra-add menu (master section 6). 56 dp, sindoor, the plus tinted for contrast.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            .size(Dimens.ButtonHeight)
            .clip(CircleShape)
            .background(c.sindoor)
            .combinedClickable(
                role = Role.Button,
                onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey); onClick() },
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ChittaIcons.Plus, contentDescription = "Add a transaction. Long-press for more.", tint = c.onSindoor, modifier = Modifier.size(Dimens.IconSize))
    }
}

/** A 48 dp icon-only button for top bars and rows. [description] is required for TalkBack. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ChittaTheme.colors.ink,
) {
    Box(
        modifier
            .size(Dimens.TouchTarget)
            .clip(CircleShape)
            .pressClickable(role = Role.Button, shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(Dimens.IconSize))
    }
}
