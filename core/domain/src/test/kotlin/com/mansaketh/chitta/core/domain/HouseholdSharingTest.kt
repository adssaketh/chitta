package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    id: String = "t1", amount: Long = -12_345L, owner: String? = "me", private: Boolean = false, account: String = "acc1",
    modified: Long? = 1_000L, synced: Long? = null,
) = Transaction(
    id = id, pairedTransactionId = null, name = "Milk", amount = amount, currency = "INR", note = "", categoryId = "cat1", subCategoryId = null,
    accountId = account, dateMs = 5_000L, modifiedAtMs = modified, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = owner, isPrivate = private, householdSyncedAtMs = synced,
)

class HouseholdSharingTest {
    // ---- money in the document (paise in Chitta, rupees as a double in the old app)

    @Test fun rupeesAreWrittenAsTheOldAppWritesThem() {
        assertEquals(-123.45, HouseholdWire.rupeesOf(-12_345L))
        assertEquals(0.05, HouseholdWire.rupeesOf(5L))
        assertEquals(1999.99, HouseholdWire.rupeesOf(199_999L))
    }

    @Test fun anOldDocumentWithOnlyRupeesGivesExactPaise() {
        assertEquals(19_999L, HouseholdWire.paiseOf(mapOf("amount" to 199.99)))
        assertEquals(-12_345L, HouseholdWire.paiseOf(mapOf("amount" to -123.45)))
        assertEquals(10L, HouseholdWire.paiseOf(mapOf("amount" to 0.1)))
        assertEquals(30L, HouseholdWire.paiseOf(mapOf("amount" to 0.1 + 0.2))) // 0.30000000000000004 is still 30 paise
        assertEquals(500L, HouseholdWire.paiseOf(mapOf("amount" to 5))) // Firestore may hand back a whole number as Long
    }

    @Test fun theExactPaiseFieldWinsWhenPresent() {
        assertEquals(12_345L, HouseholdWire.paiseOf(mapOf("amount" to 123.4, "amountPaise" to 12_345L)))
    }

    @Test fun amountsThatAreMissingOrNotNumbersAreRefused() {
        assertNull(HouseholdWire.paiseOf(emptyMap()))
        assertNull(HouseholdWire.paiseOf(mapOf("amount" to "12")))
        assertNull(HouseholdWire.paiseOf(mapOf("amount" to Double.NaN)))
    }

    // ---- the document

    @Test fun anEntryGoesUpAndComesBackUnchanged() {
        val e = entry().copy(
            note = "weekly", subCategoryId = "sub", originalDueMs = 4_000L, periodLength = 1, repeat = Repeat.fromCode(Repeat.entries.first().code),
            kind = TransactionKind.fromCode(TransactionKind.entries.first().code), originalAmount = 5_000L, originalCurrency = "USD", exchangeRate = "83.2150",
            anchorDay = 31, splitGroupId = "g1", isPrivate = true,
        )
        val doc = HouseholdWire.entryToDocument(e, "me")
        val back = assertNotNull(HouseholdWire.entryFromDocument(doc))
        // What stays on the phone is not uploaded, so it comes back empty; the sync fills owner, account and sync time itself.
        assertEquals(e.copy(householdSyncedAtMs = null), back)
    }

    @Test fun theDocumentUsesTheOldFieldNamesAndTheOwnerIdTheRulesRead() {
        val doc = HouseholdWire.entryToDocument(entry(private = true), "me")
        assertEquals("t1", doc["transactionPk"])
        assertEquals("acc1", doc["walletFk"])
        assertEquals("cat1", doc["categoryFk"])
        assertEquals(5_000L, doc["dateCreated"])
        assertEquals("me", doc["ownerId"])
        assertEquals(true, doc["isPrivate"])
        assertEquals(-123.45, doc["amount"])
        // Never uploaded: the old app kept these on the phone.
        for (k in listOf("ownerUid", "householdSyncedAt", "budgetFksExclude", "objectiveFk", "objectiveLoanFk", "pairedTransactionFk", "sharedKey")) {
            assertFalse(k in doc, "$k must not be uploaded")
        }
    }

    @Test fun anOldAppDocumentIsReadWithItsDefaults() {
        val doc = mapOf<String, Any?>(
            "transactionPk" to "x", "name" to "Rent", "amount" to -15_000.0, "note" to "", "categoryFk" to "c", "walletFk" to "w",
            "dateCreated" to 1_700_000_000_000L, "income" to false, "paid" to true, "skipPaid" to false, "isPrivate" to false, "ownerId" to "sis",
        )
        val t = assertNotNull(HouseholdWire.entryFromDocument(doc))
        assertEquals(-1_500_000L, t.amount)
        assertEquals("sis", t.ownerUid)
        assertNull(t.currency)
        assertNull(t.modifiedAtMs)
    }

    @Test fun aDocumentThatCannotBeUsedIsSkippedNotGuessed() {
        val ok = mapOf<String, Any?>("transactionPk" to "x", "amount" to 1.0, "categoryFk" to "c", "walletFk" to "w", "dateCreated" to 1L)
        assertNotNull(HouseholdWire.entryFromDocument(ok))
        for (missing in listOf("transactionPk", "amount", "categoryFk", "walletFk", "dateCreated")) {
            assertNull(HouseholdWire.entryFromDocument(ok - missing), "without $missing")
        }
    }

    @Test fun anotherMembersEntryLandsInTheirPlaceholderAccountAsHandled() {
        val theirs = HouseholdWire.asMemberEntry(entry(owner = "sis", account = "their-own-account", modified = 7_000L).copy(upcomingNotification = true, createdAnotherFuture = false), "sis")
        assertEquals("hh_sis", theirs.accountId)
        assertEquals("sis", theirs.ownerUid)
        assertEquals(7_000L, theirs.householdSyncedAtMs)
        assertEquals(true, theirs.createdAnotherFuture)
        assertEquals(false, theirs.upcomingNotification)
        assertFalse(theirs.isPrivate)
    }

    @Test fun aCategoryRoundTrips() {
        val c = Category("c1", "Food", "#fff", "bowl", null, 10L, 20L, 3, false, null, null, archived = true)
        assertEquals(c, HouseholdWire.categoryFromDocument(HouseholdWire.categoryToDocument(c)))
        assertEquals("c1", HouseholdWire.categoryToDocument(c)["categoryPk"])
        assertEquals(3, HouseholdWire.categoryToDocument(c)["order"])
        assertNull(HouseholdWire.categoryFromDocument(mapOf("name" to "no id")))
    }

    @Test fun categoryContentIgnoresTheModifiedTime() {
        val a = Category("c1", "Food", null, null, null, 1L, 100L, 0, false, null, null)
        assertTrue(HouseholdWire.sameCategoryContent(a, a.copy(modifiedAtMs = 999L)))
        assertFalse(HouseholdWire.sameCategoryContent(a, a.copy(name = "Groceries")))
    }

    @Test fun anEditMadeOnAnotherPhoneOfMineKeepsWhatNeverGoesUp() {
        val local = entry(modified = 1_000L).copy(objectiveId = "goal1", excludeFromBudgetIds = listOf("b1"), pairedTransactionId = "t2", accountId = "acc1")
        val remote = entry(modified = 2_000L).copy(name = "Milk and bread", accountId = "acc2")
        val merged = HouseholdWire.mergeOwnRow(local, remote, remoteAccountExistsHere = true)
        assertEquals("Milk and bread", merged.name)
        assertEquals("acc2", merged.accountId)
        assertEquals("goal1", merged.objectiveId)
        assertEquals(listOf("b1"), merged.excludeFromBudgetIds)
        assertEquals("t2", merged.pairedTransactionId)
        assertEquals(2_000L, merged.householdSyncedAtMs, "marked as uploaded at the server's edit time, so it is not sent back")
    }

    @Test fun anAccountThatDoesNotExistHereOrIsAPlaceholderIsNotTaken() {
        val local = entry().copy(accountId = "acc1")
        assertEquals("acc1", HouseholdWire.mergeOwnRow(local, entry(modified = 2_000L).copy(accountId = "gone"), remoteAccountExistsHere = false).accountId)
        assertEquals("acc1", HouseholdWire.mergeOwnRow(local, entry(modified = 2_000L).copy(accountId = "hh_sis"), remoteAccountExistsHere = true).accountId)
    }

    @Test fun anOwnEntryMissingHereIsRestoredAsMineAndAlreadyUploaded() {
        val back = HouseholdWire.restoreOwnRow(entry(owner = null, account = "gone", modified = 3_000L), "me", "acc1")
        assertEquals("me", back.ownerUid)
        assertEquals("acc1", back.accountId)
        assertEquals(3_000L, back.householdSyncedAtMs)
    }

    @Test fun categoryFingerprintIgnoresModifiedTimeAndOrderOfTheList() {
        val a = Category("a", "Food", null, null, null, 1L, 10L, 0, false, null, null)
        val b = Category("b", "Fuel", null, null, null, 2L, 20L, 1, false, null, null)
        assertEquals(HouseholdWire.categoriesFingerprint(listOf(a, b)), HouseholdWire.categoriesFingerprint(listOf(b.copy(modifiedAtMs = 99L), a)))
        assertFalse(HouseholdWire.categoriesFingerprint(listOf(a, b)) == HouseholdWire.categoriesFingerprint(listOf(a, b.copy(name = "Petrol"))))
    }

    // ---- placeholder accounts and ownership

    @Test fun placeholderAccountsAreRecognisedByTheirId() {
        assertEquals("hh_abc", HouseholdSharing.placeholderAccountId("abc"))
        assertTrue(HouseholdSharing.isPlaceholderAccountId("hh_abc"))
        assertFalse(HouseholdSharing.isPlaceholderAccountId("acc1"))
    }

    @Test fun ownershipWords() {
        assertTrue(HouseholdSharing.ownerIsMe(null, "me"))
        assertTrue(HouseholdSharing.ownerIsMe("", "me"))
        assertTrue(HouseholdSharing.ownerIsMe("me", "me"))
        assertFalse(HouseholdSharing.ownerIsMe("sis", "me"))
        assertTrue(HouseholdSharing.isForeignOwner("sis", "me"))
        assertFalse(HouseholdSharing.isForeignOwner("", "me"))
        assertFalse(HouseholdSharing.isForeignOwner(null, "me"))
    }

    @Test fun editAndDeleteRules() {
        assertTrue(HouseholdSharing.canEdit(false, "sis", "me"), "outside a household nothing is blocked")
        assertFalse(HouseholdSharing.canEdit(true, "sis", "me"))
        assertTrue(HouseholdSharing.canEdit(true, "me", "me"))
        assertFalse(HouseholdSharing.canDelete(true, "sis", "me", memberCanDelete = false))
        assertTrue(HouseholdSharing.canDelete(true, "sis", "me", memberCanDelete = true))
        assertTrue(HouseholdSharing.canDelete(true, "me", "me", memberCanDelete = false))
    }

    // ---- Everyone / Mine

    @Test fun mineShowsEverythingOfMineEveryoneShowsOnlyTheSharedLedger() {
        fun v(view: String, owner: String?, priv: Boolean) = HouseholdSharing.visible(true, view, owner, priv, "me")
        // Mine
        assertTrue(v("mine", "me", false)); assertTrue(v("mine", "me", true)); assertTrue(v("mine", "", false)); assertTrue(v("mine", null, false))
        assertFalse(v("mine", "sis", false))
        // Everyone
        assertTrue(v("everyone", "me", false)); assertTrue(v("everyone", "sis", false)); assertTrue(v("everyone", null, false))
        assertFalse(v("everyone", "me", true), "my private entry is not in the shared ledger")
        assertFalse(v("everyone", "", false), "a personal entry is not in the shared ledger")
    }

    @Test fun outsideAHouseholdNothingIsFiltered() {
        assertTrue(HouseholdSharing.visible(false, "everyone", "", true, "me"))
        assertTrue(HouseholdSharing.visible(false, "mine", "sis", false, ""))
    }

    @Test fun anUnknownViewCountsAsEveryone() {
        assertEquals("everyone", HouseholdSharing.cleanView(null))
        assertEquals("everyone", HouseholdSharing.cleanView("nonsense"))
        assertEquals("mine", HouseholdSharing.cleanView("mine"))
    }

    // ---- choosing what to share (PDF p.33)

    @Test fun shareExistingClaimsNewAndPersonalEntries() {
        assertEquals("me", HouseholdSharing.ownerAfterChoice(HouseholdSharing.ShareChoice.SHARE_EXISTING, null, "me"))
        assertEquals("me", HouseholdSharing.ownerAfterChoice(HouseholdSharing.ShareChoice.SHARE_EXISTING, "", "me"))
    }

    @Test fun onlyNewKeepsOldEntriesOnThePhone() {
        assertEquals("", HouseholdSharing.ownerAfterChoice(HouseholdSharing.ShareChoice.ONLY_NEW, null, "me"))
        assertNull(HouseholdSharing.ownerAfterChoice(HouseholdSharing.ShareChoice.ONLY_NEW, "", "me"), "already personal: no change")
    }

    @Test fun anEntryThatAlreadyHasAnOwnerIsNeverTouched() {
        for (c in HouseholdSharing.ShareChoice.entries) assertNull(HouseholdSharing.ownerAfterChoice(c, "someone", "me"))
    }

    // ---- merging

    @Test fun aMissingTimeCountsAsTheOldest() {
        assertTrue(HouseholdSharing.remoteIsNewer(2L, 1L))
        assertFalse(HouseholdSharing.remoteIsNewer(1L, 1L))
        assertFalse(HouseholdSharing.remoteIsNewer(null, 1L))
        assertTrue(HouseholdSharing.remoteIsNewer(1L, null))
        assertFalse(HouseholdSharing.remoteIsNewer(null, null))
    }

    @Test fun theServerDecidesWhoOwnsARow() {
        assertTrue(HouseholdSharing.serverOwnerWins(null, "sis"))
        assertTrue(HouseholdSharing.serverOwnerWins("me", "sis"))
        assertFalse(HouseholdSharing.serverOwnerWins("sis", "sis"))
    }

    private val now = 10_000_000L
    private fun row(id: String, owner: String?, synced: Long?) = HouseholdSharing.LocalRow(id, owner, synced)

    @Test fun reconcileRemovesGhostsOfOthersAndLeavesMyRowsAlone() {
        val plan = HouseholdSharing.reconcile(
            local = listOf(row("ghost", "sis", 1L), row("here", "sis", 1L), row("mine-new", "me", null), row("mine-personal", "", null)),
            serverIds = setOf("here"), deletedHere = emptySet(), myUid = "me", nowMs = now,
        )
        assertEquals(listOf("ghost"), plan.deleteIds)
        assertEquals(emptyList(), plan.reuploadIds, "a row never uploaded is the upload queue's job")
        assertEquals(emptyList(), plan.undeleteIds)
    }

    @Test fun reconcileSendsMyMissingRowsAgainOnlyAfterTheGracePeriod() {
        val old = row("old", "me", now - HouseholdSharing.GRACE_MS - 1)
        val fresh = row("fresh", "me", now - 1_000)
        val plan = HouseholdSharing.reconcile(listOf(old, fresh), emptySet(), emptySet(), "me", now)
        assertEquals(listOf("old"), plan.reuploadIds)
    }

    @Test fun reconcileDropsAStaleDeleteRecordWhenTheRowIsStillHere() {
        val plan = HouseholdSharing.reconcile(listOf(row("back", "me", 5L), row("neverSent", "me", null)), emptySet(), setOf("back", "neverSent"), "me", now)
        assertEquals(listOf("back", "neverSent"), plan.undeleteIds)
        assertEquals(listOf("back"), plan.reuploadIds)
    }

    @Test fun reconcileDoesNothingWhenEverythingMatches() {
        val plan = HouseholdSharing.reconcile(listOf(row("a", "me", 1L), row("b", "sis", 1L)), setOf("a", "b"), emptySet(), "me", now)
        assertEquals(emptyList(), plan.deleteIds + plan.reuploadIds + plan.undeleteIds)
    }
}
