package com.mansaketh.chitta.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.plan.MultiPickSheet
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
        ChittaTopBar("Customise", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 160.dp)) {
            SectionHeader("Home sections")
            Text("Budgets and Recent entries are on to start. Add what you want to see.", style = ChittaTheme.type.label, color = c.inkMuted)
            HairlineRule()
            layout.sections.forEachIndexed { i, cfg ->
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cfg.section.title + if (cfg.visible) "" else "  (off)", style = if (cfg.visible) ChittaTheme.type.bodyMedium else ChittaTheme.type.body,
                        color = if (cfg.visible) c.ink else c.inkMuted,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTarget).clickable(role = Role.Switch, onClickLabel = if (cfg.visible) "Turn off" else "Turn on") { prefs.setHomeLayout(layout.toggle(cfg.section)) }.wrapContentHeight(Alignment.CenterVertically),
                    )
                    if (cfg.visible) {
                        if (cfg.section.usesAccounts) SmallAction("Accounts") { sheet = "acc:" + cfg.section.key }
                        if (cfg.section.usesRange) SmallAction("${cfg.rangeDays ?: 30} days") { sheet = "range:" + cfg.section.key }
                    }
                    SmallAction("Up", enabled = i > 0) { prefs.setHomeLayout(layout.move(cfg.section, -1)) }
                    SmallAction("Down", enabled = i < layout.sections.lastIndex) { prefs.setHomeLayout(layout.move(cfg.section, 1)) }
                }
                HairlineRule()
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionHeader("Bottom tabs")
            Text("Four tabs you can swap. More is always the fifth. Press and hold the tab bar anywhere to come back here.", style = ChittaTheme.type.label, color = c.inkMuted)
            tabs.slots.forEachIndexed { i, key -> ValueRow("Tab ${i + 1}", key.label, onClick = { sheet = "slot:$i" }) }
            ValueRow("Opens first", tabs.first.label, onClick = { sheet = "first" })

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionHeader("More shortcuts")
            more.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    SmallAction("Up", enabled = i > 0) { prefs.setMoreOrder(MoreOrder.move(more, item, -1)) }
                    SmallAction("Down", enabled = i < more.lastIndex) { prefs.setMoreOrder(MoreOrder.move(more, item, 1)) }
                }
                HairlineRule()
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionHeader("Cash check")
            Text("Pick the accounts that hold money you spend from. Savings and investment accounts stay out.", style = ChittaTheme.type.label, color = c.inkMuted)
            ValueRow("Accounts for spending", if (cash.isEmpty()) "None picked" else "${cash.size} picked", onClick = { sheet = "cash" })
            ValueRow("Always show the cash check", if (preferCash) "On" else "Off", onClick = { prefs.setPreferCash(!preferCash) })
            Text("Off means Home shows the cash check only when it is lower than Safe to spend.", style = ChittaTheme.type.label, color = c.inkMuted)

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionHeader("Week")
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

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, style = ChittaTheme.type.labelMedium, color = if (enabled) ChittaTheme.colors.ink else ChittaTheme.colors.inkMuted.copy(alpha = 0.4f),
        modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Dimens.Gap2).wrapContentHeight(Alignment.CenterVertically),
    )
}
