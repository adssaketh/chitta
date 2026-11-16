package com.mansaketh.chitta.core.design.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.core.design.components.AmountTone
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.IconAction
import com.mansaketh.chitta.core.design.components.SheetSurface
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.components.StampTone
import com.mansaketh.chitta.core.design.components.TagChip
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.kit.BadgedIconAction
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.CategoryChip
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.CheckRow
import com.mansaketh.chitta.core.design.kit.CountBadge
import com.mansaketh.chitta.core.design.kit.DateSheet
import com.mansaketh.chitta.core.design.kit.EmptyLine
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.EntryRow
import com.mansaketh.chitta.core.design.kit.ExpandGroup
import com.mansaketh.chitta.core.design.kit.FilterChip
import com.mansaketh.chitta.core.design.kit.GaugeBar
import com.mansaketh.chitta.core.design.kit.MessageBar
import com.mansaketh.chitta.core.design.kit.MessageKind
import com.mansaketh.chitta.core.design.kit.MonthStepper
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.PickerChip
import com.mansaketh.chitta.core.design.kit.Note
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.Segmented
import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.TickRow
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.TopBarMain
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * The v8 kit in the gallery (Phase 11a). One section per PDF page of the kit: bars (page 3), rows (page 3), entries and controls (pages 4 and 5),
 * feedback (page 5), and the sheets (page 16). Sample figures are the design PDF's own. Nothing here is used by a screen yet.
 */

/** A kit section: the heading and rule keep the screen margin; [bleed] lets the content (the top bars) run edge to edge like they do on a screen. */
@Composable
private fun KitSection(title: String, bleed: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Dimens.SectionGap)) {
        Box(Modifier.padding(horizontal = Dimens.Margin)) { Section(title) }
        Box(Modifier.padding(horizontal = Dimens.Margin)) { HairlineRule() }
        Box(Modifier.height(Dimens.Space4))
        Box(if (bleed) Modifier else Modifier.padding(horizontal = Dimens.Margin)) { content() }
        Box(Modifier.height(Dimens.Space4))
    }
}

@Composable
fun GalleryKitBars() = KitSection("Kit: top bars and badge", bleed = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
        TopBarMain("Home", ChittaIcons.Home) {
            BadgedIconAction(ChittaIcons.Bell, "Bank messages", 4, {})
            IconAction(ChittaIcons.Gear, "Settings", {})
        }
        TopBarDetail("Entry", onBack = {}) {
            IconAction(ChittaIcons.Copy, "Duplicate", {})
            IconAction(ChittaIcons.Share, "Share", {})
            IconAction(ChittaIcons.Pencil, "Edit", {})
        }
        TopBarDetail("Settings", onBack = {}, subtitle = "Look and feel, security, backup")
        Row(Modifier.padding(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
            CountBadge(1)
            CountBadge(4)
            CountBadge(99)
            CountBadge(120)
        }
    }
}

@Composable
fun GalleryKitRows() = KitSection("Kit: rows") {
    var appLock by remember { mutableStateOf(true) }
    var hideRecents by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(0) }
    var hdfc by remember { mutableStateOf(true) }
    var icici by remember { mutableStateOf(false) }
    val c = ChittaTheme.colors
    Column {
        Section("Coming up", first = true, note = "See all", onNote = {}) {
            EntryRow("ICICI card bill", "Card bill", ChittaIcons.CatCreditCard, c.category[0], Paise.rupees(12_480), marginDay = 18, weekday = "Wed")
            EntryRow("Netflix", "Subscriptions", ChittaIcons.CatTelevision, c.category[7], Paise.rupees(649), marginDay = 21, weekday = "Sat")
        }
        Section("Entries") {
            EntryRow("Ratnadeep", "Groceries · Cash", ChittaIcons.CatBasket, c.category[6], Paise.rupees(1_359))
            EntryRow("Salary", "Salary · HDFC savings", ChittaIcons.CatBriefcase, c.category[5], Paise.rupees(85_000), tone = AmountTone.Income, tag = "Check")
            EntryRow("Rent for the flat in Madhapur, March to May", "Housing · ICICI card", ChittaIcons.CatHouse, c.category[1], Paise.rupees(24_000), stamp = "Upcoming", marginDay = 1, weekday = "Sun")
        }
        Section("Open something") {
            NavRow("Accounts", {}, icon = ChittaIcons.Wallet, value = "6")
            NavRow("Backup and restore", {}, icon = ChittaIcons.CatCloud, sub = "Last backup today, 6:40 pm")
            ValueRow("Theme", "Follow the phone", onClick = {})
            ValueRow("Account currency", "INR")
        }
        Section("On or off") {
            SwitchRow("App lock", appLock, { appLock = it }, sub = "Fingerprint or PIN on open")
            SwitchRow("Hide in recent apps", hideRecents, { hideRecents = it })
            SwitchRow("Cloud sync", checked = true, onCheckedChange = {}, enabled = false)
        }
        Section("Pick one") {
            TickRow("Paper", theme == 0, { theme = 0 })
            TickRow("Ink", theme == 1, { theme = 1 })
            TickRow("Follow the phone", theme == 2, { theme = 2 }, sub = "Changes with the phone's dark mode")
        }
        Section("Pick many") {
            CheckRow("HDFC savings", hdfc, { hdfc = it }, sub = "Ready", amount = Paise.rupees(5_000))
            CheckRow("ICICI card", icici, { icici = it }, sub = "Needs a look: no merchant")
        }
        ExpandGroup("Look and feel", icon = ChittaIcons.CatPalette, initiallyExpanded = true) {
            ValueRow("Theme", "Follow the phone", onClick = {})
            SwitchRow("Animations", checked = true, onCheckedChange = {})
        }
        ExpandGroup("Security", icon = ChittaIcons.Lock) {
            SwitchRow("App lock", checked = true, onCheckedChange = {})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalleryKitControls() = KitSection("Kit: buttons, chips and steppers") {
    val c = ChittaTheme.colors
    var bars by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(0) }
    var cat by remember { mutableStateOf(0) }
    var filters by remember { mutableStateOf(listOf("Food & dining", "Last 30 days")) }
    var month by remember { mutableStateOf(11) }
    val months = listOf("September 2026", "October 2026", "November 2026")
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
        ChittaButton("Save", onClick = {}, modifier = Modifier.fillMaxWidth())
        ChittaButton("Save", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        ButtonPair(
            first = { m -> ChittaButton("Add a line", {}, m, kind = ButtonKind.Secondary) },
            second = { m -> ChittaButton("Equal parts", {}, m, kind = ButtonKind.Secondary) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChittaButton("Show 4 more", {}, kind = ButtonKind.Link)
            ChittaButton("Delete entry", {}, kind = ButtonKind.Danger)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
            val cats = listOf(
                Triple("Food & dining", ChittaIcons.CatForkKnife, c.category[1]),
                Triple("Groceries", ChittaIcons.CatBasket, c.category[6]),
                Triple("Transport", ChittaIcons.CatCar, c.category[4]),
            )
            cats.forEachIndexed { i, (name, icon, color) -> CategoryChip(name, cat == i, { cat = i }, icon = icon, color = color) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            filters.forEach { f -> FilterChip(f, { filters = filters - f }) }
            if (filters.isNotEmpty()) ChittaButton("Clear all", { filters = emptyList() }, kind = ButtonKind.Link)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
            PickerChip("1-12 Nov", {}, Modifier.weight(1f, fill = false))
            PickerChip("All accounts", {}, Modifier.weight(1f, fill = false))
        }
        Segmented(listOf("Bars", "Donut"), bars, { bars = it })
        Segmented(listOf("Expense", "Income", "Transfer"), kind, { kind = it })
        MonthStepper(
            label = months[month - 9],
            onEarlier = { month -= 1 },
            onLater = { month += 1 },
            canEarlier = month > 9,
            canLater = month < 11,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Stamp("Paid"); Stamp("Overdue", tone = StampTone.Attention); Stamp("Stale"); TagChip("Split"); TagChip("Check")
        }
        GaugeBar(Segments.gauge(Paise.rupees(26_520), Paise.rupees(60_000), 10), "Food budget")
        GaugeBar(Segments.gauge(Paise.rupees(26_520), Paise.rupees(60_000), 20), "Food budget, 20 segments")
        GaugeBar(Segments.gauge(Paise.rupees(66_300), Paise.rupees(60_000), 10), "Food budget, over the limit")
    }
}

@Composable
fun GalleryKitFeedback() = KitSection("Kit: message bar, empty states and note") {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
        MessageBar("Saved Swiggy, ₹486", actionLabel = "Undo", onAction = {})
        MessageBar("Backup failed. Nothing was lost.", kind = MessageKind.Error, actionLabel = "Retry", onAction = {}, onClose = {})
        MessageBar("Could not read the file.", kind = MessageKind.Error, onClose = {})
        EmptyState(ChittaIcons.Calendar, "No bills yet", "Bills you add show here with their due dates.", "Add a bill", onAction = {})
        EmptyLine("Nothing matches “zomato”.", "Clear search", onLink = {})
        Note("Cash check: ₹1,44,460 − ₹18,458 − ₹25,250 = ₹1,00,752. This is what is left in the accounts you spend from.")
    }
}

@Composable
fun GalleryKitSheets() = KitSection("Kit: sheets and the date sheet", bleed = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space6)) {
        SheetSurface(handle = false) {
            SheetHeader("Safe to spend, explained", onClose = {})
            Box(Modifier.height(Dimens.Space3))
            Text("₹60,000 − ₹26,520 − ₹5,978 = ₹27,502".rupeeAware(), style = ChittaTheme.type.rowAmountSheet, color = ChittaTheme.colors.ink)
            Box(Modifier.height(Dimens.Space3))
            Note("Cash check: ₹1,44,460 − ₹18,458 − ₹25,250 = ₹1,00,752.")
        }
        SheetSurface(handle = false) {
            DateSheet(
                initial = LocalDate.of(2026, 11, 10),
                today = LocalDate.of(2026, 11, 12),
                firstDayOfWeek = DayOfWeek.MONDAY,
                onUse = {},
                onClose = {},
            )
        }
    }
}
