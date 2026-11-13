package com.mansaketh.chitta.core.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.mansaketh.chitta.core.domain.BackupVerdict
import java.io.File

/** What was read out of a picked or kept backup file, without opening it as the live book. */
data class BackupFacts(val verdict: BackupVerdict, val accounts: Int, val entries: Int)

/**
 * Copies of the whole book as plain database files: restore points, the backup file you save and the one you bring back.
 * Nothing here touches the live database except [snapshot], which only reads it.
 */
object DatabaseFiles {
    private const val PENDING = "pending.db"

    fun restoreDir(context: Context): File = File(context.filesDir, "restore").apply { mkdirs() }

    /**
     * A consistent copy of the live book in [dest]. Uses SQLite's `VACUUM INTO` where the phone's SQLite has it (Android 11 and newer),
     * and otherwise flushes the write-ahead log into the main file and copies that (DECISIONS D70).
     */
    fun snapshot(db: ChittaDatabase, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        val live = db.openHelper.writableDatabase
        try {
            live.execSQL("VACUUM INTO " + DatabaseUtils.sqlEscapeString(dest.absolutePath))
        } catch (e: Exception) {
            dest.delete()
            live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            File(requireNotNull(live.path) { "The database has no file path." }).copyTo(dest, overwrite = true)
        }
    }

    /** Reads a file as a read-only database and says whether it is a usable Chitta backup. Null when it cannot be opened at all. */
    fun inspect(file: File): BackupFacts? {
        val db = try { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) } catch (e: Exception) { return null }
        try {
            val tables = HashSet<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
            val isRoom = "room_master_table" in tables
            val hasTables = listOf("account", "category", "txn").all { it in tables }
            val paddhu = "wallets" in tables && "transactions" in tables
            val integrity = try { db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" } } catch (e: Exception) { false }
            fun count(table: String) = if (table in tables) DatabaseUtils.queryNumEntries(db, table).toInt() else 0
            val verdict = BackupVerdict.judge(db.version, isRoom, hasTables, paddhu, integrity, ChittaDatabase.VERSION)
            return BackupFacts(verdict, count("account"), count("txn"))
        } catch (e: Exception) {
            return null
        } finally {
            db.close()
        }
    }

    /** Keeps [source] to be swapped in the next time the app starts, before the book is opened. The live book is not touched. */
    fun stageRestore(context: Context, source: File) {
        val dir = restoreDir(context)
        val tmp = File(dir, "$PENDING.tmp")
        source.copyTo(tmp, overwrite = true)
        val pending = File(dir, PENDING)
        if (pending.exists()) pending.delete()
        if (!tmp.renameTo(pending)) { tmp.copyTo(pending, overwrite = true); tmp.delete() }
    }

    fun hasPending(context: Context): Boolean = File(restoreDir(context), PENDING).exists()

    /**
     * Run once, very early at start-up (before Room opens the book): if a restore is staged, replaces the book's file with it and clears
     * the old write-ahead files. Returns true when a restore was applied. A failure leaves the old book in place and drops the staged file.
     */
    fun applyPending(context: Context): Boolean {
        val pending = File(restoreDir(context), PENDING)
        if (!pending.exists()) return false
        return try {
            val target = context.getDatabasePath(ChittaDatabase.FILE_NAME)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, ChittaDatabase.FILE_NAME + ".restoring")
            pending.copyTo(tmp, overwrite = true)
            listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            true
        } catch (e: Exception) {
            false
        } finally {
            pending.delete()
        }
    }
}
