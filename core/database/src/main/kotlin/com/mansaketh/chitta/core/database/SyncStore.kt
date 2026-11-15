package com.mansaketh.chitta.core.database

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A table that syncs, and the column that identifies its rows. */
data class SyncTable(val name: String, val keyColumn: String)

object SyncTables {
    /**
     * The tables that sync between this person's own devices. Left out on purpose: `activity_log` and `entry_version` (each phone's own
     * history), `exchange_rate` and `rate_override` (a cache each phone refetches), `scan_template` (old notification scanning, unused),
     * and the two sync tables themselves.
     */
    val ALL: List<SyncTable> = listOf(
        SyncTable("account", "id"), SyncTable("category", "id"), SyncTable("txn", "id"), SyncTable("budget", "id"),
        SyncTable("category_budget_limit", "id"), SyncTable("objective", "id"), SyncTable("title_memory", "id"),
        SyncTable("setting", "key"), SyncTable("loan_terms", "objectiveId"), SyncTable("fixed_deposit", "id"),
        SyncTable("card_due", "id"), SyncTable("pinned_entry", "id"), SyncTable("entry_rule", "id"),
        SyncTable("mutual_fund", "id"), SyncTable("nps_account", "id"), SyncTable("sinking_fund", "id"),
        SyncTable("manual_asset", "id"), SyncTable("asset_value", "id"),
    )

    fun byName(name: String): SyncTable? = ALL.firstOrNull { it.name == name }
}

/**
 * SQLite triggers that write one `sync_change` row whenever a synced row is inserted, updated or deleted. They do nothing while
 * `sync_state.applying` is `1`, which is how a row arriving from the cloud is written without being sent straight back.
 * `INSERT OR REPLACE` does not fire the delete trigger (SQLite's recursive triggers are off), so replacing a row counts as a change, not a delete.
 */
object SyncTriggers {
    private const val NOW_MS = "(CAST(strftime('%s','now') AS INTEGER) * 1000)"
    private const val IDLE = "(SELECT `value` FROM `sync_state` WHERE `key` = 'applying') IS NOT '1'"

    fun install(db: SupportSQLiteDatabase) {
        for (t in SyncTables.ALL) {
            val k = t.keyColumn
            fun mark(row: String, deleted: Int) =
                "INSERT OR REPLACE INTO `sync_change` (`tbl`, `rowId`, `atMs`, `deleted`) VALUES ('${t.name}', $row.`$k`, $NOW_MS, $deleted)"
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `sync_i_${t.name}` AFTER INSERT ON `${t.name}` WHEN $IDLE BEGIN ${mark("NEW", 0)}; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `sync_u_${t.name}` AFTER UPDATE ON `${t.name}` WHEN $IDLE BEGIN ${mark("NEW", 0)}; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `sync_d_${t.name}` AFTER DELETE ON `${t.name}` WHEN $IDLE BEGIN ${mark("OLD", 1)}; END")
        }
    }
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_change ORDER BY atMs, tbl, rowId LIMIT :limit") suspend fun pending(limit: Int): List<SyncChangeEntity>
    @Query("SELECT * FROM sync_change WHERE tbl = :tbl AND rowId = :rowId") suspend fun change(tbl: String, rowId: String): SyncChangeEntity?
    @Query("SELECT COUNT(*) FROM sync_change") fun observePendingCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_change") suspend fun pendingCount(): Int

    /** Forget a change once it is sent, but only if it did not change again while it was on its way. */
    @Query("DELETE FROM sync_change WHERE tbl = :tbl AND rowId = :rowId AND atMs = :atMs AND deleted = :deleted")
    suspend fun clearIfUnchanged(tbl: String, rowId: String, atMs: Long, deleted: Boolean): Int

    @Query("DELETE FROM sync_change WHERE tbl = :tbl AND rowId = :rowId") suspend fun clear(tbl: String, rowId: String)

    @Query("SELECT `value` FROM sync_state WHERE `key` = :key") suspend fun state(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setState(row: SyncStateEntity)
    @Query("DELETE FROM sync_state WHERE `key` = :key") suspend fun clearState(key: String)
}

/**
 * The database side of the personal sync (Phase 9c part 2): the list of what changed here, the device id, and the switch that keeps a cloud
 * row from being sent straight back. Reading and writing the rows themselves, and talking to Firestore, come in the next steps.
 */
class SyncStore(private val db: ChittaDatabase, private val newDeviceId: () -> String = { java.util.UUID.randomUUID().toString() }) {
    private val dao get() = db.sync()

    /** This phone's id in the sync, made once and kept (device-only; it is also stamped on every row this phone sends). */
    suspend fun deviceId(): String = db.withTransaction {
        dao.state(DEVICE_ID) ?: newDeviceId().also { dao.setState(SyncStateEntity(DEVICE_ID, it)) }
    }

    suspend fun pending(limit: Int): List<SyncChangeEntity> = dao.pending(limit)
    suspend fun pendingCount(): Int = dao.pendingCount()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    suspend fun change(tbl: String, rowId: String): SyncChangeEntity? = dao.change(tbl, rowId)

    /** Marks a change as sent. False when the row changed again meanwhile, so it stays waiting and goes up again. */
    suspend fun markSent(sent: SyncChangeEntity): Boolean = dao.clearIfUnchanged(sent.tbl, sent.rowId, sent.atMs, sent.deleted) > 0

    /** A cloud copy that lost to this phone's newer one is not applied; the waiting change stays and goes up. A cloud copy that won clears it. */
    suspend fun clear(tbl: String, rowId: String) = dao.clear(tbl, rowId)

    suspend fun cursor(): String? = dao.state(CURSOR)
    suspend fun setCursor(value: String) = dao.setState(SyncStateEntity(CURSOR, value))
    suspend fun lastSyncMs(): Long? = dao.state(LAST_SYNC)?.toLongOrNull()
    suspend fun setLastSyncMs(ms: Long) = dao.setState(SyncStateEntity(LAST_SYNC, ms.toString()))

    /**
     * The first time sync is switched on: every row already here is waiting to be sent, stamped 0 so that any row the cloud already has
     * (from another phone) is newer and wins, and a row only this phone has is sent. Rows that already have a waiting change keep it.
     * Runs once; later calls do nothing. Returns how many rows were marked.
     */
    suspend fun seedIfFirstTime(): Int = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        var marked = 0
        sql.beginTransaction()
        try {
            val done = sql.query("SELECT `value` FROM `sync_state` WHERE `key` = '$SEEDED'").use { it.moveToFirst() && it.getString(0) == "1" }
            if (!done) {
                for (t in SyncTables.ALL) {
                    sql.execSQL("INSERT OR IGNORE INTO `sync_change` (`tbl`, `rowId`, `atMs`, `deleted`) SELECT '${t.name}', `${t.keyColumn}`, 0, 0 FROM `${t.name}`")
                }
                marked = sql.query("SELECT COUNT(*) FROM `sync_change`").use { if (it.moveToFirst()) it.getInt(0) else 0 }
                sql.execSQL("INSERT OR REPLACE INTO `sync_state` (`key`, `value`) VALUES ('$SEEDED', '1')")
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshVersionsAsync()
        marked
    }

    /**
     * Runs [block] with the triggers switched off, in one transaction, so what it writes (rows from the cloud) is not marked as a local
     * change. The switch is always put back, and no other write can slip in between because the transaction holds the database.
     */
    suspend fun <T> applyingRemote(block: suspend () -> T): T = db.withTransaction {
        dao.setState(SyncStateEntity(APPLYING, "1"))
        try { block() } finally { dao.setState(SyncStateEntity(APPLYING, "0")) }
    }

    /** After the whole book is replaced (restore, import over the old book) every row is a change again, so the cloud follows this phone. */
    suspend fun markEverythingChanged(nowMs: Long): Unit = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            for (t in SyncTables.ALL) {
                sql.execSQL("INSERT OR REPLACE INTO `sync_change` (`tbl`, `rowId`, `atMs`, `deleted`) SELECT '${t.name}', `${t.keyColumn}`, $nowMs, 0 FROM `${t.name}`")
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshVersionsAsync()
    }

    companion object {
        const val DEVICE_ID = "deviceId"
        const val CURSOR = "cursor"
        const val LAST_SYNC = "lastSyncMs"
        const val SEEDED = "seeded"
        const val APPLYING = "applying"
    }
}
