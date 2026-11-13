package com.mansaketh.chitta.ui

import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.BulkChange
import com.mansaketh.chitta.core.domain.BulkEdit
import com.mansaketh.chitta.core.domain.BulkResult
import com.mansaketh.chitta.core.domain.DeleteImpact
import com.mansaketh.chitta.core.domain.Deletion
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.di.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Edits and deletes shared by the Ledger and the entry detail, each posting its Undo. */
@Singleton
class EntryActions @Inject constructor(
    private val ledger: LedgerRepository,
    private val undo: UndoCenter,
    private val clock: Clock,
) {
    fun impact(selected: List<Transaction>, all: List<Transaction>, accounts: List<Account>): DeleteImpact = Deletion.impact(selected, all, accounts)

    suspend fun delete(impact: DeleteImpact) {
        val removed = ledger.delete(impact.toDelete.map { it.id })
        if (removed.isEmpty()) return
        val label = if (removed.size == 1) "Deleted · ${removed[0].name.ifBlank { "entry" }} ${IndianFormat.rupees(Paise(Math.abs(removed[0].amount)))}" else "Deleted ${removed.size} entries"
        undo.post(label) { ledger.restoreDeleted(removed) }
    }

    /** Saves [changed] entries; Undo puts the earlier versions back. Returns false when nothing actually changed. */
    suspend fun save(changed: List<Transaction>, message: String = "Saved changes"): Boolean {
        val before = ledger.update(changed)
        if (before.isEmpty()) return false
        undo.post(message) { ledger.update(before) }
        return true
    }

    fun bulk(selected: List<Transaction>, all: List<Transaction>, change: BulkChange): BulkResult = BulkEdit.apply(selected, all, change, clock.nowMs())

    suspend fun add(entries: List<Transaction>, message: String) {
        ledger.add(entries)
        val ids = entries.map { it.id }
        undo.post(message) { ledger.delete(ids) }
    }
}
