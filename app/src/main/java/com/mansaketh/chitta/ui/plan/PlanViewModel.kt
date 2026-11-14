package com.mansaketh.chitta.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.*
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.reminders.ReminderScheduler
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class PlanUi(
    val entries: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val limits: List<CategoryLimit> = emptyList(),
    val objectives: List<Objective> = emptyList(),
    val terms: List<LoanTerms> = emptyList(),
    val sinking: List<SinkingFund> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val nowMs: Long = 0,
    val zone: ZoneId = ZoneId.systemDefault(),
    val loading: Boolean = true,
)

/** What the budget sheet collects. Dates matter only for a one-time budget. */
data class BudgetForm(
    val name: String, val amount: Paise, val repeat: Repeat?, val startMs: Long, val endMs: Long, val savings: Boolean, val includeDebt: Boolean,
    val accountIds: List<String>?, val excludedCategoryIds: List<String>?, val absoluteLimits: Boolean, val rollover: Boolean = false,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val plan: PlanRepository,
    private val ledger: LedgerRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    private val prefs: AppPrefs,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val core = combine(ledger.observeAll(), plan.observeBudgets(), plan.observeLimits(), plan.observeObjectives(), plan.observeLoanTerms()) { e, b, l, o, t ->
        PlanUi(entries = e, budgets = b, limits = l, objectives = o, terms = t, nowMs = clock.nowMs(), zone = clock.zone(), loading = false)
    }
    val ui: StateFlow<PlanUi> = combine(core, categoryRepo.observeAll(), accountRepo.observeAll(), plan.observeSinkingFunds()) { p, c, a, s -> p.copy(categories = c, accounts = a, sinking = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUi())

    // ---- subscription suggestions (Phase 8g)
    private fun decodeDismissed(raw: Set<String>): Map<String, String> =
        raw.mapNotNull { e -> e.indexOf('|').takeIf { it > 0 }?.let { e.substring(0, it) to e.substring(it + 1) } }.toMap()
    private val _dismissed = MutableStateFlow(decodeDismissed(prefs.dismissedSubscriptions))
    /** Payees the person said are not subscriptions: key to the name shown. */
    val dismissedSubscriptions: StateFlow<Map<String, String>> = _dismissed
    private fun setDismissed(next: Map<String, String>) {
        _dismissed.value = next
        prefs.dismissedSubscriptions = next.map { (k, n) -> "$k|$n" }.toSet()
    }
    val suggestions: StateFlow<List<SubscriptionSuggestion>> = combine(ui, _dismissed) { u, d ->
        if (u.loading) emptyList() else Subscriptions.detect(u.entries, d.keys, u.nowMs, u.zone)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _settings = MutableStateFlow(Settings(prefs.autoPayOverdue, prefs.paidOnDueDate, prefs.reminders))
    data class Settings(val autoPay: Boolean, val paidOnDue: Boolean, val reminders: ReminderSettings)
    val settings: StateFlow<Settings> = _settings

    private val rule get() = if (prefs.paidOnDueDate) PaidDateRule.DueDate else PaidDateRule.Today

    // ---- shared save with Undo
    /** Saves [objective] (if any), [updated] and [created] in one transaction. Undo puts everything back. */
    private suspend fun commit(message: String, objective: Objective? = null, updated: List<Transaction> = emptyList(), created: List<Transaction> = emptyList(), newObjective: Boolean = false) {
        val before = plan.apply(objective, updated, created)
        undo.post(message) {
            if (created.isNotEmpty()) ledger.delete(created.map { it.id })
            if (before.isNotEmpty()) ledger.update(before)
            if (objective != null && newObjective) plan.removeObjectiveRow(objective.id)
        }
    }

    // ---- budgets
    fun saveBudget(existing: Budget?, f: BudgetForm) = viewModelScope.launch {
        val now = clock.nowMs()
        val filters = buildList {
            add(BudgetFilter.ADDED_TO_OTHER_BUDGET); add(BudgetFilter.SHARED_TO_OTHER_BUDGET); add(BudgetFilter.ADDED_TO_OBJECTIVE)
            if (f.includeDebt) add(BudgetFilter.INCLUDE_DEBT_AND_CREDIT)
        }
        val end = if (f.repeat == null) f.endMs else Periods.advance(f.startMs, f.repeat, 1, 1, clock.zone())
        val b = (existing ?: Budget(
            ids.newId(), f.name, f.amount.value, null, f.startMs, end, null, null, null, false, false, false, 1, f.repeat, now, null, false,
            ui.value.budgets.size, ui.value.accounts.firstOrNull()?.id ?: "", null, null, false,
        )).copy(
            name = f.name.trim(), amount = f.amount.value, startMs = f.startMs, endMs = end, repeat = f.repeat, income = f.savings, accountIds = f.accountIds,
            excludedCategoryIds = f.excludedCategoryIds, filters = filters, absoluteSpendingLimit = f.absoluteLimits, rollover = f.rollover && !f.savings && f.repeat != null, modifiedAtMs = now,
        )
        val before = existing
        plan.saveBudget(b)
        undo.post(if (existing == null) "Budget ${b.name} added" else "Budget ${b.name} saved") { if (before == null) plan.deleteBudget(b.id) else plan.saveBudget(before) }
    }

    // ---- sinking funds (Phase 8e)
    fun saveFund(existing: SinkingFund?, name: String, target: Paise, startMs: Long, dueMs: Long, categoryId: String?) = viewModelScope.launch {
        val now = clock.nowMs()
        val f = (existing ?: SinkingFund(ids.newId(), name, target.value, startMs, dueMs, categoryId, false, ui.value.sinking.size, now, null))
            .copy(name = name.trim(), targetPaise = target.value, startMs = startMs, targetDateMs = dueMs, categoryId = categoryId, modifiedAtMs = now)
        plan.saveSinkingFund(f)
        undo.post(if (existing == null) "Sinking fund ${f.name} added" else "Sinking fund ${f.name} saved") { if (existing == null) plan.deleteSinkingFund(f.id) else plan.saveSinkingFund(existing) }
    }

    fun deleteFund(id: String) = viewModelScope.launch {
        val gone = plan.deleteSinkingFund(id) ?: return@launch
        undo.post("Sinking fund ${gone.name} deleted") { plan.saveSinkingFund(gone) }
    }

    fun deleteBudget(id: String) = viewModelScope.launch {
        val gone = plan.deleteBudget(id) ?: return@launch
        undo.post("Budget ${gone.budget.name} deleted") { plan.restoreBudget(gone) }
    }

    /** Sets or clears one category goal. [raw] is an amount in rupees, or a percentage when the budget uses percentages. */
    fun setLimit(budget: Budget, categoryId: String, raw: String?): Boolean {
        val existing = ui.value.limits.firstOrNull { it.budgetId == budget.id && it.categoryId == categoryId }
        if (raw.isNullOrBlank()) {
            if (existing != null) viewModelScope.launch { plan.removeLimit(existing.id); undo.post("Category goal removed") { plan.saveLimit(existing) } }
            return true
        }
        val stored: Long = if (budget.absoluteSpendingLimit) {
            AmountExpression.evaluate(raw)?.value ?: return false
        } else {
            // a percentage, stored as percent x 100 (25% is 2500)
            val pct = raw.trim().removeSuffix("%").toBigDecimalOrNull() ?: return false
            if (pct.signum() <= 0 || pct > java.math.BigDecimal(100)) return false
            pct.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        }
        if (stored <= 0) return false
        val l = existing?.copy(amount = stored, modifiedAtMs = clock.nowMs()) ?: CategoryLimit(ids.newId(), categoryId, budget.id, stored, null, budget.accountId)
        viewModelScope.launch { plan.saveLimit(l); undo.post("Category goal saved") { if (existing == null) plan.removeLimit(l.id) else plan.saveLimit(existing) } }
        return true
    }

    // ---- goals and loans
    fun saveGoal(existing: Objective?, name: String, target: Paise, income: Boolean, endMs: Long?) = viewModelScope.launch {
        val now = clock.nowMs()
        val o = (existing ?: Objective(ids.newId(), ObjectiveKind.GOAL, name, target.value, ui.value.objectives.size, null, now, endMs, null, null, null, income, false, false, ui.value.accounts.firstOrNull()?.id ?: ""))
            .copy(name = name.trim(), amount = target.value, endDateMs = endMs, income = existing?.income ?: income, modifiedAtMs = now)
        plan.saveObjective(o)
        undo.post("Goal ${o.name} saved") { if (existing == null) plan.removeObjectiveRow(o.id) else plan.saveObjective(existing) }
    }

    /**
     * A new long-term loan, with its first record of money lent or borrowed. [lent] true means you lent the money (the objective's income flag, as in Paddhu).
     * [offset] is interest or fees on top. Renaming an existing loan goes through [renameLoan].
     */
    fun addLoan(name: String, lent: Boolean, initial: Paise, offset: Paise, accountId: String, onProblem: (String) -> Unit): Boolean {
        if (name.isBlank()) { onProblem("Give the loan a name."); return false }
        if (initial.isZero) { onProblem("Enter the amount lent or borrowed."); return false }
        val now = clock.nowMs()
        val o = Objective(ids.newId(), ObjectiveKind.LOAN, name.trim(), offset.value, ui.value.objectives.size, null, now, null, null, null, null, lent, false, false, accountId)
        val income = !lent // lending sends money out; borrowing brings it in
        val category = ui.value.categories.firstOrNull { it.income == income && it.mainCategoryId == null && !it.archived }?.id ?: run { onProblem("Add a category first."); return false }
        val entry = Loans.entry(o, initial.value, true, accountId, category, "Initial record", now, null, ids::newId)
        viewModelScope.launch { commit("Loan ${o.name} added", o, created = listOf(entry), newObjective = true) }
        return true
    }

    fun renameLoan(existing: Objective, name: String, offset: Paise) = viewModelScope.launch {
        val o = existing.copy(name = name.trim().ifEmpty { existing.name }, amount = offset.value, modifiedAtMs = clock.nowMs())
        plan.saveObjective(o)
        undo.post("Loan ${o.name} saved") { plan.saveObjective(existing) }
    }

    fun deleteObjective(id: String) = viewModelScope.launch {
        val r = plan.deleteObjective(id) ?: return@launch
        undo.post("${r.first.objective.name} deleted. Its entries stay in the ledger.") { plan.restoreObjective(r.first, r.second) }
    }

    fun contribute(o: Objective, raw: String, accountId: String, onProblem: (String) -> Unit): Boolean {
        val amount = AmountExpression.evaluate(raw)?.takeIf { !it.isZero } ?: run { onProblem("Enter an amount above zero."); return false }
        val category = ui.value.categories.firstOrNull { it.income == o.income && it.mainCategoryId == null && !it.archived }?.id ?: run { onProblem("Add a category first."); return false }
        val t = Goals.contribution(o, amount.value, accountId, category, o.name, clock.nowMs(), null, ids::newId)
        viewModelScope.launch { commit("Added ${IndianFormat.rupees(amount)} to ${o.name}", created = listOf(t)) }
        return true
    }

    fun repay(o: Objective, raw: String, accountId: String, lendMore: Boolean, onProblem: (String) -> Unit): Boolean {
        val amount = AmountExpression.evaluate(raw)?.takeIf { !it.isZero } ?: run { onProblem("Enter an amount above zero."); return false }
        val income = if (lendMore) !o.income else o.income
        // The entry needs a category of the matching kind; the first active main one is used (it can be changed on the entry afterwards).
        val category = ui.value.categories.firstOrNull { it.income == income && it.mainCategoryId == null && !it.archived }?.id ?: run { onProblem("Add a category first."); return false }
        val t = Loans.entry(o, amount.value, lendMore, accountId, category, o.name, clock.nowMs(), null, ids::newId)
        viewModelScope.launch { commit(if (lendMore) "Added ${IndianFormat.rupees(amount)} to ${o.name}" else "Payment of ${IndianFormat.rupees(amount)} recorded", created = listOf(t)) }
        return true
    }

    fun collectAll(t: Transaction) = viewModelScope.launch { commit("${t.name.ifBlank { "Loan" }} marked collected", updated = listOf(Loans.collectAll(t, clock.nowMs()))) }
    fun uncollect(t: Transaction) = viewModelScope.launch { commit("Collected mark removed", updated = listOf(Loans.uncollect(t, clock.nowMs()))) }

    fun collectPart(t: Transaction, raw: String, accountId: String, onProblem: (String) -> Unit): Boolean {
        val amount = AmountExpression.evaluate(raw) ?: run { onProblem("Enter an amount."); return false }
        return when (val r = Loans.collectPart(t, amount.value, accountId, clock.nowMs(), ui.value.objectives.size, ids::newId, ids::newId)) {
            is CollectResult.Invalid -> { onProblem(r.reason); false }
            is CollectResult.Ready -> {
                viewModelScope.launch { commit("Part collected. ${t.name.ifBlank { "This" }} is now a loan.", r.objective, r.updated, r.created, newObjective = true) }
                true
            }
        }
    }

    // ---- instalment loans
    fun saveTerms(loanName: String, lent: Boolean, terms: (String) -> LoanTerms) = viewModelScope.launch {
        val now = clock.nowMs()
        val o = Objective(ids.newId(), ObjectiveKind.LOAN, loanName.trim(), 0, ui.value.objectives.size, null, now, null, null, null, null, lent, false, false, ui.value.accounts.firstOrNull()?.id ?: "")
        plan.saveObjective(o)
        plan.saveTerms(terms(o.id).copy(updatedAtMs = now))
        undo.post("Instalment loan $loanName added") { plan.removeObjectiveRow(o.id) }
    }

    fun tick(t: LoanTerms, number: Int, paid: Boolean) = viewModelScope.launch {
        val next = Instalments.withTick(t, number, paid, clock.nowMs())
        plan.saveTerms(next)
        undo.post(if (paid) "Instalment $number ticked" else "Instalment $number unticked") { plan.saveTerms(t) }
    }

    // ---- bills (Upcoming, subscriptions, repeats)
    private fun goalReached(t: Transaction): Boolean {
        val id = t.objectiveId ?: return false
        val o = ui.value.objectives.firstOrNull { it.id == id } ?: return false
        return Goals.isGoal(o) && Goals.progress(o, ui.value.entries, clock.nowMs(), clock.zone()).reached
    }

    private suspend fun applyBill(message: String, c: BillChange) = commit(message, updated = listOf(c.updated), created = listOfNotNull(c.next))

    fun pay(t: Transaction, amountRaw: String? = null, accountId: String? = null, onProblem: (String) -> Unit = {}): Boolean {
        val amount = if (amountRaw.isNullOrBlank()) null else (AmountExpression.evaluate(amountRaw)?.takeIf { !it.isZero }?.value ?: run { onProblem("Enter an amount above zero."); return false })
        if (!Bills.isOpen(t)) return false
        val change = Bills.pay(t, clock.nowMs(), clock.zone(), rule, ids::newId, amount, accountId, goalReached(t))
        viewModelScope.launch { applyBill("${t.name.ifBlank { "Bill" }} paid", change) }
        return true
    }

    fun skip(t: Transaction) {
        if (!Bills.isOpen(t)) return
        val change = Bills.skip(t, clock.nowMs(), clock.zone(), ids::newId, goalReached(t))
        viewModelScope.launch { applyBill("${t.name.ifBlank { "Bill" }} skipped", change) }
    }

    fun undoPaid(t: Transaction) = viewModelScope.launch { commit("Payment removed", updated = listOf(Bills.removePayment(t, clock.nowMs()))) }
    fun undoSkip(t: Transaction) = viewModelScope.launch { commit("Skip removed", updated = listOf(Bills.removeSkip(t, clock.nowMs()))) }

    /** Fills in the amount of a bill that was saved without one (a variable bill). */
    fun setAmount(t: Transaction, raw: String): Boolean {
        val p = AmountExpression.evaluate(raw)?.takeIf { !it.isZero } ?: return false
        viewModelScope.launch { commit("Amount saved", updated = listOf(t.copy(amount = if (t.income) p.value else -p.value, modifiedAtMs = clock.nowMs()))) }
        return true
    }

    fun deleteBill(t: Transaction) = viewModelScope.launch {
        val removed = ledger.delete(listOf(t.id))
        if (removed.isNotEmpty()) undo.post("${t.name.ifBlank { "Bill" }} deleted") { ledger.restoreDeleted(removed) }
    }

    /**
     * A new bill, or a change to an existing one. [repeat] null = a one-time upcoming bill; otherwise a subscription (monthly or yearly, usually)
     * or a repeating entry. The anchor day keeps a bill on the 31st on the 31st in months that have one.
     */
    fun saveBill(existing: Transaction?, title: String, amount: Paise, income: Boolean, accountId: String, categoryId: String, dueMs: Long, repeat: Repeat?, length: Int, endMs: Long?, notify: Boolean, subscription: Boolean) = viewModelScope.launch {
        val now = clock.nowMs()
        val kind = when { repeat == null -> TransactionKind.UPCOMING; subscription -> TransactionKind.SUBSCRIPTION; else -> TransactionKind.REPETITIVE }
        val anchor = if (repeat == Repeat.MONTHLY || repeat == Repeat.YEARLY) Periods.date(dueMs, clock.zone()).dayOfMonth else null
        val base = existing ?: Transaction(
            id = ids.newId(), pairedTransactionId = null, name = title, amount = 0, currency = null, note = "", categoryId = categoryId, subCategoryId = null,
            accountId = accountId, dateMs = dueMs, modifiedAtMs = null, originalDueMs = null, income = income, periodLength = null, repeat = null, endDateMs = null,
            upcomingNotification = true, kind = kind, paid = false, createdAnotherFuture = false, skipPaid = false, methodAdded = null, objectiveId = null,
            objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
        )
        val t = base.copy(
            name = title.trim(), amount = if (income) amount.value else -amount.value, income = income, accountId = accountId, categoryId = categoryId, subCategoryId = null,
            dateMs = dueMs, repeat = repeat, periodLength = if (repeat == null) null else length.coerceAtLeast(1), endDateMs = if (repeat == null) null else endMs,
            kind = kind, anchorDay = anchor, upcomingNotification = notify, modifiedAtMs = if (existing == null) null else now,
        )
        if (existing == null) commit("Bill ${t.name} added", created = listOf(t)) else commit("Bill ${t.name} saved", updated = listOf(t))
    }

    /** "Repeat" on an ordinary entry (entry detail): turns it into the first occurrence of a repeating one. The entry itself stays as it is (paid); the next one is created. */
    fun repeatEntry(t: Transaction, repeat: Repeat?, length: Int, endMs: Long?) = viewModelScope.launch {
        val now = clock.nowMs()
        val anchor = if (repeat == Repeat.MONTHLY || repeat == Repeat.YEARLY) Periods.date(t.dateMs, clock.zone()).dayOfMonth else null
        if (repeat == null) {
            commit("Repeat removed", updated = listOf(t.copy(repeat = null, periodLength = null, endDateMs = null, kind = null, anchorDay = null, modifiedAtMs = now)))
            return@launch
        }
        val series = t.copy(kind = TransactionKind.REPETITIVE, repeat = repeat, periodLength = length.coerceAtLeast(1), endDateMs = endMs, anchorDay = anchor, paid = true, originalDueMs = t.dateMs, modifiedAtMs = now)
        val next = Bills.nextDueMs(series, clock.zone())
        val following = next?.let { series.copy(id = ids.newId(), dateMs = it, originalDueMs = null, paid = false, createdAnotherFuture = false, modifiedAtMs = null, pairedTransactionId = null) }
        commit("Now repeats ${repeatLabel(repeat, length)}", updated = listOf(series.copy(createdAnotherFuture = following != null)), created = listOfNotNull(following))
    }

    /** Adds a detected subscription as a repeating bill. The ledger entries it came from are not touched. Undo removes it. */
    fun addSuggestion(s: SubscriptionSuggestion) = viewModelScope.launch {
        commit("${s.name} added to your subscriptions", created = listOf(Subscriptions.toBill(s, ids.newId())))
    }

    /** "Not a subscription": never suggested again. Undo, or the Dismissed list, brings it back. */
    fun dismissSuggestion(s: SubscriptionSuggestion) {
        setDismissed(_dismissed.value + (s.key to s.name))
        undo.post("${s.name} will not be suggested") { setDismissed(_dismissed.value - s.key) }
    }

    fun restoreSuggestion(key: String) = setDismissed(_dismissed.value - key)

    /** Pays overdue repeating bills now if "Auto-pay overdue" is on. Called when Plan opens. */
    fun runAutoPay() = viewModelScope.launch {
        if (!prefs.autoPayOverdue) return@launch
        val changes = Bills.autoPay(ledger.observeAllOnce(), clock.nowMs(), clock.zone(), ids::newId)
        if (changes.isEmpty()) return@launch
        commit("Paid ${changes.size} overdue ${if (changes.size == 1) "bill" else "bills"} automatically", updated = changes.map { it.updated }, created = changes.mapNotNull { it.next })
    }

    // ---- settings
    fun setAutoPay(v: Boolean) { prefs.autoPayOverdue = v; _settings.value = _settings.value.copy(autoPay = v); if (v) runAutoPay() }
    fun setPaidOnDue(v: Boolean) { prefs.paidOnDueDate = v; _settings.value = _settings.value.copy(paidOnDue = v) }
    fun setReminders(r: ReminderSettings) {
        prefs.reminders = r; _settings.value = _settings.value.copy(reminders = r)
        ReminderScheduler.scheduleNext(appContext, r, clock.nowMs(), clock.zone())
    }
    fun notificationsAsked() { prefs.askedNotificationPermission = true }
    val askedNotifications get() = prefs.askedNotificationPermission
}

fun repeatLabel(r: Repeat, length: Int): String = when (r) {
    Repeat.DAILY -> if (length == 1) "every day" else "every $length days"
    Repeat.WEEKLY -> if (length == 1) "every week" else "every $length weeks"
    Repeat.MONTHLY -> if (length == 1) "every month" else "every $length months"
    Repeat.YEARLY -> if (length == 1) "every year" else "every $length years"
    Repeat.CUSTOM -> "on a custom schedule"
}
