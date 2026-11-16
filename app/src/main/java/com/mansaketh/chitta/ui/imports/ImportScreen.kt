package com.mansaketh.chitta.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.Note
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.ValueRow
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
        TopBarDetail(if (firstRun) "Bring over your book" else "Bring over your book", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset), verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
            when (val s = state) {
                ImportState.Idle -> {
                    Text("Pick the backup file from the old Paddhu Pusthakam app. This app reads a copy and shows you what it found. Nothing is saved until you confirm.", style = ChittaTheme.type.body, color = c.ink)
                    ChittaButton("Choose backup file", { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth())
                    val days = vm.undoDaysLeft()
                    if (days > 0 && !firstRun) {
                        Note("You imported a book recently. You can undo it for $days more ${if (days == 1L) "day" else "days"}. This empties the book, including anything added since.")
                        ChittaButton("Undo import", { confirmUndo = true }, modifier = Modifier.fillMaxWidth())
                    }
                }
                ImportState.Reading -> LoadingPlaceholder()
                is ImportState.Refused -> { Text(s.reason, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Choose another file", { vm.reset() }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth()) }
                is ImportState.Failed -> { Text(s.message, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Try again", { vm.reset() }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth()) }
                is ImportState.Blocked -> { Text(s.message, style = ChittaTheme.type.body, color = c.ink); ChittaButton("Back", onBack, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth()) }
                ImportState.Importing -> { Text("Importing. Keep this screen open.", style = ChittaTheme.type.body, color = c.ink); LoadingPlaceholder() }
                is ImportState.Done -> {
                    Text("Done. ${s.rows} rows are in your book. A copy of the backup is kept on this phone for 30 days.", style = ChittaTheme.type.body, color = c.ink)
                    ChittaButton("Open my book", onFinished, modifier = Modifier.fillMaxWidth())
                }
                is ImportState.Preview -> {
                    // PDF p.11 and p.12 (screens 03 and 04). The step line, the figure grid, "Needs your decision" and the restore-point note of the
                    // drawing are not in the app, so they are not built. The buttons are pinned below as a pair.
                    val r = s.report
                    Section("What was found", first = true) {
                        r.counts.filter { it.read > 0 }.forEach { ValueRow(it.table, if (it.read == it.imported) "${it.read}" else "${it.imported} of ${it.read}") }
                        if (r.firstTransactionMs != null && r.lastTransactionMs != null)
                            ValueRow("Entries from", "${Instant.ofEpochMilli(r.firstTransactionMs!!).atZone(zone).toLocalDate()} to ${Instant.ofEpochMilli(r.lastTransactionMs!!).atZone(zone).toLocalDate()}")
                    }
                    if (r.movedAmounts > 0) Text("${r.movedAmounts} amounts were not a whole number of paise and were rounded.", style = ChittaTheme.type.label, color = c.inkMuted)
                    Section("Balances to compare") {
                        val off = r.accountChecks.filter { !it.matches }.map { it.accountName }.toSet()
                        s.balances.forEach { (a, b) -> ValueRow(a.name, IndianFormat.rupees(b, alwaysShowPaise = true), matched = a.name !in off) }
                    }
                    Note("Open Paddhu Pusthakam and check these match its account balances. If one differs, do not import yet; tell me which.")
                    Section("Conversion check") {
                        Text(if (r.balancesMatch) "Every account's amounts add up exactly as they did in the backup." else "Some accounts do not add up. See below.", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
                        r.accountChecks.filter { !it.matches }.forEach { ValueRow(it.accountName, "backup ${IndianFormat.rupees(com.mansaketh.chitta.core.money.Paise(it.sourceSumPaise), alwaysShowPaise = true)} vs imported ${IndianFormat.rupees(com.mansaketh.chitta.core.money.Paise(it.importedSumPaise), alwaysShowPaise = true)}") }
                    }
                    if (r.issues.isNotEmpty()) {
                        Section("${r.errors} problems, ${r.warnings} notes") {
                            r.issues.take(12).forEach { Text((if (it.severity == Severity.ERROR) "Left out · " else "") + "${it.table}: ${it.message}", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Space1)) }
                            if (r.issues.size > 12) Text("…and ${r.issues.size - 12} more.", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                    }
                    if (r.notCarriedOver.isNotEmpty()) {
                        Section("Not carried over") { r.notCarriedOver.forEach { (k, n) -> ValueRow(k, "$n") } }
                    }
                }
            }
        }
        (state as? ImportState.Preview)?.let { s ->
            ButtonPair(
                first = { m -> ChittaButton("Choose another file", { vm.reset() }, kind = ButtonKind.Secondary, modifier = m) },
                second = { m -> ChittaButton("Import this book", { vm.import(s) }, confirmHaptic = true, modifier = m) },
                modifier = Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap3, bottom = Dimens.Gap4),
            )
        }
    }
    if (confirmUndo) ConfirmSheet("Undo import?", listOf("This empties the whole book, including anything you added after importing.", "Your backup copy stays on this phone."), "Empty the book", { confirmUndo = false; vm.undoImport(onBack) }, { confirmUndo = false })
}
