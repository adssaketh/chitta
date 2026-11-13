package com.mansaketh.chitta.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.importer.Severity
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.ui.ConfirmSheet
import java.time.Instant
import java.time.ZoneId

/**
 * Bring over your book: pick the Paddhu backup, read it without saving, show what will happen, then import.
 * The balances listed are computed with Chitta's balance rule; compare them with Paddhu's own screen before you confirm.
 */
@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit, onFinished: () -> Unit, firstRun: Boolean = false) {
    val state by vm.state.collectAsState()
    val c = ChittaTheme.colors
    var confirmUndo by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(uri) }
    val zone = remember { ZoneId.systemDefault() }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar(if (firstRun) "Bring over your book" else "Bring over your book", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
            when (val s = state) {
                ImportState.Idle -> {
                    Text("Pick the backup file from Paddhu Pusthakam. Chitta reads a copy and shows you what it found. Nothing is saved until you confirm.", style = ChittaTheme.type.body, color = c.ink)
                    ChittaButton("Choose backup file", { picker.launch(arrayOf("*/*")) })
                    val days = vm.undoDaysLeft()
                    if (days > 0 && !firstRun) {
                        HairlineRule()
                        Text("You imported a book recently. You can undo it for $days more ${if (days == 1L) "day" else "days"}. This empties the book, including anything added since.", style = ChittaTheme.type.label, color = c.inkMuted)
                        ChittaButton("Undo import", { confirmUndo = true }, style = ButtonStyle.Attention)
                    }
                }
                ImportState.Reading -> LoadingPlaceholder()
                is ImportState.Refused -> { Text(s.reason, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Choose another file", { vm.reset() }, style = ButtonStyle.Quiet) }
                is ImportState.Failed -> { Text(s.message, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Try again", { vm.reset() }, style = ButtonStyle.Quiet) }
                is ImportState.Blocked -> { Text(s.message, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Back", onBack, style = ButtonStyle.Quiet) }
                ImportState.Importing -> { Text("Importing. Keep this screen open.", style = ChittaTheme.type.body, color = c.ink); LoadingPlaceholder() }
                is ImportState.Done -> {
                    Text("Done. ${s.rows} rows are in your book. A copy of the backup is kept on this phone for 30 days.", style = ChittaTheme.type.body, color = c.ink)
                    ChittaButton("Open my book", onFinished)
                }
                is ImportState.Preview -> {
                    val r = s.report
                    Text("What was found", style = ChittaTheme.type.title, color = c.ink)
                    r.counts.filter { it.read > 0 }.forEach { ValueLine(it.table, if (it.read == it.imported) "${it.read}" else "${it.imported} of ${it.read}") }
                    if (r.firstTransactionMs != null && r.lastTransactionMs != null)
                        ValueLine("Entries from", "${Instant.ofEpochMilli(r.firstTransactionMs!!).atZone(zone).toLocalDate()} to ${Instant.ofEpochMilli(r.lastTransactionMs!!).atZone(zone).toLocalDate()}")
                    if (r.movedAmounts > 0) Text("${r.movedAmounts} amounts were not a whole number of paise and were rounded.", style = ChittaTheme.type.label, color = c.inkMuted)
                    HairlineRule()
                    Text("Balances to compare", style = ChittaTheme.type.title, color = c.ink)
                    Text("Open Paddhu Pusthakam and check these match its account balances. If one differs, do not import yet; tell me which.", style = ChittaTheme.type.label, color = c.inkMuted)
                    s.balances.forEach { (a, b) -> ValueLine(a.name, IndianFormat.rupees(b, alwaysShowPaise = true)) }
                    HairlineRule()
                    Text("Conversion check", style = ChittaTheme.type.title, color = c.ink)
                    Text(if (r.balancesMatch) "Every account's amounts add up exactly as they did in the backup." else "Some accounts do not add up. See below.", style = ChittaTheme.type.body, color = c.ink)
                    r.accountChecks.filter { !it.matches }.forEach { ValueLine(it.accountName, "backup ${IndianFormat.rupees(com.mansaketh.chitta.core.money.Paise(it.sourceSumPaise), alwaysShowPaise = true)} vs imported ${IndianFormat.rupees(com.mansaketh.chitta.core.money.Paise(it.importedSumPaise), alwaysShowPaise = true)}") }
                    if (r.issues.isNotEmpty()) {
                        HairlineRule()
                        Text("${r.errors} problems, ${r.warnings} notes", style = ChittaTheme.type.title, color = c.ink)
                        r.issues.take(12).forEach { Text((if (it.severity == Severity.ERROR) "Left out · " else "") + "${it.table}: ${it.message}", style = ChittaTheme.type.label, color = c.inkMuted) }
                        if (r.issues.size > 12) Text("…and ${r.issues.size - 12} more.", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    if (r.notCarriedOver.isNotEmpty()) {
                        HairlineRule()
                        Text("Not carried over", style = ChittaTheme.type.title, color = c.ink)
                        r.notCarriedOver.forEach { (k, n) -> ValueLine(k, "$n") }
                    }
                    Spacer(Modifier.height(Dimens.Gap2))
                    ChittaButton("Import this book", { vm.import(s) }, confirmHaptic = true)
                    ChittaButton("Choose another file", { vm.reset() }, style = ButtonStyle.Quiet)
                }
            }
        }
    }
    if (confirmUndo) ConfirmSheet("Undo import?", listOf("This empties the whole book, including anything you added after importing.", "Your backup copy stays on this phone."), "Empty the book", { confirmUndo = false; vm.undoImport(onBack) }, { confirmUndo = false })
}

@Composable
private fun ValueLine(label: String, value: String) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.weight(1f))
        Text(value, style = ChittaTheme.type.body, color = c.ink)
    }
}
