package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.ZoneId

data class LoanProgress(val total: Paise, val repaid: Paise, val outstanding: Paise, val overpaid: Paise, val gauge: Segments.Gauge)

sealed interface CollectResult {
    /** [updated] and [created] go through the ledger; [objective] (when set) is saved first. */
    data class Ready(val updated: List<Transaction>, val created: List<Transaction>, val objective: Objective?) : CollectResult
    data class Invalid(val reason: String) : CollectResult
}

/**
 * Long-term loans, and one-time lent and borrowed entries, as Paddhu has them (`objectivesListPage.dart`, `upcomingTransactionsFunctions.dart`).
 *
 * A long-term loan is a group of entries linked to it. For a loan you lent (objective income = true) the first record is money out and
 * repayments are money in; for a loan you borrowed it is the other way round. total = what was lent or borrowed + the offset typed on the
 * loan (interest or fees); repaid = the repayments. All of it counts paid entries only.
 */
object Loans {
    fun isLoan(o: Objective) = o.kind == ObjectiveKind.LOAN

    fun progress(o: Objective, entries: List<Transaction>): LoanProgress {
        var initial = 0L
        var payments = 0L
        for (t in entries) {
            if (t.objectiveLoanId != o.id || !t.paid) continue
            if (t.income == !o.income) initial = Math.addExact(initial, t.amount) else payments = Math.addExact(payments, t.amount)
        }
        val offset = o.amount.coerceAtLeast(0) // Paddhu's "difference only" loans store -1; they carry no offset
        val total = Paise((if (o.income) -initial else initial) + offset)
        val repaid = Paise(if (o.income) payments else -payments)
        val outstanding = Paise((total.value - repaid.value).coerceAtLeast(0))
        val over = Paise((repaid.value - total.value).coerceAtLeast(0))
        return LoanProgress(total, repaid, outstanding, over, Segments.gauge(Paise(repaid.value.coerceAtLeast(0)), total, 20))
    }

    /** A repayment (money moving the way payments move) or, with [lendMore], another amount lent or borrowed. */
    fun entry(
        o: Objective, amountPaise: Long, lendMore: Boolean, accountId: String, categoryId: String, title: String, nowMs: Long, ownerUid: String?, newId: () -> String,
    ): Transaction {
        val income = if (lendMore) !o.income else o.income
        return Transaction(
            id = newId(), pairedTransactionId = null, name = title, amount = if (income) amountPaise else -amountPaise, currency = null, note = "",
            categoryId = categoryId, subCategoryId = null, accountId = accountId, dateMs = nowMs, modifiedAtMs = null, originalDueMs = null,
            income = income, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null, kind = null, paid = true,
            createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = o.id,
            excludeFromBudgetIds = emptyList(), ownerUid = ownerUid, isPrivate = false, householdSyncedAtMs = null,
        )
    }

    fun isOneTime(t: Transaction) = (t.kind == TransactionKind.LENT || t.kind == TransactionKind.BORROWED) && t.objectiveLoanId == null

    /** Collect or settle all of it: the entry goes back to unpaid, so its net is zero (Paddhu does exactly this). */
    fun collectAll(t: Transaction, nowMs: Long): Transaction = t.copy(paid = false, modifiedAtMs = nowMs)

    /** Undo a collect or settle. */
    fun uncollect(t: Transaction, nowMs: Long): Transaction = t.copy(paid = true, modifiedAtMs = nowMs)

    /**
     * Part of a one-time loan comes back. As in Paddhu this makes a long-term loan: the original becomes its "Initial record" and the part
     * collected is its first repayment, in [accountId]. The loan's polarity is the opposite of the entry's (lent entry, income = false, gives a loan with income = true).
     */
    fun collectPart(
        t: Transaction, amountPaise: Long, accountId: String, nowMs: Long, sortOrder: Int,
        newEntryId: () -> String, newObjectiveId: () -> String,
    ): CollectResult {
        if (!isOneTime(t)) return CollectResult.Invalid("This is not a one-time loan.")
        if (!t.paid) return CollectResult.Invalid("This one is already collected.")
        if (amountPaise <= 0) return CollectResult.Invalid("Enter an amount above zero.")
        if (amountPaise >= Math.abs(t.amount)) return CollectResult.Invalid("That is the whole amount. Use collect all.")
        val loan = Objective(
            id = newObjectiveId(), kind = ObjectiveKind.LOAN, name = t.name, amount = 0, sortOrder = sortOrder, colour = null, createdAtMs = t.dateMs,
            endDateMs = null, modifiedAtMs = null, iconName = null, emojiIconName = null, income = !t.income, pinned = false, archived = false, accountId = t.accountId,
        )
        val initial = t.copy(kind = null, objectiveLoanId = loan.id, name = "Initial record", modifiedAtMs = nowMs)
        val repayIncome = !t.income
        val repayment = t.copy(
            id = newEntryId(), kind = null, objectiveLoanId = loan.id, income = repayIncome, amount = if (repayIncome) amountPaise else -amountPaise,
            dateMs = nowMs, accountId = accountId, pairedTransactionId = null, modifiedAtMs = null, householdSyncedAtMs = null,
        )
        return CollectResult.Ready(updated = listOf(initial), created = listOf(repayment), objective = loan)
    }
}

data class InstalmentRow(
    val number: Int, val dueMs: Long, val opening: Paise, val interest: Paise, val principalPart: Paise, val emi: Paise, val gstOnInterest: Paise,
) {
    /** What leaves the account for this month: the EMI plus the GST charged on its interest. */
    val totalDue: Paise get() = emi + gstOnInterest
}

data class InstalmentSummary(
    val principal: Paise, val totalInterest: Paise, val gstOnInterest: Paise, val fee: Paise, val gstOnFee: Paise, val totalPayable: Paise, val paidFromTicks: Paise,
) {
    val outstandingBeforePayments: Paise get() = totalPayable - paidFromTicks
}

data class InstalmentProgress(val totalPayable: Paise, val repaid: Paise, val outstanding: Paise, val gauge: Segments.Gauge)

/**
 * Instalment loans such as "Instant Loan on Credit Card": a fixed rate on the reducing balance, equal monthly instalments, 18% GST on every
 * month's interest and on the one-time fee. A port of the behaviour of Paddhu's `loanSchedule.dart`, in exact decimals instead of doubles:
 * every figure is rounded half up to the paisa where Paddhu rounds to 2 places.
 */
object Instalments {
    private val HUNDRED = BigDecimal(100)

    private fun pct(text: String, fallback: BigDecimal = BigDecimal.ZERO) = text.trim().toBigDecimalOrNull() ?: fallback

    private fun roundPaise(v: BigDecimal): Long = v.setScale(0, RoundingMode.HALF_UP).longValueExact()

    private fun interestOf(balance: Long, annualRatePct: BigDecimal): Long = roundPaise(BigDecimal(balance).multiply(annualRatePct).divide(BigDecimal(1200), 10, RoundingMode.HALF_UP))

    private fun gstOf(amount: Long, gstPct: BigDecimal): Long = roundPaise(BigDecimal(amount).multiply(gstPct).divide(HUNDRED, 10, RoundingMode.HALF_UP))

    /** The standard EMI formula, to suggest a value. The sanction letter's EMI wins. */
    fun suggestedEmi(principal: Paise, annualRatePct: String, months: Int): Paise {
        if (months <= 0) return Paise.ZERO
        val r = pct(annualRatePct).divide(BigDecimal(1200), MathContext.DECIMAL128)
        if (r.signum() == 0) return Paise(roundPaise(BigDecimal(principal.value).divide(BigDecimal(months), 10, RoundingMode.HALF_UP)))
        val f = BigDecimal.ONE.add(r).pow(months, MathContext.DECIMAL128)
        val emi = BigDecimal(principal.value).multiply(r).multiply(f).divide(f.subtract(BigDecimal.ONE), MathContext.DECIMAL128)
        return Paise(roundPaise(emi))
    }

    /** Every instalment. The last one is adjusted so the balance ends at exactly zero. */
    fun schedule(t: LoanTerms, zone: ZoneId): List<InstalmentRow> {
        val out = ArrayList<InstalmentRow>()
        var balance = t.principal
        val rate = pct(t.annualRatePct)
        val gst = pct(t.gstPct, BigDecimal(18))
        val anchor = Periods.date(t.firstEmiMs, zone).dayOfMonth
        for (i in 1..t.months) {
            val interest = interestOf(balance, rate)
            var emi = t.emi
            var principalPart = emi - interest
            if (i == t.months || principalPart > balance) {
                principalPart = balance
                emi = principalPart + interest
            }
            out += InstalmentRow(
                i, Periods.advance(t.firstEmiMs, Repeat.MONTHLY, 1, i - 1, zone, anchor),
                Paise(balance), Paise(interest), Paise(principalPart), Paise(emi), Paise(gstOf(interest, gst)),
            )
            balance -= principalPart
            if (balance <= 0) break
        }
        return out
    }

    fun summarize(t: LoanTerms, zone: ZoneId): InstalmentSummary {
        val rows = schedule(t, zone)
        var interest = 0L
        var gstInterest = 0L
        var paid = 0L
        for (r in rows) {
            interest += r.interest.value
            gstInterest += r.gstOnInterest.value
            if (r.number in t.paidInstalments) paid += r.totalDue.value
        }
        val gstFee = gstOf(t.fee, pct(t.gstPct, BigDecimal(18)))
        if (t.feePaid) paid += t.fee + gstFee
        return InstalmentSummary(
            Paise(t.principal), Paise(interest), Paise(gstInterest), Paise(t.fee), Paise(gstFee),
            Paise(t.principal + interest + gstInterest + t.fee + gstFee), Paise(paid),
        )
    }

    /**
     * [linkedSum] is the sum of the paid entries linked to the loan: payments are money out, so negative. Paid so far = ticked instalments
     * (and the fee, when marked paid) + those payments.
     */
    fun progress(t: LoanTerms, linkedSum: Long, zone: ZoneId): InstalmentProgress {
        val s = summarize(t, zone)
        val payments = if (linkedSum < 0) -linkedSum else 0L
        val repaid = Paise(s.paidFromTicks.value + payments)
        val outstanding = Paise((s.totalPayable.value - repaid.value).coerceAtLeast(0))
        return InstalmentProgress(s.totalPayable, repaid, outstanding, Segments.gauge(repaid, s.totalPayable, 20))
    }

    /** Instalments whose due date is on or before today: ticked once, when a loan is first set up. */
    fun dueBy(t: LoanTerms, nowMs: Long, zone: ZoneId): List<Int> {
        val today = Periods.date(nowMs, zone)
        return schedule(t, zone).filter { Periods.date(it.dueMs, zone) <= today }.map { it.number }
    }

    fun withTick(t: LoanTerms, number: Int, paid: Boolean, nowMs: Long): LoanTerms =
        t.copy(paidInstalments = (if (paid) t.paidInstalments + number else t.paidInstalments - number).distinct().sorted(), updatedAtMs = nowMs)

    /** The first instalment not yet ticked, for the "next EMI" line and its reminder. */
    fun next(t: LoanTerms, zone: ZoneId): InstalmentRow? = schedule(t, zone).firstOrNull { it.number !in t.paidInstalments }
}
