package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.mansaketh.chitta.core.design.components.focusRing
import com.mansaketh.chitta.core.design.components.pressScale
import com.mansaketh.chitta.core.design.components.rememberOnce
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/**
 * The four kinds of button (v8 kit, PDF page 4). One red button per screen.
 *  - [Primary]: sindoor fill. The one action that matters.
 *  - [Secondary]: surface fill, 1 dp rule border, ink text.
 *  - [Link]: underlined ink text, no box. For the quiet "Show 4 more", "Clear all".
 *  - [Danger]: warning-colour words with a trash icon, never filled. The red word on a screen that deletes.
 */
enum class ButtonKind { Primary, Secondary, Link, Danger }

/**
 * A button. It is only as wide as its words unless the caller sets the width (`Modifier.fillMaxWidth()` for a lone Save, `weight(1f)` in a
 * [ButtonPair]); the old forced full width is gone. Primary and Secondary are 56 dp tall ([small] makes them 48); Link and Danger are 48 dp
 * so the tap area is still large. A second tap inside 0.7 s is ignored (save buttons cannot be double-tapped, master section 13).
 */
@Composable
fun ChittaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    small: Boolean = false,
    /** Firmer haptic on success, for save buttons. */
    confirmHaptic: Boolean = false,
) {
    val c = ChittaTheme.colors
    val haptics = LocalHapticFeedback.current
    val once = rememberOnce {
        if (confirmHaptic) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
    }
    val source = remember { MutableInteractionSource() }
    val shown = if (enabled) 1f else 0.4f
    val boxed = kind == ButtonKind.Primary || kind == ButtonKind.Secondary
    val minHeight = when {
        !boxed -> Dimens.TouchTarget
        small -> Dimens.ButtonHeightSmall
        else -> Dimens.ButtonHeight
    }
    val fill = when (kind) {
        ButtonKind.Primary -> c.sindoor
        ButtonKind.Secondary -> c.surface
        else -> Color.Transparent
    }
    val content = when (kind) {
        ButtonKind.Primary -> c.onSindoor
        ButtonKind.Secondary, ButtonKind.Link -> c.ink
        ButtonKind.Danger -> c.warningText
    }.let { it.copy(alpha = it.alpha * shown) }
    val textStyle = if (kind == ButtonKind.Link) {
        ChittaTheme.type.button.copy(textDecoration = TextDecoration.Underline)
    } else {
        ChittaTheme.type.button
    }
    val leading = if (kind == ButtonKind.Danger && icon == null) ChittaIcons.Trash else icon
    Row(
        modifier
            .pressScale(source)
            .heightIn(min = minHeight)
            .clip(Shapes.Control)
            .background(fill.copy(alpha = fill.alpha * shown))
            .then(if (kind == ButtonKind.Secondary) Modifier.border(Dimens.Hairline, c.rule, Shapes.Control) else Modifier)
            .focusRing(source, Shapes.Control)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = once)
            .padding(horizontal = if (boxed) Dimens.Space6 else Dimens.Space2, vertical = Dimens.Space3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(Dimens.GlyphIcon))
            Spacer(Modifier.width(Dimens.Space2))
        }
        Text(text, style = textStyle, color = content, textAlign = TextAlign.Center)
    }
}

/**
 * Two buttons in one row, each `weight(1f)`, 12 dp apart (v8 §4.11). Both get the height of the taller, so one that wraps at 200% text
 * does not leave the other short. Pass the modifier you are given to each button.
 */
@Composable
fun ButtonPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        first(Modifier.weight(1f).fillMaxHeight())
        Spacer(Modifier.width(Dimens.Space3))
        second(Modifier.weight(1f).fillMaxHeight())
    }
}
