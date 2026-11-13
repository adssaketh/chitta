package com.mansaketh.chitta.core.importer

/** One database row: column name to value (Long, Double, String, ByteArray or null). */
typealias Row = Map<String, Any?>

/**
 * Where the importer reads a Paddhu backup from. The backup file is only ever opened read-only.
 * Two implementations: sqlite-jdbc in JVM tests, and android.database.sqlite on the phone (core:database).
 */
interface BackupSource {
    /** `PRAGMA user_version`: Drift's schema version. */
    fun userVersion(): Int
    fun hasTable(name: String): Boolean
    fun rows(table: String): List<Row>
}

internal fun Row.str(key: String): String? = this[key]?.toString()
internal fun Row.strOrEmpty(key: String): String = this[key]?.toString() ?: ""
internal fun Row.long(key: String): Long? = (this[key] as? Number)?.toLong()
internal fun Row.int(key: String): Int? = (this[key] as? Number)?.toInt()
internal fun Row.bool(key: String): Boolean = ((this[key] as? Number)?.toLong() ?: 0L) != 0L
internal fun Row.boolOrNull(key: String): Boolean? = (this[key] as? Number)?.let { it.toLong() != 0L }
internal fun Row.double(key: String): Double? = (this[key] as? Number)?.toDouble()
