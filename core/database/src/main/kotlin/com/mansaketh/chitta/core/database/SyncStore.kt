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
import com.mansaketh.chitta.core.domain.RowStamp
import com.mansaketh.chitta.core.domain.SyncDecision
import com.mansaketh.chitta.core.domain.SyncMerge
import com.mansaketh.chitta.core.domain.SyncRows
import com.mansaketh.chitta.core.domain.SyncSettings
import com.mansaketh.chitta.core.domain.WireRow

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

    // ---- reading and writing whole rows (Phase 9c part 2, step 2)

    /** What [applyRemote] did with a page of cloud rows. */
    data class ApplyReport(val applied: Int, val keptLocal: Int, val skipped: Int)

    /** One row as column name to value (Long, Double, String or null), or null when there is no such row. */
    suspend fun readRow(tbl: String, rowId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val t = SyncTables.byName(tbl) ?: return@withContext null
        db.openHelper.readableDatabase.query("SELECT * FROM `${t.name}` WHERE `${t.keyColumn}` = ?", arrayOf<Any>(rowId)).use { c ->
            if (!c.moveToFirst()) return@use null
            val out = LinkedHashMap<String, Any?>()
            for (i in 0 until c.columnCount) {
                out[c.getColumnName(i)] = when (c.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> null
                    android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    android.database.Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> error("Column ${c.getColumnName(i)} of ${t.name} holds a blob; the sync does not carry blobs.")
                }
            }
            out
        }
    }

    /** The owner of one entry (null when it has none or the row is gone), for the "is this mine" rule. */
    suspend fun ownerOfEntry(rowId: String): String? = withContext(Dispatchers.IO) {
        db.openHelper.readableDatabase.query("SELECT `ownerUid` FROM `txn` WHERE `id` = ?", arrayOf<Any>(rowId)).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /** Every row id a synced table holds right now. */
    suspend fun localRowIds(tbl: String): Set<String> = withContext(Dispatchers.IO) {
        val t = SyncTables.byName(tbl) ?: return@withContext emptySet()
        db.openHelper.readableDatabase.query("SELECT `${t.keyColumn}` FROM `${t.name}`").use { c ->
            val ids = HashSet<String>()
            while (c.moveToNext()) ids.add(c.getString(0))
            ids
        }
    }

    private data class Col(val name: String, val type: String, val notNull: Boolean, val hasDefault: Boolean)

    private fun columnsOf(sql: SupportSQLiteDatabase, table: String): List<Col> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val out = ArrayList<Col>()
            val nameAt = c.getColumnIndexOrThrow("name"); val typeAt = c.getColumnIndexOrThrow("type")
            val notNullAt = c.getColumnIndexOrThrow("notnull"); val defaultAt = c.getColumnIndexOrThrow("dflt_value")
            while (c.moveToNext()) out.add(Col(c.getString(nameAt), c.getString(typeAt).uppercase(), c.getInt(notNullAt) != 0, !c.isNull(defaultAt)))
            out
        }

    /**
     * Applies one page of rows from the cloud, in one database transaction with the triggers off. For each row the newer stamp wins
     * ([SyncMerge.decide]) against this phone's waiting change, if any: a cloud row that wins is written (or the row is deleted, for a delete
     * marker) and the waiting change is dropped; one that loses is left alone and this phone's change goes up later.
     * Never written: device-only settings, other members' placeholder accounts, and no delete marker ever removes another member's entry.
     * Columns this phone does not have (a newer phone's) are ignored; columns the cloud row lacks keep their defaults.
     */
    suspend fun applyRemote(rows: List<WireRow>, myDeviceId: String, myUid: String): ApplyReport = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        var applied = 0; var keptLocal = 0; var skipped = 0
        val columns = HashMap<String, List<Col>>()
        sql.beginTransaction()
        try {
            sql.execSQL("INSERT OR REPLACE INTO `sync_state` (`key`, `value`) VALUES ('$APPLYING', '1')")
            for (r in rows) {
                val t = SyncTables.byName(r.tbl)
                if (t == null || !SyncRows.syncsRow(r.tbl, r.rowId, null, myUid)) { skipped++; continue }
                val pending = sql.query("SELECT `atMs`, `deleted` FROM `sync_change` WHERE `tbl` = ? AND `rowId` = ?", arrayOf<Any>(r.tbl, r.rowId)).use {
                    if (it.moveToFirst()) RowStamp(it.getLong(0), myDeviceId, it.getInt(1) != 0) else null
                }
                when (SyncMerge.decide(pending, r.stamp)) {
                    SyncDecision.KeepLocal -> keptLocal++
                    SyncDecision.Same -> sql.execSQL("DELETE FROM `sync_change` WHERE `tbl` = ? AND `rowId` = ?", arrayOf<Any>(r.tbl, r.rowId))
                    SyncDecision.TakeRemote -> {
                        if (r.deleted) {
                            val foreign = r.tbl == "txn" && !SyncRows.isOwnEntry(
                                sql.query("SELECT `ownerUid` FROM `txn` WHERE `id` = ?", arrayOf<Any>(r.rowId)).use { if (it.moveToFirst()) it.getString(0) else null }, myUid,
                            )
                            if (foreign) { skipped++; continue }
                            sql.execSQL("DELETE FROM `${t.name}` WHERE `${t.keyColumn}` = ?", arrayOf<Any>(r.rowId))
                        } else {
                            val known = columns.getOrPut(t.name) { columnsOf(sql, t.name) }
                            if (t.keyColumn !in r.values) { skipped++; continue }
                            // Columns this phone has: the cloud's value; or, when an older phone's row lacks one this phone requires, a plain 0 or empty text.
                            val used = known.mapNotNull { col ->
                                when {
                                    col.name in r.values -> col.name to r.values[col.name]
                                    col.notNull && !col.hasDefault -> col.name to (if (col.type.contains("CHAR") || col.type.contains("TEXT")) "" else 0L)
                                    else -> null
                                }
                            }
                            val names = used.joinToString(", ") { "`${it.first}`" }
                            val marks = used.joinToString(", ") { "?" }
                            sql.execSQL("INSERT OR REPLACE INTO `${t.name}` ($names) VALUES ($marks)", used.map { it.second }.toTypedArray())
                        }
                        sql.execSQL("DELETE FROM `sync_change` WHERE `tbl` = ? AND `rowId` = ?", arrayOf<Any>(r.tbl, r.rowId))
                        applied++
                    }
                }
            }
            sql.execSQL("INSERT OR REPLACE INTO `sync_state` (`key`, `value`) VALUES ('$APPLYING', '0')")
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        if (applied > 0) db.invalidationTracker.refreshVersionsAsync()
        ApplyReport(applied, keptLocal, skipped)
    }

    /** Every row not yet waiting is marked as a stamp-0 change, so the cloud's copy wins where it has one and rows only this phone has go up. Used by "Merge". */
    suspend fun markMissingAtZero(): Unit = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            for (t in SyncTables.ALL) {
                sql.execSQL("INSERT OR IGNORE INTO `sync_change` (`tbl`, `rowId`, `atMs`, `deleted`) SELECT '${t.name}', `${t.keyColumn}`, 0, 0 FROM `${t.name}`")
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshVersionsAsync()
    }

    /**
     * "Replace this phone with the cloud": removes this person's own rows and every waiting change, without marking anything as a change.
     * Other members' entries, their placeholder accounts and device-only settings stay.
     */
    suspend fun wipeOwnRows(myUid: String): Unit = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            sql.execSQL("INSERT OR REPLACE INTO `sync_state` (`key`, `value`) VALUES ('$APPLYING', '1')")
            for (t in SyncTables.ALL) {
                when (t.name) {
                    "txn" -> sql.execSQL("DELETE FROM `txn` WHERE `ownerUid` IS NULL OR `ownerUid` = '' OR `ownerUid` = ?", arrayOf<Any>(myUid))
                    "account" -> sql.execSQL("DELETE FROM `account` WHERE `id` NOT LIKE 'hh\\_%' ESCAPE '\\'")
                    "setting" -> {
                        val keys = sql.query("SELECT `key` FROM `setting`").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
                        for (k in keys) if (SyncSettings.syncs(k)) sql.execSQL("DELETE FROM `setting` WHERE `key` = ?", arrayOf<Any>(k))
                    }
                    else -> sql.execSQL("DELETE FROM `${t.name}`")
                }
            }
            sql.execSQL("DELETE FROM `sync_change`")
            sql.execSQL("INSERT OR REPLACE INTO `sync_state` (`key`, `value`) VALUES ('$APPLYING', '0')")
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshVersionsAsync()
    }

    /** A new device id and no cloud position: this phone starts over as a different device (after the whole book was replaced). */
    suspend fun startOver(): String {
        val id = newDeviceId()
        dao.setState(SyncStateEntity(DEVICE_ID, id))
        dao.clearState(CURSOR)
        dao.clearState(LAST_SYNC)
        dao.clearState(SEEDED) // the next round marks every row again, so a different cloud (another Google account) gets the whole book
        return id
    }

    /** Forget where the cloud read stopped, so the next round reads everything again. */
    suspend fun clearCursor() = dao.clearState(CURSOR)

    companion object {
        const val DEVICE_ID = "deviceId"
        const val CURSOR = "cursor"
        const val LAST_SYNC = "lastSyncMs"
        const val SEEDED = "seeded"
        const val APPLYING = "applying"
    }
}
