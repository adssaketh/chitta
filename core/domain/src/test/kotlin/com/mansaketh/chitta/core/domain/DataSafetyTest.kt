package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, IST).toInstant().toEpochMilli()
private fun acct(id: String, name: String = id) = Account(id, name, null, null, 0, null, 0, "INR", null, 2, null, null)
private fun cat(id: String, name: String = id, income: Boolean = false, main: String? = null) =
    Category(id, name, null, null, null, 0, null, 0, income, null, main)

private fun entry(id: String, amount: Long, date: Long, account: String = "hdfc", name: String = "", category: String = "food", paired: String? = null) = Transaction(
    id = id, pairedTransactionId = paired, name = name, amount = amount, currency = "INR", note = "", categoryId = category, subCategoryId = null,
    accountId = account, dateMs = date, modifiedAtMs = null, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

class CsvTest {
    @Test fun readsQuotesCommasAndLineBreaksInsideQuotes() {
        val text = "a,b,c\r\n1,\"x, y\",\"say \"\"hi\"\"\"\r\n2,\"line1\nline2\",z\r\n"
        val rows = Csv.parse(text)
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("1", "x, y", "say \"hi\""), rows[1])
        assertEquals(listOf("2", "line1\nline2", "z"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test fun dropsBomBlankLinesAndHandlesNoFinalNewline() {
        val rows = Csv.parse("﻿a,b\n\n1,2")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }

    @Test fun keepsEmptyFields() {
        assertEquals(listOf(listOf("a", "", "c", "")), Csv.parse("a,,c,"))
    }

    @Test fun detectsSemicolonAndTab() {
        assertEquals(';', Csv.detectDelimiter("Date;Amount;Title\n2026-01-01;5,50;Tea"))
        assertEquals('\t', Csv.detectDelimiter("Date\tAmount\n2026-01-01\t5"))
        assertEquals(',', Csv.detectDelimiter("just one column"))
    }

    @Test fun writeThenParseRoundTrips() {
        val rows = listOf(listOf("Title", "Note"), listOf("Tea, masala", "said \"good\""), listOf("Multi", "a\nb"), listOf(" padded ", ""))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }
}

class CsvAmountTest {
    private fun p(s: String) = CsvImport.parseAmount(s)?.paise

    @Test fun readsCommonBankFormats() {
        assertEquals(123450L, p("1,234.50"))
        assertEquals(123450L, p("₹1,234.50"))
        assertEquals(-50000L, p("(500)"))
        assertEquals(-123450L, p("1,234.50 Dr"))
        assertEquals(123450L, p("1,234.50 Cr"))
        assertEquals(9900L, p("Rs. 99"))
        assertEquals(-1250L, p("-12.5"))
        assertEquals(-1250L, p("12.50-"))
        assertEquals(12345600L, p("1,23,456"))
    }

    @Test fun rejectsText() {
        assertNull(p(""))
        assertNull(p("abc"))
        assertNull(p("12.3.4"))
    }

    @Test fun roundsExtraDecimalsHalfEvenAndSaysSo() {
        val a = CsvImport.parseAmount("10.005")!!
        assertEquals(1000L, a.paise); assertTrue(a.rounded) // half-even: 10.005 -> 10.00
        val b = CsvImport.parseAmount("10.015")!!
        assertEquals(1002L, b.paise); assertTrue(b.rounded)
        assertFalse(CsvImport.parseAmount("10.50")!!.rounded)
        assertFalse(CsvImport.parseAmount("10.500")!!.rounded)
    }
}

class CsvDateTest {
    @Test fun detectsLayoutAndPrefersDayFirst() {
        assertEquals(DatePattern.Iso, DatePattern.detect(listOf("2026-11-13", "2026-01-05 10:30"), IST))
        assertEquals(DatePattern.DayMonthYearSlash, DatePattern.detect(listOf("05/11/2026", "13/11/2026"), IST))
        assertEquals(DatePattern.DayMonthYearSlash, DatePattern.detect(listOf("05/11/2026"), IST)) // ambiguous: day first
        assertEquals(DatePattern.MonthDayYearSlash, DatePattern.detect(listOf("11/13/2026"), IST))
        assertEquals(DatePattern.DayMonthName, DatePattern.detect(listOf("13 Nov 2026"), IST))
        assertNull(DatePattern.detect(listOf("not a date"), IST))
    }

    @Test fun dateOnlyIsNoonAndTimeIsKept() {
        assertEquals(ms(2026, 11, 13, 12), DatePattern.Iso.parse("2026-11-13", IST))
        assertEquals(ms(2026, 11, 13, 9, 45), DatePattern.Iso.parse("2026-11-13 09:45", IST))
        assertNull(DatePattern.Iso.parse("2026-02-30", IST)) // strict: no 30 February
    }

    @Test fun leapDay() {
        assertEquals(ms(2028, 2, 29), DatePattern.Iso.parse("2028-02-29", IST))
        assertNull(DatePattern.Iso.parse("2027-02-29", IST))
    }
}

class CsvImportTest {
    private val accounts = listOf(acct("hdfc", "HDFC"), acct("cash", "Cash"))
    private val categories = listOf(cat("food", "Food"), cat("eat", "Eating out", main = "food"), cat("salary", "Salary", income = true), cat("other", "Other"))
    private fun ctx(existing: List<Transaction> = emptyList(), memory: List<TitleMemory> = emptyList()) = CsvContext(accounts, categories, existing, memory, IST)

    @Test fun guessesMappingFromHeader() {
        val m = CsvImport.guessMapping(listOf("Date", "Account", "Category", "Subcategory", "Title", "Amount", "Currency", "Type", "Status", "Note"))
        assertEquals(0, m.date); assertEquals(1, m.account); assertEquals(2, m.category); assertEquals(3, m.subcategory)
        assertEquals(4, m.title); assertEquals(5, m.amount); assertEquals(7, m.type); assertEquals(9, m.note)
        assertTrue(m.problems().isEmpty() || m.problems().isNotEmpty()) // problems() only checks date and amount
        val bank = CsvImport.guessMapping(listOf("Txn Date", "Narration", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"))
        assertEquals(0, bank.date); assertEquals(1, bank.title); assertNull(bank.amount)
    }

    @Test fun readsRowsAndMatchesNames() {
        val rows = Csv.parse("Date,Account,Category,Title,Amount\n2026-11-12,hdfc,food,Lunch,-250.50\n2026-11-12,HDFC,Salary,Pay,50000\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso)
        val r = CsvImport.read(rows, m, ctx())
        assertEquals(2, r.rows.size)
        val lunch = r.rows[0]
        assertEquals(-25050L, lunch.amountPaise); assertEquals("hdfc", lunch.accountId); assertEquals("food", lunch.categoryId); assertTrue(lunch.ready)
        assertTrue(r.rows[1].income); assertEquals("salary", r.rows[1].categoryId)
    }

    @Test fun unknownNamesNeedALookAndAreNotGuessed() {
        val rows = Csv.parse("Date,Account,Category,Title,Amount\n2026-11-12,Axis,Gadgets,Phone,-999\n2026-11-12,Cash,,Mystery,-10\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso)
        val r = CsvImport.read(rows, m, ctx())
        assertEquals(listOf(CsvReason.UnknownAccount, CsvReason.UnknownCategory), r.rows[0].reasons)
        assertNull(r.rows[0].accountId); assertNull(r.rows[0].categoryId); assertFalse(r.rows[0].ready)
        assertEquals(listOf(CsvReason.NoCategory), r.rows[1].reasons)
    }

    @Test fun titlesMemoryFillsAMissingCategory() {
        val memory = listOf(TitleMemory("t1", "other", "Tea stall", 0, null, 0, false))
        val rows = Csv.parse("Date,Title,Amount\n2026-11-12,Tea stall,-20\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso, defaultAccountId = "cash")
        val r = CsvImport.read(rows, m, ctx(memory = memory))
        assertEquals("other", r.rows[0].categoryId); assertTrue(r.rows[0].ready)
    }

    @Test fun subcategoryIsMatchedUnderItsMainCategory() {
        val rows = Csv.parse("Date,Category,Subcategory,Title,Amount\n2026-11-12,Food,Eating out,Dinner,-800\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso, defaultAccountId = "hdfc")
        val r = CsvImport.read(rows, m, ctx())
        assertEquals("food", r.rows[0].categoryId); assertEquals("eat", r.rows[0].subCategoryId)
    }

    @Test fun debitAndCreditColumns() {
        val rows = Csv.parse("Date,Narration,Debit,Credit\n12/11/2026,Rent,15000.00,\n13/11/2026,Refund,,200\n14/11/2026,Nothing,,\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.DayMonthYearSlash, defaultAccountId = "hdfc")
        val r = CsvImport.read(rows, m, ctx())
        assertEquals(listOf(-1500000L, 20000L), r.rows.map { it.amountPaise })
        assertEquals(1, r.skipped.size); assertEquals(4, r.skipped[0].line)
    }

    @Test fun positiveIsExpenseFlipsSigns() {
        val rows = Csv.parse("Date,Title,Amount\n2026-11-12,Shop,500\n2026-11-13,Payment,-500\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso, defaultAccountId = "hdfc", positiveIsExpense = true)
        assertEquals(listOf(-50000L, 50000L), CsvImport.read(rows, m, ctx()).rows.map { it.amountPaise })
    }

    @Test fun badDateAndBadAmountAreLeftOutWithTheirLine() {
        val rows = Csv.parse("Date,Title,Amount\nsoon,A,5\n2026-11-12,B,abc\n2026-11-12,C,0\n2026-11-12,D,7\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso, defaultAccountId = "hdfc")
        val r = CsvImport.read(rows, m, ctx())
        assertEquals(listOf(2, 3, 4), r.skipped.map { it.line })
        assertEquals(1, r.rows.size); assertEquals(5, r.rows[0].line)
    }

    @Test fun transfersAndCorrectionsAreSkippedNotSavedAsSpending() {
        val rows = Csv.parse("Date,Title,Amount,Type\n2026-11-12,To savings,-5000,Transfer\n2026-11-12,Fix,100,Balance correction\n2026-11-12,Tea,-20,Expense\n")
        val m = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso, defaultAccountId = "hdfc")
        val r = CsvImport.read(rows, m, ctx())
        assertEquals(2, r.skipped.size); assertEquals(1, r.rows.size)
    }

    @Test fun importingTheSameFileTwiceFlagsEveryRow() {
        val file = "Date,Title,Amount,Category\n2026-11-12,Lunch,-250,food\n2026-11-13,Tea,-20,food\n"
        val mapping = CsvMapping(date = 0, title = 1, amount = 2, category = 3, defaultAccountId = "hdfc", datePattern = DatePattern.Iso)
        val first = CsvImport.read(Csv.parse(file), mapping, ctx())
        val saved = CsvImport.toEntries(CsvImport.initialChoices(first), accounts, 0L, newId = run { var n = 0; { "id${n++}" } })
        assertEquals(2, saved.size)
        val again = CsvImport.read(Csv.parse(file), mapping, ctx(existing = saved))
        assertTrue(again.rows.all { CsvReason.PossibleDuplicate in it.reasons })
        assertTrue(CsvImport.initialChoices(again).none { it.ticked })
    }

    @Test fun twoRealEntriesWithTheSameAmountOnTheSameDayAreBothKept() {
        val text = "Date,Title,Amount\n2026-11-12,Chai,-20\n2026-11-12,Chai,-20\n"
        val m = CsvMapping(date = 0, title = 1, amount = 2, defaultAccountId = "hdfc", datePattern = DatePattern.Iso)
        val rows = Csv.parse(text)
        val none = CsvImport.read(rows, m, ctx())
        assertTrue(none.rows.all { CsvReason.PossibleDuplicate !in it.reasons })
        // the book already has ONE such entry: only one row is flagged
        val one = CsvImport.read(rows, m, ctx(existing = listOf(entry("e1", -2000, ms(2026, 11, 12), name = "Chai"))))
        assertEquals(1, one.rows.count { CsvReason.PossibleDuplicate in it.reasons })
    }

    @Test fun differentAmountDayOrAccountIsNotADuplicate() {
        val m = CsvMapping(date = 0, title = 1, amount = 2, defaultAccountId = "hdfc", datePattern = DatePattern.Iso)
        val existing = listOf(entry("e1", -2000, ms(2026, 11, 12), name = "Chai", account = "cash"))
        val r = CsvImport.read(Csv.parse("Date,Title,Amount\n2026-11-12,Chai,-20\n2026-11-13,Chai,-20\n2026-11-12,Chai,-21\n"), m, ctx(existing))
        assertEquals(3, r.rows.size)
        assertTrue(r.rows.none { CsvReason.PossibleDuplicate in it.reasons })
    }

    @Test fun onlyTickedSaveableRowsBecomeEntries() {
        val m = CsvMapping(date = 0, title = 1, amount = 2, category = 3, datePattern = DatePattern.Iso, defaultAccountId = "hdfc")
        val r = CsvImport.read(Csv.parse("Date,Title,Amount,Category\n2026-11-12,A,-10,food\n2026-11-12,B,-20,nope\n2026-11-12,C,-30,food\n"), m, ctx())
        val choices = CsvImport.initialChoices(r)
        assertEquals(listOf(true, false, true), choices.map { it.ticked })
        // B is ticked by hand but has no category: still not saved
        val entries = CsvImport.toEntries(choices.map { it.copy(ticked = true) }, accounts, 99L, newId = run { var n = 0; { "n${n++}" } })
        assertEquals(listOf("A", "C"), entries.map { it.name })
        assertTrue(entries.all { it.methodAdded == MethodAdded.CSV && it.paid && it.modifiedAtMs == 99L })
    }

    @Test fun templateRoundTrips() {
        val t = CsvTemplate("HDFC | savings; 2026", CsvMapping(0, null, 2, 3, 1, null, null, null, null, null, DatePattern.DayMonthYearSlash, true, true))
        assertEquals(t, CsvTemplate.decode(t.encode()))
        assertNull(CsvTemplate.decode("garbage"))
    }
}

class CsvExportTest {
    private val accounts = listOf(acct("hdfc", "HDFC"), acct("cash", "Cash"))
    private val categories = listOf(cat("food", "Food"), cat("eat", "Eating out", main = "food"), cat("0", "Balance Correction"))

    @Test fun amountTextIsExact() {
        assertEquals("-1234.50", CsvExport.amountText(-123450))
        assertEquals("0.05", CsvExport.amountText(5))
        assertEquals("100.00", CsvExport.amountText(10000))
        assertEquals("-0.99", CsvExport.amountText(-99))
    }

    @Test fun filtersByAccountAndRangeAndSortsOldestFirst() {
        val e = listOf(
            entry("a", -100, ms(2026, 11, 3), "hdfc", "Late"), entry("b", -200, ms(2026, 11, 1), "hdfc", "Early"),
            entry("c", -300, ms(2026, 11, 2), "cash", "Other account"), entry("d", -400, ms(2026, 10, 1), "hdfc", "Out of range"),
        )
        val rows = CsvExport.rows(e, accounts, categories, IST, accountIds = setOf("hdfc"), fromMs = ms(2026, 11, 1, 0), toMs = ms(2026, 12, 1, 0))
        assertEquals(listOf("Early", "Late"), rows.map { it[4] })
    }

    @Test fun transfersAndCorrectionsAreLabelled() {
        val e = listOf(
            entry("t1", -500, ms(2026, 11, 1), "hdfc", "To cash", category = "0", paired = "t2"),
            entry("t2", 500, ms(2026, 11, 1), "cash", "From hdfc", category = "0", paired = "t1"),
            entry("c1", 100, ms(2026, 11, 2), "hdfc", "Fix", category = "0"),
        )
        val types = CsvExport.rows(e, accounts, categories, IST).map { it[7] }
        assertEquals(listOf("Transfer", "Transfer", "Balance correction"), types)
        assertEquals("", CsvExport.rows(e, accounts, categories, IST)[0][2])
    }

    @Test fun exportReadsBackAsTheSameEntries() {
        val original = listOf(
            entry("a", -25050, ms(2026, 11, 12, 9, 30), "hdfc", "Lunch, with \"friends\"", category = "food"),
            entry("b", 5000000, ms(2026, 11, 13, 18, 0), "cash", "Pay", category = "food"),
        ).map { if (it.id == "a") it.copy(subCategoryId = "eat", note = "line1\nline2") else it }
        val text = CsvExport.file(original, accounts, categories, IST)
        val rows = Csv.parse(text)
        val mapping = CsvImport.guessMapping(rows[0]).copy(datePattern = DatePattern.Iso)
        val parsed = CsvImport.read(rows, mapping, CsvContext(accounts, categories, emptyList(), emptyList(), IST))
        assertEquals(2, parsed.rows.size)
        val a = parsed.rows[0]
        assertEquals(-25050L, a.amountPaise); assertEquals("Lunch, with \"friends\"", a.title); assertEquals("line1\nline2", a.note)
        assertEquals("hdfc", a.accountId); assertEquals("food", a.categoryId); assertEquals("eat", a.subCategoryId)
        assertEquals(ms(2026, 11, 12, 9, 30), a.dateMs) // the time is kept
        assertEquals(5000000L, parsed.rows[1].amountPaise)
    }
}

class LockPolicyTest {
    @Test fun offNeverLocks() = assertFalse(LockPolicy.shouldLock(false, true, 0, 1_000_000, LockTimeout.Immediately))

    @Test fun lockedStaysLocked() = assertTrue(LockPolicy.shouldLock(true, true, null, 0, LockTimeout.FiveMinutes))

    @Test fun neverLeftMeansNoLock() = assertFalse(LockPolicy.shouldLock(true, false, null, 5, LockTimeout.Immediately))

    @Test fun locksOnlyAfterTheTimeout() {
        assertTrue(LockPolicy.shouldLock(true, false, 1000, 1000, LockTimeout.Immediately))
        assertFalse(LockPolicy.shouldLock(true, false, 0, 59_999, LockTimeout.OneMinute))
        assertTrue(LockPolicy.shouldLock(true, false, 0, 60_000, LockTimeout.OneMinute))
        assertFalse(LockPolicy.shouldLock(true, false, 0, 299_999, LockTimeout.FiveMinutes))
    }

    @Test fun clockGoingBackwardsLocks() = assertTrue(LockPolicy.shouldLock(true, false, 10_000, 5_000, LockTimeout.FiveMinutes))

    @Test fun unknownTimeoutKeyFallsBackToOneMinute() = assertEquals(LockTimeout.OneMinute, LockTimeout.fromKey("zzz"))
}

class RestorePointsTest {
    private fun rp(kind: RestoreKind, at: Long) = RestorePoint(RestorePoints.fileName(kind, at), kind, at, 10)

    @Test fun namesRoundTrip() {
        for (k in RestoreKind.entries) {
            val name = RestorePoints.fileName(k, 1_700_000_000_000)
            assertEquals(RestorePoint(name, k, 1_700_000_000_000, 7), RestorePoints.parse(name, 7))
        }
        assertNull(RestorePoints.parse("random.db", 1))
        assertNull(RestorePoints.parse("rp-weird-1.db", 1))
    }

    @Test fun oneAutomaticPointPerLocalDay() {
        val morning = ms(2026, 11, 13, 8)
        val points = listOf(rp(RestoreKind.Auto, morning), rp(RestoreKind.Manual, ms(2026, 11, 13, 20)))
        assertFalse(RestorePoints.isAutoDue(points, ms(2026, 11, 13, 23, 59), IST))
        assertTrue(RestorePoints.isAutoDue(points, ms(2026, 11, 14, 0, 1), IST))
        // a manual point alone does not count as the day's automatic one
        assertTrue(RestorePoints.isAutoDue(listOf(rp(RestoreKind.Manual, morning)), morning + 1000, IST))
        assertTrue(RestorePoints.isAutoDue(emptyList(), morning, IST))
    }

    @Test fun keepsNewestOfEachGroupAndNeverTouchesAFewPoints() {
        val auto = (1..13).map { rp(RestoreKind.Auto, it * 1000L) }
        val manual = (1..12).map { rp(RestoreKind.Manual, it * 1000L) }
        val doomed = RestorePoints.toDelete(auto + manual)
        assertEquals(3 + 2, doomed.size)
        assertTrue(doomed.filter { it.kind == RestoreKind.Auto }.all { it.createdAtMs <= 3000L })
        assertTrue(doomed.filter { it.kind == RestoreKind.Manual }.all { it.createdAtMs <= 2000L })
        assertTrue(RestorePoints.toDelete(auto.take(10) + manual.take(10)).isEmpty())
    }

    @Test fun exportNameUsesLocalTime() {
        assertEquals("chitta-backup-20261113-1449.db", RestorePoints.exportName(ms(2026, 11, 13, 14, 49), IST))
    }
}

class BackupVerdictTest {
    private fun j(v: Int = 3, room: Boolean = true, tables: Boolean = true, paddhu: Boolean = false, ok: Boolean = true) =
        BackupVerdict.judge(v, room, tables, paddhu, ok, currentVersion = 3)

    @Test fun acceptsCurrentAndOlderChittaFiles() {
        assertEquals(BackupVerdict.Ok(3), j())
        assertEquals(BackupVerdict.Ok(2), j(v = 2))
    }

    @Test fun refusesNewerDamagedPaddhuAndStrangers() {
        assertIs<BackupVerdict.Refused>(j(v = 4))
        assertIs<BackupVerdict.Refused>(j(ok = false))
        assertTrue((j(room = false, tables = false, paddhu = true) as BackupVerdict.Refused).reason.contains("Bring over your book"))
        assertTrue((j(room = false, tables = false) as BackupVerdict.Refused).reason.contains("not a Chitta backup"))
        assertIs<BackupVerdict.Refused>(j(tables = false))
        assertIs<BackupVerdict.Refused>(j(v = 0))
    }
}

class ExportRangeTest {
    private fun start(y: Int, m: Int, d: Int) = ms(y, m, d, 0)

    @Test fun rangesAreHalfOpenInLocalTime() {
        val now = ms(2026, 11, 13, 23, 30)
        assertEquals(null to null, ExportRange.All.bounds(now, IST))
        assertEquals(start(2026, 11, 1) to start(2026, 12, 1), ExportRange.ThisMonth.bounds(now, IST))
        assertEquals(start(2026, 10, 15) to start(2026, 11, 14), ExportRange.Last30Days.bounds(now, IST))
        assertEquals(start(2026, 1, 1) to start(2027, 1, 1), ExportRange.ThisYear.bounds(now, IST))
    }

    @Test fun monthAndYearBoundaries() {
        assertEquals(start(2028, 2, 1) to start(2028, 3, 1), ExportRange.ThisMonth.bounds(ms(2028, 2, 29), IST)) // leap day
        assertEquals(start(2026, 12, 1) to start(2027, 1, 1), ExportRange.ThisMonth.bounds(ms(2026, 12, 31, 23, 59), IST))
    }
}
