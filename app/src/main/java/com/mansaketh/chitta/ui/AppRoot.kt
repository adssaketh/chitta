package com.mansaketh.chitta.ui

import com.mansaketh.chitta.core.design.kit.MessageBarSlot
import com.mansaketh.chitta.core.design.kit.MessageBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.TopBarMain
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.kit.Section
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
    val cloud: com.mansaketh.chitta.ui.cloud.CloudBackupViewModel,
    val sync: com.mansaketh.chitta.ui.sync.SyncViewModel,
    val household: com.mansaketh.chitta.ui.household.HouseholdViewModel,
)

/**
 * Screens are a plain stack of strings ("detail:ID", "accounts", ...) kept across rotation. The master prompt names Navigation
 * Compose, but this app has seven screens and no deep links yet, so a stack is simpler to read and test (DECISIONS D37).
 */
@Composable
fun AppRoot(vms: AppViewModels, hidden: Boolean = false, onToggleHide: () -> Unit = {}, extra: (@Composable (onBack: () -> Unit) -> Unit)? = null) {
    val first by vms.root.needsFirstRun.collectAsState()
    val c = ChittaTheme.colors
    val firstDay by vms.home.prefs.firstDay.collectAsState()
    CompositionLocalProvider(LocalFirstDay provides firstDay) {
    Box(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        when (first) {
            null -> Unit
            true -> FirstRun(vms)
            false -> Main(vms, hidden, onToggleHide, extra)
        }
        val undo by vms.root.undo.current.collectAsState()
        undo?.let { item ->
            MessageBarSlot {
                MessageBar(item.message, actionLabel = "Undo", onAction = { vms.root.undo.undo() })
            }
        }
        DatePickHost()
    }
    }
}

@Composable
private fun FirstRun(vms: AppViewModels) {
    val c = ChittaTheme.colors
    var importing by rememberSaveable { mutableStateOf(false) }
    if (importing) {
        // Registered first, so the import screen's own Back (step 2 to step 1) takes priority over leaving the import.
        BackHandler { importing = false; vms.imports.reset() }
        ImportScreen(vms.imports, onBack = { importing = false; vms.imports.reset() }, onFinished = { importing = false }, firstRun = true)
        return
    }
    // First run (PDF p.10): round logo, name, one line, then two plain rows with full-width hairlines, and a quiet line at the bottom.
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = Dimens.Margin)) {
            Spacer(Modifier.height(Dimens.LockTop))
            com.mansaketh.chitta.ui.lock.AppIcon(Dimens.LogoFirstRun, shape = androidx.compose.foundation.shape.CircleShape)
            Spacer(Modifier.height(Dimens.Space8))
            Text("Paddhu Pusthakam", style = ChittaTheme.type.displayTitle, color = c.ink)
            Spacer(Modifier.height(Dimens.Gap3))
            Text("A calm account book with honest numbers.", style = ChittaTheme.type.body, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap9 + Dimens.Gap1))
        }
        HairlineRule()
        NavRow("Bring over your book", { importing = true }, Modifier.padding(horizontal = Dimens.Margin), icon = ChittaIcons.Download, sub = "From a Paddhu backup file", rule = false)
        HairlineRule()
        NavRow("Start fresh", { vms.imports.startFresh {} }, Modifier.padding(horizontal = Dimens.Margin), icon = ChittaIcons.Ledger, sub = "An empty book with the usual categories", rule = false)
        HairlineRule()
        Spacer(Modifier.weight(1f))
        Text(
            "Everything stays on this phone.", style = ChittaTheme.type.label, color = c.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.Margin),
        )
    }
}

@Composable
private fun Main(vms: AppViewModels, hidden: Boolean, onToggleHide: () -> Unit, extra: (@Composable (onBack: () -> Unit) -> Unit)?) {
    // A tab is the key of a TabKey, or "more". Blank means "the first tab the person chose" (design: swappable tabs).
    var tabChoice by rememberSaveable { mutableStateOf("") }
    // False when the phone's "Remove animations" is on: tabs and the add button then change at once.
    val motion = com.mansaketh.chitta.core.design.theme.rememberMotionEnabled()
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

    // The add button hides while a screen scrolls down and returns when it scrolls up, so it never covers the last row.
    val fabShown = remember { mutableStateOf(true) }
    val fabScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) fabShown.value = false else if (available.y > 8f) fabShown.value = true
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(tab, top) { fabShown.value = true }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Tabs in bottom-bar order. Switching slides the new tab in from the side you moved towards and fades the old one out.
            val tabOrder = tabLayout.slots.map { it.key } + MORE
            Box(
                Modifier.weight(1f).nestedScroll(fabScroll).pointerInput(tab, top, tabOrder) {
                    // A sideways swipe on a main tab moves to the next or previous tab (see detectTabSwipe).
                    if (top != null) return@pointerInput
                    detectTabSwipe { direction ->
                        val i = tabOrder.indexOf(tab)
                        val target = if (i < 0) null else tabOrder.getOrNull(i - direction)
                        if (target != null) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                            tabChoice = target; stack = emptyList()
                        }
                    }
                },
            ) {
                when {
                    top == null -> AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (!motion) {
                                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            } else {
                                val dir = if (tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)) 1 else -1
                                (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it / 8 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                                    (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it / 8 } + fadeOut(tween(140)))
                            }
                        },
                        label = "tabs",
                    ) { shownTab -> when (shownTab) {
                        TabKey.Home.key -> HomeScreen(
                            vms.home, hidden, onToggleHide,
                            onOpenEntry = { push("detail:$it") }, onOpenBudget = { push("budget:$it") }, onOpenGoal = { push("goal:$it") }, onOpenLoan = { push("loan:$it") },
                            onOpenPlan = { tabChoice = TabKey.Plan.key; planTab = 3 }, onOpenLedger = { tabChoice = TabKey.Ledger.key }, onCustomise = { push("customise") },
                            onOpenReview = { push("month-review") }, onOpenBell = { push("intake") },
                        )
                        TabKey.Ledger.key -> LedgerScreen(vms.ledger, onOpen = { push("detail:$it") }, onAdd = { openAdd() }, onBringIn = { push("intake") })
                        TabKey.Insights.key -> InsightsScreen(vms.insights, onOpenEntry = { push("detail:$it") })
                        TabKey.Accounts.key -> AccountsScreen(vms.manage, onBack = { tabChoice = "" }, onTransfer = { openAdd(EntryType.Transfer) }, onOpenFunds = { push("mutual-funds") }, onOpenAssets = { push("assets") })
                        TabKey.Plan.key -> PlanScreen(vms.plan, planTab, { planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Budgets.key -> PlanScreen(vms.plan, 0, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Goals.key -> PlanScreen(vms.plan, 1, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Loans.key -> PlanScreen(vms.plan, 2, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        TabKey.Bills.key -> PlanScreen(vms.plan, 3, { tabChoice = TabKey.Plan.key; planTab = it }, { push(it) }, { planSheet = it })
                        else -> {
                            val manageUi by vms.manage.ui.collectAsState()
                            val ledgerUi by vms.ledger.ui.collectAsState()
                            MoreScreen(
                                moreOrder,
                                open = { item -> push(item.key) },
                                onGallery = if (extra != null) ({ push("gallery") }) else null,
                                counts = mapOf(MoreItem.Accounts to manageUi.accounts.size, MoreItem.Categories to manageUi.categories.size),
                                entryCount = ledgerUi.totalEntries,
                                sinceMs = ledgerUi.byId.values.minOfOrNull { it.dateMs },
                            )
                        }
                    } }
                    top.startsWith("detail:") -> EntryDetailScreen(vms.detail, top.removePrefix("detail:"), onBack = ::pop, plan = vms.plan)
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
                    top == "cloud-backup" -> com.mansaketh.chitta.ui.cloud.CloudBackupScreen(
                        vms.cloud, onBack = ::pop,
                        // A file from Drive that may be a Paddhu backup goes through the same preview and approval as a file you pick.
                        onPaddhuFile = { f -> vms.imports.pick(android.net.Uri.fromFile(f)); push("import") },
                    )
                    top == "sync" -> com.mansaketh.chitta.ui.sync.SyncScreen(vms.sync, onBack = ::pop)
                    top == "household" -> com.mansaketh.chitta.ui.household.HouseholdScreen(vms.household, onBack = ::pop, onOpen = { push(it) })
                    top == "household-spent" -> com.mansaketh.chitta.ui.household.WhoSpentScreen(vms.household, onBack = ::pop)
                    top == "restore-points" -> com.mansaketh.chitta.ui.settings.RestorePointsScreen(vms.settings, onBack = ::pop)
                    top == "automation" -> com.mansaketh.chitta.ui.automation.AutomationScreen(vms.automation, onBack = ::pop)
                    top == "rules" -> com.mansaketh.chitta.ui.rules.RulesScreen(vms.rules, onBack = ::pop)
                    top == "intake" -> com.mansaketh.chitta.ui.intake.IntakeScreen(vms.intake, onBack = ::pop)
                    top == "csv-import" -> com.mansaketh.chitta.ui.imports.CsvImportScreen(vms.csvImport, onBack = ::pop)
                    top == "gallery" -> extra?.invoke(::pop)
                }
                if (top == null && tab != MORE && tab != TabKey.Accounts.key) {
                    val fabP by animateFloatAsState(if (fabShown.value && OverlayState.open == 0) 1f else 0f, animationSpec = if (motion) androidx.compose.animation.core.spring() else androidx.compose.animation.core.snap(), label = "addButton")
                    if (fabP > 0.01f) {
                        AddButton(
                            onClick = { openAdd() }, onLongClick = { addMenu = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.FabMargin)
                                .graphicsLayer { alpha = fabP; translationY = (1f - fabP) * (size.height + Dimens.FabMargin.toPx()) },
                        )
                    }
                }
            }
            // A sheet covers the bar (design page 14): while one is open the bar steps aside.
            if (OverlayState.open == 0) {
                ChittaBottomNav(
                    items = tabLayout.slots.map { NavItem(it.key, it.label, FeatureIcons.tab(it)) } + NavItem(MORE, "More", ChittaIcons.More),
                    selectedKey = tab, onSelect = { tabChoice = it; stack = emptyList() }, modifier = Modifier.navigationBarsPadding(),
                    onLongPress = { push("customise") },
                )
            }
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

@Composable
private fun MoreScreen(
    order: List<MoreItem>,
    open: (MoreItem) -> Unit,
    onGallery: (() -> Unit)?,
    counts: Map<MoreItem, Int>,
    entryCount: Int,
    sinceMs: Long?,
) {
    // PDF p.35: two headed groups (Money, App), a leading icon on each row, a muted count on the money rows, the logo and a line about the book.
    fun icon(item: MoreItem) = when (item) {
        MoreItem.Accounts -> ChittaIcons.Wallet
        MoreItem.Categories -> ChittaIcons.Tag
        MoreItem.Customise -> ChittaIcons.Sliders
        MoreItem.Import -> ChittaIcons.Download
        MoreItem.Settings -> ChittaIcons.Gear
    }
    val money = setOf(MoreItem.Accounts, MoreItem.Categories)
    Column(Modifier.fillMaxSize()) {
        TopBarMain("More", ChittaIcons.More)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            listOf("Money" to order.filter { it in money }, "App" to order.filter { it !in money }).forEachIndexed { index, (title, items) ->
                if (items.isNotEmpty()) Section(title, first = index == 0) {
                    items.forEach { NavRow(it.label, { open(it) }, icon = icon(it), value = counts[it]?.toString()) }
                }
            }
            if (onGallery != null) NavRow("Design gallery", onGallery, sub = "Debug builds only")
        }
        AppMark(Modifier.align(Alignment.CenterHorizontally).padding(bottom = Dimens.Gap6), entryCount, sinceMs)
    }
}

/**
 * The app's logo and name, quiet, at the bottom of More, with how long the book goes back and how many entries it holds (PDF p.35). The logo is
 * the launcher icon drawn from its two layers (an adaptive icon cannot be loaded as one image), cut to a circle as on the page.
 */
@Composable
private fun AppMark(modifier: Modifier = Modifier, entryCount: Int, sinceMs: Long?) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        com.mansaketh.chitta.ui.lock.AppIcon(Dimens.LogoSmall, shape = androidx.compose.foundation.shape.CircleShape)
        Spacer(Modifier.height(Dimens.Gap2))
        Text("Paddhu Pusthakam", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        if (sinceMs != null && entryCount > 0) {
            val since = java.time.Instant.ofEpochMilli(sinceMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val month = since.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
            val n = java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(entryCount)
            Text("Your book since $month ${since.year} · $n ${if (entryCount == 1) "entry" else "entries"}", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        }
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
