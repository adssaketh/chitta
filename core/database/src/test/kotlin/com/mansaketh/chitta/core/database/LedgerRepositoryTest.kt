package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.HistoryAction
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(id: String, amount: Long = -48_600, name: String = "Swiggy", account: String = "a1") = Transaction(
    id = id, pairedTransactionId = null, name = name, amount = amount, currency = "INR", note = "", categoryId = "food", subCategoryId = null,
    accountId = account, dateMs = 1_000, modifiedAtMs = 1_000, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = listOf("b1"), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
    originalAmount = -1_250, originalCurrency = "USD", exchangeRate = "83.215",
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerRepositoryTest {
    private lateinit var db: ChittaDatabase
    private var now = 10_000L
    private lateinit var repo: LedgerRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java).allowMainThreadQueries().build()
        repo = LedgerRepository(db) { now++ }
    }

    @After fun close() = db.close()

    @Test fun snapshotRoundTripKeepsEveryField() {
        val e = entry("t1")
        assertEquals(e, Snapshots.decode(Snapshots.encode(e)))
        val bare = e.copy(currency = null, originalAmount = null, originalCurrency = null, exchangeRate = null, excludeFromBudgetIds = emptyList(), pairedTransactionId = "t2")
        assertEquals(bare, Snapshots.decode(Snapshots.encode(bare)))
    }

    @Test fun addWritesTheEntryAndACreatedVersionTogether() = runBlocking {
        repo.add(listOf(entry("t1")))
        assertEquals(entry("t1"), repo.get("t1"))
        assertEquals(listOf(HistoryAction.CREATED), repo.history("t1").map { it.action })
    }

    @Test fun editRecordsAVersionAndSkipsNoOps() = runBlocking {
        repo.add(listOf(entry("t1")))
        val before = repo.update(listOf(entry("t1", name = "Swiggy Instamart")))
        assertEquals(listOf(entry("t1")), before)
        assertEquals("Swiggy Instamart", repo.get("t1")!!.name)
        assertEquals(emptyList(), repo.update(listOf(entry("t1", name = "Swiggy Instamart")))) // nothing changed
        assertEquals(listOf(HistoryAction.CREATED, HistoryAction.EDITED), repo.history("t1").map { it.action })
    }

    @Test fun deleteKeepsTheLastStateAndCanBeUndone() = runBlocking {
        repo.add(listOf(entry("t1"), entry("t2")))
        val deleted = repo.delete(listOf("t1"))
        assertNull(repo.get("t1"))
        assertNotNull(repo.get("t2"))
        assertEquals(entry("t1"), repo.history("t1").last().snapshot)
        repo.restoreDeleted(deleted)
        assertEquals(entry("t1"), repo.get("t1"))
        assertEquals(listOf(HistoryAction.CREATED, HistoryAction.DELETED, HistoryAction.RESTORED), repo.history("t1").map { it.action })
    }

    @Test fun restoreVersionBringsBackAnEarlierStateAsANewVersion() = runBlocking {
        repo.add(listOf(entry("t1")))
        repo.update(listOf(entry("t1", amount = -99_900)))
        val first = repo.history("t1").first()
        assertTrue(repo.restoreVersion("t1", first.id))
        assertEquals(-48_600L, repo.get("t1")!!.amount)
        assertEquals(3, repo.history("t1").size) // history only grew
    }

    @Test fun recentlyDeletedListsOnlyEntriesWhoseLastActionWasADelete() = runBlocking {
        repo.add(listOf(entry("t1"), entry("t2")))
        val gone = repo.delete(listOf("t1", "t2"))
        repo.restoreDeleted(gone.filter { it.id == "t2" })
        val list = db.versions().observeDeleted(10).let { it.first() }
        assertEquals(listOf("t1"), list.map { it.entryId })
    }

    @Test fun pinnedEntriesSaveAndRemove() = runBlocking {
        val pins = PinnedRepository(db)
        pins.save(com.mansaketh.chitta.core.model.PinnedEntry("p1", "Chai", 2_000, false, "food", "a1", 0, 5))
        assertEquals(listOf("Chai"), pins.observeAll().first().map { it.title })
        pins.remove("p1")
        assertEquals(emptyList(), pins.observeAll().first())
    }
}
