package com.mansaketh.chitta.core.design.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.AddButton
import com.mansaketh.chitta.core.design.components.AmountEntryText
import com.mansaketh.chitta.core.design.components.AmountText
import com.mansaketh.chitta.core.design.components.AmountTone
import com.mansaketh.chitta.core.design.components.BudgetRing
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.CardAction
import com.mansaketh.chitta.core.design.components.ChittaBottomNav
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaField
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.EmptyState
import com.mansaketh.chitta.core.design.components.ErrorState
import com.mansaketh.chitta.core.design.components.GaugeBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.HideAmountsAction
import com.mansaketh.chitta.core.design.components.Keypad
import com.mansaketh.chitta.core.design.components.KeypadKey
import com.mansaketh.chitta.core.design.components.LedgerBar
import com.mansaketh.chitta.core.design.components.LedgerDay
import com.mansaketh.chitta.core.design.components.LedgerEntry
import com.mansaketh.chitta.core.design.components.LoadingPlaceholder
import com.mansaketh.chitta.core.design.components.MonthHeader
import com.mansaketh.chitta.core.design.components.MonthRule
import com.mansaketh.chitta.core.design.components.NavDestination
import com.mansaketh.chitta.core.design.components.RingLegend
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.components.SheetSurface
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.components.StampTone
import com.mansaketh.chitta.core.design.components.UndoBar
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.ThemeMode
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Palette
import com.mansaketh.chitta.core.money.BudgetFigures
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.RangeResult
import com.mansaketh.chitta.core.money.Segments

/**
 * Sample figures for the gallery only. They are the worked examples from the design PDF
 * (budget ₹60,000, spent ₹26,520 on day 12 of 30, bills ₹5,978) and every derived number below
 * comes from the tested functions in :core:money, never typed by hand.
 */
object GallerySample {
    val budget = Paise.rupees(60_000)
    val spent = Paise.rupees(26_520)
    val bills = Paise.rupees(5_978)
    const val DAYS_IN_PERIOD = 30
    const val DAYS_ELAPSED = 12
    val safe = BudgetFigures.safeToSpend(budget, spent, bills)
    val perDay = BudgetFigures.perDay(safe, DAYS_IN_PERIOD - DAYS_ELAPSED)
    val range = BudgetFigures.range(safe, spent, DAYS_ELAPSED)
    val ring = Segments.ring(budget, spent, bills)

    /** Money spent up to each of the 12 days; ends at exactly ₹26,520. */
    val cumulative = listOf(1_800L, 3_950, 5_200, 7_900, 9_100, 11_800, 13_400, 15_900, 18_300, 21_000, 23_400, 26_520)
        .map { Paise.rupees(it) }

    fun entries(c: List<Color>) = listOf(
        LedgerEntry("Vegetables", "Food", ChittaIcons.Receipt, c[6], Paise.rupees(340)),
        LedgerEntry("Auto to office", "Travel", ChittaIcons.Transfer, c[4], Paise.rupees(120)),
        LedgerEntry("Salary", "Income", ChittaIcons.Bank, c[5], Paise.rupees(85_000), AmountTone.Income),
    )
}

@Composable
fun GallerySection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dimens.Margin).padding(top = Dimens.SectionGap)) {
        SectionHeader(title)
        HairlineRule()
        Box(Modifier.height(Dimens.Gap4))
        content()
        Box(Modifier.height(Dimens.Gap4))
    }
}

@Composable
fun GalleryColors() = GallerySection("Colours") {
    val c = ChittaTheme.colors
    val tokens = listOf(
        "Background" to c.background, "Surface" to c.surface, "Ink" to c.ink, "Muted" to c.inkMuted,
        "Rule" to c.rule, "Mark" to c.mark, "Sindoor" to c.sindoor, "Warning text" to c.warningText, "Green" to c.green,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        tokens.forEach { (name, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).border(1.dp, c.rule).background(color))
                Box(Modifier.width(Dimens.Gap3))
                Text(name, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                Text(hex(color), style = ChittaTheme.type.mono, color = c.inkMuted)
            }
        }
        Box(Modifier.height(Dimens.Gap2))
        Text("Categories", style = ChittaTheme.type.labelMedium, color = c.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), verticalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
            c.category.forEachIndexed { i, color ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(color))
                    Text(" " + Palette.CATEGORY_NAMES[i], style = ChittaTheme.type.label, color = c.ink)
                }
            }
        }
    }
}

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

@Composable
fun GalleryType() = GallerySection("Type") {
    val c = ChittaTheme.colors
    val t = ChittaTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
        AmountText(GallerySample.safe, style = t.amountHero, roundToRupee = true)
        Text("Amount hero, Fraunces 48", style = t.label, color = c.inkMuted)
        Text("A calm account book", style = t.headline, color = c.ink)
        Text("Headline, Fraunces 28", style = t.label, color = c.inkMuted)
        Text("Section title, Hanken Grotesk Medium 18", style = t.title, color = c.ink)
        Text("Body text is Hanken Grotesk 16. ₹2,340 left for 9 days.".rupeeAware(), style = t.body, color = c.ink)
        Text("Label, sentence case, 13", style = t.label, color = c.inkMuted)
        Text("INR 1,24,500.00 · UPI/4471 · ledger data", style = t.mono, color = c.ink)
        Stamp("Over pace", tone = StampTone.Attention)
    }
}

@Composable
fun GalleryAmounts() = GallerySection("Amounts") {
    val c = ChittaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        Text("Digits sit in equal slots, so the column lines up on the right.", style = ChittaTheme.type.label, color = c.inkMuted)
        listOf(Paise.rupees(111), Paise.rupees(8_080), Paise.rupees(144_460), Paise.of(613, 34), Paise.rupees(483_539)).forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AmountText(it, style = ChittaTheme.type.amount) }
        }
        HairlineRule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AmountText(Paise.rupees(85_000), style = ChittaTheme.type.amount, tone = AmountTone.Income) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AmountText(Paise(-120_000), style = ChittaTheme.type.amount) }
    }
}

@Composable
fun GalleryLedger() = GallerySection("Ledger") {
    val c = ChittaTheme.colors
    MonthHeader("November", Paise.rupees(85_000), GallerySample.spent)
    LedgerDay(13, "Fri", GallerySample.entries(c.category), Paise.rupees(120 + 340))
    LedgerDay(
        12, "Thu",
        listOf(
            LedgerEntry("Electricity", "Bills", ChittaIcons.Receipt, c.category[0], Paise.rupees(1_840), stamp = "Overdue", stampTone = StampTone.Attention),
            LedgerEntry("Rent", "Home", ChittaIcons.Home, c.category[9], Paise.rupees(22_000), stamp = "Upcoming"),
        ),
        Paise.rupees(23_840),
    )
}

@Composable
fun GalleryControls() = GallerySection("Controls") {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
        ChittaButton("Save", onClick = {}, confirmHaptic = true)
        ChittaButton("Pay now", onClick = {}, style = ButtonStyle.Attention)
        ChittaButton("Skip this one", onClick = {}, style = ButtonStyle.Quiet)
        ChittaField(text, { text = it }, label = "Title", placeholder = "What was it for?")
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap3),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap3),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AddButton(onClick = {}, onLongClick = {})
            Stamp("Paid"); Stamp("Stale"); Stamp("Income", tone = StampTone.Income)
        }
        ChittaTopBar("Home", actions = { HideAmountsAction(hidden = false, onToggle = {}); CardAction(onClick = {}) })
    }
}

@Composable
fun GalleryCharts() = GallerySection("Charts") {
    val s = GallerySample
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6), verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(s.ring)
            RingLegend(s.spent, s.bills, s.safe, Modifier.weight(1f))
        }
        Text(
            "Range: " + when (val r = s.range) {
                is RangeResult.Days -> "${r.days} days"
                RangeResult.NotEnoughData -> "Not enough data yet"
                RangeResult.NoSpending -> "No spending yet"
            },
            style = ChittaTheme.type.bodyMedium, color = ChittaTheme.colors.ink,
        )
        MonthRule(GallerySample.DAYS_IN_PERIOD, s.cumulative, s.budget)
        GaugeBar(Segments.gauge(s.spent, s.budget, 10), "Food budget")
        GaugeBar(Segments.gauge(s.spent, s.budget, 20), "Food budget, 20 segments")
        GaugeBar(Segments.gauge(Paise.rupees(66_300), s.budget, 10), "Over the limit")
        LedgerBar("Food", Paise.rupees(9_840), 1f)
        LedgerBar("Travel", Paise.rupees(4_120), 0.42f)
        LedgerBar("Bills", Paise.rupees(1_840), 0.19f, accent = true)
    }
}

@Composable
fun GalleryKeypad() = GallerySection("Keypad") {
    var raw by remember { mutableStateOf("1200+350.5") }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
        AmountEntryText(raw)
        Keypad(onKey = {
            raw = when (it) {
                is KeypadKey.Digit -> raw + it.digit
                KeypadKey.Dot -> "$raw."
                is KeypadKey.Operator -> raw + it.symbol
                KeypadKey.Backspace -> raw.dropLast(1)
            }
        })
    }
}

@Composable
fun GalleryNav() = GallerySection("Navigation and sheets") {
    var dest by remember { mutableStateOf(NavDestination.Home) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
        ChittaBottomNav(dest, { dest = it })
        UndoBar("Saved ₹340 to Food", onUndo = {})
        SheetSurface {
            Text("Safe to spend, explained", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
            Box(Modifier.height(Dimens.Gap3))
            Text("₹60,000 − ₹26,520 − ₹5,978 = ₹27,502".rupeeAware(), style = ChittaTheme.type.body, color = ChittaTheme.colors.ink)
        }
    }
}

@Composable
fun GalleryStates() = GallerySection("States") {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
        EmptyState("No transactions this week.", "Add one", onAction = {})
        HairlineRule()
        ErrorState("Backup did not finish.", "Nothing was lost. Your entries are still on this phone.", onRetry = {})
        HairlineRule()
        LoadingPlaceholder()
    }
}

@Composable
fun GalleryIcons() = GallerySection("Icons") {
    val c = ChittaTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap4), verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
        ChittaIcons.all.forEach { (name, icon) ->
            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = name, tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
                Text(name, style = ChittaTheme.type.label, color = c.inkMuted, maxLines = 1)
            }
        }
    }
}

/** Every section, in order. Debug builds open this as the app's only screen. */
@Composable
fun ComponentGallery(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    hideAmounts: Boolean,
    onHideAmountsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ChittaTopBar("Component gallery", actions = { HideAmountsAction(hideAmounts) { onHideAmountsChange(!hideAmounts) } })
        Row(Modifier.padding(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
            ThemeMode.entries.forEach { m ->
                ChittaButton(m.name, { onModeChange(m) }, Modifier.weight(1f), style = if (m == mode) ButtonStyle.Primary else ButtonStyle.Quiet)
            }
        }
        GalleryColors(); GalleryType(); GalleryAmounts(); GalleryLedger(); GalleryControls()
        GalleryCharts(); GalleryKeypad(); GalleryNav(); GalleryStates(); GalleryIcons()
        Box(Modifier.height(Dimens.SectionGap))
    }
}
