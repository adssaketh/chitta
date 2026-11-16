package com.mansaketh.chitta.ui

import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.core.design.kit.TickRow
import com.mansaketh.chitta.core.design.kit.MessageBar
import com.mansaketh.chitta.core.design.kit.MessageBarSlot
import com.mansaketh.chitta.core.design.kit.MessageKind
import com.mansaketh.chitta.core.design.kit.SwitchRow
import androidx.compose.foundation.background
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.ChittaColors
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.size

/** Category colours come from the earthy palette by a stable hash of the id (DECISIONS D38). */
fun ChittaColors.categoryTint(id: String): Color = category[Math.floorMod(id.hashCode(), category.size)]

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "Today", "Yesterday", "12 Nov", or "12 Nov 2025" for another year. */
fun friendlyDay(ms: Long, nowMs: Long, zone: ZoneId): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(dayFmt)
        else -> d.format(dayYearFmt)
    }
}

/** "12 Nov, 7:02 pm". */
fun dayAndTime(ms: Long, zone: ZoneId): String {
    val z = Instant.ofEpochMilli(ms).atZone(zone)
    return z.toLocalDate().format(dayFmt) + ", " + z.format(timeFmt).lowercase(Locale.ENGLISH)
}

fun LocalDate.weekdayShort(): String = dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)

fun categoryName(id: String, categories: List<Category>): String =
    if (id == "0") "Transfer" else categories.firstOrNull { it.id == id }?.name ?: "Unknown category"

fun accountName(id: String, accounts: List<Account>): String = accounts.firstOrNull { it.id == id }?.name ?: "Unknown account"

/** A share of the screen's height, for a list or form inside a sheet. Fixed dp heights cut off on short phones and big text. */
@Composable
fun sheetMax(fraction: Float): androidx.compose.ui.unit.Dp =
    (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * fraction).dp

/**
 * A dimmed backdrop with [content] sitting at the bottom as a sheet. Tapping the backdrop dismisses. The sheet rises on a spring with
 * no bounce and the backdrop fades in (master section 3, Motion); with animations off it appears at once.
 */
@Composable
fun BottomOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, handle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val motion = com.mansaketh.chitta.core.design.theme.rememberMotionEnabled()
    val rise = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (motion) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (motion) rise.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = rise.value.coerceIn(0f, 1f) }
                .background(ChittaTheme.colors.scrim).clickable(role = Role.Button, onClickLabel = "Close", onClick = onDismiss),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .graphicsLayer { translationY = (1f - rise.value.coerceIn(0f, 1f)) * size.height },
        ) {
            SheetSurface(modifier, handle = handle, content = content)
        }
    }
}

/**
 * One choice in a [ChoiceSheet]. [icon] makes it an action row (leading icon, no tick); [danger] writes it in warning text (Delete, never filled).
 * Without an [icon] it is a pick-one row and [selected] shows the ink tick.
 */
data class Choice<T>(
    val label: String,
    val value: T,
    val detail: String? = null,
    val selected: Boolean = false,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val danger: Boolean = false,
    /** Non-null makes it a [SwitchRow] (an on/off choice that used to flip its own title); tapping calls onPick. */
    val switch: Boolean? = null,
)

/** A result or problem at the top of a screen, as the one message bar (v8 §4.17): it stays until closed (D282). Put it in the screen's root Box. */
@Composable
fun BoxScope.NoticeBar(text: String, onClose: () -> Unit) {
    MessageBarSlot {
        MessageBar(text, kind = MessageKind.Error, onClose = onClose)
    }
}

/** A short list to pick one thing from, in a sheet: pick-one rows are [TickRow]s, action rows have a leading icon (v8 §4.8, Phase 11e). */
@Composable
fun <T> ChoiceSheet(title: String, choices: List<Choice<T>>, onPick: (T) -> Unit, onDismiss: () -> Unit, emptyText: String = "Nothing to pick.") {
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title)
        Spacer(Modifier.height(Dimens.Gap2))
        if (choices.isEmpty()) {
            Text(emptyText, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4))
        } else {
            LazyColumn(Modifier.heightIn(max = sheetMax(0.5f))) {
                items(choices) { c ->
                    if (c.switch != null) SwitchRow(c.label, c.switch, { onPick(c.value) }, sub = c.detail)
                    else if (c.icon != null) ActionChoiceRow(c) { onPick(c.value) }
                    else TickRow(c.label, c.selected, { onPick(c.value) }, sub = c.detail)
                }
            }
        }
    }
}

/** An action in a sheet: leading icon (22), the title, an optional muted line. A danger action is written in warning text. */
@Composable
private fun <T> ActionChoiceRow(choice: Choice<T>, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val tint = if (choice.danger) c.warningText else c.ink
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (choice.detail == null) Dimens.RowMinOneLine else Dimens.RowMinTwoLine)
                .pressClickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(choice.icon!!, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.IconRowLeading))
            Spacer(Modifier.width(Dimens.Space3))
            Column(Modifier.weight(1f)) {
                Text(choice.label.rupeeAware(), style = ChittaTheme.type.body, color = tint)
                if (choice.detail != null) Text(choice.detail.rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
            }
        }
        HairlineRule()
    }
}

/** One text field in a sheet with Save. [numeric] switches to a number keyboard (for rates and amounts typed in full). */
@Composable
fun TextSheet(title: String, label: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit, numeric: Boolean = false, singleLine: Boolean = true, saveLabel: String = "Save", placeholder: String = "") {
    var text by remember { mutableStateOf(initial) }
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaField(
            value = text, onValueChange = { text = it }, label = label, singleLine = singleLine, placeholder = placeholder,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        )
        Spacer(Modifier.height(Dimens.Gap6))
        ChittaButton(saveLabel, { onSave(text) }, confirmHaptic = true, modifier = Modifier.fillMaxWidth())
    }
}

/** Plain confirm sheet with an explanation, used before anything destructive (master section 13). */
@Composable
fun ConfirmSheet(title: String, lines: List<String>, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, attention: Boolean = true) {
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title)
        Spacer(Modifier.height(Dimens.Gap3))
        lines.forEach { Text(it.rupeeAware(), style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        // Cancel first, the red confirm second: only the confirm is filled (PDF p.22).
        com.mansaketh.chitta.core.design.kit.ButtonPair(
            first = { m -> ChittaButton("Cancel", onDismiss, kind = ButtonKind.Secondary, modifier = m) },
            second = { m -> ChittaButton(confirmLabel, onConfirm, confirmHaptic = true, modifier = m) },
        )
    }
}

/**
 * Tab-like text switch used on Ledger, Insights and Quick Add: the active one is ink with a 2 dp rule under it. [icons], when given,
 * puts a 16 dp icon before each label (one per label). Tabs with icons are wider, so those screens let the strip scroll sideways.
 */
@Composable
fun TextTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, icons: List<androidx.compose.ui.graphics.vector.ImageVector>? = null) {
    val c = ChittaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (icons != null) Dimens.Gap4 else Dimens.Gap6)) {
        labels.forEachIndexed { i, label ->
            val tint = if (i == selected) c.ink else c.inkMuted
            Column(Modifier.heightIn(min = Dimens.TouchTarget).pressClickable(role = Role.Tab) { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icons?.getOrNull(i)?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.IconSmall)); Spacer(Modifier.width(Dimens.Gap1)) }
                    Text(label, style = if (i == selected) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = tint)
                }
                Spacer(Modifier.height(Dimens.Gap1))
                Box(Modifier.height(Dimens.MarkerHeight).width(Dimens.MarkerWidth).background(if (i == selected) c.ink else Color.Transparent))
            }
        }
    }
}
