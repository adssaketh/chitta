package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.core.model.Account
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun e(id: String, owner: String? = null, account: String = "a1", synced: Long? = null, name: String = "Milk", amount: Long = -12_345L) = Transaction(
    id = id, pairedTransactionId = null, name = name, amount = amount, currency = "INR", note = "", categoryId = "food", subCategoryId = null,
    accountId = account, dateMs = 1_000, modifiedAtMs = 1_000, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = owner, isPrivate = false, householdSyncedAtMs = synced,
)

private fun acct(id: String, name: String = id) = Account(id, name, null, null, 1L, null, 0, "INR", null, 2, null, null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdStoreTest {
    private lateinit var db: ChittaDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var accounts: AccountRepository
    private lateinit var store: HouseholdStore
    private var now = 10_000L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { now++ }
        accounts = AccountRepository(db)
        store = HouseholdStore(db)
    }

    @After fun close() = db.close()

    // ---- other members' entries stay out of everything that is mine

    @Test fun membersEntriesAndAccountsAreHiddenFromOwnLists() = runBlocking<Unit> {
        accounts.save(acct("a1"))
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        ledger.add(listOf(e("mine", owner = "me")))
        store.apply(memberEntries = listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), ownEntries = emptyList(), removeIds = emptyList(), myUid = "me")
        assertEquals(listOf("mine"), ledger.observeAll().first().map { it.id })
        assertEquals(setOf("mine", "theirs"), ledger.observeEverything().first().map { it.id }.toSet())
        assertEquals(listOf("a1"), accounts.observeAll().first().map { it.id })
        assertEquals(setOf("a1", "hh_sis"), accounts.observeAllWithMembers().first().map { it.id }.toSet())
        assertEquals(listOf("mine"), ledger.observeAllOnce().map { it.id })
        assertEquals(listOf("mine"), BookRepository(db).readAll().transactions.map { it.id })
        assertEquals(listOf("a1"), BookRepository(db).readAll().accounts.map { it.id })
    }

    @Test fun aMembersEntryCanBeReadForItsDetailEvenThoughItIsNotInOwnLists() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), emptyList(), emptyList(), "me")
        assertNotNull(ledger.get("theirs"))
    }

    @Test fun placeholderAccountIsCreatedOnceAndKeepsUpWithTheNameAndCurrency() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        store.ensurePlaceholders(mapOf("sis" to "Sister"), "USD", 6L)
        val p = db.accounts().getPlaceholders()
        assertEquals(1, p.size)
        assertEquals("Sister", p[0].name)
        assertEquals("USD", p[0].currency)
        assertEquals("sis", p[0].ownerUid)
    }

    // ---- the upload queue

    @Test fun newEntriesAreClaimedAndWaitUntilUploaded() = runBlocking<Unit> {
        ledger.add(listOf(e("a"), e("b", owner = "")))
        assertEquals(1, store.claimNew("me"))
        assertEquals(listOf("a"), store.waiting("me", 10).map { it.id }, "a personal entry is never uploaded")
        val sent = store.waiting("me", 10).single()
        assertTrue(store.markSynced(sent, 50L))
        assertEquals(emptyList(), store.waiting("me", 10))
        assertEquals(50L, ledger.get("a")!!.householdSyncedAtMs)
    }

    @Test fun anEntryEditedWhileItWasUploadingStaysWaiting() = runBlocking<Unit> {
        ledger.add(listOf(e("a", owner = "me")))
        val sent = store.waiting("me", 10).single()
        ledger.update(listOf(sent.copy(name = "Milk and bread"))) // edited while the upload was in flight
        assertFalse(store.markSynced(sent, 50L))
        assertEquals(listOf("a"), store.waiting("me", 10).map { it.id })
    }

    @Test fun everyLocalEditMakesASyncedEntryWaitAgain() = runBlocking<Unit> {
        ledger.add(listOf(e("a", owner = "me")))
        store.markSynced(store.waiting("me", 10).single(), 50L)
        assertEquals(emptyList(), store.waiting("me", 10))
        ledger.update(listOf(ledger.get("a")!!.copy(amount = -99_900L)))
        assertEquals(listOf("a"), store.waiting("me", 10).map { it.id })
    }

    @Test fun anUploadedEntryThatIsDeletedAndUndoneGoesUpAgain() = runBlocking<Unit> {
        ledger.add(listOf(e("a", owner = "me")))
        store.markSynced(store.waiting("me", 10).single(), 50L)
        val gone = ledger.delete(listOf("a"))
        assertEquals(setOf("a"), store.deletedIds())
        ledger.restoreDeleted(gone)
        assertEquals(emptySet(), store.deletedIds())
        assertEquals(listOf("a"), store.waiting("me", 10).map { it.id })
    }

    @Test fun deletionsComeFromTheHistoryAfterTheCursorOnly() = runBlocking<Unit> {
        ledger.add(listOf(e("a", owner = "me"), e("b", owner = "me")))
        ledger.delete(listOf("a"))
        val cursor = now - 1 // the clock hands out now, then adds one: the last time used
        ledger.delete(listOf("b"))
        assertEquals(listOf("b"), store.deletedSince(cursor).map { it.entryId })
    }

    // ---- choosing what to share

    @Test fun shareExistingClaimsNewAndPersonalEntriesButNotOthers() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        ledger.add(listOf(e("new"), e("personal", owner = "")))
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), listOf(e("old", owner = "me", synced = 5L)), emptyList(), "me")
        assertEquals(2, store.shareExisting("me"))
        assertEquals("me", ledger.get("new")!!.ownerUid)
        assertEquals("me", ledger.get("personal")!!.ownerUid)
        assertEquals(5L, ledger.get("old")!!.householdSyncedAtMs, "an entry that is already shared is left as it is")
        assertEquals("sis", ledger.get("theirs")!!.ownerUid)
    }

    @Test fun onlyNewKeepsEntriesAlreadyOnThePhonePersonal() = runBlocking<Unit> {
        ledger.add(listOf(e("new"), e("shared", owner = "me")))
        assertEquals(1, store.keepExistingPersonal())
        assertEquals("", ledger.get("new")!!.ownerUid)
        assertEquals("me", ledger.get("shared")!!.ownerUid)
        assertEquals(0, store.claimNew("me"), "a personal entry is not claimed later")
    }

    // ---- what arrives

    @Test fun aMembersEntryIsRemovedWhenTheyDeleteItButMyOwnIsNeverRemovedFromHere() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), listOf(e("mine", owner = "me", synced = 5L)), emptyList(), "me")
        store.apply(emptyList(), emptyList(), listOf("theirs", "mine"), "me")
        assertNull(ledger.get("theirs"))
        assertNotNull(ledger.get("mine"))
    }

    @Test fun anEntryThatArrivesIsNotSentBack() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), listOf(e("mine", owner = "me", synced = 2L)), emptyList(), "me")
        assertEquals(emptyList(), store.waiting("me", 10))
        assertEquals(emptyList(), store.waiting("sis", 10), "their entries are never mine to upload")
    }

    // ---- leaving

    @Test fun leavingKeepsMyEntriesAsPersonalAndRemovesOnlyWhatCameFromMembers() = runBlocking<Unit> {
        accounts.save(acct("a1"))
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        ledger.add(listOf(e("personal", owner = "")))
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), listOf(e("mine", owner = "me", synced = 5L)), emptyList(), "me")
        store.removeMembersData("me")
        store.makeAllPersonal()
        assertEquals(setOf("mine", "personal"), ledger.observeEverything().first().map { it.id }.toSet())
        assertEquals("", ledger.get("mine")!!.ownerUid)
        assertNull(ledger.get("mine")!!.householdSyncedAtMs)
        assertEquals(setOf("a1"), accounts.observeAllWithMembers().first().map { it.id }.toSet())
    }

    @Test fun countsForTheHouseholdPage() = runBlocking<Unit> {
        store.ensurePlaceholders(mapOf("sis" to "Sis"), "INR", 5L)
        ledger.add(listOf(e("b", owner = "me"), e("c", owner = "")))
        store.apply(listOf(e("theirs", owner = "sis", account = "hh_sis", synced = 1L)), listOf(e("a", owner = "me", synced = 5L)), emptyList(), "me")
        assertEquals(HouseholdStore.Counts(shared = 2, waiting = 1, fromMembers = 1), store.counts("me"))
    }
}
