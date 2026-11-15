package com.mansaketh.chitta.ui.household

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.cloud.HouseholdSharedItems
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaField
import com.mansaketh.chitta.core.design.components.GaugeBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.FixedDeposits
import com.mansaketh.chitta.core.domain.Instalments
import com.mansaketh.chitta.core.domain.SharedItems
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.money.Segments
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.manage.DepositEditSheet
import com.mansaketh.chitta.ui.manage.depositLine
import com.mansaketh.chitta.ui.manage.maskedRupees
import com.mansaketh.chitta.ui.plan.dateText
import kotlinx.coroutines.launch
import java.time.ZoneId

/** A deposit somebody shared with this person (PDF has no page for it: built from the deposit detail's parts, D226). */
data class SharedDepositView(
    val docId: String, val owner: String, val deposit: FixedDeposit, val canEdit: Boolean,
    /** Who made the latest change when it was a member ("you" for this person), else null. */
    val editedBy: String? = null,
    /** Somebody else changed it after this person's own edit (step 5b). */
    val replaced: Boolean = false,
)

/** A loan somebody shared with this person. Figures use the owner's own "paid so far" (it includes payments recorded as entries there). */
data class SharedLoanView(
    val docId: String, val owner: String, val loan: SharedItems.SharedLoan, val canEdit: Boolean, val payload: Map<String, Any?> = emptyMap(),
    val editedBy: String? = null, val replaced: Boolean = false,
) {
    fun figures(zone: ZoneId): LoanFigures? {
        val t = loan.terms ?: return null
        val sum = Instalments.summarize(t, zone)
        val repaid = Paise((loan.paidSoFar ?: sum.paidFromTicks.value).coerceAtLeast(0L))
        val left = Paise((sum.totalPayable.value - repaid.value).coerceAtLeast(0L))
        return LoanFigures(sum.totalPayable, repaid, left, Segments.gauge(repaid, sum.totalPayable, 20))
    }
}

data class LoanFigures(val total: Paise, val repaid: Paise, val left: Paise, val gauge: Segments.Gauge)

data class SharedViews(val deposits: List<SharedDepositView> = emptyList(), val loans: List<SharedLoanView> = emptyList())

/** What others share with this person, ready to list. Empty when not in a household. Never part of Net worth, loan totals or reminders. */
@Composable
fun rememberSharedViews(shared: HouseholdSharedItems, zone: ZoneId): SharedViews {
    val st by shared.state.collectAsState()
    val hh by shared.householdState.collectAsState()
    val uid = shared.myUid
    return remember(st, hh.id, uid, zone) {
        if (!hh.inHousehold || uid == null || st.members.isEmpty()) return@remember SharedViews()
        val members = st.members.keys + uid
        val deposits = ArrayList<SharedDepositView>(); val loans = ArrayList<SharedLoanView>()
        for ((docId, copy) in st.copies) {
            val meta = copy.meta
            if (!SharedItems.visibleTo(meta, uid, members)) continue
            val owner = st.members[meta.ownerUid] ?: "Member"
            val canEdit = SharedItems.canEdit(meta, uid, members)
            val editor = SharedItems.editorOf(copy.payload)?.let { if (it == uid) "you" else st.members[it] ?: "a member" }
            val replaced = docId in st.replaced
            if (meta.kind == SharedItems.KIND_FD) {
                SharedItems.depositFromPayload(copy.payload, zone)?.let { deposits += SharedDepositView(docId, owner, it, canEdit, editor, replaced) }
            } else {
                loans += SharedLoanView(docId, owner, SharedItems.loanFromPayload(meta.localId, copy.payload, zone), canEdit, copy.payload, editor, replaced)
            }
        }
        SharedViews(deposits.sortedWith(compareBy({ it.deposit.closed }, { it.deposit.maturityMs })), loans.filter { !it.loan.archived }.sortedBy { it.loan.name.lowercase() })
    }
}

/** "Only you" or "Shared with Asha, Ravi"; null when this person is not in a household, so nothing is shown. */
@Composable
fun sharingLine(shared: HouseholdSharedItems, kind: String, localId: String): String? {
    val hh by shared.householdState.collectAsState()
    val st by shared.state.collectAsState()
    if (!hh.inHousehold) return null
    val uid = shared.myUid ?: return "Sign in with Google to share"
    val meta = st.own[SharedItems.docId(kind, uid, localId)] ?: return "Only you"
    return "With " + meta.visibleTo.joinToString(", ") { st.members[it] ?: "Member" } + if (meta.membersCanEdit) " (may edit)" else ""
}

/** "Last edited by Asha" on the owner's own item while a member's edit is the latest change; null otherwise. */
@Composable
fun editedByLine(shared: HouseholdSharedItems, kind: String, localId: String): String? {
    val st by shared.state.collectAsState()
    val uid = shared.myUid ?: return null
    val editor = st.editors[SharedItems.docId(kind, uid, localId)] ?: return null
    return "Last edited by " + (st.members[editor.uid] ?: "a member")
}

/** The owner picks who sees one item and whether they may edit it. Saving only stores the choice; the next round sends it. */
@Composable
fun ShareSheet(shared: HouseholdSharedItems, kind: String, localId: String, itemName: String, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val st by shared.state.collectAsState()
    val uid = shared.myUid.orEmpty()
    val current = st.own[SharedItems.docId(kind, uid, localId)]
    var picked by remember { mutableStateOf(current?.visibleTo?.toSet() ?: emptySet()) }
    var canEdit by remember { mutableStateOf(current?.membersCanEdit ?: false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val others = st.members.filterKeys { it != uid }.entries.sortedBy { it.value.lowercase() }
    BottomOverlay(onClose) {
        Text("Who sees $itemName", style = ChittaTheme.type.title, color = c.ink)
        Text("Nobody else sees it unless you pick them. You can pick up to 7 people.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        if (others.isEmpty()) Text("Nobody else is in the household yet.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3))
        others.forEach { (id, name) -> ValueRow(name, if (id in picked) "Sees it" else "Not shared", onClick = { picked = if (id in picked) picked - id else picked + id }) }
        if (others.isNotEmpty()) {
            ValueRow("Let them edit the details", if (canEdit) "On" else "Off", onClick = { canEdit = !canEdit })
            Text(
                "People you pick can change the details of this item when this is On. Only you can delete it or change who sees it. It never counts in their Net worth.",
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
        }
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { scope.launch { val r = shared.setSharing(kind, localId, picked.toList(), canEdit); if (r == null) onClose() else problem = r } })
        if (current != null) {
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaButton("Make private again", { scope.launch { val r = shared.setSharing(kind, localId, emptyList(), false); if (r == null) onClose() else problem = r } }, style = ButtonStyle.Quiet)
        }
    }
}

// ---- lists: what others share with me

fun LazyListScope.sharedDepositSection(rows: List<SharedDepositView>, nowMs: Long, zone: ZoneId, onOpen: (SharedDepositView) -> Unit) {
    if (rows.isEmpty()) return
    item { Spacer(Modifier.height(Dimens.SectionGap)); SectionHeader("Shared with you") }
    rows.forEach { v ->
        item(key = "sfd-" + v.docId) {
            ValueRow(
                v.deposit.bank.ifBlank { "Fixed deposit" } + " · " + v.owner, maskedRupees(FixedDeposits.currentValue(v.deposit, nowMs, zone)),
                onClick = { onOpen(v) },
            )
            Text(depositLine(v.deposit, nowMs, zone), style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        }
    }
    item { Text("These are not in your Net worth. Tap one to open it.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }
}

@Composable
fun SharedDepositSheet(v: SharedDepositView, shared: HouseholdSharedItems, nowMs: Long, zone: ZoneId, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val d = v.deposit
    val bankFigure = FixedDeposits.isStatedByBank(d)
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = { shared.acknowledgeReplaced(v.docId); onClose() }
    BottomOverlay(close) {
        Text(d.bank.ifBlank { "Fixed deposit" } + (if (d.fdNumber.isNotEmpty()) " · ${d.fdNumber}" else ""), style = ChittaTheme.type.title, color = c.ink)
        Text(if (v.canEdit) "Shared by ${v.owner}. You can edit the details." else "Shared by ${v.owner}. View only.", style = ChittaTheme.type.label, color = c.inkMuted)
        v.editedBy?.let { Text("Last edited by $it.", style = ChittaTheme.type.label, color = c.inkMuted) }
        if (v.replaced) Text("Somebody else changed this after your edit.", style = ChittaTheme.type.label, color = c.warningText)
        if (d.closed) Stamp("Closed") else if (FixedDeposits.hasMatured(d, nowMs, zone)) Stamp("Matured")
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Value today", maskedRupees(FixedDeposits.currentValue(d, nowMs, zone)))
        ValueRow("Interest so far", maskedRupees(FixedDeposits.accruedInterest(d, nowMs, zone)))
        ValueRow("Put in", maskedRupees(Paise(d.principal)))
        ValueRow("At maturity", maskedRupees(FixedDeposits.maturityValue(d, zone)) + if (bankFigure) " (bank)" else " (worked out)")
        ValueRow("Rate", (d.ratePct.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: d.ratePct) + "% a year")
        ValueRow("Interest added", FixedDeposits.COMPOUNDING_NAMES[d.compoundingPerYear] ?: "${d.compoundingPerYear} times a year")
        ValueRow("Booked", dateText(d.startMs, zone))
        ValueRow("Matures", dateText(d.maturityMs, zone))
        if (d.note.isNotBlank()) ValueRow("Note", d.note)
        Text("It is not in your Net worth and gives no reminders.", style = ChittaTheme.type.label, color = c.inkMuted)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        if (v.canEdit) {
            ChittaButton("Edit details", { problem = null; editing = true })
            Spacer(Modifier.height(Dimens.Gap2))
        }
        ChittaButton("Close", close, style = ButtonStyle.Quiet)
    }
    if (editing) DepositEditSheet(
        d, nowMs, zone,
        onSave = { edited ->
            // Only the details change; whose account it is, where it came from and whether it is closed stay as the owner has them.
            val payload = SharedItems.depositPayload(edited.copy(id = d.id, accountId = null, closed = d.closed, fromSms = d.fromSms, smsId = d.smsId), zone)
            if (!saving) { saving = true; scope.launch { problem = shared.editShared(v.docId, payload); saving = false; editing = false } }
        },
        onClose = { editing = false },
    )
}

@Composable
fun SharedLoanSheet(v: SharedLoanView, shared: HouseholdSharedItems, zone: ZoneId, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val t = v.loan.terms
    val f = v.figures(zone)
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = { shared.acknowledgeReplaced(v.docId); onClose() }
    BottomOverlay(close) {
        Text(v.loan.name, style = ChittaTheme.type.title, color = c.ink)
        Text(if (v.canEdit) "Shared by ${v.owner}. You can edit the name and note." else "Shared by ${v.owner}. View only.", style = ChittaTheme.type.label, color = c.inkMuted)
        v.editedBy?.let { Text("Last edited by $it.", style = ChittaTheme.type.label, color = c.inkMuted) }
        if (v.replaced) Text("Somebody else changed this after your edit.", style = ChittaTheme.type.label, color = c.warningText)
        Stamp(if (v.loan.lent) "Lent" else "Borrowed")
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        if (t == null || f == null) {
            Text("The owner has not set an instalment schedule for this loan.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3))
        } else {
            val sum = Instalments.summarize(t, zone)
            GaugeBar(f.gauge, "${v.loan.name} repaid")
            Spacer(Modifier.height(Dimens.Gap2))
            ValueRow("Still to pay", maskedRupees(f.left))
            ValueRow("Paid so far", maskedRupees(f.repaid))
            ValueRow("Total payable", maskedRupees(f.total))
            ValueRow("Borrowed", maskedRupees(sum.principal))
            ValueRow("Interest", maskedRupees(sum.totalInterest))
            ValueRow("Rate", t.annualRatePct + "% a year, ${t.months} months")
            ValueRow("Instalment", maskedRupees(Paise(t.emi)))
            Instalments.next(t, zone)?.let { ValueRow("Next", maskedRupees(it.totalDue) + " on " + dateText(it.dueMs, zone)) }
            if (t.note.isNotBlank()) ValueRow("Note", t.note)
            Text("Paid so far is the owner's figure as of their last sync, with payments they recorded as entries.", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Text("It is not in your Net worth, loan totals or reminders.", style = ChittaTheme.type.label, color = c.inkMuted)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        if (v.canEdit && t != null) {
            ChittaButton("Edit details", { problem = null; editing = true })
            Spacer(Modifier.height(Dimens.Gap2))
        }
        ChittaButton("Close", close, style = ButtonStyle.Quiet)
    }
    if (editing) SharedLoanEditSheet(
        v.loan.name, v.loan.terms?.note.orEmpty(),
        onSave = { name, note ->
            if (!saving) { saving = true; scope.launch { problem = shared.editShared(v.docId, SharedItems.loanDetailsEdit(v.payload, name, note)); saving = false; editing = false } }
        },
        onClose = { editing = false },
    )
}

/** What a member may change on a shared loan: its name and a note. The terms are the owner's (Chitta has no screen that edits them, D231). */
@Composable
private fun SharedLoanEditSheet(name0: String, note0: String, onSave: (String, String) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var name by remember { mutableStateOf(name0) }
    var note by remember { mutableStateOf(note0) }
    var problem by remember { mutableStateOf<String?>(null) }
    BottomOverlay(onClose) {
        Text("Edit loan details", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaField(name, { name = it; problem = null }, problem ?: "Name")
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaField(note, { note = it }, "Note (optional)", singleLine = false)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { if (name.isBlank()) problem = "Give the loan a name" else onSave(name, note) }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }
}

/** Where the owner's phone is up to, in words, for a status line. */
fun sharedStatus(problem: String, lastSyncMs: Long, nowMs: Long): String = when {
    problem.isNotEmpty() -> problem
    lastSyncMs == 0L -> "Not sent yet."
    else -> "Up to date."
}
