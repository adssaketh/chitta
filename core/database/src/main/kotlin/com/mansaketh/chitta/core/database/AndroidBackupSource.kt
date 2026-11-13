package com.mansaketh.chitta.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mansaketh.chitta.core.importer.BackupSource
import com.mansaketh.chitta.core.importer.Row
import java.io.File

/**
 * Reads a Paddhu backup on the phone. The file is opened with OPEN_READONLY and is never written to.
 * Copy the picked file into the app's cache first; do not open a file the user still has open elsewhere.
 */
class AndroidBackupSource(file: File) : BackupSource, AutoCloseable {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    override fun userVersion(): Int = db.version

    override fun hasTable(name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    override fun rows(table: String): List<Row> =
        db.rawQuery("SELECT * FROM \"$table\"", null).use { c ->
            val names = c.columnNames
            buildList { while (c.moveToNext()) add(names.indices.associate { i -> names[i] to c.value(i) }) }
        }

    private fun Cursor.value(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> getBlob(i)
        else -> getString(i)
    }

    override fun close() = db.close()
}
