package com.mansaketh.chitta.ui.imports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.LoadingPlaceholder
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.Note
import com.mansaketh.chitta.core.design.kit.ResultRow
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.core.design.kit.Stat
import com.mansaketh.chitta.core.design.kit.StatGrid
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.importer.Severity
import com.mansaketh.chitta.core.importer.SimilarPair
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.sheetMax
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bring over your book, in three steps (PDF p.11 and p.12; the exact match rule):
 *  1. Pick the Paddhu backup; it is read without saving anything and shown with what it holds and what needs a look.
 *  2. Dry run: every balance and total is compared with the backup's own figures.
 *  3. Import (and Done). Step 3 has no page in the PDF, so it keeps its earlier wording under the same top bar.
 * States the PDF does not draw (pick a file, reading, refused, failed, blocked) keep their earlier wording with the shared kit parts.
 */
@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit, onFinished: () -> Unit, firstRun: Boolean = false) {
    val state by vm.state.collectAsState()
    val c = ChittaTheme.colors
    var confirmUndo by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Detail?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(uri) }
    // Step 2's back (arrow or phone Back) returns to step 1; anywhere else it leaves, as before.
    BackHandler(enabled = state is ImportState.Checked) { vm.back() }

    val s = state
    val title = if (s is ImportState.Checked) "Dry run" else "Bring over your book"
    val subtitle = when (s) {
        is ImportState.Preview -> "Step 1 of 3 · nothing saved yet"
        is ImportState.Checked -> "Step 2 of 3 · nothing saved yet"
        ImportState.Importing, is ImportState.Done -> "Step 3 of 3"
        else -> null
    }

    // First run is the whole page, so it clears the navigation bar itself; from Settings the tab bar below already does.
    Column(Modifier.fillMaxSize().then(if (firstRun) Modifier.navigationBarsPadding() else Modifier)) {
        TopBarDetail(title, onBack = { if (!vm.back()) onBack() }, subtitle = subtitle)
        when (s) {
            is ImportState.Preview -> PreviewStep(s, firstRun, vm, onBack, onOpen = { sheet = it })
            is ImportState.Checked -> CheckedStep(s, vm, onCancel = { vm.reset(); onBack() })
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap3, bottom = Dimens.EndInset),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap3),
            ) {
                when (s) {
                    ImportState.Idle -> {
                        Text("Pick the backup file from the old Paddhu Pusthakam app. This app reads a copy and shows you what it found. Nothing is saved until you confirm.", style = ChittaTheme.type.body, color = c.ink)
                        ChittaButton("Choose backup file", { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth())
                        val days = vm.undoDaysLeft()
                        if (days > 0 && !firstRun) {
                            HairlineRule()
                            Text("You imported a book recently. You can undo it for $days more ${if (days == 1L) "day" else "days"}. This empties the book, including anything added since.", style = ChittaTheme.type.label, color = c.inkMuted)
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
                    else -> Unit
                }
            }
        }
    }
    sheet?.let { d ->
        val preview = (state as? ImportState.Preview)
        when (d) {
            is Detail.Merge -> BottomOverlay({ sheet = null }, handle = false) {
                SheetHeader("Two similar categories")
                Spacer(Modifier.height(Dimens.Gap3))
                listOf(
                    "“${d.pair.fromName}” and “${d.pair.intoName}” look like the same thing.",
                    "Merging moves every entry from “${d.pair.fromName}” into “${d.pair.intoName}” and removes “${d.pair.fromName}”. Nothing changes until you import, and the dry run shows the result.",
                ).forEach { Text(it.rupeeAware(), style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2)) }
                Spacer(Modifier.height(Dimens.Gap4))
                val merged = preview?.merges?.contains(d.pair.key) == true
                ButtonPair(
                    first = { m -> ChittaButton("Keep both", { vm.setMerge(d.pair.key, false); sheet = null }, kind = ButtonKind.Secondary, modifier = m) },
                    second = { m -> ChittaButton(if (merged) "Merge (chosen)" else "Merge them", { vm.setMerge(d.pair.key, true); sheet = null }, modifier = m) },
                )
            }
            is Detail.Info -> BottomOverlay({ sheet = null }, handle = false) {
                SheetHeader(d.title, onClose = { sheet = null })
                Spacer(Modifier.height(Dimens.Gap3))
                Column(Modifier.heightIn(max = sheetMax(0.5f)).verticalScroll(rememberScrollState())) {
                    d.lines.forEach { Text(it.rupeeAware(), style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2)) }
                }
            }
        }
    }
    if (confirmUndo) ConfirmSheet("Undo import?", listOf("This empties the whole book, including anything you added after importing.", "Your backup copy stays on this phone."), "Empty the book", { confirmUndo = false; vm.undoImport(onBack) }, { confirmUndo = false })
}

/** Step 1 (PDF p.11): the file, the figures, what needs a look, and the two buttons pinned at the bottom. */
@Composable
private fun ColumnScope.PreviewStep(s: ImportState.Preview, firstRun: Boolean, vm: ImportViewModel, onBack: () -> Unit, onOpen: (Detail) -> Unit) {
    val c = ChittaTheme.colors
    val rows = decisionRows(s)
    val zone = remember { ZoneId.systemDefault() }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = Dimens.Margin)) {
            Spacer(Modifier.height(Dimens.BlockGap))
            Text("From your Paddhu backup", style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
            Text(s.fileName, style = ChittaTheme.type.mono, color = c.ink, maxLines = 2)
            Text(fileMeta(s, zone), style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.BlockGap))
            StatGrid(
                listOf(
                    Stat(n(s.book.accounts.size), "accounts"), Stat(n(s.book.categories.size), "categories"), Stat(n(s.book.transactions.size), "entries"),
                    Stat(n(s.book.budgets.size), "budgets"), Stat(n(s.book.objectives.count { it.kind == ObjectiveKind.GOAL }), "goals"),
                    Stat(n(s.book.objectives.count { it.kind == ObjectiveKind.LOAN }), "loans"),
                ),
            )
        }
        if (rows.isNotEmpty()) {
            // The heading and its rule sit inside the margins; the rows run their rules edge to edge, as on First run.
            Section("Needs your decision", Modifier.padding(horizontal = Dimens.Margin), note = rows.size.toString())
            rows.forEach { r ->
                NavRow(r.title, { onOpen(r.detail) }, Modifier.padding(horizontal = Dimens.Margin), sub = r.sub, rule = false)
                HairlineRule()
            }
        }
        Spacer(Modifier.height(Dimens.EndInset))
    }
    ButtonPair(
        first = { m ->
            if (firstRun) ChittaButton("Start fresh", { vm.startFresh { vm.reset() } }, kind = ButtonKind.Secondary, modifier = m)
            else ChittaButton("Cancel", { vm.reset(); onBack() }, kind = ButtonKind.Secondary, modifier = m)
        },
        second = { m -> ChittaButton("Run dry run", { vm.runDryRun() }, modifier = m) },
        modifier = Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap3, bottom = Dimens.Margin),
    )
}

/** Step 2 (PDF p.12): the verdict, one line per comparison, the note about the restore point, and the two buttons pinned at the bottom. */
@Composable
private fun ColumnScope.CheckedStep(s: ImportState.Checked, vm: ImportViewModel, onCancel: () -> Unit) {
    val c = ChittaTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(s.checkedAtMs) { DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(Instant.ofEpochMilli(s.checkedAtMs).atZone(zone)).lowercase(Locale.ENGLISH) }
    val ok = s.result.allMatch
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Space6, bottom = Dimens.BlockGap)) {
            Text(if (ok) "Every total matches" else "Not every total matches", style = ChittaTheme.type.headline, color = c.ink)
            Spacer(Modifier.height(Dimens.Gap2))
            Text("Compared with Paddhu at $time", style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
        }
        HairlineRule()
        s.result.rows.forEach { r ->
            ResultRow(r.title, r.sub, r.ok, Modifier.padding(horizontal = Dimens.Margin), rule = false)
            HairlineRule()
        }
        Note(
            "A restore point is made first. You can undo the whole import from Settings for 30 days. Paddhu itself is never changed.",
            Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.BlockGap),
        )
        Spacer(Modifier.height(Dimens.EndInset))
    }
    val count = s.result.entryCount
    ButtonPair(
        first = { m -> ChittaButton("Cancel", onCancel, kind = ButtonKind.Secondary, modifier = m) },
        second = { m -> ChittaButton("Import ${n(count)} ${if (count == 1) "entry" else "entries"}", { vm.import(s) }, enabled = ok, confirmHaptic = true, modifier = m) },
        modifier = Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap3, bottom = Dimens.Margin),
    )
}

/** What a row under "Needs your decision" opens. */
private sealed interface Detail {
    data class Merge(val pair: SimilarPair) : Detail
    data class Info(val title: String, val lines: List<String>) : Detail
}

private data class DecisionRow(val title: String, val sub: String, val detail: Detail)

private fun n(i: Int) = IndianFormat.group(i.toString())

/**
 * The rows are only what the importer really found (D294): similar categories, rows left out, notes, rounded amounts, and what is not
 * carried over. A row that is only information opens a short explanation; similar categories offer Merge or Keep both.
 */
private fun decisionRows(s: ImportState.Preview): List<DecisionRow> {
    val r = s.report
    val rows = ArrayList<DecisionRow>()
    r.similarCategories.forEach { p ->
        val merged = p.key in s.merges
        rows += DecisionRow(
            "Two similar categories",
            if (merged) "“${p.fromName}” will be merged into “${p.intoName}”" else "“${p.fromName}” and “${p.intoName}”",
            Detail.Merge(p),
        )
    }
    fun issueLines(sev: Severity): List<String> {
        val all = r.issues.filter { it.severity == sev }
        return all.take(12).map { "${it.table}: ${it.message}" } + (if (all.size > 12) listOf("…and ${all.size - 12} more.") else emptyList())
    }
    if (r.errors > 0) rows += DecisionRow(
        "${n(r.errors)} ${if (r.errors == 1) "row" else "rows"} left out", "Could not be read, so they are not imported",
        Detail.Info("Rows left out", issueLines(Severity.ERROR)),
    )
    if (r.warnings > 0) rows += DecisionRow(
        "${n(r.warnings)} ${if (r.warnings == 1) "note" else "notes"}", "Worth a look; nothing is left out",
        Detail.Info("Notes", issueLines(Severity.WARNING)),
    )
    if (r.movedAmounts > 0) rows += DecisionRow(
        "${n(r.movedAmounts)} ${if (r.movedAmounts == 1) "amount" else "amounts"} rounded", "Not a whole number of paise",
        Detail.Info("Amounts rounded", listOf("Paddhu stored these amounts with more than two decimals. They are rounded to the nearest paisa when saved.")),
    )
    r.notCarriedOver.forEach { (key, count) ->
        val title = if (key == "settings: device-only keys") "Settings that stay on the old phone" else key.substringAfter(": ").replaceFirstChar { it.uppercase() }
        rows += DecisionRow(title, "Nothing like it here, so it is left out", Detail.Info(title, listOf("${n(count)} found in the backup. They are not carried over.")))
    }
    return rows
}

/** "2.4 MB · Jan 2024 to Nov 2026" (the range from the first and last entry; just the size when there are no entries). */
private fun fileMeta(s: ImportState.Preview, zone: ZoneId): String {
    val size = if (s.sizeBytes < 1024L * 1024L) "${maxOf(1L, (s.sizeBytes + 512L) / 1024L)} KB" else String.format(Locale.ENGLISH, "%.1f MB", s.sizeBytes / (1024.0 * 1024.0))
    val first = s.report.firstTransactionMs
    val last = s.report.lastTransactionMs
    if (first == null || last == null) return size
    val f = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    val a = f.format(Instant.ofEpochMilli(first).atZone(zone))
    val b = f.format(Instant.ofEpochMilli(last).atZone(zone))
    return "$size · " + if (a == b) a else "$a to $b"
}
