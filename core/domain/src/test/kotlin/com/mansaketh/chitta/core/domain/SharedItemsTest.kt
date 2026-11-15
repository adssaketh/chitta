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
    // ---- step 5b: members' edits

    private val ownerFd = FixedDeposit("fd1", "Axis", "X0001", 100_000_00L, "7.1", ms(2026, 1, 10), ms(2027, 1, 10), 4, null, "acc1", false, "", true, "sms9", 5L)
    private val ownerLoan = Objective("L1", ObjectiveKind.LOAN, "Card loan", 0L, 3, null, 1L, null, 2L, null, null, false, false, false, "acc1")
    private val ownerTerms = LoanTerms("L1", 50_000_00L, "15", 12, 4_500_00L, ms(2026, 2, 5), 500_00L, "18", false, listOf(1), "acc1", "", 9L)

    @Test fun editorTravelsInThePayloadAndIsDroppedAgain() {
        val base = SharedItems.depositPayload(ownerFd, zone)
        assertNull(SharedItems.editorOf(base))
        val edited = SharedItems.withEditor(base, "a")
        assertEquals("a", SharedItems.editorOf(edited))
        assertNull(SharedItems.editorOf(SharedItems.withoutEditor(edited)))
        assertNull(SharedItems.editorOf(mapOf("editedBy" to "")))
    }

    @Test fun memberEditTimeIsAlwaysAfterTheCopyTheyStartedFrom() {
        assertEquals(5_000L, SharedItems.editTime(5_000L, 4_000L))
        assertEquals(4_001L, SharedItems.editTime(3_000L, 4_000L))   // a slow clock does not lose to the copy it started from
    }

    @Test fun newestChangeWinsBetweenOwnerAndMember() {
        assertFalse(SharedItems.memberEditWaiting(null, 100, 100))
        assertFalse(SharedItems.memberEditWaiting(100, 100, 100))     // same time = the owner's own change
        assertFalse(SharedItems.memberEditWaiting(90, 100, 80))       // the owner's later edit beats the member's earlier one
        assertTrue(SharedItems.memberEditWaiting(200, 100, 100))      // the member's later edit beats the owner's older one
        assertFalse(SharedItems.memberEditWaiting(200, 100, 200))     // already applied and sent back
        assertTrue(SharedItems.memberEditWaiting(200, 0, null))       // after a reinstall
    }

    @Test fun aMemberIsRefusedWhenTheServerCopyIsNewer() {
        assertTrue(SharedItems.editStillValid(100, 100))
        assertTrue(SharedItems.editStillValid(90, 100))
        assertFalse(SharedItems.editStillValid(101, 100))
    }

    @Test fun anEditIsReplacedOnlyByAnotherPersonsLaterChange() {
        assertFalse(SharedItems.editWasReplaced(null, 300, null, "a"))   // never edited
        assertFalse(SharedItems.editWasReplaced(200, 200, "a", "a"))     // my own edit coming back
        assertFalse(SharedItems.editWasReplaced(200, 150, null, "a"))    // older than mine
        assertTrue(SharedItems.editWasReplaced(200, 300, null, "a"))     // the owner changed it later
        assertTrue(SharedItems.editWasReplaced(200, 300, "b", "a"))      // another member did
        assertFalse(SharedItems.editWasReplaced(200, 300, "a", "a"))     // a later edit of mine
    }

    @Test fun aDepositEditKeepsTheOwnersAccountAndIdentity() {
        val edited = ownerFd.copy(id = "other", bank = "SBI", ratePct = "7.5", accountId = "theirs", note = "renewed", fromSms = false, smsId = "")
        val out = SharedItems.applyDepositEdit(ownerFd, SharedItems.withEditor(SharedItems.depositPayload(edited, zone), "a"), zone, 777L)
        assertNotNull(out)
        assertEquals("fd1", out.id)
        assertEquals("acc1", out.accountId)
        assertEquals("SBI", out.bank)
        assertEquals("7.5", out.ratePct)
        assertEquals("renewed", out.note)
        assertTrue(out.fromSms)
        assertEquals("sms9", out.smsId)
        assertEquals(777L, out.updatedAtMs)
        assertEquals(100_000_00L, out.principal)
    }

    @Test fun aDepositEditThatCouldNotBeADepositIsRefused() {
        fun bad(f: (FixedDeposit) -> FixedDeposit) = SharedItems.applyDepositEdit(ownerFd, SharedItems.depositPayload(f(ownerFd), zone), zone, 1L)
        assertNull(bad { it.copy(bank = " ") })
        assertNull(bad { it.copy(principal = 0L) })
        assertNull(bad { it.copy(ratePct = "31") })
        assertNull(bad { it.copy(ratePct = "-1") })
        assertNull(bad { it.copy(maturityMs = it.startMs) })
        assertNull(bad { it.copy(maturityAmount = 90_000_00L) })
        assertNull(SharedItems.applyDepositEdit(ownerFd, mapOf("id" to "x"), zone, 1L))
        assertNotNull(bad { it.copy(ratePct = "30", maturityAmount = 120_000_00L) })
    }

    @Test fun aLoanEditChangesNameAndTermsButNotAccountsOrAmount() {
        val payload = SharedItems.loanPayload(ownerLoan.copy(name = "  Car loan  "), ownerTerms.copy(note = "bank said 3 EMI holiday", accountId = "theirs", paidInstalments = listOf(1, 2, 99)), 9_000_00L, zone)
        val out = SharedItems.applyLoanEdit(ownerLoan, ownerTerms, SharedItems.withEditor(payload, "a"), zone, 888L)
        assertNotNull(out)
        val (o, t) = out
        assertEquals("Car loan", o.name)
        assertEquals(0L, o.amount)
        assertEquals("acc1", o.accountId)
        assertEquals(888L, o.modifiedAtMs)
        assertEquals("bank said 3 EMI holiday", t.note)
        assertEquals("acc1", t.accountId)
        assertEquals("L1", t.objectiveId)
        assertEquals(listOf(1, 2), t.paidInstalments)   // an instalment that does not exist is dropped
        assertEquals(888L, t.updatedAtMs)
    }

    @Test fun aLoanEditWithoutUsableTermsIsRefused() {
        fun bad(f: (LoanTerms) -> LoanTerms) = SharedItems.applyLoanEdit(ownerLoan, ownerTerms, SharedItems.loanPayload(ownerLoan, f(ownerTerms), 0L, zone), zone, 1L)
        assertNull(bad { it.copy(emi = 0L) })
        assertNull(bad { it.copy(principal = 0L) })
        assertNull(bad { it.copy(months = 0) })
        assertNull(bad { it.copy(months = 601) })
        assertNull(bad { it.copy(annualRatePct = "101") })
        assertNull(SharedItems.applyLoanEdit(ownerLoan, ownerTerms, mapOf("objective" to mapOf("name" to "x")), zone, 1L))
        assertNotNull(bad { it.copy(annualRatePct = "0") })
    }

    @Test fun aBlankLoanNameKeepsTheOwnersName() {
        val payload = SharedItems.loanPayload(ownerLoan.copy(name = "   "), ownerTerms, 0L, zone)
        val out = SharedItems.applyLoanEdit(ownerLoan, ownerTerms, payload, zone, 1L)
        assertNotNull(out)
        assertEquals("Card loan", out.first.name)
    }

    @Test fun aLoanDetailsEditTouchesOnlyNameAndNote() {
        val payload = SharedItems.loanPayload(ownerLoan, ownerTerms, 9_000_00L, zone)
        val out = SharedItems.loanDetailsEdit(payload, "  Car loan ", " paid by cheque ")
        val back = SharedItems.loanFromPayload("L1", out, zone)
        assertEquals("Car loan", back.name)
        assertEquals("paid by cheque", back.terms?.note)
        assertEquals(ownerTerms.emi, back.terms?.emi)
        assertEquals(ownerTerms.paidInstalments, back.terms?.paidInstalments)
        assertEquals(9_000_00L, back.paidSoFar)
        // the edit still applies to the owner's loan
        val applied = SharedItems.applyLoanEdit(ownerLoan, ownerTerms, SharedItems.withEditor(out, "a"), zone, 5L)
        assertNotNull(applied)
        assertEquals("Car loan", applied.first.name)
        assertEquals("paid by cheque", applied.second.note)
    }
}
