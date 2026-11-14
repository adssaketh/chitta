package com.mansaketh.chitta.core.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A file in the app's hidden Drive folder, as Drive describes it. */
data class DriveFile(val id: String, val name: String, val sizeBytes: Long, val modifiedMs: Long)

/** A Chitta backup in Drive: a [DriveFile] whose name says when the copy was made. */
data class CloudBackup(val file: DriveFile, val createdAtMs: Long)

/**
 * Rules for the backups kept in Drive (Phase 9a): how they are named, which are Chitta's own, how many are kept, and when the
 * automatic one is due. Plain Kotlin; the network and the sign-in live in the app.
 */
object CloudBackups {
    /** The newest this many Chitta backups stay in Drive (master section 7: "keep latest 20"). */
    const val KEEP = 20

    private val NAME = Regex("^chitta-(\\d+)\\.db$")

    /** The name of a backup made at [atMs]. The time is in the name so the list is right even if Drive's own dates change. */
    fun fileName(atMs: Long) = "chitta-$atMs.db"

    /** The backup this file is, or null for any other file (a Paddhu backup, a stray file). */
    fun parse(file: DriveFile): CloudBackup? {
        val m = NAME.matchEntire(file.name) ?: return null
        val at = m.groupValues[1].toLongOrNull() ?: return null
        return CloudBackup(file, at)
    }

    /** Chitta's own backups, newest first. */
    fun backups(files: List<DriveFile>): List<CloudBackup> = files.mapNotNull(::parse).sortedByDescending { it.createdAtMs }

    /** Files in the folder that are not Chitta backups, newest first. These are never deleted by Chitta. */
    fun others(files: List<DriveFile>): List<DriveFile> = files.filter { parse(it) == null }.sortedByDescending { it.modifiedMs }

    /**
     * What to delete after a new backup has arrived safely: every Chitta backup beyond the newest [KEEP]. Other files are never
     * listed here, so an old Paddhu backup in the same folder is left alone.
     */
    fun toDelete(files: List<DriveFile>): List<DriveFile> = backups(files).drop(KEEP).map { it.file }

    /**
     * The automatic backup is due when none has been made on today's date in [zone]. A last backup dated in the future (the clock was
     * set back) counts as due, so a wrong clock never stops backups for good.
     */
    fun isAutoDue(lastSuccessMs: Long?, nowMs: Long, zone: ZoneId): Boolean {
        if (lastSuccessMs == null || lastSuccessMs <= 0L) return true
        val last = Instant.ofEpochMilli(lastSuccessMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return last != today
    }

    /** Whole calendar days since the last backup in [zone], or null when there has been none. Never negative. */
    fun daysSince(lastSuccessMs: Long?, nowMs: Long, zone: ZoneId): Long? {
        if (lastSuccessMs == null || lastSuccessMs <= 0L) return null
        val last = Instant.ofEpochMilli(lastSuccessMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(last, today).coerceAtLeast(0)
    }

    /** A plain sentence when automatic backups are on but have not worked for a few days; null when there is nothing to say. */
    fun behindNote(auto: Boolean, lastSuccessMs: Long?, nowMs: Long, zone: ZoneId): String? {
        if (!auto) return null
        val days = daysSince(lastSuccessMs, nowMs, zone) ?: return "No backup has been made yet."
        return if (days >= 3) "The last backup was $days days ago." else null
    }
}
