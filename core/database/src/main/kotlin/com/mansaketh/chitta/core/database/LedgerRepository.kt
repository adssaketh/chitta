package com.mansaketh.chitta.core.database

import androidx.room.withTransaction
import com.mansaketh.chitta.core.model.EntryVersion
import com.mansaketh.chitta.core.model.HistoryAction
import com.mansaketh.chitta.core.model.PinnedEntry
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Every write to an entry goes through here, inside one database transaction together with its history row,
 * so an entry can never change without a record (master sections 8 and 13). History rows are only added.
 */
class LedgerRepository(private val db: ChittaDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    fun observeAll(): Flow<List<Transaction>> = db.transactions().observeAll().map { rows -> rows.map { it.toModel() } }
    fun observe(id: String): Flow<Transaction?> = db.transactions().observe(id).map { it?.toModel() }
    /** Every entry once, newest first, for work that needs the whole list (Phase 5 unlinking a deleted goal). */
    suspend fun observeAllOnce(): List<Transaction> = db.transactions().getAll().map { it.toModel() }
    suspend fun get(id: String): Transaction? = db.transactions().get(id)?.toModel()

    fun observeHistory(entryId: String): Flow<List<EntryVersion>> = db.versions().observeFor(entryId).map { rows -> rows.map { it.toModel() } }
    suspend fun history(entryId: String): List<EntryVersion> = db.versions().getFor(entryId).map { it.toModel() }

    /** The newest snapshot of every entry whose last action was a delete, most recent first. */
    fun observeRecentlyDeleted(limit: Int = 50): Flow<List<EntryVersion>> =
        db.versions().observeDeleted(limit).map { rows -> rows.map { it.toModel() } }

    /** Adds new entries (one, or both legs of a transfer) and records them as CREATED. */
    suspend fun add(entries: List<Transaction>) = db.withTransaction {
        db.transactions().upsertAll(entries.map { it.toEntity() })
        db.versions().add(entries.map { version(it, HistoryAction.CREATED) })
    }

    /** Saves changed entries, skipping any that did not actually change. Returns the entries before the change, for Undo. */
    suspend fun update(entries: List<Transaction>): List<Transaction> = db.withTransaction {
        val before = db.transactions().getMany(entries.map { it.id }).associateBy { it.id }
        val real = entries.filter { before[it.id]?.toModel() != it }
        db.transactions().upsertAll(real.map { it.toEntity() })
        db.versions().add(real.map { version(it, HistoryAction.EDITED) })
        real.mapNotNull { before[it.id]?.toModel() }
    }

    /** Deletes entries, first writing each one's last state to history. Returns what was deleted, for Undo. */
    suspend fun delete(ids: Collection<String>): List<Transaction> = db.withTransaction {
        val rows = db.transactions().getMany(ids.toList())
        db.versions().add(rows.map { version(it.toModel(), HistoryAction.DELETED) })
        db.transactions().deleteMany(rows.map { it.id })
        rows.map { it.toModel() }
    }

    /** Puts deleted entries back (Undo of a delete, or restoring from Recently deleted). */
    suspend fun restoreDeleted(entries: List<Transaction>) = db.withTransaction {
        db.transactions().upsertAll(entries.map { it.toEntity() })
        db.versions().add(entries.map { version(it, HistoryAction.RESTORED) })
    }

    /** Sets an entry back to what it was in an earlier version; the restore itself is a new history row. */
    suspend fun restoreVersion(entryId: String, versionId: Long): Boolean = db.withTransaction {
        val old = db.versions().getFor(entryId).firstOrNull { it.id == versionId } ?: return@withTransaction false
        val snapshot = Snapshots.decode(old.snapshotJson)
        db.transactions().upsert(snapshot.toEntity())
        db.versions().add(listOf(version(snapshot, HistoryAction.RESTORED)))
        true
    }

    private fun version(t: Transaction, action: HistoryAction) =
        EntryVersionEntity(entryId = t.id, actionCode = action.code, atMs = clock(), snapshotJson = Snapshots.encode(t))

    private fun EntryVersionEntity.toModel() =
        EntryVersion(id, entryId, HistoryAction.fromCode(actionCode)!!, atMs, Snapshots.decode(snapshotJson))
}

class PinnedRepository(private val db: ChittaDatabase) {
    fun observeAll(): Flow<List<PinnedEntry>> = db.pinned().observeAll().map { rows -> rows.map { PinnedEntry(it.id, it.title, it.amountPaise, it.income, it.categoryId, it.accountId, it.sortOrder, it.createdAtMs) } }
    suspend fun save(p: PinnedEntry) = db.pinned().upsert(PinnedEntryEntity(p.id, p.title, p.amount, p.income, p.categoryId, p.accountId, p.sortOrder, p.createdAtMs))
    suspend fun remove(id: String) = db.pinned().delete(id)
}
