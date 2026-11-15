package com.mansaketh.chitta.core.database

import androidx.room.withTransaction
import com.mansaketh.chitta.core.domain.HouseholdSharing
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The database side of the household sync (Phase 9b, step 3). Everything the sync reads or writes goes through here, so the rules about
 * what may be written live in one place and are tested with a real (in-memory) database.
 *
 * Rules this class keeps:
 *  - Entries that arrive from the household are written here, never through [LedgerRepository], so they are not marked "waiting to upload".
 *  - An entry is marked uploaded only if it did not change while it was on its way.
 *  - Other members' entries are only ever written into their own placeholder account; this person's own entries are never deleted here.
 */
class HouseholdStore(private val db: ChittaDatabase) {
    // ---- what wakes the sync

    fun observeWaiting(uid: String): Flow<Int> = db.transactions().observeWaitingCount(uid)
    fun observeDeleteCount(): Flow<Int> = db.versions().observeDeleteCount()
    fun observeCategories(): Flow<List<Category>> = db.categories().observeAll().map { rows -> rows.map { it.toModel() } }

    // ---- upload side

    /** New entries (nobody owns them yet) belong to this person once sharing is on. */
    suspend fun claimNew(uid: String): Int = db.transactions().claimNew(uid)

    suspend fun waiting(uid: String, limit: Int): List<Transaction> = db.transactions().waiting(uid, limit).map { it.toModel() }

    /**
     * Marks [sent] as uploaded at [atMs], but only if the entry is still exactly what was sent. If it was edited meanwhile it stays waiting
     * and goes up again. Returns whether it was marked.
     */
    suspend fun markSynced(sent: Transaction, atMs: Long): Boolean = db.withTransaction {
        val now = db.transactions().get(sent.id)?.toModel() ?: return@withTransaction false
        if (now.householdSyncedAtMs != null || now != sent.copy(householdSyncedAtMs = null)) return@withTransaction false
        db.transactions().setSynced(sent.id, atMs)
        true
    }

    suspend fun markDirty(ids: List<String>) = db.withTransaction { ids.chunked(CHUNK).forEach { db.transactions().markDirty(it) } }

    /** Entries deleted here after [sinceMs] (newest history row is a delete), oldest first. */
    suspend fun deletedSince(sinceMs: Long): List<DeleteMark> = db.versions().deletedSince(sinceMs)
    suspend fun deletedIds(): Set<String> = db.versions().deletedIds().toSet()

    // ---- choosing what to share

    /** "Share the entries already on the phone": new and personal entries become mine and go up. */
    suspend fun shareExisting(uid: String): Int = db.transactions().shareExisting(uid)

    /** "Only new entries": entries already here become personal and stay on the phone. */
    suspend fun keepExistingPersonal(): Int = db.transactions().keepExistingPersonal()

    // ---- download side

    suspend fun syncRows(): List<HouseholdSharing.LocalRow> = db.transactions().syncRows().map { HouseholdSharing.LocalRow(it.id, it.ownerUid, it.householdSyncedAtMs) }

    suspend fun get(ids: List<String>): Map<String, Transaction> =
        ids.chunked(CHUNK).flatMap { db.transactions().getMany(it) }.associate { it.id to it.toModel() }

    suspend fun ownAccountIds(): List<String> = db.accounts().getAll().filterNot { HouseholdSharing.isPlaceholderAccountId(it.id) }.map { it.id }

    /** The currency of the first own account, which the placeholder accounts copy so another member's amounts are shown as entered. */
    suspend fun ownCurrency(preferredAccountId: String?): String? {
        val own = db.accounts().getAll().filterNot { HouseholdSharing.isPlaceholderAccountId(it.id) }
        return (own.firstOrNull { it.id == preferredAccountId } ?: own.firstOrNull())?.currency
    }

    /**
     * Writes what the household sent, in one database transaction:
     *  - [memberEntries]: other members' entries (already in their placeholder account);
     *  - [ownEntries]: my own entries that were missing here or edited later on another phone of mine;
     *  - [removeIds]: other members' entries that are gone from the household (never my own).
     */
    suspend fun apply(memberEntries: List<Transaction>, ownEntries: List<Transaction>, removeIds: List<String>, myUid: String) = db.withTransaction {
        if (memberEntries.isNotEmpty() || ownEntries.isNotEmpty()) {
            db.transactions().upsertAll((memberEntries + ownEntries).map { it.toEntity() })
        }
        if (removeIds.isNotEmpty()) {
            val rows = removeIds.chunked(CHUNK).flatMap { db.transactions().getMany(it) }
            // Only another member's row can be removed from here.
            val theirs = rows.filter { HouseholdSharing.isForeignOwner(it.ownerUid, myUid) }.map { it.id }
            theirs.chunked(CHUNK).forEach { db.transactions().deleteMany(it) }
        }
    }

    /** Creates the placeholder account of each member in [names] that has none yet, and keeps their names and the currency up to date. */
    suspend fun ensurePlaceholders(names: Map<String, String>, currency: String?, nowMs: Long) = db.withTransaction {
        val existing = db.accounts().getPlaceholders().associateBy { it.id }
        var order = (db.accounts().getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        for ((uid, name) in names) {
            val id = HouseholdSharing.placeholderAccountId(uid)
            val old = existing[id]
            if (old == null) {
                db.accounts().upsert(
                    AccountEntity(
                        id = id, name = name, colour = null, iconName = null, createdAtMs = nowMs, modifiedAtMs = nowMs, sortOrder = order++,
                        currency = currency, currencyFormat = null, decimals = 2, homeWidgetsJson = null, ownerUid = uid,
                    ),
                )
            } else if (old.name != name || old.currency != currency) {
                db.accounts().upsert(old.copy(name = name, currency = currency, modifiedAtMs = nowMs))
            }
        }
    }

    suspend fun categories(): List<Category> = db.categories().getAll().map { it.toModel() }
    suspend fun saveCategories(rows: List<Category>) = db.categories().upsertAll(rows.map { it.toEntity() })

    // ---- leaving

    /** Removes what came from other members: their entries and their placeholder accounts. My own entries and accounts are never touched. */
    suspend fun removeMembersData(myUid: String) = db.withTransaction {
        db.transactions().deleteInPlaceholders()
        if (myUid.isNotEmpty()) db.transactions().deleteForeign(myUid)
        db.accounts().deletePlaceholders()
    }

    /** After leaving or being removed: what I shared stays here as personal entries, and nothing more goes up. */
    suspend fun makeAllPersonal() = db.transactions().makeAllPersonal()

    // ---- numbers for the Household page and the diagnostics view

    suspend fun counts(uid: String): Counts = Counts(
        shared = db.transactions().countShared(uid), waiting = db.transactions().countWaiting(uid), fromMembers = db.transactions().countMembersEntries(),
    )

    data class Counts(val shared: Int, val waiting: Int, val fromMembers: Int)

    private companion object {
        /** SQLite allows 999 variables in one statement. */
        const val CHUNK = 300
    }
}
