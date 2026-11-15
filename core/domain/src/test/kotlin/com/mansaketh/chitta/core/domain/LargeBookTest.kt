package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()

/**
 * Phase 9c performance check (master section 9: 60 fps through 10,000 transactions). CI has no phone, so this times the pure
 * functions that every list and total on screen goes through, on a book of 10,000 entries over 30 months. The limits are loose
 * on purpose (a shared CI machine is noisy): they catch an accidental quadratic loop, not a few milliseconds. Scroll speed and
 * cold start are checked on the phone (see the phase notes).
 */
class LargeBookTest {
    private val categories = (0 until 12).map { Category("c$it", "c$it", null, null, null, it, null, 0, it == 11, null, null) } +
        Category("salary", "salary", null, null, null, 12, null, 0, true, null, null)

    private fun tx(i: Int): Transaction {
        // 10,000 entries over Jan 2024 to Jun 2026 (about 330 a month): day 1..28, varying hour and amount.
        val monthIndex = i % 30
        val dateMs = ms(2024 + monthIndex / 12, monthIndex % 12 + 1, i % 28 + 1, i % 24)
        val income = i % 11 == 0
        val amount = (100L + (i * 37L) % 90_000) * (if (income) 1 else -1)
        return Transaction(
            id = "t$i", pairedTransactionId = null, name = "Shop ${i % 400}", amount = amount, currency = "INR", note = "",
            categoryId = "c${i % 12}", subCategoryId = null, accountId = "a${i % 3}", dateMs = dateMs, modifiedAtMs = null,
            originalDueMs = null, income = income, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
            kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
            objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
        )
    }

    private val entries = (0 until 10_000).map(::tx)
    private val accounts = (0 until 3).map { Account("a$it", "a$it", null, null, it, null, 0, "INR", null, 2, null, null) }
    private val now = ms(2026, 6, 15)

    private inline fun <T> timed(name: String, limitMs: Long, block: () -> T): T {
        block() // warm up, so the JIT is not what gets timed
        val start = System.nanoTime()
        val result = block()
        val took = (System.nanoTime() - start) / 1_000_000
        println("LargeBookTest: $name took $took ms (limit $limitMs ms)")
        assertTrue(took <= limitMs, "$name took $took ms, limit $limitMs ms")
        return result
    }

    @Test fun ledgerGroupsTenThousandEntries() {
        val months = timed("Ledger grouping", 1_000) { LedgerGrouping.group(entries, categories, IST) }
        assertEquals(30, months.size)
    }

    @Test fun ledgerFiltersTenThousandEntries() {
        val found = timed("Ledger text search", 500) { LedgerSearch.apply(entries, LedgerFilter(text = "shop 17"), categories, accounts) }
        assertTrue(found.isNotEmpty())
    }

    @Test fun insightsSummaryOverAllTime() {
        val period = Period(ms(2024, 1, 1, 0), ms(2026, 7, 1, 0))
        val summary = timed("Insights summary", 500) { Insights.summary(entries, categories, period, now, IST) }
        assertTrue(summary != null)
        timed("Insights category totals", 500) { Insights.categoryTotals(entries, categories, period, income = false) }
    }

    @Test fun duplicateScanStaysNearLinear() {
        timed("Duplicate scan", 2_000) { Duplicates.scan(entries) }
    }
}
