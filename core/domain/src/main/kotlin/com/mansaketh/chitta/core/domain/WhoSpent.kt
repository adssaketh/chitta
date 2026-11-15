package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.YearMonth
import java.time.ZoneId

/** One member's line on "Who spent how much". [name] is already the text to show ("You", a nickname, or [WhoSpent.FORMER_MEMBER]). */
data class MemberSpend(val uid: String, val name: String, val isMe: Boolean, val spent: Paise, val entryCount: Int, val percent: Int?)

/** The page's numbers: members biggest first, the total of all of them, and the month. */
data class WhoSpentResult(val period: Period, val members: List<MemberSpend>, val total: Paise)

/**
 * "Who spent how much" (Phase 9b step 4, PDF p.34): shared entries only, split by member for a month.
 * Private entries and personal ones (owner "" or not yet claimed) are never counted, so every phone shows the same numbers.
 * What counts as spending is exactly what the Ledger header and Insights count ([Totals.of]): transfers and balance corrections are
 * left out, lent and borrowed entries are left out, and a refund reduces what its member spent.
 * There is no share, split or "who owes whom" here: the owner dropped settle-up (DECISIONS D220).
 */
object WhoSpent {
    const val FORMER_MEMBER = "Former member"

    /** The calendar month as a [Period] in [zone]. */
    fun monthPeriod(month: YearMonth, zone: ZoneId): Period =
        Period(month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(), month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli())

    /** True when an entry belongs to the shared ledger: somebody's uid is on it and it is not marked private. */
    fun isShared(t: Transaction): Boolean = !t.ownerUid.isNullOrEmpty() && !t.isPrivate

    /**
     * Money out per member for [period]. [entries] may hold anything (the phone's own entries and other members' copies); only shared
     * ones dated in the period count. Every current member is listed, even with nothing spent. A uid that is no longer a member but still
     * has shared entries is listed as [FORMER_MEMBER]. [percent] is each person's share of the total, rounded, and null when the total is not above zero.
     */
    fun compute(entries: List<Transaction>, categories: List<Category>, period: Period, members: List<HouseholdMember>, myUid: String): WhoSpentResult {
        val shared = entries.filter { isShared(it) && it.dateMs in period }
        val byUid = shared.groupBy { it.ownerUid!! }
        val uids = (members.map { it.uid } + byUid.keys).distinct()
        val spent = uids.associateWith { uid -> byUid[uid].orEmpty().let { Totals.of(it, categories).moneyOut } }
        val total = spent.values.fold(Paise.ZERO) { acc, p -> acc + p }
        val lines = uids.map { uid ->
            val member = members.firstOrNull { it.uid == uid }
            val amount = spent.getValue(uid)
            MemberSpend(
                uid = uid,
                name = when {
                    uid == myUid -> "You"
                    member != null -> Households.displayName(member)
                    else -> FORMER_MEMBER
                },
                isMe = uid == myUid,
                spent = amount,
                entryCount = byUid[uid].orEmpty().count { !Totals.isNeutral(it) && Balances.countsTowardBalance(it) },
                percent = if (total.value > 0) mulDivRound(amount.value, 100, total.value).toInt() else null,
            )
        }.sortedWith(compareByDescending<MemberSpend> { it.spent.value }.thenBy { it.name }.thenBy { it.uid })
        return WhoSpentResult(period, lines, total)
    }
}
