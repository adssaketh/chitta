package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedItemsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val members = setOf("owner", "a", "b", "c")

    @Test fun docIdIncludesOwnerAndReplacesSlash() {
        assertEquals("fd-owner-abc", SharedItems.docId("fd", "owner", "abc"))
        assertEquals("loan-u1-a_b", SharedItems.docId("loan", "u1", "a/b"))
    }

    @Test fun cleanVisibleToKeepsMembersOnlyWithoutOwnerBlanksOrDuplicates() {
        val out = SharedItems.cleanVisibleTo("owner", listOf("a", "", "owner", "x", "b", "a"), members)
        assertEquals(listOf("a", "b"), out)
    }

    @Test fun cleanVisibleToStopsAtSeven() {
        val many = (1..9).map { "m$it" }
        val out = SharedItems.cleanVisibleTo("owner", many, many.toSet() + "owner")
        assertEquals(7, out.size)
        assertEquals(many.take(7), out)
    }

    @Test fun visibilityAndEditRights() {
        val item = SharedItems.Meta("fd", "owner", "1", listOf("a"), membersCanEdit = false)
        assertTrue(SharedItems.visibleTo(item, "owner", members))
        assertTrue(SharedItems.visibleTo(item, "a", members))
        assertFalse(SharedItems.visibleTo(item, "b", members))
        assertFalse(SharedItems.canEdit(item, "a", members))
        assertTrue(SharedItems.canEdit(item.copy(membersCanEdit = true), "a", members))
        assertFalse(SharedItems.canEdit(item.copy(membersCanEdit = true), "b", members))
        assertTrue(SharedItems.canEdit(item, "owner", members))
        assertTrue(SharedItems.canManage(item, "owner"))
        assertFalse(SharedItems.canManage(item.copy(membersCanEdit = true), "a"))
    }

    @Test fun itemsOfSomebodyWhoLeftAreSeenByNobody() {
        val item = SharedItems.Meta("fd", "gone", "1", listOf("a"))
        assertFalse(SharedItems.visibleTo(item, "a", members))
        assertFalse(SharedItems.visibleTo(item.copy(ownerUid = "owner"), "a", members - "a"))
    }

    @Test fun audienceChangeNamesWhoIsAddedAndRemoved() {
        val ch = SharedItems.audienceChange(listOf("a", "b"), listOf("b", "c"))
        assertEquals(listOf("c"), ch.added)
        assertEquals(listOf("a"), ch.removed)
        assertTrue(SharedItems.audienceChange(listOf("a"), listOf("a")).isEmpty)
    }

    @Test fun metaRoundTripsThroughTheDocument() {
        val m = SharedItems.Meta("loan", "owner", "L1", listOf("a", "b"), true, 1234L)
        assertEquals(m, SharedItems.metaFromDocument(SharedItems.metaToFields(m)))
    }

    @Test fun badDocumentsAreRefused() {
        assertNull(SharedItems.metaFromDocument(mapOf("kind" to "gold", "ownerUid" to "o", "localId" to "1")))
        assertNull(SharedItems.metaFromDocument(mapOf("kind" to "fd", "ownerUid" to "", "localId" to "1")))
        assertFalse(SharedItems.isUsable("fd-o-1", mapOf("kind" to "fd", "ownerUid" to "o", "localId" to "1")))      // no payload
        assertFalse(SharedItems.isUsable("fd-x-1", mapOf("kind" to "fd", "ownerUid" to "o", "localId" to "1", "payload" to emptyMap<String, Any?>())))
        assertTrue(SharedItems.isUsable("fd-o-1", mapOf("kind" to "fd", "ownerUid" to "o", "localId" to "1", "payload" to emptyMap<String, Any?>())))
    }

    @Test fun onlyNewerDocumentsAreWritten() {
        val held = mapOf("a" to 100L, "b" to 100L)
        val incoming = mapOf("a" to 100L, "b" to 101L, "c" to 5L)
        assertEquals(setOf("b", "c"), SharedItems.idsToWrite(held, incoming))
    }

    @Test fun depositSurvivesTheWireAndLosesItsAccount() {
        val fd = FixedDeposit("fd1", "Axis", "X0001", 100_000_00L, "7.1", ms(2026, 1, 10), ms(2027, 1, 10), 4, 107_300_55L, "acc1", false, "for the house", true, "sms9", 5L)
        val back = SharedItems.depositFromPayload(SharedItems.depositPayload(fd, zone), zone)
        assertNotNull(back)
        assertEquals(fd.copy(accountId = null, updatedAtMs = null), back)
    }

    @Test fun depositPayloadUsesTheOldAppsShape() {
        val fd = FixedDeposit("fd1", "HDFC", "", 30_000_00L, "7", ms(2026, 3, 1), ms(2027, 3, 1), 4, null, null, false, "", false, "", null)
        val p = SharedItems.depositPayload(fd, zone)
        assertEquals(30_000.0, p["principal"])
        assertEquals(7.0, p["rate"])
        assertEquals("2026-03-01T00:00:00.000", p["start"])
        assertNull(p["maturityAmount"])
        assertFalse(p.containsKey("wallet"))
    }

    @Test fun depositFromTheOldAppIsRead() {
        val p = mapOf<String, Any?>("id" to "d", "bank" to "SBI", "no" to "9", "principal" to 50000.5, "rate" to 6.75, "start" to "2026-05-01T00:00:00.000", "maturity" to "2027-05-01T00:00:00.000", "comp" to 4, "maturityAmount" to null, "wallet" to "w1", "closed" to false, "note" to "", "sms" to false, "smsId" to "")
        val fd = SharedItems.depositFromPayload(p, zone)!!
        assertEquals(50_000_50L, fd.principal)
        assertEquals("6.75", fd.ratePct)
        assertEquals(ms(2026, 5, 1), fd.startMs)
        assertNull(fd.accountId)
    }

    @Test fun depositWithoutDatesIsRefused() {
        assertNull(SharedItems.depositFromPayload(mapOf("id" to "d", "start" to "nonsense", "maturity" to "2027-05-01T00:00:00.000"), zone))
    }

    @Test fun loanSurvivesTheWire() {
        val o = Objective("L1", ObjectiveKind.LOAN, "Card loan", 50_000_00L, 3, null, 1L, null, 2L, null, null, false, true, false, "acc1")
        val t = LoanTerms("L1", 50_000_00L, "15", 12, 4_500_00L, ms(2026, 2, 5), 500_00L, "18", true, listOf(1, 2, 3), "acc1", "note", 9L)
        val p = SharedItems.loanPayload(o, t, 14_000_00L, zone)
        val back = SharedItems.loanFromPayload("L1", p, zone)
        assertEquals("Card loan", back.name)
        assertFalse(back.lent)
        assertFalse(back.archived)
        assertEquals(14_000_00L, back.paidSoFar)
        assertEquals(t.copy(accountId = null, updatedAtMs = null), back.terms)
        assertFalse((p["objective"] as Map<*, *>).containsKey("walletFk"))
    }

    @Test fun loanWithoutTermsOrFigureFallsBackHarmlessly() {
        val back = SharedItems.loanFromPayload("L9", mapOf("objective" to mapOf("name" to " ", "archived" to true)), zone)
        assertEquals("Loan", back.name)
        assertTrue(back.archived)
        assertNull(back.terms)
        assertNull(back.paidSoFar)
    }

    @Test fun paidFigureChangeSendsAgain() {
        assertTrue(SharedItems.paidChanged(null, 0))
        assertFalse(SharedItems.paidChanged(500, 500))
        assertTrue(SharedItems.paidChanged(500, 600))
    }
}
