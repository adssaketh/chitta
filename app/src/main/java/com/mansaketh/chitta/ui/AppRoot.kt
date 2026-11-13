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

class AppViewModels(
    val root: RootViewModel, val ledger: LedgerViewModel, val quickAdd: QuickAddViewModel,
    val detail: EntryDetailViewModel, val manage: ManageViewModel, val imports: ImportViewModel,
)

/**
 * Screens are a plain stack of strings ("detail:ID", "accounts", ...) kept across rotation. The master prompt names Navigation
 * Compose, but this app has seven screens and no deep links yet, so a stack is simpler to read and test (DECISIONS D37).
 */
@Composable
fun AppRoot(vms: AppViewModels, extra: (@Composable (onBack: () -> Unit) -> Unit)? = null) {
    val first by vms.root.needsFirstRun.collectAsState()
    val c = ChittaTheme.colors
    Box(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        when (first) {
            null -> Unit
            true -> FirstRun(vms)
            false -> Main(vms, extra)
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
private fun Main(vms: AppViewModels, extra: (@Composable (onBack: () -> Unit) -> Unit)?) {
    var tab by rememberSaveable { mutableStateOf(NavDestination.Ledger.name) }
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val pinned by vms.quickAdd.ui.collectAsState()
    val top = stack.lastOrNull()
    fun push(s: String) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }
    BackHandler(enabled = adding) { adding = false }
    BackHandler(enabled = !adding && stack.isNotEmpty()) { pop() }
    fun openAdd(type: EntryType = EntryType.Expense, pin: com.mansaketh.chitta.core.model.PinnedEntry? = null) { vms.quickAdd.reset(type, pin); adding = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    top == null -> when (NavDestination.valueOf(tab)) {
                        NavDestination.Ledger -> LedgerScreen(vms.ledger, onOpen = { push("detail:$it") }, onAdd = { openAdd() })
                        NavDestination.More -> MoreScreen(onAccounts = { push("accounts") }, onCategories = { push("categories") }, onImport = { push("import") }, onGallery = if (extra != null) ({ push("gallery") }) else null)
                        else -> NotBuilt(NavDestination.valueOf(tab).label)
                    }
                    top.startsWith("detail:") -> EntryDetailScreen(vms.detail, top.removePrefix("detail:"), onBack = ::pop, onHistory = { push("history:" + top.removePrefix("detail:")) })
                    top.startsWith("history:") -> EntryHistoryScreen(vms.detail, top.removePrefix("history:"), onBack = ::pop)
                    top == "accounts" -> AccountsScreen(vms.manage, onBack = ::pop, onTransfer = { openAdd(EntryType.Transfer) })
                    top == "categories" -> CategoriesScreen(vms.manage, onBack = ::pop)
                    top == "import" -> ImportScreen(vms.imports, onBack = { vms.imports.reset(); pop() }, onFinished = { vms.imports.reset(); stack = emptyList() })
                    top == "gallery" -> extra?.invoke(::pop)
                }
                if (top == null && tab != NavDestination.More.name) {
                    AddButton(onClick = { openAdd() }, onLongClick = { addMenu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.Margin))
                }
            }
            ChittaBottomNav(NavDestination.valueOf(tab), { tab = it.name; stack = emptyList() }, Modifier.navigationBarsPadding())
        }
        if (addMenu) ChoiceSheet(
            "Add",
            buildList {
                add(Choice("Expense", "expense")); add(Choice("Income", "income")); add(Choice("Transfer between accounts", "transfer"))
                pinned.pinned.forEach { add(Choice("Pinned · ${it.title}", "pin:${it.id}")) }
            },
            { v ->
                addMenu = false
                when {
                    v == "expense" -> openAdd(EntryType.Expense)
                    v == "income" -> openAdd(EntryType.Income)
                    v == "transfer" -> openAdd(EntryType.Transfer)
                    v.startsWith("pin:") -> pinned.pinned.firstOrNull { it.id == v.removePrefix("pin:") }?.let { openAdd(pin = it) }
                }
            },
            { addMenu = false },
        )
        if (adding) QuickAddSheet(vms.quickAdd, onClose = { adding = false })
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

@Composable
private fun MoreScreen(onAccounts: () -> Unit, onCategories: () -> Unit, onImport: () -> Unit, onGallery: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin)) {
        Text("More", style = ChittaTheme.type.headline, color = ChittaTheme.colors.ink, modifier = Modifier.padding(vertical = Dimens.Gap3))
        ValueRow("Accounts", "", onClick = onAccounts)
        ValueRow("Categories", "", onClick = onCategories)
        ValueRow("Bring over your book", "", onClick = onImport)
        if (onGallery != null) ValueRow("Design gallery", "Debug builds only", onClick = onGallery)
    }
}
