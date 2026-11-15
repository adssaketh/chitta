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
import com.mansaketh.chitta.ui.manage.depositLine
import com.mansaketh.chitta.ui.manage.maskedRupees
import com.mansaketh.chitta.ui.plan.dateText
import kotlinx.coroutines.launch
import java.time.ZoneId

/** A deposit somebody shared with this person (PDF has no page for it: built from the deposit detail's parts, D226). */
data class SharedDepositView(val docId: String, val owner: String, val deposit: FixedDeposit, val canEdit: Boolean)

/** A loan somebody shared with this person. Figures use the owner's own "paid so far" (it includes payments recorded as entries there). */
data class SharedLoanView(val docId: String, val owner: String, val loan: SharedItems.SharedLoan, val canEdit: Boolean) {
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
            if (meta.kind == SharedItems.KIND_FD) {
                SharedItems.depositFromPayload(copy.payload, zone)?.let { deposits += SharedDepositView(docId, owner, it, canEdit) }
            } else {
                loans += SharedLoanView(docId, owner, SharedItems.loanFromPayload(meta.localId, copy.payload, zone), canEdit)
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
                "In this app people you share with can only look for now. Editing comes in the next update. The old app already honours this setting. It never counts in their Net worth.",
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
    item { SectionHeader("Shared with you") }
    rows.forEach { v ->
        item(key = "sfd-" + v.docId) {
            ValueRow(
                v.deposit.bank.ifBlank { "Fixed deposit" } + " · " + v.owner, maskedRupees(FixedDeposits.currentValue(v.deposit, nowMs, zone)),
                onClick = { onOpen(v) },
            )
            Text(depositLine(v.deposit, nowMs, zone), style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        }
    }
    item { Text("View only. These are not in your Net worth.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }
}

@Composable
fun SharedDepositSheet(v: SharedDepositView, nowMs: Long, zone: ZoneId, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val d = v.deposit
    val bankFigure = FixedDeposits.isStatedByBank(d)
    BottomOverlay(onClose) {
        Text(d.bank.ifBlank { "Fixed deposit" } + (if (d.fdNumber.isNotEmpty()) " · ${d.fdNumber}" else ""), style = ChittaTheme.type.title, color = c.ink)
        Text("Shared by ${v.owner}. View only.", style = ChittaTheme.type.label, color = c.inkMuted)
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
        Text("It is not in your Net worth and gives no reminders.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }
}

@Composable
fun SharedLoanSheet(v: SharedLoanView, zone: ZoneId, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val t = v.loan.terms
    val f = v.figures(zone)
    BottomOverlay(onClose) {
        Text(v.loan.name, style = ChittaTheme.type.title, color = c.ink)
        Text("Shared by ${v.owner}. View only.", style = ChittaTheme.type.label, color = c.inkMuted)
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
            Text("Paid so far is the owner's figure as of their last sync, with payments they recorded as entries.", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Text("It is not in your Net worth, loan totals or reminders.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }
}

/** Where the owner's phone is up to, in words, for a status line. */
fun sharedStatus(problem: String, lastSyncMs: Long, nowMs: Long): String = when {
    problem.isNotEmpty() -> problem
    lastSyncMs == 0L -> "Not sent yet."
    else -> "Up to date."
}
