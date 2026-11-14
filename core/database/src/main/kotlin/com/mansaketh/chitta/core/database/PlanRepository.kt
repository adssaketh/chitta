package com.mansaketh.chitta.core.database

import androidx.room.withTransaction
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.CategoryLimit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A budget with its category limits, for Undo of a delete. */
data class BudgetWithLimits(val budget: Budget, val limits: List<CategoryLimit>)

/** A goal or loan with its instalment terms, if it has any, for Undo of a delete. */
data class ObjectiveWithTerms(val objective: Objective, val terms: LoanTerms?)

/**
 * Budgets, goals, loans and instalment terms (Phase 5). Entries are not written here: bills, contributions and repayments go through
 * [LedgerRepository] so each change keeps its history row. [apply] groups several of those with an objective in one transaction.
 */
class PlanRepository(private val db: ChittaDatabase, private val ledger: LedgerRepository) {
    // ---- budgets
    fun observeBudgets(): Flow<List<Budget>> = db.budgets().observeAll().map { rows -> rows.map { it.toModel() } }
    fun observeLimits(): Flow<List<CategoryLimit>> = db.budgets().observeLimits().map { rows -> rows.map { it.toModel() } }
    suspend fun budget(id: String): Budget? = db.budgets().get(id)?.toModel()
    suspend fun saveBudget(b: Budget) = db.budgets().upsert(b.toEntity())
    suspend fun saveLimit(l: CategoryLimit) = db.budgets().upsertLimits(listOf(l.toEntity()))
    suspend fun removeLimit(id: String) = db.budgets().deleteLimit(id)

    /** Deletes a budget and its limits; the result restores both. Entries are untouched (a budget only reads them). */
    suspend fun deleteBudget(id: String): BudgetWithLimits? = db.withTransaction {
        val b = db.budgets().get(id)?.toModel() ?: return@withTransaction null
        val limits = db.budgets().getLimitsFor(id).map { it.toModel() }
        db.budgets().deleteLimitsFor(id)
        db.budgets().delete(id)
        BudgetWithLimits(b, limits)
    }

    suspend fun restoreBudget(r: BudgetWithLimits) = db.withTransaction {
        db.budgets().upsert(r.budget.toEntity())
        db.budgets().upsertLimits(r.limits.map { it.toEntity() })
    }

    // ---- sinking funds
    fun observeSinkingFunds(): Flow<List<SinkingFund>> = db.misc().observeSinkingFunds().map { rows -> rows.map { it.toModel() } }
    suspend fun sinkingFund(id: String): SinkingFund? = db.misc().getSinkingFund(id)?.toModel()
    suspend fun saveSinkingFund(f: SinkingFund) = db.misc().upsertSinkingFund(f.toEntity())
    /** Deletes a fund; the result restores it. Entries are untouched (a fund only reads them). */
    suspend fun deleteSinkingFund(id: String): SinkingFund? = db.withTransaction {
        val f = db.misc().getSinkingFund(id)?.toModel() ?: return@withTransaction null
        db.misc().deleteSinkingFund(id)
        f
    }

    // ---- goals and loans
    fun observeObjectives(): Flow<List<Objective>> = db.objectives().observeAll().map { rows -> rows.map { it.toModel() } }
    fun observeLoanTerms(): Flow<List<LoanTerms>> = db.misc().observeLoanTerms().map { rows -> rows.map { it.toModel() } }
    suspend fun objective(id: String): Objective? = db.objectives().get(id)?.toModel()
    suspend fun saveObjective(o: Objective) = db.objectives().upsert(o.toEntity())
    suspend fun saveTerms(t: LoanTerms) = db.misc().upsertLoanTerms(t.toEntity())
    /** Removes just the objective row (Undo of a loan that a partial collect made). */
    suspend fun removeObjectiveRow(id: String) = db.objectives().delete(id)

    /**
     * Deletes a goal or loan. Entries linked to it stay (they are money that moved) but lose the link, each as an edit in its history,
     * so the result can put the links back. Returns what is needed to restore: the objective, its terms, and the entries as they were.
     */
    suspend fun deleteObjective(id: String): Triple<ObjectiveWithTerms, List<Transaction>, Unit>? = db.withTransaction {
        val o = db.objectives().get(id)?.toModel() ?: return@withTransaction null
        val terms = db.misc().getLoanTerms().firstOrNull { it.objectiveId == id }?.toModel()
        val linked = ledger.observeAllOnce().filter { it.objectiveId == id || it.objectiveLoanId == id }
        val unlinked = linked.map { it.copy(objectiveId = if (it.objectiveId == id) null else it.objectiveId, objectiveLoanId = if (it.objectiveLoanId == id) null else it.objectiveLoanId) }
        if (unlinked.isNotEmpty()) ledger.update(unlinked)
        db.misc().deleteLoanTerms(id)
        db.objectives().delete(id)
        Triple(ObjectiveWithTerms(o, terms), linked, Unit)
    }

    suspend fun restoreObjective(r: ObjectiveWithTerms, linkedBefore: List<Transaction>) = db.withTransaction {
        db.objectives().upsert(r.objective.toEntity())
        r.terms?.let { db.misc().upsertLoanTerms(it.toEntity()) }
        if (linkedBefore.isNotEmpty()) ledger.update(linkedBefore)
    }

    /**
     * Saves an objective (when given) first, then entries that changed, then entries that are new, all in one transaction, so a partial
     * collect either makes the loan, its initial record and its first repayment, or none of them. Returns the earlier versions of [updated].
     */
    suspend fun apply(objective: Objective?, updated: List<Transaction>, created: List<Transaction>): List<Transaction> = db.withTransaction {
        objective?.let { db.objectives().upsert(it.toEntity()) }
        val before = if (updated.isNotEmpty()) ledger.update(updated) else emptyList()
        if (created.isNotEmpty()) ledger.add(created)
        before
    }
}
