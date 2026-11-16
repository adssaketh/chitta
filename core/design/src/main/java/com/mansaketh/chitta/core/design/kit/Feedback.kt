package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.mansaketh.chitta.core.design.components.IconAction
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes
import com.mansaketh.chitta.core.money.Segments

/*
 * Feedback and states (v8 kit, PDF page 5 and page 8): gauge, message bar, empty states, note.
 */

/**
 * A 10- or 20-segment gauge (v8 §4.16): 6 dp tall, 3 dp between 10 segments and 2 dp between 20, ink fill, and sindoor only when over the limit.
 * Empty segments are the rule colour. [description] says what it measures ("Food budget"); TalkBack reads the filled count.
 */
@Composable
fun GaugeBar(
    gauge: Segments.Gauge,
    description: String,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    val fill = if (gauge.over) c.sindoor else c.ink
    val spoken = "$description: ${gauge.filled} of ${gauge.segments} segments" + if (gauge.over) ", over the limit" else ""
    val gapDp = if (gauge.segments <= 10) Dimens.GaugeGap10 else Dimens.GaugeGap20
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.GaugeHeight)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val gap = gapDp.toPx()
        val segW = (size.width - gap * (gauge.segments - 1)) / gauge.segments
        repeat(gauge.segments) { i ->
            drawRect(
                color = if (i < gauge.filled) fill else c.rule,
                topLeft = Offset(i * (segW + gap), 0f),
                size = Size(segW, size.height),
            )
        }
    }
}

/** What a [MessageBar] is: a save (text and Undo, gone after [MESSAGE_BAR_SAVE_MILLIS]) or an error (stays until it is closed). */
enum class MessageKind { Save, Error }

/** How long a saved message with Undo stays up (D282). Errors have no timeout. */
const val MESSAGE_BAR_SAVE_MILLIS: Long = 10_000L

/**
 * The one message bar (v8 §4.17, D282), in inverse colours (ink with page-coloured text in Paper, the reverse in Ink), 12 dp corners.
 *  - [MessageKind.Save]: the text and an underlined action ("Undo"). The host removes it after [MESSAGE_BAR_SAVE_MILLIS].
 *  - [MessageKind.Error]: a warning icon, the text, an optional action ("Retry") and a close button. It stays until [onClose] is called.
 * It is a polite live region, so TalkBack announces it. The bar does not place itself; use [MessageBarSlot] (16 dp from the sides, 88 dp from the bottom).
 */
@Composable
fun MessageBar(
    message: String,
    modifier: Modifier = Modifier,
    kind: MessageKind = MessageKind.Save,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val c = ChittaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MessageBarMinHeight)
            .clip(Shapes.Control)
            .background(c.ink)
            .padding(start = Dimens.Space4)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind == MessageKind.Error) {
            Icon(ChittaIcons.Warning, contentDescription = null, tint = c.warningOnInverse, modifier = Modifier.size(Dimens.GlyphIcon))
            Spacer(Modifier.width(Dimens.Space3))
        }
        Text(
            message.rupeeAware(),
            style = ChittaTheme.type.body,
            color = c.background,
            modifier = Modifier.weight(1f).padding(vertical = Dimens.Space3),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = ChittaTheme.type.bodyMedium,
                color = c.background,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .heightIn(min = Dimens.TouchTarget)
                    .pressClickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Dimens.Space4, vertical = Dimens.Space3),
            )
        }
        if (kind == MessageKind.Error && onClose != null) {
            IconAction(ChittaIcons.Close, "Close message", onClose, tint = c.background)
        } else if (actionLabel == null || onAction == null) {
            Spacer(Modifier.width(Dimens.Space4))
        }
    }
}

/** Puts a [MessageBar] where it belongs on a screen: bottom centre, [Dimens.MessageBarInset] from each side, [Dimens.MessageBarBottom] above the bottom edge (over the nav). */
@Composable
fun BoxScope.MessageBarSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = Dimens.MessageBarInset)
            .padding(bottom = Dimens.MessageBarBottom),
    ) { content() }
}

/**
 * First use and nothing-to-show (v8 §4.18): a stroke icon (40, muted), one fact in Fraunces 22, a muted 15 sp line saying what will appear, and one action.
 * No illustration of coins or piggy banks (master section 4). The action is a Secondary button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = Dimens.SectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconEmpty))
        Spacer(Modifier.height(Dimens.Space4))
        Text(title.rupeeAware(), style = ChittaTheme.type.sheetTitle, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dimens.Space2))
        Text(body.rupeeAware(), style = ChittaTheme.type.emptyBody, color = c.inkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dimens.Space6))
        ChittaButton(actionLabel, onAction, kind = ButtonKind.Secondary)
    }
}

/** For sheets and "Nothing matches": one muted sentence and one underlined link after it ("Nothing matches “zomato”." + "Clear search"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyLine(
    text: String,
    linkLabel: String,
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = Dimens.Space2),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.rupeeAware(), style = ChittaTheme.type.body, color = c.inkMuted)
        Text(
            linkLabel,
            style = ChittaTheme.type.body.copy(textDecoration = TextDecoration.Underline),
            color = c.ink,
            modifier = Modifier
                .heightIn(min = Dimens.TouchTarget)
                .pressClickable(role = Role.Button, onClick = onLink)
                .padding(horizontal = Dimens.Space1)
                .wrapContentHeightCenter(),
        )
    }
}

private fun Modifier.wrapContentHeightCenter(): Modifier =
    this.then(Modifier.wrapContentHeight(Alignment.CenterVertically))

/** A quiet boxed explanation (v8 §4.21): hairline outline, surface fill, 12 dp corners, 14 sp muted text, no shadow. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Text(
        text.rupeeAware(),
        style = ChittaTheme.type.rowMeta14,
        color = c.inkMuted,
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.Control)
            .background(c.surface)
            .border(Dimens.Hairline, c.rule, Shapes.Control)
            .padding(Dimens.NoteInner),
    )
}
