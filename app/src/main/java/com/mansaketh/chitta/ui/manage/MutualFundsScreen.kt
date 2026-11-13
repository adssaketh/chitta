package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.MutualFunds
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.*
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.plan.pickDate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface FundSheet {
    data object None : FundSheet
    data object Import : FundSheet
    data class Detail(val f: MutualFund) : FundSheet
    data class Edit(val f: MutualFund?) : FundSheet
}

private val priceDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "12 Nov 2026" from a yyyy-MM-dd price date, or the text itself if it does not parse. */
private fun priceDateText(text: String): String = runCatching { LocalDate.parse(text).format(priceDateFmt) }.getOrDefault(text)

private fun unitsText(f: MutualFund): String = MutualFunds.units(f)?.stripTrailingZeros()?.toPlainString() ?: f.units

private fun sourceName(source: String): String = when (source) {
    MutualFund.SOURCE_CONSOLE -> "Zerodha Console"
    MutualFund.SOURCE_CAS -> "CAMS or KFintech statement"
    else -> "Typed by hand"
}

private fun fundLine(f: MutualFund, nowMs: Long, zone: ZoneId): String {
    val days = MutualFunds.daysSinceUnits(f, nowMs, zone)
    val age = if (MutualFunds.isStale(f, nowMs, zone)) "units $days days old" else "units as of ${dateText(f.unitsAsOfMs, zone)}"
    return "${unitsText(f)} units · ${sourceName(f.source)} · $age"
}

/**
 * Mutual funds (PDF p.26): total value and gain, one row per fund with where its units came from. Value is units times the latest AMFI
 * price with its date. A fund with old units is stamped STALE (at most two stamps on the screen, master section 3; further stale funds say
 * their age in words). A fund with no average price says so instead of showing a guessed gain. The price fetch is a later step, so until
 * it exists funds show "No price yet".
 */
@Composable
fun MutualFundsScreen(vm: ManageViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<FundSheet>(FundSheet.None) }
    val totals = MutualFunds.totals(ui.funds)
    val stampedIds = ui.funds.filter { MutualFunds.isStale(it, ui.nowMs, ui.zone) }.take(2).map { it.id }.toSet()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar("Mutual funds", onBack = onBack, actions = { IconAction(ChittaIcons.Plus, "Add or import holdings", onClick = { sheet = FundSheet.Import }) })
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
                if (ui.loading) item { LoadingPlaceholder() }
                else if (ui.funds.isEmpty()) item {
                    EmptyState("No mutual funds yet. Add one by hand, or import holdings.", "Add holdings", { sheet = FundSheet.Import })
                } else {
                    item {
                        Text("Value", style = ChittaTheme.type.label, color = c.inkMuted)
                        if (totals.priced > 0) AmountText(totals.value, style = ChittaTheme.type.amountHero, alwaysShowPaise = true)
                        else Text("No price yet", style = ChittaTheme.type.title, color = c.inkMuted)
                        Text(
                            totals.priceDate?.let { "Price as of ${priceDateText(it)}" } ?: "Prices come from AMFI's daily file. That step is not built yet.",
                            style = ChittaTheme.type.label, color = c.inkMuted,
                        )
                        if (totals.priced in 1 until totals.count) Text("${totals.unpriced} of ${totals.count} funds have no price and are left out.", style = ChittaTheme.type.label, color = c.inkMuted)
                        Spacer(Modifier.height(Dimens.Gap3))
                        val gain = totals.gain
                        if (gain != null) {
                            ValueRow("Gain", (if (gain.value > 0) "+" else "") + maskedRupees(gain))
                            Text(
                                "Covers ${totals.gainCovers} of ${totals.priced} priced ${if (totals.priced == 1) "fund" else "funds"}. A fund with no average price is left out.",
                                style = ChittaTheme.type.label, color = c.inkMuted,
                            )
                        } else if (totals.priced > 0) Text("No gain to show: no fund with a price has an average price.", style = ChittaTheme.type.label, color = c.inkMuted)
                        Spacer(Modifier.height(Dimens.Gap3))
                        HairlineRule()
                    }
                    items(ui.funds, key = { it.id }) { f -> FundRow(f, ui.nowMs, ui.zone, f.id in stampedIds) { sheet = FundSheet.Detail(f) } }
                }
            }
        }

        when (val s = sheet) {
            FundSheet.Import -> ImportHoldingsSheet(onByHand = { sheet = FundSheet.Edit(null) }, onClose = { sheet = FundSheet.None })
            is FundSheet.Detail -> {
                val cur = ui.funds.firstOrNull { it.id == s.f.id } ?: s.f
                FundDetailSheet(cur, ui.nowMs, ui.zone, onEdit = { sheet = FundSheet.Edit(cur) }, onDelete = { vm.deleteFund(cur.id); sheet = FundSheet.None }, onClose = { sheet = FundSheet.None })
            }
            is FundSheet.Edit -> FundEditSheet(s.f, ui.nowMs, ui.zone, { vm.saveFund(it); sheet = FundSheet.None }, { sheet = FundSheet.None })
            FundSheet.None -> Unit
        }
    }
}

@Composable
private fun FundRow(f: MutualFund, nowMs: Long, zone: ZoneId, stamp: Boolean, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val value = MutualFunds.value(f)
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.name, style = ChittaTheme.type.title, color = c.ink)
            Text(fundLine(f, nowMs, zone), style = ChittaTheme.type.label, color = c.inkMuted)
            if (f.investedPaise == null) Text("No average price", style = ChittaTheme.type.label, color = c.inkMuted)
            if (stamp) Stamp("Stale")
        }
        if (value != null) AmountText(value, alwaysShowPaise = true) else Text("No price yet", style = ChittaTheme.type.label, color = c.inkMuted)
    }
    HairlineRule()
}

/** PDF p.27. Three sources; only "By hand" works until the two file readers have a sample file to be built against. */
@Composable
private fun ImportHoldingsSheet(onByHand: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    BottomOverlay(onClose) {
        Text("Import holdings", style = ChittaTheme.type.title, color = c.ink)
        Text(
            "Importing one source never changes the others. A file and its password are never saved.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Zerodha Console file", "Not built yet")
        ValueRow("CAMS or KFintech statement", "Not built yet")
        ValueRow("By hand", "Add a fund", onClick = onByHand)
    }
}

@Composable
private fun FundDetailSheet(f: MutualFund, nowMs: Long, zone: ZoneId, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    val value = MutualFunds.value(f)
    val gain = MutualFunds.gain(f)
    BottomOverlay(onClose) {
        Text(f.name, style = ChittaTheme.type.title, color = c.ink)
        if (MutualFunds.isStale(f, nowMs, zone)) Stamp("Stale")
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Value", value?.let { maskedRupees(it) } ?: "No price yet")
        ValueRow("Units", unitsText(f))
        ValueRow("Price per unit", MutualFunds.nav(f)?.let { "₹${it.stripTrailingZeros().toPlainString()}" + (f.navDate?.let { d -> " · ${priceDateText(d)}" } ?: "") } ?: "No price yet")
        ValueRow("Put in", f.investedPaise?.let { maskedRupees(Paise(it)) } ?: "No average price")
        ValueRow("Gain", gain?.let { (if (it.value > 0) "+" else "") + maskedRupees(it) } ?: if (value == null) "Needs a price" else "Needs an average price")
        ValueRow("Units from", sourceName(f.source))
        ValueRow("Units as of", dateText(f.unitsAsOfMs, zone))
        if (f.isin.isNotBlank()) ValueRow("ISIN", f.isin)
        if (f.note.isNotBlank()) ValueRow("Note", f.note)
        Text("Value is units times the latest price. Units change only when you type or import them.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Edit", onEdit)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Delete", { confirmDelete = true }, style = ButtonStyle.Quiet)
    }
    if (confirmDelete) ConfirmSheet(
        "Delete this fund?", listOf("It leaves the list. No account balance changes. You can undo for 10 seconds."), "Delete",
        { confirmDelete = false; onDelete() }, { confirmDelete = false },
    )
}

private enum class FundField { Name, Isin, Units, Average, Note }

/** Add or edit a fund by hand. Nothing is saved until Save, and the check says in plain words what is wrong. */
@Composable
private fun FundEditSheet(existing: MutualFund?, nowMs: Long, zone: ZoneId, onSave: (MutualFund) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val today = Periods.startOfDay(Periods.date(nowMs, zone), zone)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var isin by remember { mutableStateOf(existing?.isin ?: "") }
    var units by remember { mutableStateOf(existing?.units ?: "") }
    // The average price is typed per unit; what was put in is kept in paise. Editing shows it back as the average.
    var average by remember {
        mutableStateOf(
            existing?.let { f ->
                val u = MutualFunds.units(f); val inv = f.investedPaise
                if (u != null && inv != null) BigDecimal(inv).movePointLeft(2).divide(u, 4, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString() else ""
            } ?: "",
        )
    }
    var asOf by remember { mutableStateOf(existing?.unitsAsOfMs ?: today) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var editing by remember { mutableStateOf<FundField?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    BottomOverlay(onClose) {
        Text(if (existing == null) "New mutual fund" else "Edit mutual fund", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Fund name", name.ifBlank { "not set" }, onClick = { editing = FundField.Name })
        ValueRow("ISIN", isin.ifBlank { "not set" }, onClick = { editing = FundField.Isin })
        ValueRow("Units", units.ifBlank { "not set" }, onClick = { editing = FundField.Units })
        ValueRow("Average price per unit", average.ifBlank { "none" }, onClick = { editing = FundField.Average })
        ValueRow("Units as of", dateText(asOf, zone), onClick = { pickDate(context, asOf, zone, { asOf = Periods.startOfDay(Periods.date(it, zone), zone) }) })
        ValueRow("Note", note.ifBlank { "none" }, onClick = { editing = FundField.Note })
        Text("The ISIN lets the price match later. It is on your statement, in 12 characters.", style = ChittaTheme.type.label, color = c.inkMuted)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", {
            val u = units.trim().toBigDecimalOrNull()
            val avg = if (average.isBlank()) null else average.trim().toBigDecimalOrNull()
            val code = isin.trim().uppercase()
            problem = when {
                name.isBlank() -> "Enter the fund's name."
                u == null || u.signum() <= 0 || u.scale() > 3 -> "Enter the units as a number above zero, up to 3 decimal places."
                average.isNotBlank() && (avg == null || avg.signum() <= 0) -> "Enter the average price as a number above zero, or leave it empty."
                code.isNotEmpty() && !MutualFunds.isIsinShape(code) -> "An ISIN has 12 characters, such as INF846K01DP8. Leave it empty if you do not have it."
                else -> null
            }
            if (problem == null) onSave(
                MutualFund(
                    existing?.id ?: "", name.trim(), code, existing?.schemeCode ?: "", u!!.stripTrailingZeros().toPlainString(),
                    avg?.let { MutualFunds.investedFrom(u, it) }, existing?.source ?: MutualFund.SOURCE_MANUAL, asOf, existing?.navText, existing?.navDate, note.trim(), null,
                ),
            )
        }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }

    when (editing) {
        FundField.Name -> TextSheet("Fund name", "Name as on your statement", name, { name = it; editing = null }, { editing = null })
        FundField.Isin -> TextSheet("ISIN", "12 characters (empty to clear)", isin, { isin = it; problem = null; editing = null }, { editing = null })
        FundField.Units -> TextSheet("Units", "Units held, for example 125.432", units, { units = it; problem = null; editing = null }, { editing = null }, numeric = true)
        FundField.Average -> TextSheet("Average price per unit", "Rupees per unit (empty if you do not know)", average, { average = it; problem = null; editing = null }, { editing = null }, numeric = true)
        FundField.Note -> TextSheet("Note", "Anything to remember (empty to clear)", note, { note = it; editing = null }, { editing = null }, singleLine = false)
        null -> Unit
    }
}
