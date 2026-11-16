package com.mansaketh.chitta.ui.household

import androidx.compose.foundation.clickable
import com.mansaketh.chitta.core.design.kit.MonthStepper
import com.mansaketh.chitta.core.design.kit.GaugeBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.AmountText
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.GaugeBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.MemberSpend
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Who spent how much (Phase 9b step 4). Matches the design PDF Light p.34 (screen 29/31): the household total, then each member with a
 * 20-segment bar and their share. The PDF's "Settle up" link is left out because settle-up was dropped (DECISIONS D220).
 * Not in the PDF, added by the owner's request for a month switch: the two arrows beside the month name.
 */
@Composable
fun WhoSpentScreen(vm: HouseholdViewModel, onBack: () -> Unit) {
    val c = ChittaTheme.colors
    val result by vm.spent.collectAsState()
    val month by vm.month.collectAsState()
    val ui by vm.ui.collectAsState()
    // The member list comes from the server; read it when the page opens.
    LaunchedEffect(Unit) { vm.opened() }

    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Who spent how much", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            MonthStepper(monthLabel(month), vm::earlierMonth, vm::laterMonth, canLater = month < YearMonth.now())
            val r = result
            when {
                !ui.local.inHousehold -> Note("You are not in a household.")
                r == null -> Note(if (ui.connected) "Reading the household." else "Open the Household page and continue with Google to read the members.")
                else -> {
                    Spacer(Modifier.height(Dimens.Gap4))
                    Text("Household total", style = ChittaTheme.type.label, color = c.inkMuted)
                    AmountText(r.total, style = ChittaTheme.type.amountHero)
                    Spacer(Modifier.height(Dimens.SectionGap))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Spending by member", style = ChittaTheme.type.title, color = c.ink)
                        Text("${r.members.size}", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    Spacer(Modifier.height(Dimens.HeadingGap))
                    HairlineRule()
                    r.members.forEach { MemberSpendRow(it, r.total) }
                    Spacer(Modifier.height(Dimens.Gap4))
                    Text(
                        "Private and personal entries aren't counted, and neither are loans or balance corrections.",
                        style = ChittaTheme.type.label, color = c.inkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4))
}

/** "November · shared entries only": the label between the month arrows. */
private fun monthLabel(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + (if (month.year != YearMonth.now().year) " ${month.year}" else "") + " · shared entries only"

@Composable
private fun MemberSpendRow(m: MemberSpend, total: Paise) {
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Dimens.RowPadV)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(m.name, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f, fill = false))
            AmountText(m.spent, style = ChittaTheme.type.amountRow)
        }
        GaugeBar(Segments.gauge(m.spent, total, segments = 20), "${m.name}'s share of shared spending")
        Text(
            if (m.percent != null) "${m.percent}% of shared spending" else "Nothing shared this month",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2, bottom = Dimens.RowPadV),
        )
        HairlineRule()
    }
}
