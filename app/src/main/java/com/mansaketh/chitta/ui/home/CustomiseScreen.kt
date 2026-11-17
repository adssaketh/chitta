package com.mansaketh.chitta.ui.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.kit.ChittaSwitch
import com.mansaketh.chitta.core.domain.HomeLayout
import com.mansaketh.chitta.data.UiPrefs
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.core.design.icons.KitIcons
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.HomeSection
import com.mansaketh.chitta.core.domain.MoreOrder
import com.mansaketh.chitta.core.domain.TabKey
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.plan.MultiPickSheet
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.layout.size

/**
 * Customise Home and tabs (master section 3). Everything here is a device setting kept in [UiPrefs]: which Home sections show and in
 * what order, the four bottom tabs, the More shortcuts, and which accounts count as spending money for the cash check.
 */
@Composable
fun CustomiseScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val prefs = vm.prefs
    val layout by prefs.homeLayout.collectAsState()
    val tabs by prefs.tabLayout.collectAsState()
    val more by prefs.moreOrder.collectAsState()
    val cash by prefs.cashAccounts.collectAsState()
    val preferCash by prefs.preferCash.collectAsState()
    val firstDay by prefs.firstDay.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    val c = ChittaTheme.colors
    val liveAccounts = ui.accounts.filter { !it.archived }

    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Customise", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            Section("Home sections", first = true)
            HomeSectionRows(layout, prefs, onAccounts = { sheet = "acc:$it" }, onRange = { sheet = "range:$it" })

            Section("Bottom tabs")
            tabs.slots.forEachIndexed { i, key -> ValueRow("Tab ${i + 1}", key.label, onClick = { sheet = "slot:$i" }) }
            ValueRow("Opens first", tabs.first.label, onClick = { sheet = "first" })

            Section("More shortcuts")
            more.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    MoveButtons(i > 0, i < more.lastIndex, { prefs.setMoreOrder(MoreOrder.move(more, item, -1)) }, { prefs.setMoreOrder(MoreOrder.move(more, item, 1)) })
                }
                HairlineRule()
            }

            Section("Cash check")
            SwitchRow("Always show the cash check", preferCash, { prefs.setPreferCash(it) }, sub = "Even when a budget is set")
            ValueRow("Accounts for spending", if (cash.isEmpty()) "None" else "${cash.size}", onClick = { sheet = "cash" })

            Section("Week")
            ValueRow("First day of the week", firstDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH), onClick = { sheet = "weekday" })
        }
    }

    val s = sheet
    when {
        s == null -> Unit
        s.startsWith("acc:") -> {
            val section = HomeSection.entries.first { it.key == s.removePrefix("acc:") }
            MultiPickSheet(section.title, liveAccounts.map { it.id to it.name }, layout.sections.first { it.section == section }.accountIds, "All accounts",
                onDone = { prefs.setHomeLayout(layout.withAccounts(section, it)); sheet = null }, onDismiss = { sheet = null })
        }
        s.startsWith("range:") -> {
            val section = HomeSection.entries.first { it.key == s.removePrefix("range:") }
            val now = layout.sections.first { it.section == section }.rangeDays ?: 30
            ChoiceSheet("Days to look back", listOf(7, 14, 30, 60, 90).map { Choice("$it days", it, selected = it == now) },
                onPick = { prefs.setHomeLayout(layout.withRange(section, it)); sheet = null }, onDismiss = { sheet = null })
        }
        s.startsWith("slot:") -> {
            val i = s.removePrefix("slot:").toInt()
            ChoiceSheet("Tab ${i + 1}", TabKey.entries.map { Choice(it.label, it, selected = it == tabs.slots[i]) },
                onPick = { prefs.setTabLayout(tabs.set(i, it)); sheet = null }, onDismiss = { sheet = null })
        }
        s == "first" -> ChoiceSheet("Opens first", tabs.slots.map { Choice(it.label, it, selected = it == tabs.first) },
            onPick = { prefs.setTabLayout(tabs.withFirst(it)); sheet = null }, onDismiss = { sheet = null })
        s == "cash" -> MultiPickSheet("Accounts for spending", liveAccounts.map { it.id to it.name }, cash, "None",
            onDone = { prefs.setCashAccounts(it); sheet = null }, onDismiss = { sheet = null })
        s == "weekday" -> ChoiceSheet("First day of the week", listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).map { Choice(it.getDisplayName(TextStyle.FULL, Locale.ENGLISH), it, selected = it == firstDay) },
            onPick = { prefs.setFirstDay(it); sheet = null }, onDismiss = { sheet = null })
    }
}

/**
 * Home sections as drawn on PDF p.37 (both Paper and Ink): switch at the start, title, then the two arrows at the end, one hairline between rows.
 * No leading icon. Sections that take accounts or a range show those two choices as a muted line under the title, only while the section is on.
 */
@Composable
private fun HomeSectionRows(layout: HomeLayout, prefs: UiPrefs, onAccounts: (String) -> Unit, onRange: (String) -> Unit) {
    val c = ChittaTheme.colors
    layout.sections.forEachIndexed { i, cfg ->
        val source = remember { MutableInteractionSource() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.RowMinOneLine)
                        .pressFeedback(source)
                        .toggleable(value = cfg.visible, interactionSource = source, indication = null, role = Role.Switch, onValueChange = { prefs.setHomeLayout(layout.toggle(cfg.section)) })
                        .padding(vertical = Dimens.RowPadV),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChittaSwitch(cfg.visible)
                    Spacer(Modifier.width(Dimens.TitleAmountGap))
                    Text(cfg.section.title, style = ChittaTheme.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (cfg.visible && (cfg.section.usesAccounts || cfg.section.usesRange)) {
                    Row(Modifier.padding(start = Dimens.SwitchWidth + Dimens.TitleAmountGap)) {
                        if (cfg.section.usesAccounts) SmallAction("Accounts") { onAccounts(cfg.section.key) }
                        if (cfg.section.usesRange) SmallAction("${cfg.rangeDays ?: 30} days") { onRange(cfg.section.key) }
                    }
                }
            }
            MoveButtons(i > 0, i < layout.sections.lastIndex, { prefs.setHomeLayout(layout.move(cfg.section, -1)) }, { prefs.setHomeLayout(layout.move(cfg.section, 1)) })
        }
        HairlineRule()
    }
}

/** The two arrow buttons that move a Home section or a More shortcut. An arrow with nowhere to go is dimmed and does nothing. */
@Composable
private fun MoveButtons(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Row {
        MoveButton(KitIcons.ArrowUp, "Move up", canUp, onUp)
        MoveButton(KitIcons.ArrowDown, "Move down", canDown, onDown)
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconAction(
        icon, description, if (enabled) onClick else ({}),
        Modifier.alpha(if (enabled) 1f else 0.4f).semantics { if (!enabled) disabled() },
    )
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, style = ChittaTheme.type.labelMedium, color = if (enabled) ChittaTheme.colors.ink else ChittaTheme.colors.inkMuted.copy(alpha = 0.4f),
        modifier = Modifier.heightIn(min = Dimens.TouchTarget).pressClickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Dimens.Gap2).wrapContentHeight(Alignment.CenterVertically),
    )
}

