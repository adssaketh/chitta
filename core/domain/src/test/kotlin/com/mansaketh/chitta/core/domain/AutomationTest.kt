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

class NotificationScanTest {
    private val allowed = setOf("com.bank.app")
    private val now = 1_800_000_000_000L

    @Test fun keepsAnAmountFromATickedApp() {
        val m = NotificationScan.accept("com.bank.app", "Account alert", "Rs 450.00 debited from A/c XX1234 to Tea Stall", now, allowed, "com.mansaketh.chitta")
        assertNotNull(m)
        assertEquals("com.bank.app", m.sender)
        assertEquals("Account alert. Rs 450.00 debited from A/c XX1234 to Tea Stall", m.body)
        assertTrue(m.id.startsWith("nt-"))
    }

    @Test fun ignoresAppsThatAreNotTickedAndChittaItself() {
        assertNull(NotificationScan.accept("com.other", null, "Rs 450 debited", now, allowed, "x"))
        assertNull(NotificationScan.accept("com.bank.app", null, "Rs 450 debited", now, allowed, "com.bank.app"))
    }

    @Test fun ignoresNotificationsWithoutRupees() {
        assertNull(NotificationScan.accept("com.bank.app", "Hello", "Your OTP is 123456", now, allowed, "x"))
        assertNull(NotificationScan.accept("com.bank.app", "Hello", "", now, allowed, "x"))
    }

    @Test fun rupeeSignAndInrCount() {
        assertNotNull(NotificationScan.accept("com.bank.app", null, "₹250 paid", now, allowed, "x"))
        assertNotNull(NotificationScan.accept("com.bank.app", null, "INR 1,200.50 credited", now, allowed, "x"))
    }

    @Test fun sameNotificationInTheSameMinuteGetsTheSameId() {
        val a = NotificationScan.accept("com.bank.app", null, "Rs 450 debited", now, allowed, "x")!!
        val b = NotificationScan.accept("com.bank.app", null, "Rs 450 debited", now + 20_000, allowed, "x")!!
        val c = NotificationScan.accept("com.bank.app", null, "Rs 450 debited", now + 600_000, allowed, "x")!!
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, c.id)
    }

    @Test fun titleIsNotRepeatedWhenTheTextHasIt() {
        val m = NotificationScan.accept("com.bank.app", "Rs 450 debited", "Rs 450 debited from A/c XX1234", now, allowed, "x")!!
        assertEquals("Rs 450 debited from A/c XX1234", m.body)
    }

    @Test fun senderIsTrimmedToSafeCharacters() {
        assertEquals(32, NotificationScan.senderOf("com.very.long.package.name.of.some.bank.app").length)
        assertEquals("comappx", NotificationScan.senderOf("com app!x").replace(".", ""))
    }

    @Test fun packageNames() {
        assertTrue(NotificationScan.looksLikePackage("com.phonepe.app"))
        assertFalse(NotificationScan.looksLikePackage("phonepe"))
        assertFalse(NotificationScan.looksLikePackage("com.x; rm"))
    }
}

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
