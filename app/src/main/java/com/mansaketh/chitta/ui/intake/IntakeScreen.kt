package com.mansaketh.chitta.ui.intake

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.mansaketh.chitta.core.intake.IntakeChoice
import com.mansaketh.chitta.core.intake.IntakeReason
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.intake.PingBridge
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ValueRow
import java.time.Instant
import java.time.ZoneId

/**
 * Bank messages from Ping and UPI payments. Design: this is the same review list as the CSV import (master sections 3 and 13: confident
 * rows are already saved with Undo, the rest wait here, one Save for the ticked ones). A logo from Ping is shown when Ping has one.
 */
@Composable
fun IntakeScreen(vm: IntakeViewModel, onBack: () -> Unit) {
    val stage by vm.stage.collectAsState()
    val open by vm.signals.open.collectAsState()
    // The share receiver (or a tap on the bank messages that arrived) asks for the list to open.
    LaunchedEffect(open) {
        val what = open ?: return@LaunchedEffect
        vm.signals.consume()
        if (what.startsWith("upi:")) vm.readShared(what.removePrefix("upi:")) else vm.openWaiting()
    }
    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Bank messages and UPI", onBack = { vm.reset(); onBack() })
        when (val s = stage) {
            is IntakeStage.Home -> HomeStep(vm, s)
            is IntakeStage.Working -> Column(Modifier.padding(horizontal = Dimens.Margin)) {
                Text(s.what + "…", style = ChittaTheme.type.body, color = ChittaTheme.colors.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
                LoadingPlaceholder()
            }
            is IntakeStage.Failed -> Column(Modifier.padding(horizontal = Dimens.Margin), verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
                Text(s.message, style = ChittaTheme.type.body, color = ChittaTheme.colors.ink)
                ChittaButton("Back", { vm.reset() }, style = ButtonStyle.Quiet)
            }
            is IntakeStage.Saved -> Column(Modifier.padding(horizontal = Dimens.Margin), verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
                val text = buildString {
                    append("Saved ${s.count} ${if (s.count == 1) "entry" else "entries"}. Undo is on the bar for 10 seconds.")
                    if (s.matched > 0) append(" ${s.matched} ${if (s.matched == 1) "payment was" else "payments were"} matched to entries you already have; their details were added to the note.")
                }
                Text(text, style = ChittaTheme.type.body, color = ChittaTheme.colors.ink)
                ChittaButton("Done", { vm.reset() })
            }
            is IntakeStage.Review -> ReviewStep(vm, s)
        }
    }
}

@Composable
private fun HomeStep(vm: IntakeViewModel, s: IntakeStage.Home) {
    val c = ChittaTheme.colors
    val pending by vm.pendingCount.collectAsState()
    val known by vm.knownCount.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readScreenshot) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readPdf) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readCsv) }
    val ping = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickPingFile) }
    val known2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importKnownAccounts) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 120.dp)) {
        s.message?.let { Text(it, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap3)) }
        SectionHeader("Waiting for your look")
        HairlineRule()
        ValueRow("Bank messages waiting", if (pending == 0) "None" else "$pending", onClick = if (pending > 0) ({ vm.openWaiting() }) else null)
        Text(
            "Messages Chitta is sure about are saved at once, and the Undo bar lets you take them back. A message goes here when its account is not in your list, it names no merchant, or no category fits.",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
        )

        Spacer(Modifier.height(Dimens.SectionGap))
        SectionHeader("UPI payments")
        HairlineRule()
        ValueRow("Screenshot of a payment", "PhonePe, Google Pay, Paytm, BHIM, CRED, MobiKwik", onClick = { image.launch(arrayOf("image/*")) })
        ValueRow("Statement PDF", "PhonePe", onClick = { pdf.launch(arrayOf("application/pdf")) })
        ValueRow("Statement CSV", "PhonePe", onClick = { csv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) })
        Text("The picture is read on this phone and never sent anywhere. You review every payment before it is saved.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))

        Spacer(Modifier.height(Dimens.SectionGap))
        SectionHeader("From Ping")
        HairlineRule()
        ValueRow("Ping file (a date range)", "", onClick = { ping.launch(arrayOf("*/*")) })
        ValueRow("Known accounts", if (known == 0) "None" else "$known", onClick = { sheet = "known" })
        Text(
            "Known accounts are your own names for the cards and accounts in your bank messages. Only messages for those accounts are ever saved without a look. The list stays on this phone.",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
        )
    }
    if (sheet == "known") ChoiceSheet(
        "Known accounts",
        buildList { add(Choice("Load a list from a file", "load")); if (known > 0) add(Choice("Remove the list", "clear")) },
        { v -> sheet = null; if (v == "load") known2.launch(arrayOf("*/*")) else vm.clearKnownAccounts() }, { sheet = null },
    )
}

private fun stillApplies(reason: IntakeReason, ch: IntakeChoice): Boolean = when (reason) {
    IntakeReason.NoCategory -> ch.categoryId == null
    IntakeReason.NoAccount -> ch.accountId == null
    IntakeReason.UnknownAccount -> ch.accountId == null
    else -> true
}

@Composable
private fun Logo(brandKey: String, sender: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, brandKey, sender) { value = PingBridge.logo(context, brandKey.ifEmpty { null }, sender) }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp)) } ?: Spacer(Modifier.size(0.dp))
}

@Composable
private fun ReviewStep(vm: IntakeViewModel, s: IntakeStage.Review) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var rowSheet by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var bulk by remember { mutableStateOf(false) }
    val ticked = s.choices.count { it.ticked && it.saveable }
    val unticked = s.choices.count { !it.ticked }
    val missingCategory = s.choices.count { it.categoryId == null }
    val canSave = ticked > 0 || s.claims.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Dimens.Margin)) {
            Text("$ticked ticked to save · $unticked left unticked", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(top = Dimens.Gap2))
            if (s.alreadyImported > 0 || s.skipped > 0) Text("${s.alreadyImported + s.skipped} left out because they are already in your book or repeated", style = ChittaTheme.type.label, color = c.inkMuted)
            if (s.claims.isNotEmpty()) Text("${s.claims.size} ${if (s.claims.size == 1) "payment is" else "payments are"} already in your book from its other half. Saving adds the details to those entries' notes.", style = ChittaTheme.type.label, color = c.inkMuted)
            Text("Rows that need a look start unticked. Tap a row to change it.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap1))
            if (missingCategory > 0) ChittaButton("Set a category for $missingCategory rows without one", { bulk = true }, style = ButtonStyle.Quiet)
        }
        if (s.choices.isEmpty()) Text("Nothing to look at.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
        LazyColumn(Modifier.weight(1f).padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(s.choices) { i, ch ->
                val r = ch.candidate.row
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).clickable(role = Role.Button) { rowSheet = i to "row" }.padding(vertical = Dimens.Gap2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TickBox(ch.ticked)
                    Spacer(Modifier.width(Dimens.Gap3))
                    if (!r.isUpi) { Logo(r.brandKey, r.sender); Spacer(Modifier.width(Dimens.Gap2)) }
                    Column(Modifier.weight(1f)) {
                        Text(r.title.ifBlank { "(no title)" }, style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
                        val account = s.accounts.firstOrNull { it.id == ch.accountId }?.name ?: r.accountName ?: "No account"
                        val category = s.picks.firstOrNull { it.categoryId == ch.categoryId && it.subCategoryId == ch.subCategoryId }?.label ?: r.categoryLabel.ifBlank { "No category" }
                        Text(Instant.ofEpochMilli(r.dateMs).atZone(zone).toLocalDate().toString() + " · " + account + " · " + category, style = ChittaTheme.type.label, color = c.inkMuted, maxLines = 1)
                        val reasons = ch.candidate.reasons.filter { stillApplies(it, ch) }
                        if (reasons.isNotEmpty()) Text(reasons.joinToString(". ") { it.text }, style = ChittaTheme.type.labelMedium, color = c.ink)
                    }
                    Spacer(Modifier.width(Dimens.Gap2))
                    Text((if (r.isExpense) "-" else "+") + IndianFormat.rupees(Paise(r.amountPaise), alwaysShowPaise = true), style = ChittaTheme.type.body, color = if (!r.isExpense) c.green else c.ink)
                }
                HairlineRule()
            }
        }
        Column(Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap2, bottom = Dimens.Gap4)) {
            ChittaButton("Save $ticked ${if (ticked == 1) "entry" else "entries"}", { vm.save() }, enabled = canSave, confirmHaptic = true)
        }
    }

    rowSheet?.let { (i, kind) ->
        val ch = s.choices.getOrNull(i) ?: run { rowSheet = null; return@let }
        val r = ch.candidate.row
        when (kind) {
            "row" -> ChoiceSheet(
                r.title.ifBlank { "Row" },
                buildList {
                    add(Choice(if (ch.ticked) "Leave this row out" else "Include this row", "toggle"))
                    add(Choice("Change account", "account")); add(Choice("Change category", "category"))
                    if (!r.isUpi && PingBridge.viewIntent(context, r.note) != null) add(Choice("View SMS in Ping", "ping"))
                    add(Choice(if (s.source == ReviewSource.Waiting) "Remove from this list for good" else "Remove from this list", "dismiss"))
                },
                { act ->
                    when (act) {
                        "toggle" -> { vm.toggle(i); rowSheet = null }
                        "ping" -> { PingBridge.open(context, r.note); rowSheet = null }
                        "dismiss" -> { vm.dismiss(i); rowSheet = null }
                        else -> rowSheet = i to act
                    }
                },
                { rowSheet = null },
            )
            "account" -> ChoiceSheet(
                "Account",
                buildList {
                    s.accounts.forEach { add(Choice(it.name, it.id, selected = it.id == ch.accountId)) }
                    if (ch.accountId == null && r.walletName.isNotBlank() && r.walletName != "Unassigned") add(Choice("Make an account called \"${r.walletName}\"", "__create"))
                },
                { v -> if (v == "__create") vm.createAccountFor(i) else vm.setAccount(i, v); rowSheet = null }, { rowSheet = null },
            )
            else -> ChoiceSheet("Category", s.picks.map { Choice(it.label, it) }, { vm.setCategory(i, it); rowSheet = null }, { rowSheet = null })
        }
    }
    if (bulk) ChoiceSheet("Category for rows without one", s.picks.map { Choice(it.label, it) }, { vm.setCategoryForMissing(it); bulk = false }, { bulk = false })
}

/** A 24 dp tick box: a check when the row will be saved, an empty outline when it will not. */
@Composable
private fun TickBox(ticked: Boolean) {
    val c = ChittaTheme.colors
    if (ticked) Icon(ChittaIcons.Check, contentDescription = "Will be saved", tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
    else Spacer(Modifier.size(Dimens.IconSize).border(1.dp, c.rule, RoundedCornerShape(4.dp)))
}
