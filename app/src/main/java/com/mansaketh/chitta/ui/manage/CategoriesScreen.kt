package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.clickable
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.TopBarDetail
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
import com.mansaketh.chitta.core.design.icons.KitIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.model.Category
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.mansaketh.chitta.core.design.kit.Glyph
import com.mansaketh.chitta.core.design.kit.Segmented
import com.mansaketh.chitta.ui.NoticeBar
import com.mansaketh.chitta.ui.categoryTint
import com.mansaketh.chitta.ui.*

private sealed interface CatSheet {
    data object None : CatSheet
    data object Add : CatSheet
    data class AddSub(val parent: Category) : CatSheet
    data class Actions(val c: Category) : CatSheet
    data class Rename(val c: Category) : CatSheet
    data class Icon(val c: Category) : CatSheet
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
            TopBarDetail("Categories", onBack = onBack, actions = { IconAction(ChittaIcons.Plus, "Add a category", onClick = { sheet = CatSheet.Add }) })
            Segmented(listOf("Spending", "Income"), if (income) 1 else 0, { income = it == 1 }, Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2))
            HairlineRule()
            if (!ui.loading && mains.isEmpty()) EmptyState(null, null, "No categories here yet.", "Add a category", { sheet = CatSheet.Add }, Modifier.padding(horizontal = Dimens.Margin))
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
                mains.forEach { main ->
                    item(key = main.id) { CategoryRow(main, 0, vm.entriesIn(main)) { sheet = CatSheet.Actions(main) } }
                    items(ui.categories.filter { it.mainCategoryId == main.id }.sortedBy { it.sortOrder }, key = { it.id }) { sub -> CategoryRow(sub, 1, vm.entriesIn(sub)) { sheet = CatSheet.Actions(sub) } }
                }
            }
        }

        notice?.let { NoticeBar(it) { notice = null } }
        when (val s = sheet) {
            CatSheet.Add -> TextSheet(if (income) "New income category" else "New spending category", "Name", "", { vm.addCategory(it, income, null); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.AddSub -> TextSheet("New subcategory of ${s.parent.name}", "Name", "", { vm.addCategory(it, s.parent.income, s.parent); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.Rename -> TextSheet("Rename", "Name", s.c.name, { vm.renameCategory(s.c, it); sheet = CatSheet.None }, { sheet = CatSheet.None })
            is CatSheet.Actions -> ChoiceSheet(
                s.c.name,
                buildList {
                    add(Choice("Rename", "rename", icon = ChittaIcons.Pencil))
                    add(Choice("Choose an icon", "icon", "Search by word, like fuel or rent", icon = ChittaIcons.CatPalette))
                    add(Choice("Move up", "up", icon = KitIcons.ArrowUp)); add(Choice("Move down", "down", icon = KitIcons.ArrowDown))
                    if (s.c.mainCategoryId == null) add(Choice("Add a subcategory", "sub", icon = ChittaIcons.Plus))
                    if (s.c.mainCategoryId == null) add(Choice("Make it a subcategory of…", "convert", icon = ChittaIcons.CatFolder))
                    if (s.c.mainCategoryId == null) add(Choice("Counts as a transfer", "transfer", "On leaves its entries out of spending, income, Net total and budgets. Balances still change.", switch = s.c.countsAsTransfer))
                    add(Choice("Merge into…", "merge", "Moves its entries and titles, then removes it", icon = ChittaIcons.Merge))
                    add(Choice(if (s.c.archived) "Bring back" else "Archive", "archive", if (s.c.archived) null else "Hidden from Quick Add. Entries stay.", icon = if (s.c.archived) ChittaIcons.Undo else ChittaIcons.Archive))
                },
                { v ->
                    when (v) {
                        "rename" -> sheet = CatSheet.Rename(s.c)
                        "icon" -> sheet = CatSheet.Icon(s.c)
                        "up" -> { vm.move(s.c, true); sheet = CatSheet.None }
                        "down" -> { vm.move(s.c, false); sheet = CatSheet.None }
                        "sub" -> sheet = CatSheet.AddSub(s.c)
                        "convert" -> sheet = CatSheet.Convert(s.c)
                        "merge" -> sheet = CatSheet.Merge(s.c)
                        "transfer" -> { vm.setCountsAsTransfer(s.c, !s.c.countsAsTransfer); sheet = CatSheet.None }
                        else -> { vm.archiveCategory(s.c, !s.c.archived); sheet = CatSheet.None }
                    }
                },
                { sheet = CatSheet.None },
            )
            is CatSheet.Icon -> IconPickerSheet(s.c, { key -> vm.setCategoryIcon(s.c, key); sheet = CatSheet.None }, { sheet = CatSheet.None })
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

/** One category (PDF p.38): its glyph in its colour, the name with the entry count under it, a caret. A subcategory has a small colour dot and is indented by the glyph's width plus the gap. */
@Composable
private fun CategoryRow(cat: Category, depth: Int, count: Int, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val tint = c.categoryTint(cat.id)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.RowMinTwoLine).pressClickable(role = Role.Button, onClick = onClick)
                .padding(top = Dimens.RowPadV, bottom = Dimens.RowPadV).then(if (depth == 0) Modifier else Modifier.padding(start = Dimens.SubcategoryIndent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (depth == 0) {
                Glyph(CategoryIconCatalog.imageFor(cat.iconName, cat.name) ?: ChittaIcons.Tag, tint)
            } else {
                Box(Modifier.size(Dimens.SubcategoryDot).clip(CircleShape).background(tint))
            }
            Spacer(Modifier.width(Dimens.Space3))
            Column(Modifier.weight(1f)) {
                Text(cat.name, style = if (depth == 0) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = if (cat.archived) c.inkMuted else c.ink)
                Text("$count ${if (count == 1) "entry" else "entries"}" + (if (cat.archived) " · archived" else "") + (if (cat.countsAsTransfer) " · counts as a transfer" else ""), style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
            }
            Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconCaret))
        }
        HairlineRule()
    }
}
