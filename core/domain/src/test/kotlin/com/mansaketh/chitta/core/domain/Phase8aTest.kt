package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private fun tx(
    id: String, amount: Long, name: String = "", account: String = "hdfc", cat: String = "food", date: Long = ms(2026, 11, 10),
    kind: com.mansaketh.chitta.core.model.TransactionKind? = null, split: String? = null, paired: String? = null, note: String = "",
) = Transaction(
    id = id, pairedTransactionId = paired, name = name, amount = amount, currency = "INR", note = note, categoryId = cat, subCategoryId = null,
    accountId = account, dateMs = date, modifiedAtMs = date, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = kind, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
    splitGroupId = split,
)

class SplitTest {
    @Test fun equalSplitGivesLeftoverPaiseToFirstLine() {
        // ₹100.00 over 3 lines = 3333, 3333, 3334 paise would be wrong: leftover 1 paise goes to the FIRST line.
        val lines = Splits.equalLines(Paise(10_000), listOf("a", "b", "c"))
        assertEquals(listOf(3_334L, 3_333L, 3_333L), lines.map { it.amount.value })
        assertEquals(Paise(10_000), lines.fold(Paise.ZERO) { s, l -> s + l.amount })
    }

    @Test fun equalSplitOfTenPaiseOverSevenLines() {
        val lines = Splits.equalLines(Paise(10), List(7) { "c$it" })
        assertEquals(listOf(4L, 1, 1, 1, 1, 1, 1), lines.map { it.amount.value })
    }

    @Test fun saveIsBlockedUntilLinesEqualTheTotal() {
        val total = r(1_200)
        val lines = listOf(SplitLine("food", null, r(700)), SplitLine("home", null, r(400)))
        assertEquals(SplitCheck.Left(r(100)), Splits.check(total, lines))
        assertEquals(r(100), Splits.remainder(total, lines))
        val over = lines + SplitLine("fun", null, r(200))
        assertEquals(SplitCheck.Over(r(100)), Splits.check(total, over))
        assertEquals(r(-100), Splits.remainder(total, over))
        val ok = lines + SplitLine("fun", null, r(100))
        assertEquals(SplitCheck.Balanced, Splits.check(total, ok))
    }

    @Test fun oneLineOrZeroLineIsRefused() {
        assertEquals(SplitCheck.NeedsTwoLines, Splits.check(r(10), listOf(SplitLine("a", null, r(10)))))
        assertEquals(SplitCheck.ZeroLine, Splits.check(r(10), listOf(SplitLine("a", null, r(10)), SplitLine("b", null, Paise.ZERO))))
    }

    @Test fun buildMakesOneNegativeEntryPerLineSharingAGroup() {
        var n = 0
        val template = tx("t", -120_000, "Big Bazaar")
        val lines = listOf(SplitLine("food", null, r(700)), SplitLine("home", null, r(500)))
        val out = assertNotNull(Splits.build(template, r(1_200), lines) { "id${n++}" })
        assertEquals(listOf(-70_000L, -50_000L), out.map { it.amount })
        assertEquals(setOf("id0"), out.map { it.splitGroupId }.toSet())
        assertEquals(-120_000L, out.sumOf { it.amount })
        assertEquals(setOf("food", "home"), out.map { it.categoryId }.toSet())
    }

    @Test fun buildRefusesAnUnbalancedSplit() {
        val lines = listOf(SplitLine("food", null, r(700)), SplitLine("home", null, r(400)))
        assertNull(Splits.build(tx("t", -120_000), r(1_200), lines) { "x" })
    }

    @Test fun siblingsAndGroupTotal() {
        val a = tx("a", -70_000, split = "g"); val b = tx("b", -50_000, split = "g"); val c = tx("c", -9_900)
        assertEquals(listOf("b"), Splits.siblings(a, listOf(a, b, c)).map { it.id })
        assertEquals(r(1_200), Splits.groupTotal(a, listOf(a, b, c)))
        assertEquals(Paise(9_900), Splits.groupTotal(c, listOf(a, b, c)))
    }
}

class RulesTest {
    private val swiggy = Rule("r1", "Swiggy", listOf(RuleCondition(RuleField.Title, RuleOp.Contains, "swiggy")), RuleActions(categoryId = "food"), order = 1)

    @Test fun titleContainsIgnoresCase() {
        val out = Rules.apply(listOf(swiggy), RuleInput("SWIGGY ORDER 123", amount = r(300), accountId = "hdfc"))
        assertEquals("food", out.categoryId); assertEquals(listOf("r1"), out.matchedRuleIds)
    }

    @Test fun allConditionsMustHoldAndEmptyNeverMatches() {
        val rule = Rule("r2", "Big swiggy", listOf(RuleCondition(RuleField.Title, RuleOp.Contains, "swiggy"), RuleCondition(RuleField.Amount, RuleOp.AtLeast, amount = r(500))), RuleActions(categoryId = "party"), 1)
        assertNull(Rules.apply(listOf(rule), RuleInput("Swiggy", amount = r(499), accountId = null)).categoryId)
        assertEquals("party", Rules.apply(listOf(rule), RuleInput("Swiggy", amount = r(500), accountId = null)).categoryId)
        assertTrue(Rules.apply(listOf(Rule("r3", "empty", emptyList(), RuleActions(categoryId = "x"), 1)), RuleInput("a", amount = r(1), accountId = null)).changesNothing)
    }

    @Test fun firstRuleWinsPerFieldButLaterRuleFillsOtherFields() {
        val a = Rule("a", "A", listOf(RuleCondition(RuleField.Title, RuleOp.Contains, "uber")), RuleActions(categoryId = "travel"), 1)
        val b = Rule("b", "B", listOf(RuleCondition(RuleField.Title, RuleOp.Contains, "uber")), RuleActions(categoryId = "other", accountId = "icici"), 2)
        val out = Rules.apply(listOf(b, a), RuleInput("Uber trip", amount = r(200), accountId = "hdfc"))
        assertEquals("travel", out.categoryId); assertEquals("icici", out.accountId); assertEquals(listOf("a", "b"), out.matchedRuleIds)
    }

    @Test fun disabledRuleAndAccountCondition() {
        assertTrue(Rules.apply(listOf(swiggy.copy(enabled = false)), RuleInput("swiggy", amount = r(1), accountId = null)).changesNothing)
        val byAccount = Rule("c", "Card", listOf(RuleCondition(RuleField.Account, RuleOp.Equals, accountId = "amex")), RuleActions(excludeFromBudgetIds = listOf("b1")), 1)
        assertEquals(listOf("b1"), Rules.apply(listOf(byAccount), RuleInput("x", amount = r(1), accountId = "amex")).excludeFromBudgetIds)
        assertTrue(Rules.apply(listOf(byAccount), RuleInput("x", amount = r(1), accountId = "hdfc")).changesNothing)
    }

    @Test fun previewListsOnlyRealChangesAndSkipsTransfersAndSplits() {
        val already = tx("1", -30_000, "Swiggy", cat = "food")
        val change = tx("2", -30_000, "Swiggy 2", cat = "misc")
        val transfer = tx("3", -30_000, "Swiggy transfer", cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "4")
        val split = tx("5", -30_000, "Swiggy split", cat = "misc", split = "g")
        val preview = Rules.previewHistory(listOf(swiggy), listOf(already, change, transfer, split))
        assertEquals(listOf("2"), preview.map { it.entryId })
        assertEquals("misc", change.categoryId)                 // preview wrote nothing
        assertEquals("food", Rules.applyChange(change, preview[0]).categoryId)
    }

    @Test fun applyToLeavesTransfersAlone() {
        val t = tx("t", -100, cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "p")
        assertEquals(t, Rules.applyTo(t, RuleOutcome("food", null, null, null, listOf("x"))))
    }
}

class DuplicatesTest {
    @Test fun sameAmountSimilarTitleWithinADayIsFlagged() {
        val a = tx("a", -45_000, "Swiggy", date = ms(2026, 11, 10, 12))
        val b = tx("b", -45_000, "SWIGGY ORDER", date = ms(2026, 11, 10, 20))
        assertEquals(listOf(DuplicatePair("a", "b", DuplicateReason.SameAmountAndTitle)), Duplicates.scan(listOf(b, a)))
        assertEquals(setOf("b"), Duplicates.flaggedIds(listOf(a, b)))
    }

    @Test fun twoRealPaymentsOfOneAmountWithDifferentTitlesStayTwo() {
        val a = tx("a", -10_000, "Tea stall", date = ms(2026, 11, 10, 9))
        val b = tx("b", -10_000, "Auto fare", date = ms(2026, 11, 10, 18))
        assertTrue(Duplicates.scan(listOf(a, b)).isEmpty())
    }

    @Test fun sameAmountWithinTenMinutesIsFlaggedWhateverTheTitle() {
        val a = tx("a", -10_000, "Tea", date = ms(2026, 11, 10, 9, 0))
        val b = tx("b", -10_000, "Chai", date = ms(2026, 11, 10, 9, 8))
        assertEquals(DuplicateReason.SameAmountCloseTime, Duplicates.scan(listOf(a, b)).single().reason)
    }

    @Test fun differentAmountOrAccountOrOverADayApartIsNotFlagged() {
        val a = tx("a", -10_000, "Swiggy")
        assertTrue(Duplicates.scan(listOf(a, tx("b", -10_001, "Swiggy"))).isEmpty())
        assertTrue(Duplicates.scan(listOf(a, tx("c", -10_000, "Swiggy", account = "icici"))).isEmpty())
        assertTrue(Duplicates.scan(listOf(a, tx("d", -10_000, "Swiggy", date = ms(2026, 11, 12)))).isEmpty())
    }

    @Test fun transfersAndLinesOfOneSplitAreNeverFlagged() {
        val t1 = tx("t1", -10_000, "Move", cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "t2")
        val t2 = tx("t2", -10_000, "Move", cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "t1")
        assertTrue(Duplicates.scan(listOf(t1, t2)).isEmpty())
        val s1 = tx("s1", -10_000, "Mart", split = "g"); val s2 = tx("s2", -10_000, "Mart", split = "g")
        assertTrue(Duplicates.scan(listOf(s1, s2)).isEmpty())
    }

    @Test fun titleSimilarityNeedsFourLettersForContainment() {
        assertTrue(Duplicates.similarTitles("Swiggy", "swiggy order"))
        assertFalse(Duplicates.similarTitles("Tea", "Tea stall"))
        assertFalse(Duplicates.similarTitles("", "x"))
    }
}

class CardsTest {
    private val setup = CardSetup("card", limit = r(100_000), statementDay = 15, dueDay = 5, minimumDue = Paise.of(1_234, 50))

    // Statement cut on 15 Oct. Owed on that day: 12,000 + 6,000 = 18,000. After it: spend 4,000 and 1,500, a payment of 10,000.
    private val book = listOf(
        tx("s1", -1_200_000, "Rent", account = "card", date = ms(2026, 10, 3)),
        tx("s2", -600_000, "Flights", account = "card", date = ms(2026, 10, 15, 9)),
        tx("u1", -400_000, "Phone", account = "card", date = ms(2026, 10, 20)),
        tx("u2", -150_000, "Dinner", account = "card", date = ms(2026, 11, 2)),
        tx("p1", 1_000_000, "Payment", account = "card", date = ms(2026, 11, 4), cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "x"),
        tx("other", -999_999, "Elsewhere", account = "hdfc", date = ms(2026, 10, 20)),
    )

    @Test fun outstandingIsStatementPlusUnbilledMinusPayments() {
        val f = Cards.figures(setup, book, LocalDate.of(2026, 11, 13), IST)
        assertEquals(LocalDate.of(2026, 11, 15).minusMonths(1), f.statementEnd)
        assertEquals(r(18_000), f.statementBalance)
        assertEquals(r(5_500), f.unbilled)
        assertEquals(r(10_000), f.paymentsSinceStatement)
        assertEquals(r(13_500), f.outstanding)                  // 18,000 + 5,500 - 10,000
        assertNotNull(f.statementBalance.takeIf { it != f.outstanding }) // statement balance is never shown as outstanding
    }

    @Test fun availableAndUtilisation() {
        val f = Cards.figures(setup, book, LocalDate.of(2026, 11, 13), IST)
        assertEquals(r(86_500), f.available)
        assertEquals(1_350, f.utilisationBp)                    // 13,500 / 1,00,000 = 13.5%
        assertFalse(f.overLimit)
    }

    @Test fun noLimitHidesUtilisation() {
        val f = Cards.figures(setup.copy(limit = null), book, LocalDate.of(2026, 11, 13), IST)
        assertNull(f.available); assertNull(f.utilisationBp); assertFalse(f.overLimit)
    }

    @Test fun overLimitIsItsOwnState() {
        val f = Cards.figures(setup.copy(limit = r(10_000)), book, LocalDate.of(2026, 11, 13), IST)
        assertTrue(f.overLimit); assertEquals(r(-3_500), f.available)
    }

    @Test fun overpaymentBecomesCreditNotNegativeOutstanding() {
        val more = book + tx("p2", 2_000_000, "Overpay", account = "card", date = ms(2026, 11, 8), cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "y")
        val f = Cards.figures(setup, more, LocalDate.of(2026, 11, 13), IST)
        assertEquals(Paise.ZERO, f.outstanding); assertEquals(r(6_500), f.credit)
    }

    @Test fun dueDateReminderAndShortMonths() {
        val f = Cards.figures(setup, book, LocalDate.of(2026, 11, 13), IST)
        assertEquals(LocalDate.of(2026, 12, 5), f.nextDue); assertEquals(22, f.daysToDue)
        assertEquals(LocalDate.of(2026, 12, 2), f.reminderDate)
        val s31 = setup.copy(statementDay = 31, dueDay = 31)
        assertEquals(LocalDate.of(2026, 2, 28), Cards.nextDue(s31, LocalDate.of(2026, 2, 10)))
        assertEquals(LocalDate.of(2026, 1, 31), Cards.lastStatementEnd(s31, LocalDate.of(2026, 2, 10)))
        assertEquals(LocalDate.of(2028, 2, 29), Cards.nextDue(s31, LocalDate.of(2028, 2, 10)))   // leap day
    }

    @Test fun statementDayAndPaymentDayBoundaries() {
        val onCut = Cards.figures(setup, book, LocalDate.of(2026, 10, 15), IST)
        assertEquals(LocalDate.of(2026, 10, 15), onCut.statementEnd)
        assertEquals(r(18_000), onCut.statementBalance)         // an entry on the cut day is in the statement
        assertEquals(Paise.ZERO, onCut.unbilled)
        assertEquals(0, Cards.figures(setup, book, LocalDate.of(2026, 12, 5), IST).daysToDue)
    }

    @Test fun reminderOnReminderDayAndDueDayOnlyWhileOwed() {
        val figs = { d: LocalDate -> Cards.figures(setup, book, d, IST) }
        // Due 5 Dec, reminder 3 days before = 2 Dec.
        val ahead = Cards.reminderFor(setup, figs(LocalDate.of(2026, 12, 2)), LocalDate.of(2026, 12, 2))
        assertNotNull(ahead); assertFalse(ahead.dueToday); assertEquals(3, ahead.daysToDue)
        val due = Cards.reminderFor(setup, figs(LocalDate.of(2026, 12, 5)), LocalDate.of(2026, 12, 5))
        assertNotNull(due); assertTrue(due.dueToday)
        assertNull(Cards.reminderFor(setup, figs(LocalDate.of(2026, 12, 3)), LocalDate.of(2026, 12, 3)))
        // Nothing owed: no reminder.
        val paid = book + tx("pay", 2_500_000, account = "card", date = ms(2026, 11, 30), cat = QuickAdd.TRANSFER_CATEGORY_ID, paired = "z")
        assertNull(Cards.reminderFor(setup, Cards.figures(setup, paid, LocalDate.of(2026, 12, 2), IST), LocalDate.of(2026, 12, 2)))
    }

    @Test fun zeroDayReminderSendsOnlyTheDueDayOne() {
        val s0 = setup.copy(remindDaysBefore = 0)
        val f = Cards.figures(s0, book, LocalDate.of(2026, 12, 5), IST)
        assertTrue(Cards.reminderFor(s0, f, LocalDate.of(2026, 12, 5))!!.dueToday)
    }
}

class AppLinksTest {
    private val accounts = listOf(
        com.mansaketh.chitta.core.model.Account("a1", "HDFC", null, null, 0, null, 0, "INR", null, 2, null, null),
        com.mansaketh.chitta.core.model.Account("a2", "Old", null, null, 0, null, 1, "INR", null, 2, null, null, archived = true),
    )
    private val cats = listOf(
        com.mansaketh.chitta.core.model.Category("food", "Food", null, null, null, 0, null, 0, false, null, null),
        com.mansaketh.chitta.core.model.Category("pay", "Salary", null, null, null, 0, null, 1, true, null, null),
    )
    private val now = ms(2026, 11, 13)

    @Test fun aFullLinkFillsTheDraftAndNeverSaves() {
        val r = AppLinks.parse(mapOf("amount" to "250", "title" to "Tea", "category" to "food", "account" to "hdfc", "note" to "with Ravi"), accounts, cats, now, IST)
        assertEquals("250", r.draft.amountRaw); assertEquals("Tea", r.draft.title); assertEquals("food", r.draft.categoryId)
        assertEquals("a1", r.draft.accountId); assertEquals("with Ravi", r.draft.note); assertEquals(EntryType.Expense, r.draft.type)
        assertTrue(r.notes.isEmpty())
    }

    @Test fun badPartsAreLeftBlankAndExplained() {
        val r = AppLinks.parse(mapOf("amount" to "abc", "category" to "Nope", "account" to "Old", "date" to "13/11/2026"), accounts, cats, now, IST)
        assertEquals("", r.draft.amountRaw); assertNull(r.draft.categoryId); assertNull(r.draft.accountId); assertEquals(now, r.draft.dateMs)
        assertEquals(4, r.notes.size)
    }

    @Test fun incomeOnlyMatchesIncomeCategoriesAndACalculationIsAllowed() {
        val r = AppLinks.parse(mapOf("type" to "income", "category" to "Salary", "amount" to "1000+500"), accounts, cats, now, IST)
        assertEquals(EntryType.Income, r.draft.type); assertEquals("pay", r.draft.categoryId); assertEquals("1000+500", r.draft.amountRaw)
        assertNull(AppLinks.parse(mapOf("type" to "income", "category" to "Food"), accounts, cats, now, IST).draft.categoryId)
    }

    @Test fun anEmptyLinkOpensAnEmptyExpense() {
        val r = AppLinks.parse(emptyMap(), accounts, cats, now, IST)
        assertEquals(EntryType.Expense, r.draft.type); assertEquals("", r.draft.amountRaw); assertTrue(r.notes.isEmpty())
    }
}
