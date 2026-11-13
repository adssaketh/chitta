package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.ui.*

private sealed interface CatSheet {
    data object None : CatSheet
    data object Add : CatSheet
    data class AddSub(val parent: Category) : CatSheet
    data class Actions(val c: Category) : CatSheet
    data class Rename(val c: Category) : CatSheet
    data class Convert(val c: Category) : CatSheet
    data class Merge(val c: Category) : CatSheet
    data class ConfirmMerge(val from: Category, val to: Category) : CatSheet
}

/** Categories: add, rename, reorder, subcategory, convert, merge, archive. Icon search comes later. */
@Composable
fun CategoriesScreen(vm: ManageViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    var income by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<CatSheet>(CatSheet.None) }
    var notice by remember { mutableStateOf<String?>(null) }
    val mains = ui.categories.filter { it.mainCategoryId == null && it.income == income && it.id != "0" }.sortedWith(compareBy<Category> { it.archived }.thenBy { it.sortOrder })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar("Categories", onBack = onBack, actions = { IconAction(ChittaIcons.Plus, "Add a category", onClick = { sheet = CatSheet.Add }) })
            TextTabs(listOf("Spending", "Income"), if (income) 1 else 0, { income = it == 1 }, Modifier.padding(horizontal = Dimens.Margin))
            HairlineRule()
            notice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2)) }
            if (!ui.loading && mains.isEmpty()) EmptyState("No categories here yet.", "Add a category", { sheet = CatSheet.Add }, Modifier.padding(horizontal = Dimens.Margin))
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
                mains.forEach { main ->
                    item(key = main.id) { CategoryRow(main, 0, vm.entriesIn(main)) { sheet = CatSheet.Actions(main) } }
                    items(ui.categories.filter { it.mainCategoryId == main.id }.sortedBy { it.sortOrder }, key = { it.id }) { sub -> CategoryRow(sub, 1, vm.entriesIn(sub)) { sheet = CatSheet.Actions(sub) } }
                }
            }
        }

        when (val s = sheet) {
            CatSheet.Add -> TextSheet(if (income) "New income category" else "New spending category", "Name", "", { vm.addCategory(it, income, null); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.AddSub -> TextSheet("New subcategory of ${s.parent.name}", "Name", "", { vm.addCategory(it, s.parent.income, s.parent); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.Rename -> TextSheet("Rename", "Name", s.c.name, { vm.renameCategory(s.c, it); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.Actions -> ChoiceSheet(
                s.c.name,
                buildList {
                    add(Choice("Rename", "rename"))
                    add(Choice("Move up", "up")); add(Choice("Move down", "down"))
                    if (s.c.mainCategoryId == null) add(Choice("Add a subcategory", "sub"))
                    if (s.c.mainCategoryId == null) add(Choice("Make it a subcategory of…", "convert"))
                    add(Choice("Merge into…", "merge", "Moves its entries and titles, then removes it"))
                    add(Choice(if (s.c.archived) "Bring back" else "Archive", "archive", if (s.c.archived) null else "Hidden from Quick Add. Entries stay."))
                },
                { v ->
                    when (v) {
                        "rename" -> sheet = CatSheet.Rename(s.c)
                        "up" -> { vm.move(s.c, true); sheet = CatSheet.None }
                        "down" -> { vm.move(s.c, false); sheet = CatSheet.None }
                        "sub" -> sheet = CatSheet.AddSub(s.c)
                        "convert" -> sheet = CatSheet.Convert(s.c)
                        "merge" -> sheet = CatSheet.Merge(s.c)
                        else -> { vm.archiveCategory(s.c, !s.c.archived); sheet = CatSheet.None }
                    }
                },
                { sheet = CatSheet.None },
            )
            is CatSheet.Convert -> ChoiceSheet("Make ${s.c.name} a subcategory of", ui.categories.filter { it.mainCategoryId == null && it.id != s.c.id && it.income == s.c.income && it.id != "0" }.map { Choice(it.name, it) },
                { parent -> notice = vm.convertToSub(s.c, parent); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.Merge -> ChoiceSheet("Merge ${s.c.name} into", ui.categories.filter { it.id != s.c.id && it.income == s.c.income && it.id != "0" && it.mainCategoryId != s.c.id }.map { Choice(it.name, it) },
                { to -> sheet = CatSheet.ConfirmMerge(s.c, to) }, { sheet = CatSheet.None })
            is CatSheet.ConfirmMerge -> ConfirmSheet(
                "Merge?",
                listOf("${vm.entriesIn(s.from)} entries from ${s.from.name} move to ${s.to.name}, and ${s.from.name} is removed.", "The entry changes can be undone for 10 seconds. The removed category cannot come back by Undo."),
                "Merge", { vm.merge(s.from, s.to); sheet = CatSheet.None }, { sheet = CatSheet.None },
            )
            CatSheet.None -> Unit
        }
    }
}

@Composable
private fun CategoryRow(cat: Category, depth: Int, count: Int, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(start = (depth * 24).dp, top = Dimens.Gap2, bottom = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(cat.name, style = ChittaTheme.type.title, color = if (cat.archived) c.inkMuted else c.ink)
            Text("$count ${if (count == 1) "entry" else "entries"}" + if (cat.archived) " · archived" else "", style = ChittaTheme.type.label, color = c.inkMuted)
        }
    }
    HairlineRule()
}
