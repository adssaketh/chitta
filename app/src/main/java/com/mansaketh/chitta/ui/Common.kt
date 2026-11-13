package com.mansaketh.chitta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** A dimmed backdrop with [content] sitting at the bottom as a sheet. Tapping the backdrop dismisses. */
@Composable
fun BottomOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(ChittaTheme.colors.scrim).clickable(role = Role.Button, onClickLabel = "Close", onClick = onDismiss))
        Column(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()) {
            SheetSurface(modifier, content = content)
        }
    }
}

data class Choice<T>(val label: String, val value: T, val detail: String? = null, val selected: Boolean = false)

/** A short list to pick one thing from, in a sheet. */
@Composable
fun <T> ChoiceSheet(title: String, choices: List<Choice<T>>, onPick: (T) -> Unit, onDismiss: () -> Unit, emptyText: String = "Nothing to pick.") {
    BottomOverlay(onDismiss) {
        Text(title, style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        if (choices.isEmpty()) {
            Text(emptyText, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4))
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(choices) { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Button) { onPick(c.value) }.padding(vertical = Dimens.Gap2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label, style = if (c.selected) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = ChittaTheme.colors.ink)
                            if (c.detail != null) Text(c.detail, style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
                        }
                        if (c.selected) Icon(ChittaIcons.Check, contentDescription = "Selected", tint = ChittaTheme.colors.ink, modifier = Modifier.size(Dimens.IconSize))
                    }
                    HairlineRule()
                }
            }
        }
    }
}

/** One text field in a sheet with Save. [numeric] switches to a number keyboard (for rates and amounts typed in full). */
@Composable
fun TextSheet(title: String, label: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit, numeric: Boolean = false, singleLine: Boolean = true, saveLabel: String = "Save", placeholder: String = "") {
    var text by remember { mutableStateOf(initial) }
    BottomOverlay(onDismiss) {
        Text(title, style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaField(
            value = text, onValueChange = { text = it }, label = label, singleLine = singleLine, placeholder = placeholder,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        )
        Spacer(Modifier.height(Dimens.Gap6))
        ChittaButton(saveLabel, { onSave(text) }, confirmHaptic = true)
    }
}

/** Plain confirm sheet with an explanation, used before anything destructive (master section 13). */
@Composable
fun ConfirmSheet(title: String, lines: List<String>, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, attention: Boolean = true) {
    BottomOverlay(onDismiss) {
        Text(title, style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        lines.forEach { Text(it.rupeeAware(), style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton(confirmLabel, onConfirm, style = if (attention) ButtonStyle.Attention else ButtonStyle.Primary, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Cancel", onDismiss, style = ButtonStyle.Quiet)
    }
}

/** A label on the left, its value and a caret on the right: the detail screen's editable rows. */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = Dimens.Gap2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(value, style = ChittaTheme.type.body, color = ChittaTheme.colors.ink)
            if (onClick != null) {
                Spacer(Modifier.width(Dimens.Gap2))
                Icon(ChittaIcons.CaretRight, contentDescription = null, tint = ChittaTheme.colors.inkMuted, modifier = Modifier.size(16.dp))
            }
        }
        HairlineRule()
    }
}

/** A small outlined chip for choices on Quick Add (never a filled pill for every label; only the selected one is filled). */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, tint: Color? = null, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Row(
        modifier.heightIn(min = Dimens.TouchTarget).clip(Shapes.Control).then(if (selected) Modifier.background(c.ink) else Modifier.border(1.dp, c.mark, RoundedCornerShape(12.dp)))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Dimens.Gap3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2),
    ) {
        if (tint != null) Box(Modifier.size(8.dp).background(tint))
        Text(text, style = ChittaTheme.type.label, color = if (selected) c.background else c.ink, maxLines = 1)
    }
}

/** Tab-like text switch used on Ledger and Quick Add: the active one is ink with a 2 dp rule under it. */
@Composable
fun TextTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Tab) { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, style = if (i == selected) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = if (i == selected) c.ink else c.inkMuted)
                Spacer(Modifier.height(Dimens.Gap1))
                Box(Modifier.height(2.dp).width(24.dp).background(if (i == selected) c.ink else Color.Transparent))
            }
        }
    }
}
