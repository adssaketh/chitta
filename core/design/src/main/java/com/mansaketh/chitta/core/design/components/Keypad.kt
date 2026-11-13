package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/** What a keypad key sends. The Quick Add screen (Phase 4) turns these into the amount string. */
sealed interface KeypadKey {
    data class Digit(val digit: Char) : KeypadKey
    data object Dot : KeypadKey
    data class Operator(val symbol: Char) : KeypadKey
    data object Backspace : KeypadKey
}

private val OPERATOR_NAMES = mapOf('+' to "plus", '−' to "minus", '×' to "times", '÷' to "divided by")

/**
 * Full-width number pad with calculator operators. Digits on the left, operators in a muted column on the
 * right. Keys are at least 56 dp tall; each key gives a light tick (turn off with [haptics]).
 * Only sends keys: the calculator itself is built in Phase 4.
 */
@Composable
fun Keypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    haptics: Boolean = true,
) {
    val feedback = LocalHapticFeedback.current
    val send: (KeypadKey) -> Unit = {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onKey(it)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap1)) {
        val grid = listOf(
            listOf('7', '8', '9') to '÷',
            listOf('4', '5', '6') to '×',
            listOf('1', '2', '3') to '−',
        )
        grid.forEach { (digits, op) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap1)) {
                digits.forEach { d -> Key(d.toString(), d.toString()) { send(KeypadKey.Digit(d)) } }
                Key(op.toString(), OPERATOR_NAMES.getValue(op), muted = true) { send(KeypadKey.Operator(op)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap1)) {
            Key(".", "decimal point") { send(KeypadKey.Dot) }
            Key("0", "0") { send(KeypadKey.Digit('0')) }
            IconKey("Delete last character") { send(KeypadKey.Backspace) }
            Key("+", OPERATOR_NAMES.getValue('+'), muted = true) { send(KeypadKey.Operator('+')) }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, description: String, muted: Boolean = false, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Dimens.ButtonHeight)
            .clip(Shapes.Control)
            .background(if (pressed) c.rule else c.background)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ChittaTheme.type.amount, color = if (muted) c.inkMuted else c.ink)
    }
}

@Composable
private fun RowScope.IconKey(description: String, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Dimens.ButtonHeight)
            .clip(Shapes.Control)
            .background(if (pressed) c.rule else c.background)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ChittaIcons.Backspace, contentDescription = description, tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
    }
}
