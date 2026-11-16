package com.mansaketh.chitta.ui

import com.mansaketh.chitta.core.design.kit.MessageBarSlot
import com.mansaketh.chitta.core.design.kit.MessageBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.Section
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
        ImportScreen(vms.imports, onBack = { importing = false; vms.imports.reset() }, onFinished = { importing = false }, firstRun = true)
        BackHandler { importing = false; vms.imports.reset() }
        return
    }
    // PDF p.10, screen 02: the icon above the name, the two ways in as plain rows with a caret. "Everything stays on this phone" is not in the app, so it is not built.
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin).navigationBarsPadding(), verticalArrangement = Arrangement.Center) {
        com.mansaketh.chitta.ui.lock.AppIcon(Dimens.LogoLarge)
        Spacer(Modifier.height(Dimens.Gap4))
        Text("Paddhu Pusthakam", style = ChittaTheme.type.headline, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        Text("Start with an empty book, or bring over your book from Paddhu Pusthakam.", style = ChittaTheme.type.body, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        HairlineRule()
        NavRow("Bring over my book", { importing = true }, icon = ChittaIcons.Download)
        NavRow("Start fresh", { vms.imports.startFresh {} }, icon = ChittaIcons.Ledger, sub = "Start fresh makes a Cash account and a few categories you can rename.")
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
                // Opening a detail screen slides it in from the end and fades (0.25 s); going back reverses that. Off when the app's Animations
                // switch is off or the phone's "Remove animations" is on (the same flag the tabs use). The pair carries the depth so back is told from forward.
                AnimatedContent(
                    targetState = top to stack.size,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (!motion) {
                            androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        } else if (targetState.second > initialState.second) {
                            (slideInHorizontally(com.mansaketh.chitta.core.design.theme.Motion.expandTween()) { it / 3 } + fadeIn(com.mansaketh.chitta.core.design.theme.Motion.expandTween())) togetherWith
                                fadeOut(com.mansaketh.chitta.core.design.theme.Motion.expandTween())
                        } else {
                            fadeIn(com.mansaketh.chitta.core.design.theme.Motion.expandTween()) togetherWith
                                (slideOutHorizontally(com.mansaketh.chitta.core.design.theme.Motion.expandTween()) { it / 3 } + fadeOut(com.mansaketh.chitta.core.design.theme.Motion.expandTween()))
                        }
                    },
                    label = "screens",
                ) { (shown, _) ->
                    when {
                    shown == null -> AnimatedContent(
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
                    } }
                    shown.startsWith("detail:") -> EntryDetailScreen(vms.detail, shown.removePrefix("detail:"), onBack = ::pop, onHistory = { push("history:" + shown.removePrefix("detail:")) }, plan = vms.plan)
                    shown.startsWith("budget:") -> BudgetDetailScreen(vms.plan, shown.removePrefix("budget:"), ::pop, { planSheet = "edit-budget:" + shown.removePrefix("budget:") })
                    shown.startsWith("goal:") -> GoalDetailScreen(vms.plan, shown.removePrefix("goal:"), ::pop, { planSheet = "edit-goal:" + shown.removePrefix("goal:") }, { push("detail:$it") })
                    shown.startsWith("loan:") -> LoanDetailScreen(vms.plan, shown.removePrefix("loan:"), ::pop, { planSheet = "edit-loan:" + shown.removePrefix("loan:") }, { push("detail:$it") })
                    shown.startsWith("history:") -> EntryHistoryScreen(vms.detail, shown.removePrefix("history:"), onBack = ::pop)
                    shown == "customise" -> CustomiseScreen(vms.home, onBack = ::pop)
                    shown == "month-review" -> com.mansaketh.chitta.ui.insights.MonthReviewScreen(vms.insights, onBack = ::pop)
                    shown == "accounts" -> AccountsScreen(vms.manage, onBack = ::pop, onTransfer = { openAdd(EntryType.Transfer) }, onOpenFunds = { push("mutual-funds") }, onOpenAssets = { push("assets") })
                    shown == "assets" -> com.mansaketh.chitta.ui.assets.AssetsScreen(vms.assets, onBack = ::pop)
                    shown == "mutual-funds" -> com.mansaketh.chitta.ui.manage.MutualFundsScreen(vms.manage, onBack = ::pop)
                    shown == "categories" -> CategoriesScreen(vms.manage, onBack = ::pop)
                    shown == "import" -> ImportScreen(vms.imports, onBack = { vms.imports.reset(); pop() }, onFinished = { vms.imports.reset(); stack = emptyList() })
                    shown == "settings" -> com.mansaketh.chitta.ui.settings.SettingsScreen(vms.settings, onBack = ::pop, onOpen = { push(it) })
                    shown == "cloud-backup" -> com.mansaketh.chitta.ui.cloud.CloudBackupScreen(
                        vms.cloud, onBack = ::pop,
                        // A file from Drive that may be a Paddhu backup goes through the same preview and approval as a file you pick.
                        onPaddhuFile = { f -> vms.imports.pick(android.net.Uri.fromFile(f)); push("import") },
                    )
                    shown == "sync" -> com.mansaketh.chitta.ui.sync.SyncScreen(vms.sync, onBack = ::pop)
                    shown == "household" -> com.mansaketh.chitta.ui.household.HouseholdScreen(vms.household, onBack = ::pop, onOpen = { push(it) })
                    shown == "household-spent" -> com.mansaketh.chitta.ui.household.WhoSpentScreen(vms.household, onBack = ::pop)
                    shown == "restore-points" -> com.mansaketh.chitta.ui.settings.RestorePointsScreen(vms.settings, onBack = ::pop)
                    shown == "automation" -> com.mansaketh.chitta.ui.automation.AutomationScreen(vms.automation, onBack = ::pop)
                    shown == "rules" -> com.mansaketh.chitta.ui.rules.RulesScreen(vms.rules, onBack = ::pop)
                    shown == "intake" -> com.mansaketh.chitta.ui.intake.IntakeScreen(vms.intake, onBack = ::pop)
                    shown == "csv-import" -> com.mansaketh.chitta.ui.imports.CsvImportScreen(vms.csvImport, onBack = ::pop)
                    shown == "gallery" -> extra?.invoke(::pop)
                    else -> Unit
                }
                }
                if (top == null && tab != MORE && tab != TabKey.Accounts.key) {
                    val fabP by animateFloatAsState(if (fabShown.value) 1f else 0f, animationSpec = if (motion) androidx.compose.animation.core.spring() else androidx.compose.animation.core.snap(), label = "addButton")
                    if (fabP > 0.01f) {
                        AddButton(
                            onClick = { openAdd() }, onLongClick = { addMenu = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.FabMargin)
                                .graphicsLayer { alpha = fabP; translationY = (1f - fabP) * (size.height + Dimens.FabMargin.toPx()) },
                        )
                    }
                }
            }
            ChittaBottomNav(
                items = tabLayout.slots.map { NavItem(it.key, it.label, FeatureIcons.tab(it)) } + NavItem(MORE, "More", ChittaIcons.More),
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

@Composable
private fun MoreScreen(order: List<MoreItem>, open: (MoreItem) -> Unit, onGallery: (() -> Unit)?) {
    // PDF p.35, screen 27: two headed groups. The saved order still decides the order inside each group. The counts (6, 41) and the
    // "book since" line of the drawing are not in the app, so they are not built.
    val money = order.filter { it == MoreItem.Accounts || it == MoreItem.Categories }
    val app = order.filter { it !in money }
    Column(Modifier.fillMaxSize()) {
        TopBarMain("More", ChittaIcons.More)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            Section("Money", first = true) { money.forEach { NavRow(it.label, { open(it) }, icon = FeatureIcons.more(it)) } }
            Section("App") {
                app.forEach { NavRow(it.label, { open(it) }, icon = FeatureIcons.more(it)) }
                if (onGallery != null) NavRow("Design gallery", onGallery, sub = "Debug builds only", icon = ChittaIcons.CatPalette)
            }
        }
        AppMark(Modifier.align(Alignment.CenterHorizontally).padding(bottom = Dimens.Gap6))
    }
}

/**
 * The app's logo and name, quiet, at the bottom of More. The logo is the launcher icon drawn from its two layers (an adaptive icon
 * cannot be loaded as one image), cut to a rounded square the way a launcher shows it.
 */
@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        com.mansaketh.chitta.ui.lock.AppIcon(Dimens.LogoSmall)
        Spacer(Modifier.height(Dimens.Gap2))
        Text("Paddhu Pusthakam", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
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
