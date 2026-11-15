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

    // ---- rows to and from the cloud

    private fun wire(tbl: String, id: String, at: Long, device: String, deleted: Boolean = false, values: Map<String, Any?> = emptyMap()) =
        com.mansaketh.chitta.core.domain.WireRow(tbl, id, at, device, deleted, ChittaDatabase.VERSION, values)

    private fun accountValues(id: String, name: String) = mapOf<String, Any?>(
        "id" to id, "name" to name, "colour" to null, "iconName" to null, "createdAtMs" to 1L, "modifiedAtMs" to null, "sortOrder" to 0L,
        "currency" to "INR", "currencyFormat" to null, "decimals" to 2L, "homeWidgetsJson" to null, "ownerUid" to null,
    )

    @Test fun aRowReadBackHasItsValuesAndTypes() = runBlocking<Unit> {
        db.accounts().upsert(account("a1", "HDFC"))
        val row = store.readRow("account", "a1")!!
        assertEquals("HDFC", row["name"])
        assertEquals(2L, row["decimals"])
        assertNull(row["colour"])
        assertNull(store.readRow("account", "missing"))
    }

    // Worked example: the cloud row is stamped 9000, this phone's waiting change 5000, so the cloud row wins and nothing is left waiting.
    @Test fun aNewerCloudRowIsWrittenAndIsNotSentBack() = runBlocking<Unit> {
        db.accounts().upsert(account("a1", "Mine"))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_change SET atMs = 5000 WHERE tbl = 'account'")
        val r = store.applyRemote(listOf(wire("account", "a1", 9000, "B", values = accountValues("a1", "Theirs"))), "device-1", "me")
        assertEquals(1, r.applied)
        assertEquals("Theirs", db.accounts().getAll().single().name)
        assertNull(store.change("account", "a1"))
        assertEquals(0, store.pendingCount())
    }

    @Test fun anOlderCloudRowLosesAndThisPhonesChangeStaysWaiting() = runBlocking<Unit> {
        db.accounts().upsert(account("a1", "Mine"))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_change SET atMs = 9000 WHERE tbl = 'account'")
        val r = store.applyRemote(listOf(wire("account", "a1", 5000, "B", values = accountValues("a1", "Theirs"))), "device-1", "me")
        assertEquals(1, r.keptLocal)
        assertEquals("Mine", db.accounts().getAll().single().name)
        assertEquals(9000L, store.change("account", "a1")!!.atMs)
    }

    @Test fun aRowOnlyTheCloudHasIsAddedAndAMarkerDeletesIt() = runBlocking<Unit> {
        store.applyRemote(listOf(wire("account", "a9", 100, "B", values = accountValues("a9", "New"))), "device-1", "me")
        assertEquals(listOf("a9"), db.accounts().getAll().map { it.id })
        assertEquals(0, store.pendingCount(), "what arrives is not a local change")
        store.applyRemote(listOf(wire("account", "a9", 200, "B", deleted = true)), "device-1", "me")
        assertTrue(db.accounts().getAll().isEmpty())
        assertEquals(0, store.pendingCount())
    }

    @Test fun aLaterLocalEditBeatsAnEarlierCloudDelete() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_change SET atMs = 4000 WHERE tbl = 'account'")
        store.applyRemote(listOf(wire("account", "a1", 3000, "B", deleted = true)), "device-1", "me")
        assertEquals(1, db.accounts().getAll().size)
    }

    @Test fun unknownColumnsAreIgnoredAndMissingOnesKeepTheirDefaults() = runBlocking<Unit> {
        val values = accountValues("a2", "Newer phone") + mapOf("someFutureColumn" to "x") - "archived"
        store.applyRemote(listOf(wire("account", "a2", 100, "B", values = values)), "device-1", "me")
        val row = db.accounts().getAll().single()
        assertEquals("Newer phone", row.name)
        assertEquals(false, row.archived)
    }

    @Test fun deviceOnlySettingsAndHouseholdAccountsAreNeverWritten() = runBlocking<Unit> {
        val r = store.applyRemote(
            listOf(
                wire("setting", "lastBackup", 100, "B", values = mapOf("key" to "lastBackup", "json" to "1")),
                wire("setting", "theme", 100, "B", values = mapOf("key" to "theme", "json" to "\"dark\"")),
                wire("account", "hh_u1", 100, "B", values = accountValues("hh_u1", "Their account")),
            ),
            "device-1", "me",
        )
        assertEquals(1, r.applied)
        assertEquals(2, r.skipped)
        assertTrue(db.accounts().getAll().isEmpty())
        assertEquals(setOf("theme"), db.openHelper.readableDatabase.query("SELECT `key` FROM setting").use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } })
    }

    @Test fun replaceWipesOwnRowsButKeepsHouseholdPlaceholdersAndPendingGoes() = runBlocking<Unit> {
        db.accounts().upsert(account("a1"))
        db.accounts().upsert(account("hh_u1"))
        store.wipeOwnRows("me")
        assertEquals(listOf("hh_u1"), db.accounts().getAll().map { it.id })
        assertEquals(0, store.pendingCount())
    }

    @Test fun startOverGivesANewDeviceIdAndForgetsTheCursor() = runBlocking<Unit> {
        store.deviceId(); store.setCursor("c"); store.setLastSyncMs(5)
        val fresh = SyncStore(db) { "device-2" }.startOver()
        assertEquals("device-2", fresh)
        assertNull(store.cursor()); assertNull(store.lastSyncMs())
        assertEquals("device-2", store.deviceId())
    }

    @Test fun markMissingAtZeroKeepsRealChanges() = runBlocking<Unit> {
        store.applyingRemote { db.accounts().upsert(account("old")) }
        db.accounts().upsert(account("new"))
        val realAt = store.change("account", "new")!!.atMs
        store.markMissingAtZero()
        assertEquals(0L, store.change("account", "old")!!.atMs)
        assertEquals(realAt, store.change("account", "new")!!.atMs)
    }
}
