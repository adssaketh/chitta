package com.mansaketh.chitta.core.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How long Chitta may stay in the background before it locks again. */
enum class LockTimeout(val key: String, val label: String, val ms: Long) {
    Immediately("0", "Right away", 0L),
    OneMinute("1", "After 1 minute", 60_000L),
    FiveMinutes("5", "After 5 minutes", 300_000L);

    companion object {
        fun fromKey(key: String?): LockTimeout = entries.firstOrNull { it.key == key } ?: OneMinute
    }
}

object LockPolicy {
    /**
     * Whether the lock screen should be showing when the app comes to the front. Locked stays locked. A clock that went backwards
     * counts as "away too long", so changing the time never skips the lock.
     */
    fun shouldLock(enabled: Boolean, alreadyLocked: Boolean, leftAtMs: Long?, nowMs: Long, timeout: LockTimeout): Boolean {
        if (!enabled) return false
        if (alreadyLocked) return true
        if (leftAtMs == null) return false
        val away = nowMs - leftAtMs
        return away < 0 || away >= timeout.ms
    }
}

/** Why a restore point was made. The name of the file carries it, so the list can explain itself. */
enum class RestoreKind(val tag: String, val label: String) {
    Auto("auto", "Automatic"),
    Manual("manual", "Made by you"),
    BeforeRestore("before-restore", "Before a restore"),
    BeforeImport("before-import", "Before a CSV import"),
    BeforeDelete("before-delete", "Before deleting everything"),
}

data class RestorePoint(val fileName: String, val kind: RestoreKind, val createdAtMs: Long, val sizeBytes: Long)

/** Rules for the local restore points (Data Recovery): when to make one, what to keep, how they are named. */
object RestorePoints {
    const val KEEP_AUTO = 10
    const val KEEP_OTHER = 10

    fun fileName(kind: RestoreKind, atMs: Long) = "rp-${kind.tag}-$atMs.db"

    private val NAME = Regex("^rp-([a-z-]+)-(\\d+)\\.db$")

    fun parse(fileName: String, sizeBytes: Long): RestorePoint? {
        val m = NAME.matchEntire(fileName) ?: return null
        val kind = RestoreKind.entries.firstOrNull { it.tag == m.groupValues[1] } ?: return null
        val at = m.groupValues[2].toLongOrNull() ?: return null
        return RestorePoint(fileName, kind, at, sizeBytes)
    }

    /** Newest first. */
    fun sorted(points: List<RestorePoint>) = points.sortedByDescending { it.createdAtMs }

    /** One automatic point per calendar day: due when none exists for today (in [zone]). */
    fun isAutoDue(points: List<RestorePoint>, nowMs: Long, zone: ZoneId): Boolean {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return points.none { it.kind == RestoreKind.Auto && Instant.ofEpochMilli(it.createdAtMs).atZone(zone).toLocalDate() == today }
    }

    /** What to delete so that only the newest [KEEP_AUTO] automatic points and the newest [KEEP_OTHER] of all other kinds stay. */
    fun toDelete(points: List<RestorePoint>): List<RestorePoint> {
        val (auto, other) = sorted(points).partition { it.kind == RestoreKind.Auto }
        return auto.drop(KEEP_AUTO) + other.drop(KEEP_OTHER)
    }

    /** The name suggested when saving a backup file to Downloads or Drive. */
    fun exportName(nowMs: Long, zone: ZoneId): String =
        "chitta-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(Instant.ofEpochMilli(nowMs).atZone(zone)) + ".db"
}

/** What a picked file turned out to be, decided from facts the phone reads out of it. */
sealed interface BackupVerdict {
    data class Ok(val version: Int) : BackupVerdict
    data class Refused(val reason: String) : BackupVerdict

    companion object {
        /**
         * [userVersion] is the file's schema number, [isRoomFile] whether it has Room's bookkeeping table, [hasChittaTables] whether the
         * book's tables are there, [looksLikePaddhu] whether it has Paddhu's own tables, and [integrityOk] whether SQLite's quick check passed.
         */
        fun judge(userVersion: Int, isRoomFile: Boolean, hasChittaTables: Boolean, looksLikePaddhu: Boolean, integrityOk: Boolean, currentVersion: Int): BackupVerdict = when {
            looksLikePaddhu && !isRoomFile -> Refused("This is a Paddhu Pusthakam backup. Use \"Bring over your book\" for that one.")
            !isRoomFile || !hasChittaTables -> Refused("This file is not a Chitta backup.")
            !integrityOk -> Refused("This backup file is damaged, so it was not used.")
            userVersion > currentVersion -> Refused("This backup was made by a newer version of Chitta. Update the app first.")
            userVersion < 1 -> Refused("This file is not a Chitta backup.")
            else -> Ok(userVersion)
        }
    }
}

/** The date ranges offered when exporting CSV. [bounds] gives (from inclusive, to exclusive) in epoch milliseconds, null meaning no limit. */
enum class ExportRange(val label: String) {
    All("Everything"),
    ThisMonth("This month"),
    Last30Days("Past 30 days"),
    ThisYear("This calendar year");

    fun bounds(nowMs: Long, zone: ZoneId): Pair<Long?, Long?> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        fun start(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (this) {
            All -> null to null
            ThisMonth -> start(today.withDayOfMonth(1)) to start(today.withDayOfMonth(1).plusMonths(1))
            Last30Days -> start(today.minusDays(29)) to start(today.plusDays(1))
            ThisYear -> start(today.withDayOfYear(1)) to start(today.withDayOfYear(1).plusYears(1))
        }
    }
}
