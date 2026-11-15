package com.mansaketh.chitta.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun at(ms: Long, device: String, deleted: Boolean = false) = RowStamp(ms, device, deleted)

class SyncTest {
    // Worked example 1: phone A changed the entry at 10:00:01, phone B at 10:00:05. B's edit is newer, so both end up with B's.
    @Test fun newerEditWinsOnBothSides() {
        val a = at(36_001_000, "A")
        val b = at(36_005_000, "B")
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(local = a, remote = b)) // on phone A: take B's
        assertEquals(SyncDecision.KeepLocal, SyncMerge.decide(local = b, remote = a)) // on phone B: keep and send
    }

    // Worked example 2: the same millisecond on two phones. The larger device id wins, on both phones, so they do not swap forever.
    @Test fun tieGoesToTheLargerDeviceIdOnBothSides() {
        val a = at(5_000, "phone-a")
        val b = at(5_000, "phone-b")
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(a, b))
        assertEquals(SyncDecision.KeepLocal, SyncMerge.decide(b, a))
    }

    @Test fun identicalStampsAreTheSame() {
        assertEquals(SyncDecision.Same, SyncMerge.decide(at(7, "A"), at(7, "A")))
        assertEquals(SyncDecision.Same, SyncMerge.decide(null, null))
    }

    @Test fun aRowOnlyOneSideKnowsMovesToTheOther() {
        assertEquals(SyncDecision.KeepLocal, SyncMerge.decide(at(1, "A"), null))
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(null, at(1, "B")))
        // A delete marker the phone never saw a row for is taken too, so it can pass it on.
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(null, at(1, "B", deleted = true)))
    }

    // Worked example 3: a delete at 3000 beats an edit at 2000; an edit at 4000 beats that delete.
    @Test fun deleteAndEditAreJustStamps() {
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(local = at(2_000, "A"), remote = at(3_000, "B", deleted = true)))
        assertEquals(SyncDecision.KeepLocal, SyncMerge.decide(local = at(4_000, "A"), remote = at(3_000, "B", deleted = true)))
    }

    @Test fun sameDeviceSameMillisecondDeleteIsTheLaterAct() {
        assertEquals(SyncDecision.KeepLocal, SyncMerge.decide(at(9, "A", deleted = true), at(9, "A")))
        assertEquals(SyncDecision.TakeRemote, SyncMerge.decide(at(9, "A"), at(9, "A", deleted = true)))
    }

    /** Whatever the stamps, exactly one side takes, or both agree: the two phones can never both give way or both insist. */
    @Test fun bothSidesAlwaysReachTheSameWinner() {
        val stamps = listOf(null, at(1, "A"), at(1, "B"), at(2, "A"), at(2, "A", deleted = true), at(2, "B", deleted = true), at(3, "C"))
        for (x in stamps) for (y in stamps) {
            val onX = SyncMerge.decide(x, y)
            val onY = SyncMerge.decide(y, x)
            val ok = when (onX) {
                SyncDecision.Same -> onY == SyncDecision.Same
                SyncDecision.KeepLocal -> onY == SyncDecision.TakeRemote
                SyncDecision.TakeRemote -> onY == SyncDecision.KeepLocal
            }
            assertTrue(ok, "x=$x y=$y gave $onX / $onY")
        }
    }

    @Test fun deleteMarkersExpireAfter180Days() {
        val day = 86_400_000L
        assertFalse(SyncMerge.tombstoneExpired(0, 179 * day))
        assertTrue(SyncMerge.tombstoneExpired(0, 180 * day))
        assertFalse(SyncMerge.tooStale(null, 999 * day)) // never synced: nothing to be stale about
        assertFalse(SyncMerge.tooStale(10 * day, 189 * day))
        assertTrue(SyncMerge.tooStale(10 * day, 190 * day))
    }

    @Test fun documentIdsRoundTripEvenWithSlashesAndPercents() {
        assertEquals("txn~abc-123", SyncRows.docId("txn", "abc-123"))
        for (id in listOf("abc-123", "a/b", "100%", "a%2Fb", "x~y")) {
            assertEquals("txn" to id, SyncRows.parseDocId(SyncRows.docId("txn", id)), id)
        }
        assertFalse(SyncRows.docId("setting", "a/b").contains('/'))
        assertNull(SyncRows.parseDocId("nodivider"))
        assertNull(SyncRows.parseDocId("~x"))
        assertNull(SyncRows.parseDocId("txn~"))
    }

    @Test fun householdCopiesAreNotThisPersonsOwn() {
        assertTrue(SyncRows.isOwnEntry(null, "me"))
        assertTrue(SyncRows.isOwnEntry("", "me"))
        assertTrue(SyncRows.isOwnEntry("me", "me"))
        assertFalse(SyncRows.isOwnEntry("other", "me"))
        assertTrue(SyncRows.isHouseholdPlaceholderAccount("hh_u1"))
        assertFalse(SyncRows.isHouseholdPlaceholderAccount("a1"))
    }

    @Test fun deviceOnlySettingsDoNotSync() {
        assertFalse(SyncSettings.syncs("lastBackup"))
        assertFalse(SyncSettings.syncs("householdId"))
        assertFalse(SyncSettings.syncs("householdAnythingNew")) // any key starting "household" stays on the phone
        assertFalse(SyncSettings.syncs("requireAuth"))
        assertTrue(SyncSettings.syncs("theme"))
        assertTrue(SyncSettings.syncs("hideAmounts"))
    }

    // Worked example 4: the server says 10:00:00; a phone whose clock reads 10:01:59 is fine, 10:02:01 is not (limit 2 minutes).
    @Test fun clockSkewLimit() {
        val server = 36_000_000L
        assertFalse(SyncMerge.skewTooBig(server + 119_000, server))
        assertTrue(SyncMerge.skewTooBig(server + 121_000, server))
        assertTrue(SyncMerge.skewTooBig(server - 121_000, server))
    }

    @Test fun onlyOwnRowsGoToTheCloud() {
        assertFalse(SyncRows.syncsRow("account", "hh_u1", "u1", "me"))
        assertTrue(SyncRows.syncsRow("account", "acc-cash", null, "me"))
        assertFalse(SyncRows.syncsRow("txn", "t1", "other", "me"))
        assertTrue(SyncRows.syncsRow("txn", "t1", "me", "me"))
        assertTrue(SyncRows.syncsRow("txn", "t1", null, "me"))
        assertFalse(SyncRows.syncsRow("setting", "lastBackup", null, "me"))
        assertTrue(SyncRows.syncsRow("setting", "theme", null, "me"))
        assertTrue(SyncRows.syncsRow("budget", "b1", null, "me"))
    }

    // Worked example 5: 59 s is "just now"; 5 min; 3 h; 2 days.
    @Test fun agoWords() {
        val now = 10L * 86_400_000L
        assertEquals("just now", SyncStatus.ago(now - 59_000, now))
        assertEquals("5 min ago", SyncStatus.ago(now - 5 * 60_000, now))
        assertEquals("3 h ago", SyncStatus.ago(now - 3 * 3_600_000, now))
        assertEquals("2 days ago", SyncStatus.ago(now - 2 * 86_400_000, now))
    }

    @Test fun statusLine() {
        val now = 10L * 86_400_000L
        assertEquals("Not signed in", SyncStatus.line(false, false, 0, null, false, now))
        assertEquals("Needs your choice", SyncStatus.line(true, false, 0, null, true, now))
        assertEquals("Syncing", SyncStatus.line(true, true, 3, now, false, now))
        assertEquals("3 waiting to send", SyncStatus.line(true, false, 3, now, false, now))
        assertEquals("Not synced yet", SyncStatus.line(true, false, 0, null, false, now))
        assertEquals("Up to date, 5 min ago", SyncStatus.line(true, false, 0, now - 5 * 60_000, false, now))
    }
}
