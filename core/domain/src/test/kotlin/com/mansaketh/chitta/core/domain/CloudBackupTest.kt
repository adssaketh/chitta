package com.mansaketh.chitta.core.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val KOLKATA = ZoneId.of("Asia/Kolkata")
private val UTC = ZoneId.of("UTC")
private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
private fun backupFile(atMs: Long, id: String = "id$atMs") = DriveFile(id, CloudBackups.fileName(atMs), 1000, atMs)

class CloudBackupsTest {
    @Test fun nameRoundTripsAndIsReadBack() {
        val ms = at(KOLKATA, 2026, 11, 14, 10, 57)
        val name = CloudBackups.fileName(ms)
        assertEquals("chitta-$ms.db", name)
        val parsed = assertNotNull(CloudBackups.parse(DriveFile("a", name, 5, 0)))
        assertEquals(ms, parsed.createdAtMs)
    }

    @Test fun otherFilesAreNotBackups() {
        listOf("cashew-2026-11-01.sqlite", "chitta-.db", "chitta-12x.db", "chitta-12.db.bak", "Chitta-12.db", "rp-auto-12.db", "").forEach {
            assertNull(CloudBackups.parse(DriveFile("a", it, 5, 0)), "'$it' must not count as a Chitta backup")
        }
        assertNotNull(CloudBackups.parse(DriveFile("a", "chitta-0.db", 5, 0)))
    }

    @Test fun backupsAreNewestFirstAndOthersAreKeptApart() {
        val paddhu = DriveFile("p", "paddhu-backup.sqlite", 9000, 777)
        val files = listOf(backupFile(100), paddhu, backupFile(300), backupFile(200))
        assertEquals(listOf(300L, 200L, 100L), CloudBackups.backups(files).map { it.createdAtMs })
        assertEquals(listOf(paddhu), CloudBackups.others(files))
    }

    @Test fun nothingIsDeletedUpToTwentyBackups() {
        val files = (1..20).map { backupFile(it * 1000L) }
        assertTrue(CloudBackups.toDelete(files).isEmpty())
    }

    @Test fun theOldestAreDeletedBeyondTwentyAndOtherFilesNever() {
        val paddhu = DriveFile("p", "paddhu-backup.sqlite", 9000, 1)
        val files = (1..25).map { backupFile(it * 1000L) } + paddhu
        val gone = CloudBackups.toDelete(files)
        assertEquals(5, gone.size)
        assertEquals(setOf("id1000", "id2000", "id3000", "id4000", "id5000"), gone.map { it.id }.toSet())
        assertFalse(gone.any { it.id == "p" })
    }

    @Test fun orderComesFromTheNameNotFromDrivesOwnDate() {
        // Drive says the oldest-named file was changed most recently: the name still decides which is oldest.
        val old = DriveFile("old", CloudBackups.fileName(1_000), 1, modifiedMs = 9_999_999)
        val rest = (2..21).map { backupFile(it * 1000L) }
        assertEquals(listOf("old"), CloudBackups.toDelete(rest + old).map { it.id })
    }

    @Test fun autoBackupIsDueWhenNoneHasBeenMade() {
        assertTrue(CloudBackups.isAutoDue(null, at(KOLKATA, 2026, 11, 14, 9), KOLKATA))
        assertTrue(CloudBackups.isAutoDue(0L, at(KOLKATA, 2026, 11, 14, 9), KOLKATA))
    }

    @Test fun autoBackupIsNotDueTwiceOnOneDay() {
        val last = at(KOLKATA, 2026, 11, 14, 0, 5)
        assertFalse(CloudBackups.isAutoDue(last, at(KOLKATA, 2026, 11, 14, 23, 59), KOLKATA))
    }

    @Test fun autoBackupIsDueJustAfterMidnight() {
        val last = at(KOLKATA, 2026, 11, 14, 23, 30)
        assertTrue(CloudBackups.isAutoDue(last, at(KOLKATA, 2026, 11, 15, 0, 10), KOLKATA))
    }

    @Test fun theDayIsTheOneInThePhonesZone() {
        // 23:30 UTC on the 14th is already 05:00 on the 15th in Kolkata.
        val last = at(UTC, 2026, 11, 14, 23, 30)
        val now = at(UTC, 2026, 11, 15, 1, 0)
        assertTrue(CloudBackups.isAutoDue(last, now, UTC))
        assertFalse(CloudBackups.isAutoDue(last, now, KOLKATA))
    }

    @Test fun aClockSetBackStillGetsABackup() {
        val last = at(KOLKATA, 2026, 11, 20, 9)
        assertTrue(CloudBackups.isAutoDue(last, at(KOLKATA, 2026, 11, 14, 9), KOLKATA))
        assertEquals(0L, CloudBackups.daysSince(last, at(KOLKATA, 2026, 11, 14, 9), KOLKATA))
    }

    @Test fun daysSinceCountsCalendarDays() {
        val last = at(KOLKATA, 2026, 2, 27, 23, 50)
        assertEquals(1L, CloudBackups.daysSince(last, at(KOLKATA, 2026, 2, 28, 0, 10), KOLKATA))
        assertEquals(2L, CloudBackups.daysSince(last, at(KOLKATA, 2026, 3, 1, 8), KOLKATA)) // 2026 is not a leap year
        assertNull(CloudBackups.daysSince(null, 5, KOLKATA))
        val leapLast = at(KOLKATA, 2028, 2, 28, 12)
        assertEquals(2L, CloudBackups.daysSince(leapLast, at(KOLKATA, 2028, 3, 1, 12), KOLKATA)) // 29 Feb exists in 2028
    }

    @Test fun behindNoteOnlyWhenAutoIsOnAndDaysHavePassed() {
        val now = at(KOLKATA, 2026, 11, 14, 9)
        assertNull(CloudBackups.behindNote(false, null, now, KOLKATA))
        assertEquals("No backup has been made yet.", CloudBackups.behindNote(true, null, now, KOLKATA))
        assertNull(CloudBackups.behindNote(true, at(KOLKATA, 2026, 11, 12, 9), now, KOLKATA))
        assertEquals("The last backup was 3 days ago.", CloudBackups.behindNote(true, at(KOLKATA, 2026, 11, 11, 9), now, KOLKATA))
    }
}
