package com.mansaketh.chitta.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.Motion
import com.mansaketh.chitta.core.design.theme.rememberMotionEnabled
import com.mansaketh.chitta.core.design.tokens.Dimens

/*
 * Press, tint and focus feedback for everything tappable (UI polish 2, D265).
 * Pressed: a faint ink tint and a 0.98 scale on a spring. Focused: a 2 dp ink outline.
 * The scale is skipped when the phone's "Remove animations" or the in-app Animations switch is off; the tint is not motion, so it stays.
 * The clickable modifiers here pass indication = null because the feedback is drawn by these modifiers instead of a ripple.
 */

/** Shrinks the content to [Motion.PressScale] while pressed. Put it first in the chain so everything after it scales. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = rememberMotionEnabled()
    val scale by animateFloatAsState(if (pressed && motion) Motion.PressScale else 1f, Motion.pressSpring<Float>(), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A faint ink tint behind the content while pressed. */
@Composable
fun Modifier.pressTint(source: MutableInteractionSource, shape: Shape = RectangleShape): Modifier {
    val pressed by source.collectIsPressedAsState()
    val ink = ChittaTheme.colors.ink
    return this.background(if (pressed) ink.copy(alpha = Motion.PressTintAlpha) else Color.Transparent, shape)
}

/** A [Dimens.FocusRing] ink outline while focused (keyboard, switch access, D-pad). */
@Composable
fun Modifier.focusRing(source: MutableInteractionSource, shape: Shape = RectangleShape): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) this.border(Dimens.FocusRing, ChittaTheme.colors.ink, shape) else this
}

/** Scale, tint and focus outline together: the feedback for a list row. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource, shape: Shape = RectangleShape): Modifier =
    this.pressScale(source).pressTint(source, shape).focusRing(source, shape)

/** A clickable row or icon with the app's press feedback. Replaces `clickable` wherever the whole item is the tap target. */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    shape: Shape = RectangleShape,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressFeedback(source, shape)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/** Same, with a long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressCombinedClickable(
    role: Role? = Role.Button,
    shape: Shape = RectangleShape,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressFeedback(source, shape)
        .combinedClickable(interactionSource = source, indication = null, role = role, onLongClick = onLongClick, onClick = onClick)
}
