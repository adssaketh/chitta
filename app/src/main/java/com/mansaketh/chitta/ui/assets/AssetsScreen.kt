package com.mansaketh.chitta.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.ManualAssets
import com.mansaketh.chitta.core.model.AssetKind
import com.mansaketh.chitta.core.model.AssetValue
import com.mansaketh.chitta.core.model.ManualAsset
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.*
import com.mansaketh.chitta.ui.manage.maskedRupees
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.plan.pickDate

private sealed interface AssetSheet {
    data object None : AssetSheet
    data class New(val kind: AssetKind) : AssetSheet
    data class Actions(val a: ManualAsset) : AssetSheet
    data class NewValue(val a: ManualAsset) : AssetSheet
    data class Edit(val a: ManualAsset) : AssetSheet
    data class History(val a: ManualAsset) : AssetSheet
    data class RemoveValue(val a: ManualAsset, val v: AssetValue) : AssetSheet
    data class Delete(val a: ManualAsset) : AssetSheet
}

/**
 * Gold, EPF and PPF (Phase 8i). Values are typed in: each update adds a dated value and the older ones stay as history. Net worth counts
 * the latest value of each. There is no PDF page for this screen: it is built from the Accounts list rows and sheets (PDF p.25) and the
 * Mutual funds rows (p.26). At most two STALE stamps are drawn (master section 3); further old values say their age in words.
 */
@Composable
fun AssetsScreen(vm: AssetsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<AssetSheet>(AssetSheet.None) }
    // Which rows get a STALE stamp: the first two old ones on the screen, in the order shown.
    var stamps = 0

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar("Gold, EPF and PPF", onBack = onBack)
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
                item {
                    Text(
                        "Type what each is worth and the date. Each update is kept as history. Nothing here moves money in your accounts.",
                        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
                    )
                }
                if (ui.loading) item { LoadingPlaceholder() }
                AssetKind.entries.forEach { kind ->
                    val rows = ui.assets.filter { it.kind == kind }
                    val total = ManualAssets.total(kind, ui.assets, ui.values, ui.nowMs)
                    item(key = "h-" + kind.name) {
                        SectionHeader(kind.label, action = "Add", onAction = { sheet = AssetSheet.New(kind) })
                        if (rows.isEmpty() && !ui.loading) Text("No ${kind.label} yet.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2))
                    }
                    rows.forEach { a ->
                        val values = ui.valuesOf(a.id)
                        val status = ManualAssets.status(values)
                        val stale = status != null && ManualAssets.isStale(kind, status.latest.asOfMs, ui.nowMs, ui.zone)
                        val stamp = stale && stamps < 2
                        if (stamp) stamps++
                        item(key = "a-" + a.id) { AssetRow(a, status?.latest, status?.change, stale, stamp, ui) { sheet = AssetSheet.Actions(a) } }
                    }
                    if (rows.isNotEmpty()) item(key = "t-" + kind.name) {
                        ValueRow("Total ${kind.label}", maskedRupees(total.value))
                        ValueRow("Count ${kind.label} in Net worth", if (ui.counts(kind)) "On" else "Off", onClick = { vm.setCounted(kind, !ui.counts(kind)) })
                        Text(
                            if (ui.counts(kind)) "Counted at the latest value of each, the oldest dated ${total.oldestAsOfMs?.let { dateText(it, ui.zone) } ?: "-"}." else "Off, so Net worth leaves ${kind.label} out.",
                            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
                        )
                    }
                    item(key = "s-" + kind.name) { Spacer(Modifier.height(Dimens.SectionGap)) }
                }
            }
        }

        when (val s = sheet) {
            AssetSheet.None -> Unit
            is AssetSheet.New -> AssetFormSheet(
                title = "New ${s.kind.label}", ui = ui, askName = true, askValue = true,
                onSave = { name, value, asOf, note -> vm.add(s.kind, name, value, asOf, note) }, onClose = { sheet = AssetSheet.None },
            )
            is AssetSheet.Actions -> ChoiceSheet(
                s.a.name,
                listOf(
                    Choice("Update value", "value", "Adds a dated value; the old ones stay as history"),
                    Choice("History", "history"),
                    Choice("Rename or edit note", "edit"),
                    Choice("Delete", "delete", "Removes it and its history. You can undo for 10 seconds."),
                ),
                { v -> sheet = when (v) { "value" -> AssetSheet.NewValue(s.a); "history" -> AssetSheet.History(s.a); "edit" -> AssetSheet.Edit(s.a); else -> AssetSheet.Delete(s.a) } },
                { sheet = AssetSheet.None },
            )
            is AssetSheet.NewValue -> AssetFormSheet(
                title = "Update ${s.a.name}", ui = ui, askName = false, askValue = true,
                onSave = { _, value, asOf, _ -> vm.addValue(s.a, value, asOf) }, onClose = { sheet = AssetSheet.None },
            )
            is AssetSheet.Edit -> AssetFormSheet(
                title = "Edit ${s.a.name}", ui = ui, askName = true, askValue = false, initialName = s.a.name, initialNote = s.a.note,
                onSave = { name, _, _, note -> vm.edit(s.a, name, note) }, onClose = { sheet = AssetSheet.None },
            )
            is AssetSheet.History -> {
                val values = ui.valuesOf(s.a.id).sortedWith(compareByDescending<AssetValue> { it.asOfMs }.thenByDescending { it.recordedAtMs })
                ChoiceSheet(
                    "History · ${s.a.name}",
                    values.map { Choice(dateText(it.asOfMs, ui.zone) + "  " + maskedPaise(it.valuePaise), it) },
                    { v -> sheet = AssetSheet.RemoveValue(s.a, v) }, { sheet = AssetSheet.None }, emptyText = "No values yet.",
                )
            }
            is AssetSheet.RemoveValue -> ConfirmSheet(
                "Remove this value?",
                listOf("${dateText(s.v.asOfMs, ui.zone)}: ${maskedPaise(s.v.valuePaise)}.", "The other values stay. You can undo for 10 seconds."),
                "Remove", { vm.removeValue(s.v); sheet = AssetSheet.None }, { sheet = AssetSheet.History(s.a) },
            )
            is AssetSheet.Delete -> ConfirmSheet(
                "Delete ${s.a.name}?",
                listOf("This removes it and all ${ui.valuesOf(s.a.id).size} of its values, and Net worth stops counting it.", "You can undo for 10 seconds."),
                "Delete", { vm.delete(s.a); sheet = AssetSheet.None }, { sheet = AssetSheet.None },
            )
        }
    }
}

@Composable
private fun maskedPaise(p: Long): String = maskedRupees(Paise(p))

@Composable
private fun AssetRow(a: ManualAsset, latest: AssetValue?, change: Paise?, stale: Boolean, stamp: Boolean, ui: AssetsUi, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = ChittaTheme.type.title, color = c.ink)
            if (latest == null) {
                Text("No value yet", style = ChittaTheme.type.label, color = c.inkMuted)
            } else {
                val age = ManualAssets.daysOld(latest.asOfMs, ui.nowMs, ui.zone)
                Text(
                    "as of ${dateText(latest.asOfMs, ui.zone)}" + (if (stale && !stamp) " · $age days old" else "") + changeText(change),
                    style = ChittaTheme.type.label, color = c.inkMuted,
                )
            }
            if (a.note.isNotBlank()) Text(a.note, style = ChittaTheme.type.label, color = c.inkMuted)
        }
        if (stamp) { Stamp("Stale"); Spacer(Modifier.width(Dimens.Gap2)) }
        if (latest != null) AmountText(Paise(latest.valuePaise), alwaysShowPaise = true)
    }
    HairlineRule()
}

/** " · up 10,000" or " · down 5,000" against the value before, with the amount masked in hide-amounts mode. */
@Composable
private fun changeText(change: Paise?): String = when {
    change == null || change.value == 0L -> ""
    change.value > 0 -> " · up " + maskedRupees(change)
    else -> " · down " + maskedRupees(Paise(-change.value))
}

/** One sheet for adding an asset, updating its value and editing its name: [askName] and [askValue] choose the fields. */
@Composable
private fun AssetFormSheet(
    title: String, ui: AssetsUi, askName: Boolean, askValue: Boolean,
    onSave: (name: String, value: String, asOfMs: Long, note: String) -> String?, onClose: () -> Unit,
    initialName: String = "", initialNote: String = "",
) {
    val context = LocalContext.current
    val c = ChittaTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    var value by remember { mutableStateOf("") }
    var asOf by remember { mutableStateOf(ui.nowMs) }
    var problem by remember { mutableStateOf<String?>(null) }
    BottomOverlay(onClose) {
        Text(title, style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        if (askName) {
            ChittaField(name, { name = it }, "Name", placeholder = "For example: Gold coins, EPF (main), PPF")
            Spacer(Modifier.height(Dimens.Gap2))
        }
        if (askValue) {
            ChittaField(value, { value = it }, "Worth in rupees", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            ValueRow("Value as of", dateText(asOf, ui.zone), onClick = { pickDate(context, asOf, ui.zone, { asOf = it }) })
        }
        if (askName) {
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaField(note, { note = it }, "Note (optional)", singleLine = false)
        }
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { problem = onSave(name, value, asOf, note); if (problem == null) onClose() }, confirmHaptic = true)
    }
}
