package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = 1793000000000L

    private fun account(id: String, name: String) = Account(id, name, null, null, 0, null, 0, "INR", null, 2, null, null)
    private fun cat(id: String, name: String, income: Boolean = false) =
        Category(id, name, null, null, null, 0, null, 0, income, null, null)
    private fun row(id: String = "n1-a", title: String = "Zomato", amount: Long = 25000, expense: Boolean = true, known: Boolean = true, label: String = "dining",
                    merchant: String = "Zomato", wallet: String = "Axis", ts: Long = day, upi: Boolean = false, tick: Boolean = true, closed: Boolean = false) =
        IntakeRow(if (upi) "pp-$id" else id, "20261026-$id", "AX-AXISBK-S", ts, amount, expense, title, merchant, label, "Axis 1234", wallet, closed, tick, known)

    private val accounts = listOf(account("a1", "Axis"))
    private val cats = listOf(cat("1", "Dining"), cat("11", "Income", true))
    private fun ctx(existing: List<Transaction> = emptyList(), memory: List<TitleMemory> = emptyList(), accts: List<Account> = accounts) =
        IntakeContext(accts, cats, existing, memory, zone)

    private fun existing(id: String, note: String, amount: Long = -25000, name: String = "Zomato") =
        autoEntry(row()).copy(id = id, note = note, amount = amount, name = name)

    private fun autoEntry(r: IntakeRow): Transaction =
        IntakePlanner.autoEntries(IntakePlanner.plan(listOf(r), ctx()), accounts, 0) { "x" }.first()

    @Test fun confidentMessageSavesItselfWithPaiseAndSign() {
        val plan = IntakePlanner.plan(listOf(row(amount = 69743)), ctx())
        assertEquals(1, plan.toAutoSave.size)
        val e = IntakePlanner.autoEntries(plan, accounts, 5) { "id1" }.single()
        assertEquals(-69743L, e.amount) // ₹697.43 out, exact
        assertFalse(e.income)
        assertEquals("1", e.categoryId)
        assertTrue(e.note.contains("Ping SMS #n1-a"))
    }

    @Test fun incomeSavesAsPositive() {
        val plan = IntakePlanner.plan(listOf(row(id = "i1", expense = false, label = "income", merchant = "", title = "Salary", amount = 5000000)), ctx())
        val e = IntakePlanner.autoEntries(plan, accounts, 5) { "id" }.single()
        assertEquals(5000000L, e.amount)
        assertTrue(e.income)
    }

    @Test fun unknownAccountClosedPensionNoMerchantNoCategoryGoToReview() {
        listOf(
            row(known = false), row(closed = true), row(tick = false), row(merchant = ""), row(label = ""),
        ).forEach { assertEquals(1, IntakePlanner.plan(listOf(it), ctx()).toReview.size, it.toString()) }
    }

    @Test fun accountNotInBookGoesToReviewWithNoAccountReason() {
        val p = IntakePlanner.plan(listOf(row()), ctx(accts = emptyList()))
        assertEquals(1, p.toReview.size)
        assertTrue(IntakeReason.NoAccount in p.toReview[0].reasons)
    }

    @Test fun learnedCategoryBeatsKeywordAndMakesAMissingLabelConfident() {
        val mem = listOf(TitleMemory("t", "11", "Zomato", 0, null, 0, exactMatch = true))
        val p = IntakePlanner.plan(listOf(row(label = "")), ctx(memory = mem))
        assertEquals("11", p.toAutoSave.single().categoryId)
    }

    @Test fun alreadyImportedIsDroppedByIdOrByTextKeyButNotByPrefix() {
        val byId = existing("e1", "Ping SMS #n1-a Ping SMS key 20261026-zzz")
        assertEquals(1, IntakePlanner.plan(listOf(row()), ctx(listOf(byId))).alreadyImported)
        val byKey = existing("e2", "Ping SMS #55 Ping SMS key 20261026-n1-a")
        assertEquals(1, IntakePlanner.plan(listOf(row()), ctx(listOf(byKey))).alreadyImported)
        val longer = existing("e3", "Ping SMS #n1-ab")
        assertEquals(0, IntakePlanner.plan(listOf(row()), ctx(listOf(longer))).alreadyImported)
    }

    @Test fun possibleDuplicateNeverAutoSavesAndEachExistingEntryFlagsOnlyOneRow() {
        val ex = existing("e1", "typed by hand")
        val p = IntakePlanner.plan(listOf(row(id = "a"), row(id = "b")), ctx(listOf(ex)))
        assertEquals(1, p.toReview.size)
        assertEquals(1, p.toAutoSave.size)
        assertTrue(IntakeReason.PossibleDuplicate in p.toReview[0].reasons)
    }

    @Test fun upiPaymentMergesWithItsBankSmsTwinOnce() {
        val sms = row(id = "s", title = "UPI debit", merchant = "", label = "")
        val upi = row(id = "u", title = "Chai Stall", merchant = "Chai Stall", label = "dining", upi = true)
        val merged = Twins.merge(listOf(sms, upi), zone)
        assertEquals(1, merged.size)
        assertEquals("Chai Stall", merged[0].title)
        assertFalse(merged[0].isUpi)
        // two UPI payments but one SMS: only one twin is claimed
        val upi2 = upi.copy(sourceId = "pp-u2")
        assertEquals(2, Twins.merge(listOf(sms, upi, upi2), zone).size)
    }

    @Test fun upiRowsAreNeverSavedWithoutALook() {
        val p = IntakePlanner.plan(listOf(row(id = "u", upi = true)), ctx())
        assertEquals(0, p.toAutoSave.size)
    }

    @Test fun reviewOnlySavesTickedAndSaveableRows() {
        val p = IntakePlanner.plan(listOf(row(id = "a", known = false), row(id = "b", known = false, label = "")), ctx())
        val choices = IntakePlanner.initialChoices(p)
        assertTrue(choices.all { !it.ticked }) // reasons exist, so nothing is ticked by itself
        val ticked = choices.map { it.copy(ticked = true) }
        var idn = 0
        val saved = IntakePlanner.toEntries(ticked, accounts, 1) { "n" + (idn++) }.size
        assertEquals(1, saved) // the second has no category, so it cannot be saved
        assertNull(choices[1].categoryId)
    }

    @Test fun upiPaymentAlreadyInTheBookFromItsBankSmsOnlyAddsItsDetails() {
        val smsEntry = existing("e1", "Axis 1234\nPing SMS #n1-a Ping SMS key 20261026-n1-a Ping SMS from AX-AXISBK-S")
        val upi = row(id = "u", upi = true, title = "Chai Stall")
        val p = IntakePlanner.plan(listOf(upi), ctx(listOf(smsEntry)))
        assertEquals(0, p.candidates.size)
        assertEquals("e1", p.claims.single().entryId)
        assertTrue(p.claims.single().row.claimNote.contains("Ping SMS #pp-u"))
        // the same book entry is claimed only once: a second UPI payment of the same amount stays its own row
        val p2 = IntakePlanner.plan(listOf(upi, upi.copy(sourceId = "pp-u2", contentKey = "pp-u2")), ctx(listOf(smsEntry)))
        assertEquals(1, p2.candidates.size)
        assertEquals(1, p2.claims.size)
    }

    @Test fun bankSmsWhoseUpiHalfIsAlreadyInTheBookIsClaimedNotSavedAgain() {
        val upiEntry = existing("e1", "PhonePe\nPing SMS #pp-T1 Ping SMS key pp-T1")
        val p = IntakePlanner.plan(listOf(row(id = "s")), ctx(listOf(upiEntry)))
        assertEquals(0, p.candidates.size)
        assertEquals(1, p.claims.size)
        // an entry that already has both halves is not claimed again
        val both = existing("e2", "Ping SMS #pp-T1 Ping SMS #n9-z")
        assertEquals(0, IntakePlanner.plan(listOf(row(id = "s2")), ctx(listOf(both))).claims.size)
    }
}
