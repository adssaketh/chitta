package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun account(id: String, name: String = id) = AccountEntity(id, name, null, null, 1L, null, 0, "INR", null, 2, null, null)

/** The sync triggers and the change list, on a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncStoreTest {
    private lateinit var db: ChittaDatabase
    private lateinit var store: SyncStore

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java)
            .addCallback(ChittaDatabase.syncCallback).allowMainThreadQueries().build()
        store = SyncStore(db) { "device-1" }
    }

    @After fun close() = db.close()

    @Test fun anInsertIsAChange() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        val c = store.change("account", "a1")!!
        assertFalse(c.deleted)
        assertTrue(c.atMs > 0)
        assertEquals(1, store.pendingCount())
    }

    @Test fun anEditKeepsOneRowAndAdoptsTheLaterTime() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_change SET atMs = 5 WHERE tbl = 'account'")
        db.accounts().rename("a1", "HDFC", 9L)
        val c = store.change("account", "a1")!!
        assertTrue(c.atMs > 5, "the edit is stamped with the time it happened")
        assertEquals(1, store.pendingCount())
    }

    @Test fun replacingARowIsAChangeNotADelete() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        db.accounts().upsert(account("a1", "Renamed")) // REPLACE: SQLite deletes and inserts, but the delete trigger does not fire
        assertFalse(store.change("account", "a1")!!.deleted)
    }

    @Test fun aDeleteLeavesADeleteMarker() = runBlocking<Unit> {
        db.categories().upsert(CategoryEntity("c1", "Food", null, null, null, 1L, null, 0, false, null, null))
        db.categories().deleteMany(listOf("c1"))
        assertTrue(store.change("category", "c1")!!.deleted)
        // Added again later: the newer change says it exists.
        db.categories().upsert(CategoryEntity("c1", "Food", null, null, null, 2L, null, 0, false, null, null))
        assertFalse(store.change("category", "c1")!!.deleted)
    }

    @Test fun settingsAreTrackedByTheirKey() = runBlocking<Unit> {
        db.misc().putSettings(listOf(SettingEntity("theme", "\"dark\"")))
        assertEquals("theme", store.pending(10).single { it.tbl == "setting" }.rowId)
    }

    @Test fun tablesThatDoNotSyncAreNotTracked() = runBlocking<Unit> {
        db.rates().putRates(listOf(ExchangeRateEntity("USD", "83.1", "INR", "2026-11-12", 1L)))
        assertTrue(store.pending(10).none { it.tbl == "exchange_rate" })
        assertNull(SyncTables.byName("exchange_rate"))
        assertNull(SyncTables.byName("entry_version"))
        assertNull(SyncTables.byName("activity_log"))
    }

    @Test fun rowsFromTheCloudAreNotSentBack() = runBlocking<Unit> {
        store.applyingRemote { db.accounts().upsert(account("fromCloud")) }
        assertNull(store.change("account", "fromCloud"))
        // The switch is put back: the next local change is noticed.
        db.accounts().upsert(account("local"))
        assertEquals(listOf("local"), store.pending(10).map { it.rowId })
    }

    @Test fun theSwitchIsPutBackEvenWhenTheBlockFails() = runBlocking<Unit> {
        runCatching { store.applyingRemote { error("boom") } }
        db.accounts().upsert(account("a1"))
        assertEquals(1, store.pendingCount())
    }

    @Test fun markSentOnlyClearsAnUnchangedRow() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        val sent = store.pending(10).single()
        // The row changes again while it is on its way: the stamp moves, so the sent copy must not clear it.
        db.openHelper.writableDatabase.execSQL("UPDATE sync_change SET atMs = atMs + 5000 WHERE tbl = 'account'")
        assertFalse(store.markSent(sent))
        assertEquals(1, store.pendingCount())
        assertTrue(store.markSent(store.pending(10).single()))
        assertEquals(0, store.pendingCount())
    }

    @Test fun firstTimeSeedMarksEveryRowOnceAtZeroAndKeepsRealChanges() = runBlocking<Unit> {
        store.applyingRemote { db.accounts().upsert(account("old1")); db.accounts().upsert(account("old2")) } // rows with no change recorded
        db.accounts().upsert(account("new1")) // a real change, recorded with a real time
        val realAt = store.change("account", "new1")!!.atMs
        assertEquals(3, store.seedIfFirstTime())
        assertEquals(0L, store.change("account", "old1")!!.atMs)
        assertEquals(realAt, store.change("account", "new1")!!.atMs)
        assertEquals(0, store.seedIfFirstTime(), "a second call does nothing")
    }

    @Test fun markEverythingChangedStampsAllRowsNow() = runBlocking<Unit> {
        store.applyingRemote { db.accounts().upsert(account("a1")) }
        store.markEverythingChanged(777L)
        assertEquals(777L, store.change("account", "a1")!!.atMs)
    }

    @Test fun deviceIdIsMadeOnceAndKept() = runBlocking<Unit> {
        val first = store.deviceId()
        assertEquals("device-1", first)
        assertEquals(first, SyncStore(db) { "device-2" }.deviceId())
    }

    @Test fun cursorAndLastSyncRoundTrip() = runBlocking<Unit> {
        assertNull(store.cursor()); assertNull(store.lastSyncMs())
        store.setCursor("c-42"); store.setLastSyncMs(1234L)
        assertEquals("c-42", store.cursor()); assertEquals(1234L, store.lastSyncMs())
        assertNotEquals(null, store.cursor())
    }
}
