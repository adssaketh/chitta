package com.mansaketh.chitta.ui.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.LoadingPlaceholder
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.CsvImport
import com.mansaketh.chitta.core.domain.CsvMapping
import com.mansaketh.chitta.core.domain.DatePattern
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.ValueRow
import java.time.Instant
import java.time.ZoneId

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file.csv"

/** Import entries from a CSV file in three steps (columns, review, save). Nothing is saved before the last tap. */
@Composable
fun CsvImportScreen(vm: CsvImportViewModel, onBack: () -> Unit) {
    val stage by vm.stage.collectAsState()
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(uri, displayName(context, uri)) }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Import from CSV", onBack = { vm.reset(); onBack() })
        when (val s = stage) {
            CsvStage.Idle -> Column(Modifier.padding(horizontal = Dimens.Margin), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Gap3)) {
                Text("Pick a CSV file from a bank or another app, or a Zerodha Console ledger (Excel or CSV). You confirm which column is which, then review every row before anything is saved.", style = ChittaTheme.type.body, color = c.ink)
                Text("Transfers between your own accounts are not imported from CSV. Add those with Transfer.", style = ChittaTheme.type.label, color = c.inkMuted)
                ChittaButton("Choose a CSV or Excel file", { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) })
            }
            CsvStage.Reading, CsvStage.Saving -> Column(Modifier.padding(horizontal = Dimens.Margin)) { LoadingPlaceholder() }
            is CsvStage.Failed -> Column(Modifier.padding(horizontal = Dimens.Margin), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Gap3)) {
                Text(s.message, style = ChittaTheme.type.body, color = c.ink)
                ChittaButton("Try again", { vm.reset() }, style = ButtonStyle.Quiet)
            }
            is CsvStage.Saved -> Column(Modifier.padding(horizontal = Dimens.Margin), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Gap3)) {
                Text("Saved ${s.count} ${if (s.count == 1) "entry" else "entries"}. Undo is on the bar for 10 seconds.", style = ChittaTheme.type.body, color = c.ink)
                ChittaButton("Done", { vm.reset(); onBack() })
            }
            is CsvStage.Mapping -> MappingStep(vm, s)
            is CsvStage.Review -> ReviewStep(vm, s)
        }
    }
}

@Composable
private fun MappingStep(vm: CsvImportViewModel, s: CsvStage.Mapping) {
    val c = ChittaTheme.colors
    val m = s.mapping
    var sheet by remember { mutableStateOf<String?>(null) }
    val header = s.rows[0]
    val firstData = s.rows.getOrNull(if (m.hasHeader) 1 else 0).orEmpty()
    val problems = m.problems()
    val accounts by vm.accountsForPicker.collectAsState()

    fun columnLabel(i: Int?): String = if (i == null) "Not in the file" else "Column ${i + 1}" + (if (m.hasHeader) ": " + header.getOrElse(i) { "" } else "")
    val fields = listOf(
        Triple("date", "Date", m.date), Triple("amount", "Amount", m.amount), Triple("debit", "Money out column", m.debit), Triple("credit", "Money in column", m.credit),
        Triple("title", "Title", m.title), Triple("category", "Category", m.category), Triple("subcategory", "Subcategory", m.subcategory),
        Triple("account", "Account", m.account), Triple("note", "Note", m.note), Triple("type", "Type (income or expense)", m.type),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 160.dp)) {
        Text("${s.fileName} · ${s.rows.size - (if (m.hasHeader) 1 else 0)} rows", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))
        if (s.notice != null) Text(s.notice, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2))
        SectionHeader("Which column is which")
        HairlineRule()
        fields.forEach { (key, label, value) -> ValueRow(label, columnLabel(value), onClick = { sheet = "col:$key" }) }
        ValueRow("Date looks like", m.datePattern.label, onClick = { sheet = "datefmt" })
        ValueRow("In the amount column", if (m.positiveIsExpense) "Positive is money out" else "Negative is money out", onClick = { vm.setMapping(m.copy(positiveIsExpense = !m.positiveIsExpense)) })
        ValueRow("First row is a header", if (m.hasHeader) "Yes" else "No", onClick = { vm.setMapping(m.copy(hasHeader = !m.hasHeader)) })
        ValueRow("Account for rows with none", accounts.firstOrNull { it.id == m.defaultAccountId }?.name ?: "None chosen", onClick = { sheet = "account" })

        if (firstData.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.Gap4))
            Text("First row: " + firstData.joinToString(" | ").take(160), style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Spacer(Modifier.height(Dimens.Gap4))
        problems.forEach { Text(it, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(bottom = Dimens.Gap1)) }
        ChittaButton("Review rows", { vm.toReview() }, enabled = problems.isEmpty())
        Spacer(Modifier.height(Dimens.Gap2))
        if (s.templates.isNotEmpty()) ChittaButton("Use a saved setup", { sheet = "template" }, style = ButtonStyle.Quiet)
        ChittaButton("Save this setup", { sheet = "save-template" }, style = ButtonStyle.Quiet)
    }

    val sheetKey = sheet
    when {
        sheetKey != null && sheetKey.startsWith("col:") -> {
            val key = sheetKey.removePrefix("col:")
            val choices = listOf(Choice("Not in the file", -1)) + header.indices.map { i ->
                Choice("Column ${i + 1}: ${header[i]}".trim(), i, detail = firstData.getOrNull(i)?.take(40)?.takeIf { it.isNotBlank() })
            }
            ChoiceSheet(fields.first { it.first == key }.second, choices, { picked ->
                val v = picked.takeIf { it >= 0 }
                vm.setMapping(when (key) {
                    "date" -> m.copy(date = v); "amount" -> m.copy(amount = v); "debit" -> m.copy(debit = v); "credit" -> m.copy(credit = v)
                    "title" -> m.copy(title = v); "category" -> m.copy(category = v); "subcategory" -> m.copy(subcategory = v)
                    "account" -> m.copy(account = v); "note" -> m.copy(note = v); else -> m.copy(type = v)
                })
                sheet = null
            }, { sheet = null })
        }
        sheetKey == "datefmt" -> ChoiceSheet("Date looks like", DatePattern.entries.map { Choice(it.label, it, selected = it == m.datePattern) }, { vm.setMapping(m.copy(datePattern = it)); sheet = null }, { sheet = null })
        sheetKey == "account" -> AccountChoice(vm, m) { sheet = null }
        sheetKey == "template" -> ChoiceSheet("Saved setups", s.templates.map { Choice(it.name, it) }, { vm.applyTemplate(it); sheet = null }, { sheet = null })
        sheetKey == "save-template" -> TextSheet("Save this setup", "Name", "", { vm.saveTemplate(it); sheet = null }, { sheet = null }, placeholder = "For example: HDFC savings")
    }
}

@Composable
private fun AccountChoice(vm: CsvImportViewModel, m: CsvMapping, close: () -> Unit) {
    val accounts by vm.accountsForPicker.collectAsState()
    ChoiceSheet("Account for rows with none", accounts.map { Choice(it.name, it.id, selected = it.id == m.defaultAccountId) }, { vm.setMapping(m.copy(defaultAccountId = it)); close() }, close)
}

@Composable
private fun ReviewStep(vm: CsvImportViewModel, s: CsvStage.Review) {
    val c = ChittaTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var rowSheet by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var bulk by remember { mutableStateOf(false) }
    val ticked = s.choices.count { it.ticked && it.saveable }
    val needsLook = s.choices.count { !it.ticked }
    val missingCategory = s.choices.count { it.categoryId == null }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Dimens.Margin)) {
            Text("$ticked ticked to save · $needsLook left unticked · ${s.parse.skipped.size} left out", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
            Text("Rows that look like something you already have, or need a category or account, start unticked. Tap a row to change it.", style = ChittaTheme.type.label, color = c.inkMuted)
            if (missingCategory > 0) ChittaButton("Set a category for $missingCategory rows without one", { bulk = true }, style = ButtonStyle.Quiet)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(s.choices) { i, ch ->
                val r = ch.row
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).clickable(role = Role.Button) { rowSheet = i to "row" }.padding(vertical = Dimens.Gap2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box24(ch.ticked)
                    Spacer(Modifier.width(Dimens.Gap3))
                    Column(Modifier.weight(1f)) {
                        Text(r.title.ifBlank { "(no title)" }, style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
                        val account = s.accounts.firstOrNull { it.id == ch.accountId }?.name ?: r.accountLabel.ifBlank { "No account" }
                        val category = s.picks.firstOrNull { it.categoryId == ch.categoryId && it.subCategoryId == ch.subCategoryId }?.label ?: r.categoryLabel.ifBlank { "No category" }
                        Text(Instant.ofEpochMilli(r.dateMs).atZone(zone).toLocalDate().toString() + " · " + account + " · " + category, style = ChittaTheme.type.label, color = c.inkMuted, maxLines = 1)
                        val reasons = r.reasons.filter { reason -> stillApplies(reason, ch) }
                        if (reasons.isNotEmpty()) Text(reasons.joinToString(". ") { it.text }, style = ChittaTheme.type.labelMedium, color = c.ink)
                    }
                    Spacer(Modifier.width(Dimens.Gap2))
                    Text((if (r.amountPaise < 0) "-" else "+") + IndianFormat.rupees(Paise(Math.abs(r.amountPaise)), alwaysShowPaise = true), style = ChittaTheme.type.body, color = if (r.income) c.green else c.ink)
                }
                HairlineRule()
            }
            if (s.parse.skipped.isNotEmpty()) item {
                Spacer(Modifier.height(Dimens.Gap4))
                Text("Left out", style = ChittaTheme.type.title, color = c.ink)
                s.parse.skipped.take(30).forEach { Text("Line ${it.line}: ${it.reason}", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1)) }
                if (s.parse.skipped.size > 30) Text("…and ${s.parse.skipped.size - 30} more.", style = ChittaTheme.type.label, color = c.inkMuted)
            }
        }
        Column(Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap2, bottom = Dimens.Gap4)) {
            ChittaButton("Save $ticked ${if (ticked == 1) "entry" else "entries"}", { vm.save() }, enabled = ticked > 0, confirmHaptic = true)
        }
    }

    rowSheet?.let { (i, kind) ->
        val ch = s.choices[i]
        when (kind) {
            "row" -> ChoiceSheet(
                ch.row.title.ifBlank { "Row" },
                listOf(Choice(if (ch.ticked) "Leave this row out" else "Include this row", "toggle"), Choice("Change account", "account"), Choice("Change category", "category")),
                { act -> if (act == "toggle") { vm.toggle(i); rowSheet = null } else { rowSheet = i to act } }, { rowSheet = null },
            )
            "account" -> ChoiceSheet("Account", s.accounts.map { Choice(it.name, it.id, selected = it.id == ch.accountId) }, { vm.setAccount(i, it); rowSheet = null }, { rowSheet = null })
            else -> ChoiceSheet("Category", s.picks.map { Choice(it.label, it) }, { vm.setCategory(i, it); rowSheet = null }, { rowSheet = null })
        }
    }
    if (bulk) ChoiceSheet("Category for rows without one", s.picks.map { Choice(it.label, it) }, { vm.setCategoryForMissing(it); bulk = false }, { bulk = false })
}

/** A reason is shown only while it is still true: picking an account or a category clears its note. */
private fun stillApplies(reason: com.mansaketh.chitta.core.domain.CsvReason, ch: CsvImport.Choice): Boolean = when (reason) {
    com.mansaketh.chitta.core.domain.CsvReason.NoCategory, com.mansaketh.chitta.core.domain.CsvReason.UnknownCategory -> ch.categoryId == null
    com.mansaketh.chitta.core.domain.CsvReason.NoAccount, com.mansaketh.chitta.core.domain.CsvReason.UnknownAccount -> ch.accountId == null
    else -> true
}

/** A 24 dp tick box: a check when the row will be saved, an empty outline when it will not. */
@Composable
private fun Box24(ticked: Boolean) {
    val c = ChittaTheme.colors
    if (ticked) Icon(ChittaIcons.Check, contentDescription = "Will be saved", tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
    else Spacer(Modifier.size(Dimens.IconSize).border(1.dp, c.rule, RoundedCornerShape(4.dp)))
}
