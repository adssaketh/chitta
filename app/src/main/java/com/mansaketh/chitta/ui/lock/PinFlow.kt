package com.mansaketh.chitta.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.fragment.app.FragmentActivity
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes
import com.mansaketh.chitta.data.PinGate
import com.mansaketh.chitta.data.PinRules
import kotlinx.coroutines.delay

/** What the PIN screen is asking for: the current PIN, a new one, or the new one again. */
enum class PinMode { Enter, Choose, Confirm }

/**
 * The PIN screen of PDF p.9, and the same screen when a PIN is chosen or changed in Settings.
 *
 * [atLock] adds the two bottom actions the page draws ("Forgot PIN?" and "Use fingerprint"); "Forgot PIN?" asks for the phone's own lock and
 * then lets a new PIN be chosen. [onCancel] (Settings only) puts a "Cancel" link where "Forgot PIN?" sits. With [thenChoose] a correct
 * current PIN leads on to choosing a new one (Change PIN); otherwise it ends the flow.
 */
@Composable
fun PinFlow(
    gate: PinGate,
    startMode: PinMode,
    onDone: () -> Unit,
    atLock: Boolean = false,
    thenChoose: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var mode by rememberSaveable { mutableStateOf(startMode) }
    var entered by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var waitLeft by remember { mutableIntStateOf(gate.waitSecondsLeft()) }
    val fingerprintReady = remember { atLock && BiometricGate.canUseBiometric(context) }
    val fingerprint = fingerprintReady && mode == PinMode.Enter

    // The wait counts down on screen; the keys come back at zero.
    LaunchedEffect(waitLeft > 0) {
        while (waitLeft > 0) { delay(1000); waitLeft = gate.waitSecondsLeft() }
    }
    // Ask for the fingerprint once as the lock appears; Cancel in that prompt leaves the PIN pad.
    LaunchedEffect(Unit) {
        if (atLock && startMode == PinMode.Enter && activity != null && BiometricGate.canUseBiometric(activity)) {
            BiometricGate.promptBiometric(activity, "Unlock Paddhu Pusthakam", onDone)
        }
    }

    fun finishEntry() {
        val pin = entered
        entered = ""
        when (mode) {
            PinMode.Enter -> when (val r = gate.verify(pin)) {
                PinRules.Check.Ok -> { message = null; if (thenChoose) mode = PinMode.Choose else onDone() }
                is PinRules.Check.Wrong -> message = PinRules.message(r)
                is PinRules.Check.Waiting -> { message = null; waitLeft = r.secondsLeft }
            }
            PinMode.Choose -> { first = pin; message = null; mode = PinMode.Confirm }
            PinMode.Confirm -> if (pin == first) { gate.set(pin); onDone() } else { first = ""; message = "The PINs did not match. Choose a PIN again."; mode = PinMode.Choose }
        }
    }

    val waiting = waitLeft > 0
    PinScreen(
        prompt = when (mode) { PinMode.Enter -> "Enter your PIN"; PinMode.Choose -> "Choose a PIN"; PinMode.Confirm -> "Enter it again" },
        filled = entered.length,
        message = if (waiting) PinRules.message(PinRules.Check.Waiting(waitLeft)) else message,
        keysEnabled = !waiting,
        onDigit = { d -> if (entered.length < PinRules.LENGTH) { entered += d; if (entered.length == PinRules.LENGTH) finishEntry() } },
        onBackspace = { entered = entered.dropLast(1) },
        bottomStart = {
            when {
                atLock && mode == PinMode.Enter -> PinLink("Forgot PIN?") {
                    activity?.let { a -> BiometricGate.prompt(a, "Reset your PIN", "Use your phone's lock, then choose a new PIN", onSuccess = { entered = ""; message = null; mode = PinMode.Choose }) }
                }
                onCancel != null -> PinLink("Cancel", onCancel)
                else -> Unit
            }
        },
        bottomEnd = {
            if (fingerprint && activity != null) {
                ChittaButton("Use fingerprint", { BiometricGate.promptBiometric(activity, "Unlock Paddhu Pusthakam", onDone) }, kind = ButtonKind.Secondary, icon = ChittaIcons.Fingerprint, small = true)
            }
        },
    )
}

/**
 * Lock (PDF p.9), Paper and Ink: round logo, name, one prompt line, four dots, a red line for a wrong PIN, the pad, then the two actions.
 * The keys have no tile; digits are Fraunces. A pressed key shows the rule colour, as on the Quick Add pad.
 */
@Composable
private fun PinScreen(
    prompt: String,
    filled: Int,
    message: String?,
    keysEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    bottomStart: @Composable () -> Unit,
    bottomEnd: @Composable () -> Unit,
) {
    val c = ChittaTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = Dimens.Margin).padding(bottom = Dimens.Margin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Dimens.LockTop))
        AppIcon(Dimens.LogoLock, shape = CircleShape)
        Spacer(Modifier.height(Dimens.Space5))
        Text("Paddhu Pusthakam", style = ChittaTheme.type.headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dimens.Gap2))
        Text(prompt, style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Space5))
        Row(
            Modifier.clearAndSetSemantics { contentDescription = "$filled of ${PinRules.LENGTH} digits entered" },
            horizontalArrangement = Arrangement.spacedBy(Dimens.PinDotGap),
        ) {
            repeat(PinRules.LENGTH) { i ->
                val on = i < filled
                Box(
                    Modifier.size(Dimens.PinDot).clip(CircleShape)
                        .then(if (on) Modifier.background(c.ink) else Modifier.border(Dimens.PinDotStroke, c.mark, CircleShape)),
                )
            }
        }
        Spacer(Modifier.height(Dimens.Space4))
        Box(Modifier.heightIn(min = Dimens.Gap6), contentAlignment = Alignment.TopCenter) {
            if (message != null) Text(message, style = ChittaTheme.type.label, color = c.warningText, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        PinPad(keysEnabled, onDigit, onBackspace)
        Spacer(Modifier.height(Dimens.Gap3))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.CenterStart) { bottomStart() }
            Box(contentAlignment = Alignment.CenterEnd) { bottomEnd() }
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val feedback = LocalHapticFeedback.current
    val tick = { feedback.performHapticFeedback(HapticFeedbackType.VirtualKey) }
    Column(Modifier.fillMaxWidth()) {
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { d -> PinKey(enabled, d.toString()) { tick(); onDigit(d) } }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            PinKey(enabled, "0") { tick(); onDigit('0') }
            PinKey(enabled, null) { tick(); onBackspace() }
        }
    }
}

/** A digit, or the delete key when [digit] is null. Disabled keys are drawn at 40% and ignore taps. */
@Composable
private fun RowScope.PinKey(enabled: Boolean, digit: String?, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Dimens.PinKeyHeight)
            .clip(Shapes.Control)
            .background(if (pressed) c.rule else Color.Transparent)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = digit ?: "Delete last digit" },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) c.ink else c.ink.copy(alpha = 0.4f)
        if (digit != null) Text(digit, style = ChittaTheme.type.amountLarge, color = tint)
        else Icon(ChittaIcons.Backspace, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.IconRowLeading))
    }
}

/** "Forgot PIN?" as drawn: ink words with a heavier rule-coloured line under them. 48 dp tall to tap. */
@Composable
private fun PinLink(text: String, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Box(Modifier.heightIn(min = Dimens.TouchTarget).pressClickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            style = ChittaTheme.type.rowMeta14,
            color = c.ink,
            modifier = Modifier.drawBehind {
                val y = size.height + Dimens.PinLinkRule.toPx()
                drawLine(c.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = Dimens.PinLinkRule.toPx())
            },
        )
    }
}
