package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BroadcastAddTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = 1_800_000_000_000L
    private val account = Account("a1", "HDFC", null, null, 0, null, 0, "INR", null, 2, null, null)
    private val food = Category("c1", "Food", null, null, null, 0, null, 0, false, null, null)
    private fun parse(vararg p: Pair<String, String>) = BroadcastAdd.parse(mapOf(*p), listOf(account), listOf(food), now, zone)

    @Test fun noSecretSetMeansOff() {
        assertFalse(BroadcastAdd.secretOk(null, "anything12345"))
        assertFalse(BroadcastAdd.secretOk("short", "short"))
    }

    @Test fun onlyTheExactSecretPasses() {
        assertTrue(BroadcastAdd.secretOk("abcdef0123456789", "abcdef0123456789"))
        assertFalse(BroadcastAdd.secretOk("abcdef0123456789", "abcdef0123456780"))
        assertFalse(BroadcastAdd.secretOk("abcdef0123456789", null))
        assertFalse(BroadcastAdd.secretOk("abcdef0123456789", ""))
    }

    @Test fun aCompleteRequestIsReady() {
        val r = parse("amount" to "250", "title" to "Tea", "category" to "Food", "account" to "HDFC")
        assertTrue(r is BroadcastAdd.Result.Ready)
        assertEquals("250", (r as BroadcastAdd.Result.Ready).draft.amountRaw)
        assertEquals("c1", r.draft.categoryId)
    }

    @Test fun missingPiecesAreRefusedNotGuessed() {
        assertTrue(parse("title" to "Tea", "category" to "Food", "account" to "HDFC") is BroadcastAdd.Result.Rejected)
        assertTrue(parse("amount" to "250", "category" to "Food", "account" to "Nowhere") is BroadcastAdd.Result.Rejected)
        assertTrue(parse("amount" to "250", "category" to "Unknown", "account" to "HDFC") is BroadcastAdd.Result.Rejected)
        assertTrue(parse("amount" to "0", "category" to "Food", "account" to "HDFC") is BroadcastAdd.Result.Rejected)
    }

    @Test fun transfersAreRefused() =
        assertTrue(parse("amount" to "250", "type" to "transfer", "category" to "Food", "account" to "HDFC") is BroadcastAdd.Result.Rejected)

    @Test fun newSecretsAreLongAndDifferent() {
        val a = BroadcastAdd.newSecret(); val b = BroadcastAdd.newSecret()
        assertEquals(32, a.length)
        assertNotEquals(a, b)
        assertTrue(a.length >= BroadcastAdd.MIN_SECRET)
    }
}
