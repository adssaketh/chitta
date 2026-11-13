package com.mansaketh.chitta.ui.manage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mansaketh.chitta.core.domain.Nps
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.intake.CasPlan
import com.mansaketh.chitta.core.intake.CasReading
import com.mansaketh.chitta.core.intake.CasSummary
import com.mansaketh.chitta.core.intake.IntakeFormatException
import com.mansaketh.chitta.core.intake.NpsPlan
import com.mansaketh.chitta.core.intake.NpsReading
import com.mansaketh.chitta.core.intake.NpsStatement
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.model.NpsAccount
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.intake.TextReaders
import com.mansaketh.chitta.ui.*
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.plan.pickDate
import kotlinx.coroutines.launch
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
    data class NpsAccountEdit(val a: NpsAccount?) : FundSheet
    data class NpsAccountDetail(val a: NpsAccount) : FundSheet
    /** An NPS scheme: [groupId] is its account. */
    data class SchemeEdit(val f: MutualFund?, val groupId: String) : FundSheet
}

/** Where a statement import is. The file and its password are read in memory and dropped: nothing about them is kept. */
private sealed interface CasUi {
    data object None : CasUi
    data object Reading : CasUi
    data class NeedPassword(val uri: Uri, val wrong: Boolean) : CasUi
    data class Preview(val plan: CasPlan, val reading: CasReading.Ready) : CasUi
    data class NpsPreview(val plan: NpsPlan, val reading: NpsReading.Ready) : CasUi
    data class Error(val message: String) : CasUi
}

private val priceDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "12 Nov 2026" from a yyyy-MM-dd price date, or the text itself if it does not parse. */
private fun priceDateText(text: String): String = runCatching { LocalDate.parse(text).format(priceDateFmt) }.getOrDefault(text)

private fun unitsText(f: MutualFund): String = MutualFunds.units(f)?.stripTrailingZeros()?.toPlainString() ?: f.units

private fun sourceName(source: String): String = when (source) {
    MutualFund.SOURCE_CONSOLE -> "Zerodha Console"
    MutualFund.SOURCE_CAS -> "Account statement"
    else -> "Typed by hand"
}

private fun fundLine(f: MutualFund, nowMs: Long, zone: ZoneId): String {
    val days = MutualFunds.daysSinceUnits(f, nowMs, zone)
    val age = if (MutualFunds.isStale(f, nowMs, zone)) "units $days days old" else "units as of ${dateText(f.unitsAsOfMs, zone)}"
    return "${unitsText(f)} units · ${sourceName(f.source)} · $age"
}

private fun schemeLine(f: MutualFund): String =
    "${unitsText(f)} units · " + (f.navDate?.let { "price as of ${priceDateText(it)}" } ?: "no price yet")

/**
 * Mutual funds (PDF p.26) with a separate NPS tab. The two are never added together: each tab has its own totals.
 *
 * Mutual funds tab: total value and gain, one row per fund with where its units came from. Value is units times the latest price with its
 * date. A fund with old units is stamped STALE (at most two stamps on the screen, master section 3; further stale funds say their age in
 * words). A fund with no average price says so instead of showing a guessed gain. Statement imports bring their own price and date; the
 * AMFI price fetch is a later step, so a fund typed by hand reads "No price yet" until then.
 *
 * NPS tab: one block per NPS account with value, net contributions and the gain for the whole account (the statement has no cost per
 * scheme), then one row per scheme (units and the statement's price with its date).
 */
@Composable
fun MutualFundsScreen(vm: ManageViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<FundSheet>(FundSheet.None) }
    var cas by remember { mutableStateOf<CasUi>(CasUi.None) }

    fun readStatement(uri: Uri, password: String?) {
        cas = CasUi.Reading
        scope.launch {
            cas = try {
                val text = TextReaders.readPdf(context, uri, password)
                // One file picker serves both: an NPS statement is told from a mutual fund summary by its text.
                if (NpsStatement.looksLike(text)) when (val r = NpsStatement.read(text)) {
                    is NpsReading.Ready -> CasUi.NpsPreview(vm.planNps(r), r)
                    is NpsReading.Refused -> CasUi.Error(r.reason)
                } else when (val r = CasSummary.read(text)) {
                    is CasReading.Ready -> CasUi.Preview(vm.planCas(r), r)
                    is CasReading.Refused -> CasUi.Error(r.reason)
                }
            } catch (e: TextReaders.PdfPasswordRequired) {
                CasUi.NeedPassword(uri, e.wrong)
            } catch (e: IntakeFormatException) {
                CasUi.Error((e.message ?: "That file could not be read.") + " Nothing was changed.")
            }
        }
    }
    val pickStatement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) readStatement(uri, null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar(
                "Mutual funds", onBack = onBack,
                actions = {
                    IconAction(
                        ChittaIcons.Plus, if (tab == 0) "Add or import holdings" else "Add an NPS account",
                        onClick = { sheet = FundSheet.Import },
                    )
                },
            )
            TextTabs(listOf("Mutual funds", "NPS"), tab, { tab = it }, Modifier.padding(horizontal = Dimens.Margin))
            if (tab == 0) FundsTab(ui, onOpen = { sheet = FundSheet.Detail(it) }, onAdd = { sheet = FundSheet.Import })
            else NpsTab(
                ui, onAddAccount = { sheet = FundSheet.Import }, onAccount = { sheet = FundSheet.NpsAccountDetail(it) },
                onAddScheme = { sheet = FundSheet.SchemeEdit(null, it.id) }, onScheme = { sheet = FundSheet.Detail(it) },
            )
        }

        when (val s = sheet) {
            FundSheet.Import -> ImportHoldingsSheet(
                nps = tab == 1,
                onStatement = { sheet = FundSheet.None; pickStatement.launch(arrayOf("application/pdf")) },
                onByHand = { sheet = if (tab == 1) FundSheet.NpsAccountEdit(null) else FundSheet.Edit(null) }, onClose = { sheet = FundSheet.None },
            )
            is FundSheet.Detail -> {
                val cur = (ui.funds + ui.npsSchemes).firstOrNull { it.id == s.f.id } ?: s.f
                FundDetailSheet(
                    cur, ui.nowMs, ui.zone,
                    onEdit = { sheet = if (cur.isNps) FundSheet.SchemeEdit(cur, cur.groupId) else FundSheet.Edit(cur) },
                    onDelete = { vm.deleteFund(cur.id); sheet = FundSheet.None }, onClose = { sheet = FundSheet.None },
                )
            }
            is FundSheet.Edit -> FundEditSheet(s.f, ui.nowMs, ui.zone, { vm.saveFund(it); sheet = FundSheet.None }, { sheet = FundSheet.None })
            is FundSheet.SchemeEdit -> SchemeEditSheet(s.f, s.groupId, ui.nowMs, ui.zone, { vm.saveFund(it); sheet = FundSheet.None }, { sheet = FundSheet.None })
            is FundSheet.NpsAccountEdit -> NpsAccountEditSheet(s.a, ui.nowMs, ui.zone, { vm.saveNps(it); sheet = FundSheet.None }, { sheet = FundSheet.None })
            is FundSheet.NpsAccountDetail -> {
                val cur = ui.nps.firstOrNull { it.id == s.a.id } ?: s.a
                NpsAccountDetailSheet(
                    cur, ui.npsSchemes.count { it.groupId == cur.id }, ui.zone,
                    onEdit = { sheet = FundSheet.NpsAccountEdit(cur) }, onDelete = { vm.deleteNps(cur.id); sheet = FundSheet.None }, onClose = { sheet = FundSheet.None },
                )
            }
            FundSheet.None -> Unit
        }

        when (val c = cas) {
            CasUi.None -> Unit
            CasUi.Reading -> BottomOverlay({}) { Text("Reading the statement…", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink) }
            is CasUi.NeedPassword -> TextSheet(
                "Statement password", if (c.wrong) "That password did not work. Try again" else "This PDF has a password. It is used once and never saved",
                "", { readStatement(c.uri, it) }, { cas = CasUi.None }, saveLabel = "Open",
            )
            is CasUi.Error -> BottomOverlay({ cas = CasUi.None }) {
                Text("Could not import", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
                Text(c.message, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaButton("Close", { cas = CasUi.None }, style = ButtonStyle.Quiet)
            }
            is CasUi.Preview -> CasPreviewSheet(c.plan, c.reading, onConfirm = { vm.applyCas(c.plan); cas = CasUi.None; tab = 0 }, onClose = { cas = CasUi.None })
            is CasUi.NpsPreview -> NpsPreviewSheet(c.plan, c.reading, onConfirm = { vm.applyNps(c.plan); cas = CasUi.None; tab = 1 }, onClose = { cas = CasUi.None })
        }
    }
}

@Composable
private fun FundsTab(ui: ManageUi, onOpen: (MutualFund) -> Unit, onAdd: () -> Unit) {
    val c = ChittaTheme.colors
    val totals = MutualFunds.totals(ui.funds)
    val stampedIds = ui.funds.filter { MutualFunds.isStale(it, ui.nowMs, ui.zone) }.take(2).map { it.id }.toSet()
    LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
        if (ui.loading) item { LoadingPlaceholder() }
        else if (ui.funds.isEmpty()) item {
            EmptyState("No mutual funds yet. Add one by hand, or import your statement.", "Add holdings", onAdd)
        } else {
            item {
                Spacer(Modifier.height(Dimens.Gap3))
                Text("Value", style = ChittaTheme.type.label, color = c.inkMuted)
                if (totals.priced > 0) AmountText(totals.value, style = ChittaTheme.type.amountHero, alwaysShowPaise = true)
                else Text("No price yet", style = ChittaTheme.type.title, color = c.inkMuted)
                Text(
                    totals.priceDate?.let { "Price as of ${priceDateText(it)}" } ?: "Import your statement for prices. The daily price fetch is not built yet.",
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
            items(ui.funds, key = { it.id }) { f -> FundRow(f, ui.nowMs, ui.zone, f.id in stampedIds) { onOpen(f) } }
        }
    }
}

@Composable
private fun NpsTab(ui: ManageUi, onAddAccount: () -> Unit, onAccount: (NpsAccount) -> Unit, onAddScheme: (NpsAccount) -> Unit, onScheme: (MutualFund) -> Unit) {
    val c = ChittaTheme.colors
    LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
        if (ui.loading) item { LoadingPlaceholder() }
        else if (ui.nps.isEmpty()) item {
            EmptyState("No NPS account yet. Add one from your NPS statement.", "Add NPS account", onAddAccount)
        } else ui.nps.forEach { acc ->
            val schemes = ui.npsSchemes.filter { it.groupId == acc.id }.sortedBy { it.name }
            val t = Nps.totals(acc, schemes)
            item(key = "nps-" + acc.id) {
                Spacer(Modifier.height(Dimens.Gap3))
                SectionHeader(acc.name, action = "Add scheme", onAction = { onAddScheme(acc) })
                Column(Modifier.fillMaxWidth().clickable { onAccount(acc) }) {
                    Text("Value", style = ChittaTheme.type.label, color = c.inkMuted)
                    if (t.priced > 0) AmountText(t.value, style = ChittaTheme.type.amountHero, alwaysShowPaise = true)
                    else Text("No price yet", style = ChittaTheme.type.title, color = c.inkMuted)
                    Text(
                        t.priceDate?.let { "Price as of ${priceDateText(it)}" } ?: "Add each scheme's units and price from the statement.",
                        style = ChittaTheme.type.label, color = c.inkMuted,
                    )
                    if (t.priced in 1 until t.schemes) Text("${t.schemes - t.priced} of ${t.schemes} schemes have no price and are left out.", style = ChittaTheme.type.label, color = c.inkMuted)
                    Spacer(Modifier.height(Dimens.Gap2))
                    ValueRow("Put in so far", t.netContributions?.let { maskedRupees(it) } ?: "not set")
                    val gain = t.gain
                    ValueRow("Gain", gain?.let { (if (it.value > 0) "+" else "") + maskedRupees(it) } ?: "Needs a price on every scheme and what you put in")
                    Text("The gain is for the whole account: the statement gives no cost for each scheme.", style = ChittaTheme.type.label, color = c.inkMuted)
                }
                Spacer(Modifier.height(Dimens.Gap2))
                HairlineRule()
            }
            if (schemes.isEmpty()) item(key = "nps-empty-" + acc.id) {
                Text("No schemes yet. Add one.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))
            }
            items(schemes, key = { it.id }) { f -> FundRow(f, ui.nowMs, ui.zone, stamp = false) { onScheme(f) } }
            item(key = "nps-gap-" + acc.id) { Spacer(Modifier.height(Dimens.SectionGap)) }
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
            Text(if (f.isNps) schemeLine(f) else fundLine(f, nowMs, zone), style = ChittaTheme.type.label, color = c.inkMuted)
            if (!f.isNps && f.investedPaise == null) Text("No average price", style = ChittaTheme.type.label, color = c.inkMuted)
            if (stamp) Stamp("Stale")
        }
        if (value != null) AmountText(value, alwaysShowPaise = true) else Text("No price yet", style = ChittaTheme.type.label, color = c.inkMuted)
    }
    HairlineRule()
}

/** PDF p.27. Statement and by hand work; the Zerodha Console file reader waits for a sample file. The NPS tab offers the NPS statement. */
@Composable
private fun ImportHoldingsSheet(nps: Boolean, onStatement: () -> Unit, onByHand: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    BottomOverlay(onClose) {
        Text(if (nps) "Add NPS" else "Import holdings", style = ChittaTheme.type.title, color = c.ink)
        Text(
            "Importing one source never changes the others. A file and its password are never saved. You see what will change before anything is saved.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        if (nps) {
            ValueRow("NPS statement (PDF from the CRA)", "Choose file", onClick = onStatement)
            ValueRow("By hand", "Add an account", onClick = onByHand)
        } else {
            ValueRow("CAMS, KFintech or MFCentral summary (PDF)", "Choose file", onClick = onStatement)
            ValueRow("Zerodha Console file", "Not built yet")
            ValueRow("By hand", "Add a fund", onClick = onByHand)
        }
    }
}

/** What the statement would change, shown before anything is saved (master section 13, imports). */
@Composable
private fun CasPreviewSheet(plan: CasPlan, reading: CasReading.Ready, onConfirm: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val incoming = plan.add + plan.update
    val total = incoming.fold(Paise.ZERO) { a, f -> a + (MutualFunds.value(f) ?: Paise.ZERO) }
    BottomOverlay(onClose) {
        Text("Import this statement?", style = ChittaTheme.type.title, color = c.ink)
        Text(
            (reading.asOf?.let { "As on ${it.format(priceDateFmt)}. " } ?: "") + "Nothing is saved until you tap Import. You can undo for 10 seconds.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("New funds", plan.add.size.toString())
        ValueRow("Funds updated", plan.update.size.toString())
        ValueRow("Funds removed", plan.remove.size.toString())
        ValueRow("Value in the file", maskedRupees(total))
        incoming.take(8).forEach { f -> Text("${f.name} · ${unitsText(f)} units", style = ChittaTheme.type.label, color = c.inkMuted) }
        if (incoming.size > 8) Text("and ${incoming.size - 8} more", style = ChittaTheme.type.label, color = c.inkMuted)
        if (plan.remove.isNotEmpty()) Text("Removed because the statement no longer holds them: " + plan.remove.joinToString { it.name }, style = ChittaTheme.type.label, color = c.inkMuted)
        if (plan.leftOutAsTypedByHand.isNotEmpty()) Text(
            "Left out, already typed by hand (never counted twice): " + plan.leftOutAsTypedByHand.joinToString { it.name } + ". Delete the hand-typed one to use the statement.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        if (reading.exitedRows > 0) Text("${reading.exitedRows} funds with no units left in the file are ignored.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Import", onConfirm, enabled = incoming.isNotEmpty() || plan.remove.isNotEmpty(), confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Cancel", onClose, style = ButtonStyle.Quiet)
    }
}

/** What an NPS statement would change: the account, each scheme's units and price, and what was put in. Nothing is saved before Import. */
@Composable
private fun NpsPreviewSheet(plan: NpsPlan, reading: NpsReading.Ready, onConfirm: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val value = Paise(reading.statedValuePaise)
    val net = Paise(reading.netContributionsPaise)
    val gain = value - net
    BottomOverlay(onClose) {
        Text(if (plan.accountIsNew) "Add ${plan.account.name}?" else "Update ${plan.account.name}?", style = ChittaTheme.type.title, color = c.ink)
        Text("Prices as of ${reading.priceDate.format(priceDateFmt)}. Nothing is saved until you tap Import. You can undo for 10 seconds.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Value", maskedRupees(value))
        ValueRow("Put in so far", maskedRupees(net))
        ValueRow("Gain", (if (gain.value > 0) "+" else "") + maskedRupees(gain))
        ValueRow("New schemes", plan.add.size.toString())
        ValueRow("Schemes updated", plan.update.size.toString())
        ValueRow("Schemes removed", plan.remove.size.toString())
        (plan.add + plan.update).forEach { f -> Text("${f.name} · ${unitsText(f)} units", style = ChittaTheme.type.label, color = c.inkMuted) }
        if (plan.remove.isNotEmpty()) Text("Removed because the statement no longer holds them: " + plan.remove.joinToString { it.name }, style = ChittaTheme.type.label, color = c.inkMuted)
        if (plan.leftOutAsTypedByHand.isNotEmpty()) Text(
            "Left out, already typed by hand (never counted twice): " + plan.leftOutAsTypedByHand.joinToString { it.name } + ". Delete the hand-typed one to use the statement.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Import", onConfirm, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Cancel", onClose, style = ButtonStyle.Quiet)
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
        if (!f.isNps) {
            ValueRow("Put in", f.investedPaise?.let { maskedRupees(Paise(it)) } ?: "No average price")
            ValueRow("Gain", gain?.let { (if (it.value > 0) "+" else "") + maskedRupees(it) } ?: if (value == null) "Needs a price" else "Needs an average price")
            ValueRow("Units from", sourceName(f.source))
        }
        ValueRow("Units as of", dateText(f.unitsAsOfMs, zone))
        if (f.folio.isNotBlank()) ValueRow("Folio", f.folio)
        if (f.isin.isNotBlank()) ValueRow("ISIN", f.isin)
        if (f.note.isNotBlank()) ValueRow("Note", f.note)
        Text("Value is units times the latest price. Units change only when you type or import them.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Edit", onEdit)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Delete", { confirmDelete = true }, style = ButtonStyle.Quiet)
    }
    if (confirmDelete) ConfirmSheet(
        if (f.isNps) "Delete this scheme?" else "Delete this fund?", listOf("It leaves the list. No account balance changes. You can undo for 10 seconds."), "Delete",
        { confirmDelete = false; onDelete() }, { confirmDelete = false },
    )
}

private enum class FundField { Name, Isin, Units, Average, Price, Note }

private fun plainDecimal(d: BigDecimal): String = d.stripTrailingZeros().toPlainString()

/** Add or edit a mutual fund by hand. Nothing is saved until Save, and the check says in plain words what is wrong. */
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
                if (u != null && inv != null) plainDecimal(BigDecimal(inv).movePointLeft(2).divide(u, 4, java.math.RoundingMode.HALF_EVEN)) else ""
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
                    existing?.id ?: "", name.trim(), code, existing?.schemeCode ?: "", plainDecimal(u!!),
                    avg?.let { MutualFunds.investedFrom(u, it) }, existing?.source ?: MutualFund.SOURCE_MANUAL, asOf, existing?.navText, existing?.navDate, note.trim(), null,
                    existing?.folio ?: "", "",
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
        FundField.Price, null -> Unit
    }
}

/** Add or edit one NPS scheme (E, C, G or any other): name, units, and the price with its date, all from the statement. */
@Composable
private fun SchemeEditSheet(existing: MutualFund?, groupId: String, nowMs: Long, zone: ZoneId, onSave: (MutualFund) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val today = Periods.startOfDay(Periods.date(nowMs, zone), zone)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var units by remember { mutableStateOf(existing?.units ?: "") }
    var price by remember { mutableStateOf(existing?.navText ?: "") }
    var priceDate by remember { mutableStateOf(existing?.navDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull() } ?: today) }
    var editing by remember { mutableStateOf<FundField?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    BottomOverlay(onClose) {
        Text(if (existing == null) "New NPS scheme" else "Edit NPS scheme", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Scheme name", name.ifBlank { "not set" }, onClick = { editing = FundField.Name })
        ValueRow("Units", units.ifBlank { "not set" }, onClick = { editing = FundField.Units })
        ValueRow("Price per unit", price.ifBlank { "not set" }, onClick = { editing = FundField.Price })
        ValueRow("Price as of", dateText(priceDate, zone), onClick = { pickDate(context, priceDate, zone, { priceDate = Periods.startOfDay(Periods.date(it, zone), zone) }) })
        Text("Units and price are on the statement's Investment Details table. The statement's date is the price date.", style = ChittaTheme.type.label, color = c.inkMuted)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", {
            val u = units.trim().toBigDecimalOrNull()
            val p = price.trim().toBigDecimalOrNull()
            problem = when {
                name.isBlank() -> "Enter the scheme's name."
                u == null || u.signum() <= 0 || u.scale() > 4 -> "Enter the units as a number above zero, up to 4 decimal places."
                p == null || p.signum() <= 0 || p.scale() > 4 -> "Enter the price as a number above zero, up to 4 decimal places."
                else -> null
            }
            if (problem == null) onSave(
                MutualFund(
                    existing?.id ?: "", name.trim(), "", "", plainDecimal(u!!), null, existing?.source ?: MutualFund.SOURCE_MANUAL, priceDate,
                    plainDecimal(p!!), java.time.Instant.ofEpochMilli(priceDate).atZone(zone).toLocalDate().toString(), existing?.note ?: "", null, "", groupId,
                ),
            )
        }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }

    when (editing) {
        FundField.Name -> TextSheet("Scheme name", "For example, ICICI Pension Fund Scheme E - Tier I", name, { name = it; editing = null }, { editing = null })
        FundField.Units -> TextSheet("Units", "Total units, for example 19.1467", units, { units = it; problem = null; editing = null }, { editing = null }, numeric = true)
        FundField.Price -> TextSheet("Price per unit", "Latest price, for example 70.1151", price, { price = it; problem = null; editing = null }, { editing = null }, numeric = true)
        else -> Unit
    }
}

private enum class NpsField { Name, Net }

/** Add or edit an NPS account: its name, and what was paid in minus what was withdrawn, as the statement states it. */
@Composable
private fun NpsAccountEditSheet(existing: NpsAccount?, nowMs: Long, zone: ZoneId, onSave: (NpsAccount) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val today = Periods.startOfDay(Periods.date(nowMs, zone), zone)
    var name by remember { mutableStateOf(existing?.name ?: "NPS Tier I") }
    var net by remember { mutableStateOf(existing?.netContributionsPaise?.let { plainDecimal(BigDecimal(it).movePointLeft(2)) } ?: "") }
    var asOf by remember { mutableStateOf(existing?.asOfMs ?: today) }
    var editing by remember { mutableStateOf<NpsField?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    BottomOverlay(onClose) {
        Text(if (existing == null) "New NPS account" else "Edit NPS account", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Name", name.ifBlank { "not set" }, onClick = { editing = NpsField.Name })
        ValueRow("Put in so far", net.ifBlank { "not set" }, onClick = { editing = NpsField.Net })
        ValueRow("Statement as of", dateText(asOf, zone), onClick = { pickDate(context, asOf, zone, { asOf = Periods.startOfDay(Periods.date(it, zone), zone) }) })
        Text("Put in so far is the statement's Total Contribution minus any withdrawal. Your PRAN is not needed and is never saved.", style = ChittaTheme.type.label, color = c.inkMuted)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", {
            val v = if (net.isBlank()) null else net.trim().toBigDecimalOrNull()
            problem = when {
                name.isBlank() -> "Enter a name for the account."
                net.isNotBlank() && (v == null || v.signum() < 0 || v.scale() > 2) -> "Enter what you put in as rupees, up to 2 decimal places, or leave it empty."
                else -> null
            }
            if (problem == null) onSave(NpsAccount(existing?.id ?: "", name.trim(), v?.movePointRight(2)?.longValueExact(), asOf, existing?.note ?: "", null))
        }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }

    when (editing) {
        NpsField.Name -> TextSheet("Name", "For example, NPS Tier I", name, { name = it; editing = null }, { editing = null })
        NpsField.Net -> TextSheet("Put in so far", "Rupees, for example 13677 (empty if unknown)", net, { net = it; problem = null; editing = null }, { editing = null }, numeric = true)
        null -> Unit
    }
}

@Composable
private fun NpsAccountDetailSheet(a: NpsAccount, schemeCount: Int, zone: ZoneId, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    BottomOverlay(onClose) {
        Text(a.name, style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Put in so far", a.netContributionsPaise?.let { maskedRupees(Paise(it)) } ?: "not set")
        ValueRow("Schemes", schemeCount.toString())
        ValueRow("Statement as of", dateText(a.asOfMs, zone))
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Edit", onEdit)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Delete", { confirmDelete = true }, style = ButtonStyle.Quiet)
    }
    if (confirmDelete) ConfirmSheet(
        "Delete this NPS account?", listOf("Its $schemeCount ${if (schemeCount == 1) "scheme goes" else "schemes go"} with it. Mutual funds are not touched. You can undo for 10 seconds."), "Delete",
        { confirmDelete = false; onDelete() }, { confirmDelete = false },
    )
}
