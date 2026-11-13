package com.mansaketh.chitta.ui.ledger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.BulkChange
import com.mansaketh.chitta.core.domain.LedgerSearch
import com.mansaketh.chitta.core.domain.LedgerTab
import com.mansaketh.chitta.core.domain.Totals
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.*
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class BulkStage { Menu, Category, Account }

/** Where did it go? Day-grouped rows, sticky month header, tabs, search and filters, multi-select with bulk edit (PDF p.12). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(vm: LedgerViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    val pending by vm.pendingDelete.collectAsState()
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkStage?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) } // "account" or "category" filter picker
    val selecting = ui.selected.isNotEmpty()
    val c = ChittaTheme.colors
    val zone = remember { java.time.ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(ChittaIcons.Close, "Clear selection", vm::clearSelection)
                    Text("${ui.selected.size} selected", style = ChittaTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
                    IconAction(ChittaIcons.Pencil, "Change selected entries", onClick = { bulk = BulkStage.Menu  })
                    IconAction(ChittaIcons.Copy, "Copy details", onClick = { copy(context, vm.copyText()); notice = "Copied ${ui.selected.size} entries."  })
                    IconAction(ChittaIcons.Share, "Share totals", onClick = { share(context, vm.totalsText())  })
                    IconAction(ChittaIcons.Trash, "Delete selected entries", vm::requestDelete)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = Dimens.Margin, end = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ledger", style = ChittaTheme.type.headline, color = c.ink, modifier = Modifier.weight(1f))
                    IconAction(ChittaIcons.Filter, "Filter by account or category", onClick = { picker = "menu"  })
                    IconAction(ChittaIcons.Search, if (searching) "Close search" else "Search", onClick = { searching = !searching; if (!searching) vm.setText("")  })
                }
            }
            if (searching && !selecting) {
                ChittaField(
                    value = ui.filter.text, onValueChange = vm::setText, label = "Search title, note, category or account",
                    modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2),
                )
            }
            val tabs = LedgerTab.entries
            TextTabs(
                labels = listOf("All", "Spending", "Income", "Upcoming"), selected = tabs.indexOf(ui.filter.tab),
                onSelect = { vm.setTab(tabs[it]) }, modifier = Modifier.padding(horizontal = Dimens.Margin),
            )
            HairlineRule()
            val filtered = ui.filter.accountIds.isNotEmpty() || ui.filter.categoryIds.isNotEmpty()
            if (filtered) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtered: ${ui.matching} of ${ui.totalEntries} entries", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f))
                    SectionHeader("", action = "Clear", onAction = vm::clearFilters)
                }
            }
            notice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2)) }

            when {
                ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
                ui.totalEntries == 0 -> EmptyState("No transactions yet. Add one.", "Add a transaction", onAdd, Modifier.padding(horizontal = Dimens.Margin))
                ui.matching == 0 -> Text("Nothing matches. Clear the search or filters.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 160.dp)) {
                    ui.months.forEach { month ->
                        stickyHeader(key = "m-${month.month}") {
                            MonthHeader(monthLabel(month.month), month.flow.moneyIn, month.flow.moneyOut)
                        }
                        items(month.days, key = { "d-${it.date}" }) { day ->
                            LedgerDay(
                                dayNumber = day.date.dayOfMonth, weekday = day.date.weekdayShort(),
                                entries = day.entries.map { t -> t.toRow(ui, c.inkMuted) { id -> c.categoryTint(id) } }, dayTotal = day.net,
                                onEntryClick = { e -> if (selecting) vm.toggle(e.id) else onOpen(e.id) },
                                onEntryLongClick = { e -> if (selecting) vm.toggle(e.id) else vm.startSelecting(e.id) },
                            )
                        }
                    }
                }
            }
        }

        pending?.let { impact ->
            val lines = buildList {
                add("This removes ${impact.toDelete.size} ${if (impact.toDelete.size == 1) "entry" else "entries"}.")
                if (impact.extraTransferLegs > 0) add("It also removes the other half of ${impact.extraTransferLegs} transfer${if (impact.extraTransferLegs == 1) "" else "s"}, so no balance is left half-moved.")
                impact.balanceShifts.forEach { add("${it.account.name}: ${IndianFormat.rupees(it.before)} becomes ${IndianFormat.rupees(it.after)}.") }
                if (impact.linkedToGoalOrLoan > 0) add("${impact.linkedToGoalOrLoan} ${if (impact.linkedToGoalOrLoan == 1) "is" else "are"} linked to a goal or loan.")
                add("You can undo this for 10 seconds, and it stays in history.")
            }
            ConfirmSheet("Delete?", lines, "Delete", onConfirm = vm::confirmDelete, onDismiss = vm::cancelDelete)
        }

        when (bulk) {
            BulkStage.Menu -> ChoiceSheet(
                "Change ${ui.selected.size} entries",
                listOf(Choice("Category", BulkStage.Category), Choice("Account", BulkStage.Account), Choice("Date to today", null, "Moves them to the current day and time")),
                onPick = { v ->
                    if (v == null) { vm.bulk(BulkChange(dateMs = System.currentTimeMillis())) { n, s -> notice = bulkNotice(n, s) }; bulk = null } else bulk = v
                },
                onDismiss = { bulk = null },
            )
            BulkStage.Category -> ChoiceSheet(
                "Move to category",
                ui.categories.filter { it.mainCategoryId == null && !it.archived }.map { Choice(it.name, it.id) },
                onPick = { id -> vm.bulk(BulkChange(categoryId = id)) { n, s -> notice = bulkNotice(n, s) }; bulk = null },
                onDismiss = { bulk = null },
            )
            BulkStage.Account -> ChoiceSheet(
                "Move to account",
                ui.accounts.filter { !it.archived }.map { Choice(it.name, it.id) },
                onPick = { id -> vm.bulk(BulkChange(accountId = id)) { n, s -> notice = bulkNotice(n, s) }; bulk = null },
                onDismiss = { bulk = null },
            )
            null -> Unit
        }

        when (picker) {
            "menu" -> ChoiceSheet("Filter", listOf(Choice("By account", "account"), Choice("By category", "category")), onPick = { picker = it }, onDismiss = { picker = null })
            "account" -> ChoiceSheet(
                "Show one account",
                ui.accounts.filter { !it.archived }.map { Choice(it.name, it.id, selected = it.id in ui.filter.accountIds) },
                onPick = { id -> vm.setAccounts(setOf(id)); picker = null }, onDismiss = { picker = null },
            )
            "category" -> ChoiceSheet(
                "Show one category",
                ui.categories.filter { it.mainCategoryId == null }.map { Choice(it.name, it.id, selected = it.id in ui.filter.categoryIds) },
                onPick = { id -> vm.setCategories(setOf(id)); picker = null }, onDismiss = { picker = null },
            )
        }
    }
}

private fun bulkNotice(changed: Int, skipped: Int) =
    if (skipped == 0) "Changed $changed ${if (changed == 1) "entry" else "entries"}." else "Changed $changed. $skipped transfer ${if (skipped == 1) "half" else "halves"} left alone, because moving one would break the pair."

private fun monthLabel(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

internal fun Transaction.toRow(ui: LedgerUi, muted: androidx.compose.ui.graphics.Color, tint: (String) -> androidx.compose.ui.graphics.Color): LedgerEntry {
    val neutral = Totals.isNeutral(this)
    val mutedColor = muted
    val tintFor: (String) -> androidx.compose.ui.graphics.Color = tint
    val incomeCat = ui.categories.firstOrNull { it.id == categoryId }?.income == true
    val label = categoryName(categoryId, ui.categories)
    val upcoming = LedgerSearch.isUpcoming(this)
    val stamp = when {
        upcoming -> "Upcoming"
        kind != null && skipPaid -> "Skipped"
        kind != null && paid && kind != com.mansaketh.chitta.core.model.TransactionKind.LENT && kind != com.mansaketh.chitta.core.model.TransactionKind.BORROWED -> "Paid"
        kind == com.mansaketh.chitta.core.model.TransactionKind.LENT -> "Lent"
        kind == com.mansaketh.chitta.core.model.TransactionKind.BORROWED -> "Borrowed"
        else -> null
    }
    return LedgerEntry(
        title = name.ifBlank { label },
        category = listOfNotNull(label, accountName(accountId, ui.accounts), "Split".takeIf { splitGroupId != null }, "Possible duplicate".takeIf { id in ui.possibleDuplicates }).joinToString(" · "),
        glyph = if (neutral) ChittaIcons.Transfer else ChittaIcons.Tag,
        categoryColor = if (neutral) mutedColor else tintFor(categoryId),
        amount = Paise(Math.abs(amount)),
        tone = if (!neutral && (incomeCat || amount > 0)) AmountTone.Income else AmountTone.Expense,
        stamp = stamp,
        id = id,
        selected = id in ui.selected,
    )
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Chitta entries", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share totals").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
