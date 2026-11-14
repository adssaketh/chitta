package com.mansaketh.chitta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.EntryType
import com.mansaketh.chitta.core.domain.MoreItem
import com.mansaketh.chitta.core.domain.TabKey
import com.mansaketh.chitta.ui.home.CustomiseScreen
import com.mansaketh.chitta.ui.home.HomeScreen
import com.mansaketh.chitta.ui.home.HomeViewModel
import com.mansaketh.chitta.ui.insights.InsightsScreen
import com.mansaketh.chitta.ui.insights.InsightsViewModel
import com.mansaketh.chitta.ui.add.QuickAddSheet
import com.mansaketh.chitta.ui.add.QuickAddViewModel
import com.mansaketh.chitta.ui.detail.EntryDetailScreen
import com.mansaketh.chitta.ui.detail.EntryDetailViewModel
import com.mansaketh.chitta.ui.detail.EntryHistoryScreen
import com.mansaketh.chitta.ui.imports.ImportScreen
import com.mansaketh.chitta.ui.imports.ImportViewModel
import com.mansaketh.chitta.ui.ledger.LedgerScreen
import com.mansaketh.chitta.ui.ledger.LedgerViewModel
import com.mansaketh.chitta.ui.manage.AccountsScreen
import com.mansaketh.chitta.ui.manage.CategoriesScreen
import com.mansaketh.chitta.ui.manage.ManageViewModel
import com.mansaketh.chitta.ui.plan.*
import com.mansaketh.chitta.core.model.Objective

class AppViewModels(
    val root: RootViewModel, val ledger: LedgerViewModel, val quickAdd: QuickAddViewModel,
    val detail: EntryDetailViewModel, val manage: ManageViewModel, val imports: ImportViewModel, val plan: PlanViewModel,
    val home: HomeViewModel, val insights: InsightsViewModel,
    val settings: com.mansaketh.chitta.ui.settings.SettingsViewModel, val csvImport: com.mansaketh.chitta.ui.imports.CsvImportViewModel,
    val intake: com.mansaketh.chitta.ui.intake.IntakeViewModel,
    val rules: com.mansaketh.chitta.ui.rules.RulesViewModel,
    val automation: com.mansaketh.chitta.ui.automation.AutomationViewModel,
    val assets: com.mansaketh.chitta.ui.assets.AssetsViewModel,
)

/**
 * Screens are a plain stack of strings ("detail:ID", "accounts", ...) kept across rotation. The master prompt names Navigation
 * Compose, but this app has seven screens and no deep links yet, so a stack is simpler to read and test (DECISIONS D37).
 */
@Composable
fun AppRoot(vms: AppViewModels, hidden: Boolean = false, onToggleHide: () -> Unit = {}, extra: (@Composable (onBack: () -> Unit) -> Unit)? = null) {
    val first by vms.root.needsFirstRun.collectAsState()
    val c = ChittaTheme.colors
    Box(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        when (first) {
            null -> Unit
            true -> FirstRun(vms)
            false -> Main(vms, hidden, onToggleHide, extra)
        }
        val undo by vms.root.undo.current.collectAsState()
        undo?.let { item ->
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = Dimens.Margin).padding(bottom = if (first == false) 72.dp else Dimens.Gap4)) {
                UndoBar(item.message, onUndo = { vms.root.undo.undo() })
            }
        }
    }
}

@Composable
private fun FirstRun(vms: AppViewModels) {
    val c = ChittaTheme.colors
    var importing by rememberSaveable { mutableStateOf(false) }
    if (importing) {
        ImportScreen(vms.imports, onBack = { importing = false; vms.imports.reset() }, onFinished = { importing = false }, firstRun = true)
        BackHandler { importing = false; vms.imports.reset() }
        return
    }
    Column(Modifier.fillMaxSize().padding(Dimens.Margin).navigationBarsPadding(), verticalArrangement = Arrangement.Center) {
        Text("Chitta", style = ChittaTheme.type.headline, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        Text("Start with an empty book, or bring over your book from Paddhu Pusthakam.", style = ChittaTheme.type.body, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Bring over my book", { importing = true })
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Start fresh", { vms.imports.startFresh {} }, style = ButtonStyle.Quiet)
        Text("Start fresh makes a Cash account and a few categories you can rename.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
    }
}

@Composable
private fun Main(vms: AppViewModels, hidden: Boolean, onToggleHide: () -> Unit, extra: (@Composable (onBack: () -> Unit) -> Unit)?) {
    // A tab is the key of a TabKey, or "more". Blank means "the first tab the person chose" (design: swappable tabs).
    var tabChoice by rememberSaveable { mutableStateOf("") }
    val tabLayout by vms.home.prefs.tabLayout.collectAsState()
    val moreOrder by vms.home.prefs.moreOrder.collectAsState()
    val tab = tabChoice.takeIf { it == MORE || tabLayout.slots.any { s -> s.key == it } } ?: tabLayout.first.key
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var planTab by rememberSaveable { mutableStateOf(0) }
    // "new-budget", "edit-goal:ID", ... the form open over the Plan screens
    var planSheet by rememberSaveable { mutableStateOf<String?>(null) }
    val pinned by vms.quickAdd.ui.collectAsState()
    val top = stack.lastOrNull()
    fun push(s: String) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }
    // The share receiver (or a message that arrived) asks for the bank messages screen.
    val intakeOpen by vms.intake.signals.open.collectAsState()
    LaunchedEffect(intakeOpen) { if (intakeOpen != null && stack.lastOrNull() != "intake") stack = stack + "intake" }
    // An app link (chitta://add?...) opens Quick Add pre-filled; the person checks it and saves.
    val link by vms.intake.signals.link.collectAsState()
    LaunchedEffect(link) {
        val params = link ?: return@LaunchedEffect
        vms.intake.signals.consumeLink()
        vms.quickAdd.applyLink(params) { adding = true }
    }
    // A launcher shortcut (chitta://open?tab=ledger) shows a bottom tab. Ignored when the person has swapped that tab out of the bar.
    val tabRequest by vms.intake.signals.tab.collectAsState()
    LaunchedEffect(tabRequest) {
        val key = tabRequest ?: return@LaunchedEffect
        vms.intake.signals.consumeTab()
        if (tabLayout.slots.any { it.key == key }) { stack = emptyList(); adding = false; tabChoice = key }
    }
    BackHandler(enabled = adding) { adding = false }
    BackHandler(enabled = !adding && stack.isNotEmpty()) { pop() }
    fun openAdd(type: EntryType = EntryType.Expense, pin: com.mansaketh.chitta.core.model.PinnedEntry? = null) { vms.quickAdd.reset(type, pin); adding = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    top == null -> when (tab) {
                        TabKey.Home.key -> HomeScreen(
                            vms.home, hidden, onToggleHide,
                            onOpenEntry = { push("detail:$it") }, onOpenBudget = { push("budget:$it") }, onOpenGoal = { push("goal:$it") }, onOpenLoan = { push("loan:$it") },
                            onOpenPlan = { tabChoice = TabKey.Plan.key; planTab = 3 }, onOpenLedger = { tabChoice = TabKey.Ledger.key }, onCustomise = { push("customise") },
                            onOpenReview = { push("month-review") },
                        )
                        TabKey.Ledger.key -> LedgerScreen(vms.ledger, onOpen = { push("detail:$it") }, onAdd = { openAdd() })
                        TabKey.Insights.key -> InsightsScreen(vms.insights, onOpenEntry = { push("detail:$it") })
                        TabKey.Accounts.key -> AccountsScreen(vms.manage, onBack = { tabChoice = "" }, onTransfer = { openAdd(EntryType.Transfer) }, onOpenFunds = { push("mutual-funds") }, onOpenAssets = { push("assets") })
                        TabKey.Plan.key -> PlanScreen(vms.plan, planTab, { planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Budgets.key -> PlanScreen(vms.plan, 0, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Goals.key -> PlanScreen(vms.plan, 1, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Loans.key -> PlanScreen(vms.plan, 2, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Bills.key -> PlanScreen(vms.plan, 3, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        else -> MoreScreen(
                            moreOrder,
                            open = { item -> push(item.key) },
                            onGallery = if (extra != null) ({ push("gallery") }) else null,
                        )
                    }
                    top.startsWith("detail:") -> EntryDetailScreen(vms.detail, top.removePrefix("detail:"), onBack = ::pop, onHistory = { push("history:" + top.removePrefix("detail:")) }, plan = vms.plan)
                    top.startsWith("budget:") -> BudgetDetailScreen(vms.plan, top.removePrefix("budget:"), ::pop, { planSheet = "edit-budget:" + top.removePrefix("budget:") })
                    top.startsWith("goal:") -> GoalDetailScreen(vms.plan, top.removePrefix("goal:"), ::pop, { planSheet = "edit-goal:" + top.removePrefix("goal:") }, { push("detail:$it") })
                    top.startsWith("loan:") -> LoanDetailScreen(vms.plan, top.removePrefix("loan:"), ::pop, { planSheet = "edit-loan:" + top.removePrefix("loan:") }, { push("detail:$it") })
                    top.startsWith("history:") -> EntryHistoryScreen(vms.detail, top.removePrefix("history:"), onBack = ::pop)
                    top == "customise" -> CustomiseScreen(vms.home, onBack = ::pop)
                    top == "month-review" -> com.mansaketh.chitta.ui.insights.MonthReviewScreen(vms.insights, onBack = ::pop)
                    top == "accounts" -> AccountsScreen(vms.manage, onBack = ::pop, onTransfer = { openAdd(EntryType.Transfer) }, onOpenFunds = { push("mutual-funds") }, onOpenAssets = { push("assets") })
                    top == "assets" -> com.mansaketh.chitta.ui.assets.AssetsScreen(vms.assets, onBack = ::pop)
                    top == "mutual-funds" -> com.mansaketh.chitta.ui.manage.MutualFundsScreen(vms.manage, onBack = ::pop)
                    top == "categories" -> CategoriesScreen(vms.manage, onBack = ::pop)
                    top == "import" -> ImportScreen(vms.imports, onBack = { vms.imports.reset(); pop() }, onFinished = { vms.imports.reset(); stack = emptyList() })
                    top == "settings" -> com.mansaketh.chitta.ui.settings.SettingsScreen(vms.settings, onBack = ::pop, onOpen = { push(it) })
                    top == "restore-points" -> com.mansaketh.chitta.ui.settings.RestorePointsScreen(vms.settings, onBack = ::pop)
                    top == "automation" -> com.mansaketh.chitta.ui.automation.AutomationScreen(vms.automation, onBack = ::pop)
                    top == "rules" -> com.mansaketh.chitta.ui.rules.RulesScreen(vms.rules, onBack = ::pop)
                    top == "intake" -> com.mansaketh.chitta.ui.intake.IntakeScreen(vms.intake, onBack = ::pop)
                    top == "csv-import" -> com.mansaketh.chitta.ui.imports.CsvImportScreen(vms.csvImport, onBack = ::pop)
                    top == "gallery" -> extra?.invoke(::pop)
                }
                if (top == null && tab != MORE && tab != TabKey.Accounts.key) {
                    AddButton(onClick = { openAdd() }, onLongClick = { addMenu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.Margin))
                }
            }
            ChittaBottomNav(
                items = tabLayout.slots.map { NavItem(it.key, it.label, tabIcon(it)) } + NavItem(MORE, "More", ChittaIcons.More),
                selectedKey = tab, onSelect = { tabChoice = it; stack = emptyList() }, modifier = Modifier.navigationBarsPadding(),
                onLongPress = { push("customise") },
            )
        }
        if (addMenu) ChoiceSheet(
            "Add",
            buildList {
                add(Choice("Expense", "expense")); add(Choice("Income", "income")); add(Choice("Transfer between accounts", "transfer"))
                add(Choice("New bill", "new-bill")); add(Choice("New budget", "new-budget")); add(Choice("New goal", "new-goal")); add(Choice("New loan", "new-loan"))
                pinned.pinned.forEach { add(Choice("Pinned · ${it.title}", "pin:${it.id}")) }
            },
            { v ->
                addMenu = false
                when {
                    v == "expense" -> openAdd(EntryType.Expense)
                    v == "income" -> openAdd(EntryType.Income)
                    v == "transfer" -> openAdd(EntryType.Transfer)
                    v.startsWith("new-") -> { tabChoice = TabKey.Plan.key; stack = emptyList(); planTab = when (v) { "new-budget" -> 0; "new-goal" -> 1; "new-loan" -> 2; "new-fund" -> 5; else -> 3 }; planSheet = v }
                    v.startsWith("pin:") -> pinned.pinned.firstOrNull { it.id == v.removePrefix("pin:") }?.let { openAdd(pin = it) }
                }
            },
            { addMenu = false },
        )
        if (adding) QuickAddSheet(vms.quickAdd, onClose = { adding = false })
        planSheet?.let { PlanSheetHost(vms.plan, it) { planSheet = null } }
    }
}

@Composable
private fun NotBuilt(name: String) {
    Column(Modifier.fillMaxSize().padding(Dimens.Margin)) {
        Text(name, style = ChittaTheme.type.headline, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        Text("This screen is not built yet. It comes in a later phase.", style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
    }
}

private const val MORE = "more"

private fun tabIcon(k: TabKey) = when (k) {
    TabKey.Home -> ChittaIcons.Home
    TabKey.Ledger -> ChittaIcons.Ledger
    TabKey.Plan -> ChittaIcons.Plan
    TabKey.Insights -> ChittaIcons.Insights
    TabKey.Accounts -> ChittaIcons.Bank
    TabKey.Budgets -> ChittaIcons.Wallet
    TabKey.Goals -> ChittaIcons.Check
    TabKey.Loans -> ChittaIcons.Users
    TabKey.Bills -> ChittaIcons.Receipt
}

@Composable
private fun MoreScreen(order: List<MoreItem>, open: (MoreItem) -> Unit, onGallery: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin)) {
        Text("More", style = ChittaTheme.type.headline, color = ChittaTheme.colors.ink, modifier = Modifier.padding(vertical = Dimens.Gap3))
        order.forEach { ValueRow(it.label, "", onClick = { open(it) }) }
        if (onGallery != null) ValueRow("Design gallery", "Debug builds only", onClick = onGallery)
    }
}

/** The Plan forms (budget, goal, loan, bill) open over whatever screen asked for them. [which] is "new-x" or "edit-x:ID". */
@Composable
private fun PlanSheetHost(vm: PlanViewModel, which: String, close: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val kind = which.substringAfter("-").substringBefore(":")
    val id = which.substringAfter(":", "")
    when (kind) {
        "budget" -> BudgetSheet(ui.budgets.firstOrNull { it.id == id }, ui, { f -> vm.saveBudget(ui.budgets.firstOrNull { it.id == id }, f); close() }, close)
        "fund" -> FundSheet(
            ui.sinking.firstOrNull { it.id == id }, ui,
            { n, t, s, d, c -> vm.saveFund(ui.sinking.firstOrNull { it.id == id }, n, t, s, d, c); close() },
            { vm.deleteFund(id); close() }, close,
        )
        "goal" -> GoalSheet(ui.objectives.firstOrNull { it.id == id }, ui.zone, ui.nowMs, { n, t, inc, end -> vm.saveGoal(ui.objectives.firstOrNull { it.id == id }, n, t, inc, end); close() }, close)
        "loan" -> {
            val existing: Objective? = ui.objectives.firstOrNull { it.id == id }
            LoanSheet(existing, ui, { n, lent, initial, off, acc, problem -> vm.addLoan(n, lent, initial, off, acc, problem).also { if (it) close() } }, { n, off -> existing?.let { vm.renameLoan(it, n, off) }; close() }, { n, lent, terms -> vm.saveTerms(n, lent, terms); close() }, close)
        }
        "bill" -> BillSheet(ui.entries.firstOrNull { it.id == id }, ui, { title, amount, income, acc, cat, due, rep, len, end, notify, sub ->
            vm.saveBill(ui.entries.firstOrNull { it.id == id }, title, amount, income, acc, cat, due, rep, len, end, notify, sub); close()
        }, close)
    }
}
