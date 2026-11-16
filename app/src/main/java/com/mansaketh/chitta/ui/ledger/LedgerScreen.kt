package com.mansaketh.chitta.ui.ledger

import android.content.ClipData
import com.mansaketh.chitta.ui.FeatureIcons
import com.mansaketh.chitta.core.domain.TabKey
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.TopBarMain
import com.mansaketh.chitta.core.design.kit.FilterChip
import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.mansaketh.chitta.core.domain.HouseholdSharing
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.mansaketh.chitta.ui.ownsSideways

private enum class BulkStage { Menu, Category, Account }

/** How long the options under the search box take to fold away or come back while the keyboard is up. */
private const val OptionsMillis = 220

/** Where did it go? Day-grouped rows, sticky month header, tabs, search and filters, multi-select with bulk edit (PDF p.12). */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(vm: LedgerViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    val pending by vm.pendingDelete.collectAsState()
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkStage?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) } // "account" or "category" filter picker
    // Removing a typed-filter chip rewrites the search text; the box takes that one outside change (count, new text).
    var searchEdit by remember { mutableStateOf(0 to "") }
    val selecting = ui.selected.isNotEmpty()
    val said by vm.message.collectAsState()
    LaunchedEffect(said) { said?.let { notice = it; vm.messageShown() } }
    val c = ChittaTheme.colors
    // With the keyboard open the pinned header leaves room for about one row, so the first drag on the list closes the keyboard and gives the list its space back.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val hideKeyboardOnScroll = remember(keyboard) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
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
                TopBarMain("Ledger", ChittaIcons.Ledger, actions = {
                    IconAction(ChittaIcons.Filter, "Filter by account or category", onClick = { picker = "menu"  })
                    IconAction(ChittaIcons.Search, if (searching) "Close search" else "Search", onClick = { searching = !searching; if (!searching) vm.setText("")  })
                })
            }
            if (searching && !selecting) {
                SearchBox(
                    value = ui.filter.text, onValueChange = vm::setText,
                    editCount = searchEdit.first, editText = searchEdit.second,
                    modifier = Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.InlineGap),
                )
                ui.problems.forEach { problem ->
                    Text("Not used: $problem", style = ChittaTheme.type.label, color = c.ink, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Space1))
                }
            }
            // While the keyboard is up for the search box, "Search tips", Everyone / Mine and the tabs make room for the list. They fold away and come
            // back together, over about 0.2 s, instead of jumping, and they follow the Animations setting.
            val motion = com.mansaketh.chitta.core.design.theme.rememberMotionEnabled()
            val collapseOptions = searching && !selecting && androidx.compose.foundation.layout.WindowInsets.isImeVisible
            AnimatedVisibility(
                visible = !collapseOptions,
                enter = if (motion) expandVertically(tween(OptionsMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(OptionsMillis, delayMillis = 40)) else EnterTransition.None,
                exit = if (motion) shrinkVertically(tween(OptionsMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(OptionsMillis / 2)) else ExitTransition.None,
            ) {
                Column {
                    if (searching && !selecting) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.End) {
                            QuietLink("Search tips", { picker = "tips" }, underline = true)
                        }
                    }
                    if (ui.lens.active && !selecting) {
                        // Everyone / Mine (PDF p.31): which entries the list and its monthly totals show. Only while sharing is on.
                        val views = listOf(HouseholdSharing.VIEW_EVERYONE, HouseholdSharing.VIEW_MINE)
                        TextTabs(
                            labels = listOf("Everyone", "Mine"), selected = views.indexOf(ui.lens.view).coerceAtLeast(0),
                            onSelect = { vm.setView(views[it]) },
                            modifier = Modifier.padding(horizontal = Dimens.Margin),
                        )
                    }
                    val tabs = LedgerTab.entries
                    TextTabs(
                        labels = listOf("All", "Spending", "Income", "Upcoming"), selected = tabs.indexOf(ui.filter.tab),
                        onSelect = { vm.setTab(tabs[it]) },
                        modifier = Modifier.ownsSideways().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin),
                    )
                    HairlineRule()
                }
            }
            val filtered = ui.filter.accountIds.isNotEmpty() || ui.filter.categoryIds.isNotEmpty() || ui.typedFilters
            // Anything narrowing the list, including plain words: the page then shows one count and total, no day totals (PDF p.18).
            val narrowed = filtered || ui.filter.text.isNotBlank()
            val typedChips = remember(ui.filter.text, ui.categories, ui.allAccounts) { TypedFilterChips.chips(ui.filter.text, ui.categories, ui.allAccounts, now, zone) }
            if (filtered) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin).padding(top = Dimens.InlineGap),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap), verticalArrangement = Arrangement.spacedBy(Dimens.InlineGap),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    ui.filter.accountIds.forEach { id -> FilterChip(accountName(id, ui.allAccounts), onRemove = { vm.setAccounts(ui.filter.accountIds - id) }) }
                    ui.filter.categoryIds.forEach { id -> FilterChip(categoryName(id, ui.categories), onRemove = { vm.setCategories(ui.filter.categoryIds - id) }) }
                    typedChips.forEach { chip ->
                        FilterChip(chip.label, onRemove = {
                            val text = TypedFilterChips.without(ui.filter.text, chip)
                            searchEdit = searchEdit.first + 1 to text
                            vm.setText(text)
                        })
                    }
                    QuietLink("Clear all", vm::clearFilters)
                }
            }
            notice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2)) }

            // A small tick each time a day passes the top while scrolling (not when the list changes by itself).
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val scrollHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            LaunchedEffect(listState) {
                androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect {
                    if (listState.isScrollInProgress) scrollHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                }
            }

            when {
                ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
                ui.totalEntries == 0 -> EmptyState(FeatureIcons.tab(TabKey.Ledger), null, "No transactions yet. Add one.", "Add a transaction", onAdd, Modifier.padding(horizontal = Dimens.Margin))
                ui.matching == 0 -> Text("Nothing matches. Clear the search or filters.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
                else -> LazyColumn(Modifier.fillMaxSize().nestedScroll(hideKeyboardOnScroll).padding(horizontal = Dimens.Margin), state = listState, contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
                    if (narrowed) item(key = "filtered-total") {
                        // The total covers only what matches (it is summed from the shown months, so it follows every filter and tab).
                        FilteredTotal(ui.matching, ui.months.fold(Paise(0L)) { a, m -> a + m.flow.moneyIn }, ui.months.fold(Paise(0L)) { a, m -> a + m.flow.moneyOut })
                    }
                    ui.months.forEach { month ->
                        // Unfiltered: the month with In and Out. Filtered: no header for one month (the page draws none); with several months only the month's name, so the margin dates stay readable.
                        if (!narrowed) stickyHeader(key = "m-${month.month}") {
                            MonthHeader(monthLabel(month.month), month.flow.moneyIn, month.flow.moneyOut)
                        } else if (ui.months.size > 1) stickyHeader(key = "m-${month.month}") {
                            MonthLabel(monthLabel(month.month))
                        }
                        items(month.days, key = { "d-${it.date}" }) { day ->
                            LedgerDay(
                                selecting = selecting, showDayTotal = !narrowed,
                                dayNumber = day.date.dayOfMonth, weekday = day.date.weekdayShort(),
                                entries = day.entries.map { t -> t.toRow(ui, c.inkMuted, accountOnly = narrowed) { id -> c.categoryTint(id) } }, dayTotal = day.net,
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
            "tips" -> BottomOverlay({ picker = null }, handle = false) {
                SheetHeader("Search tips", onClose = { picker = null })
                Spacer(Modifier.height(Dimens.Gap3))
                Text(
                    "Filters: amt>500, cat:food, acc:hdfc, last:30d, type:lent, note:word, from:2026-10-01. A minus leaves one out: -cat:food.",
                    style = ChittaTheme.type.body, color = c.inkMuted,
                )
            }
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

internal fun Transaction.toRow(ui: LedgerUi, muted: androidx.compose.ui.graphics.Color, accountOnly: Boolean = false, tint: (String) -> androidx.compose.ui.graphics.Color): LedgerEntry {
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
        // While filtered the page's second line is the account alone (PDF p.18).
        category = listOfNotNull(label.takeUnless { accountOnly }, accountName(accountId, ui.allAccounts)).joinToString(" · "),
        tags = listOfNotNull("Split".takeIf { splitGroupId != null }, "Possible duplicate".takeIf { id in ui.possibleDuplicates }, "Check".takeIf { com.mansaketh.chitta.core.intake.hasCheckMark(note) }),
        glyph = if (neutral) ChittaIcons.Transfer else (ui.categories.firstOrNull { it.id == categoryId }.let { cat -> com.mansaketh.chitta.ui.manage.CategoryIconCatalog.imageFor(cat?.iconName, cat?.name) } ?: ChittaIcons.Tag),
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
    cm.setPrimaryClip(ClipData.newPlainText("Paddhu Pusthakam entries", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share totals").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The Ledger search field (PDF p.18): a search icon, the query in mono, and a clear button inside one box with a rule border. */
@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, editCount: Int, editText: String, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // The box keeps its own text and cursor. Handing it a plain String that comes back from the ViewModel a moment
    // later resets the cursor to the start after the first letter, so "happy" became "appyh". The only outside
    // change taken in is a clear (the clear button); what is typed always stays as typed.
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value)) }
    LaunchedEffect(value) { if (value.isEmpty() && field.text.isNotEmpty()) field = androidx.compose.ui.text.input.TextFieldValue("") }
    // A removed filter chip rewrites the text from outside, once per removal (the count changes); the cursor goes to the end.
    LaunchedEffect(editCount) { if (editCount > 0) field = androidx.compose.ui.text.input.TextFieldValue(editText, TextRange(editText.length)) }
    Row(
        modifier.fillMaxWidth().heightIn(min = Dimens.ButtonHeight)
            .clip(com.mansaketh.chitta.core.design.tokens.Shapes.Control)
            .background(c.surface)
            .border(Dimens.Hairline, c.rule, com.mansaketh.chitta.core.design.tokens.Shapes.Control)
            .padding(start = Dimens.Gap3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ChittaIcons.Search, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall))
        Spacer(Modifier.width(Dimens.Gap2))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (field.text.isEmpty()) Text("Search", style = ChittaTheme.type.body, color = c.inkMuted)
            BasicTextField(
                value = field, onValueChange = { changed -> val textChanged = changed.text != field.text; field = changed; if (textChanged) onValueChange(changed.text) }, singleLine = true,
                textStyle = ChittaTheme.type.mono.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .semantics { contentDescription = "Search title, note, category or account" },
            )
        }
        if (field.text.isNotEmpty()) IconAction(ChittaIcons.Close, "Clear search", { field = androidx.compose.ui.text.input.TextFieldValue(""); onValueChange("") })
        else Spacer(Modifier.width(Dimens.Gap3))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A quiet text link, muted (PDF p.18: "Search tips" underlined, "Clear all" plain). 48 dp tall to tap, the words draw small. */
@Composable
private fun QuietLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, underline: Boolean = false) {
    val c = ChittaTheme.colors
    Box(modifier.heightIn(min = Dimens.TouchTarget).pressClickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (underline) ChittaTheme.type.label.copy(textDecoration = TextDecoration.Underline) else ChittaTheme.type.chip,
            color = c.inkMuted,
        )
    }
}

/**
 * The line under the chips while the list is narrowed (PDF p.18): the number of matches and what they add up to, then a hairline.
 * Only money out: one figure. Only money in: one green figure. Both: In and Out side by side (Screen 10, answer 4).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilteredTotal(count: Int, moneyIn: Paise, moneyOut: Paise, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = Dimens.Space4, bottom = Dimens.HeadingGap),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Dimens.InlineGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (count == 1) "1 entry" else "$count entries", style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
            if (!moneyIn.isZero && !moneyOut.isZero) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) {
                        Text("In", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.alignByBaseline())
                        AmountText(moneyIn, style = ChittaTheme.type.amountSmall, tone = AmountTone.Income, modifier = Modifier.alignByBaseline())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) {
                        Text("Out", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.alignByBaseline())
                        AmountText(moneyOut, style = ChittaTheme.type.amountSmall, modifier = Modifier.alignByBaseline())
                    }
                }
            } else if (!moneyIn.isZero) {
                AmountText(moneyIn, style = ChittaTheme.type.sheetTitle, tone = AmountTone.Income, fallbackStyles = listOf(ChittaTheme.type.amountSmall))
            } else {
                AmountText(moneyOut, style = ChittaTheme.type.sheetTitle, fallbackStyles = listOf(ChittaTheme.type.amountSmall))
            }
        }
        HairlineRule()
    }
}

/** Sticky month name for a filtered list that spans more than one month. */
@Composable
private fun MonthLabel(month: String, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        Text(month, style = ChittaTheme.type.sectionHeading, color = c.ink, modifier = Modifier.padding(top = Dimens.HeadingGap, bottom = Dimens.InlineGap))
        HairlineRule()
    }
}
