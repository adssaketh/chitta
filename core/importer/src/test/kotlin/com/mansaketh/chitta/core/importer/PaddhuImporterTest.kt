package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.importer.TestDb.DAY
import com.mansaketh.chitta.core.importer.TestDb.T0
import com.mansaketh.chitta.core.model.ActivityEntity
import com.mansaketh.chitta.core.model.AccountWidget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.TransactionKind
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaddhuImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val importer = PaddhuImporter(ZoneId.of("Asia/Kolkata"))

    private fun ready(version: Int = 48, skip: Set<String> = emptySet(), populate: (java.sql.Connection) -> Unit = { TestDb.sampleBook(it) }): ImportOutcome.Ready {
        val f = TestDb.create(File(tmp.root, "b-${System.nanoTime()}.sqlite"), version, populate, skip)
        JdbcBackupSource(f).use { return assertIs(importer.read(it)) }
    }

    @Test fun readsEveryTableAndCountsMatch() {
        val r = ready()
        val counts = r.report.counts.associate { it.table to (it.read to it.imported) }
        assertEquals(2 to 2, counts["wallets"])
        assertEquals(3 to 3, counts["categories"])
        assertEquals(8 to 8, counts["transactions"])
        assertEquals(1 to 1, counts["budgets"])
        assertEquals(1 to 1, counts["category_budget_limits"])
        assertEquals(1 to 1, counts["objectives"])
        assertEquals(1 to 1, counts["associated_titles"])
        assertEquals(1 to 1, counts["scanner_templates"])
        assertEquals(1 to 1, counts["delete_logs"])
        assertEquals(0, r.report.errors)
        assertEquals(48, r.report.schemaVersion)
    }

    @Test fun nullTypeIsANormalEntryAndOrdinalsMapByCode() {
        val tx = ready().book.transactions.associateBy { it.id }
        assertNull(tx.getValue("t1").kind)                         // NULL type, as on the real backup
        assertEquals(TransactionKind.LENT, tx.getValue("t5").kind)  // ordinal 3
        assertEquals(TransactionKind.UPCOMING, tx.getValue("t6").kind)
    }

    @Test fun amountsBecomeExactPaiseAndKeepTheirSign() {
        val tx = ready().book.transactions.associateBy { it.id }
        assertEquals(-61_334L, tx.getValue("t1").amount)
        assertEquals(-61_333L, tx.getValue("t2").amount)
        assertEquals(6_000_000L, tx.getValue("t4").amount)
        assertEquals(0, ready().report.movedAmounts)
    }

    @Test fun splitExampleAddsUpToTheTotal() {
        // PDF p.4 / master section 12: 613.34 + 613.33 + 613.33 = 1,840.00
        val t = ready().book.transactions.associateBy { it.id }
        assertEquals(-184_000L, t.getValue("t1").amount + t.getValue("t2").amount + t.getValue("t3").amount)
    }

    @Test fun datesAreSecondsInTheFileAndMillisecondsInTheBook() {
        val b = ready().book
        assertEquals(T0 * 1000, b.accounts.first { it.id == "0" }.createdAtMs)
        assertEquals((T0 + DAY) * 1000, b.transactions.first { it.id == "t1" }.dateMs)
        assertEquals(null, b.transactions.first { it.id == "t1" }.modifiedAtMs)
    }

    @Test fun currencyIsCopiedFromTheAccountAndHouseholdColumnsSurvive() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE transactions SET owner_uid='uid-1', is_private=1, household_synced_at=? WHERE transaction_pk='t4'", T0 + 1)
        }
        val t4 = r.book.transactions.first { it.id == "t4" }
        assertEquals("inr", t4.currency)
        assertEquals("uid-1", t4.ownerUid)
        assertTrue(t4.isPrivate)
        assertEquals((T0 + 1) * 1000, t4.householdSyncedAtMs)
    }

    @Test fun jsonListColumnsAreParsed() {
        val b = ready().book
        assertEquals(listOf("b1"), b.transactions.first { it.id == "t1" }.excludeFromBudgetIds)
        assertEquals(emptyList(), b.transactions.first { it.id == "t2" }.excludeFromBudgetIds)
        val budget = b.budgets.single()
        assertEquals(listOf("0", "w2"), budget.accountIds)
        assertEquals(listOf("c-food"), budget.categoryIds)
        assertNull(budget.excludedCategoryIds)
        assertEquals(listOf(BudgetFilter.DEFAULT_FILTERS), budget.filters)
        assertEquals(Repeat.MONTHLY, budget.repeat)
        assertEquals(listOf(AccountWidget.WALLET_SWITCHER, AccountWidget.WALLET_LIST), b.accounts.first { it.id == "0" }.homeWidgets)
    }

    @Test fun otherTablesMapToo() {
        val b = ready().book
        assertEquals(ObjectiveKind.LOAN, b.objectives.single().kind)
        assertEquals(4_500_000L, b.objectives.single().amount)
        assertEquals(1_200_050L, b.categoryLimits.single().amount) // 12000.5
        assertEquals("c-food", b.categories.first { it.id == "c-rest" }.mainCategoryId)
        assertEquals(MethodAdded.PREVIEW, b.categories.first { it.id == "c-rest" }.methodAdded)
        assertEquals("Zomato", b.titleMemory.single().title)
        assertEquals("HDFC UPI", b.scanTemplates.single().name)
        assertEquals(ActivityEntity.TRANSACTION, b.activityLog.single().entity)
    }

    @Test fun transferPairIsKeptLinked() {
        val tx = ready().book.transactions.associateBy { it.id }
        assertEquals("t8", tx.getValue("t7").pairedTransactionId)
        assertEquals("t7", tx.getValue("t8").pairedTransactionId)
    }

    @Test fun balancesMatchPerAccount() {
        val checks = ready().report.accountChecks.associateBy { it.accountId }
        assertTrue(ready().report.balancesMatch)
        // t1..t5 + t7 on account 0: -613.34-613.33-613.33+60000-2000-5000 = 51160.00
        assertEquals(5_116_000L, checks.getValue("0").importedSumPaise)
        assertEquals(checks.getValue("0").sourceSumPaise, checks.getValue("0").importedSumPaise)
        assertEquals(6, checks.getValue("0").transactions)
    }

    @Test fun halfPaiseAmountIsRoundedAndReported() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE transactions SET amount=0.125 WHERE transaction_pk='t4'")
        }
        assertEquals(1, r.report.movedAmounts)
        assertEquals(12L, r.book.transactions.first { it.id == "t4" }.amount)
        assertTrue(r.report.issues.any { it.severity == Severity.WARNING && it.id == "t4" })
        assertTrue(r.report.balancesMatch) // source is summed exactly, then rounded once, so rounding per row can differ
    }

    // ---- version gate ------------------------------------------------------------------------------

    @Test fun acceptsTheWholeSupportedRange() {
        assertIs<ImportOutcome.Ready>(JdbcBackupSource(TestDb.create(File(tmp.root, "v33"), 33)).use { importer.read(it) })
        assertIs<ImportOutcome.Ready>(JdbcBackupSource(TestDb.create(File(tmp.root, "v48"), 48)).use { importer.read(it) })
    }

    @Test fun refusesOlderAndNewerSchemas() {
        val old = JdbcBackupSource(TestDb.create(File(tmp.root, "v32"), 32)).use { importer.read(it) }
        assertTrue(assertIs<ImportOutcome.Refused>(old).reason.contains("Open it in Paddhu Pusthakam first"))
        val new = JdbcBackupSource(TestDb.create(File(tmp.root, "v49"), 49)).use { importer.read(it) }
        assertTrue(assertIs<ImportOutcome.Refused>(new).reason.contains("newer"))
        val zero = JdbcBackupSource(TestDb.create(File(tmp.root, "v0"), 0)).use { importer.read(it) }
        assertIs<ImportOutcome.Refused>(zero) // an empty file like the 0-byte chitta.sqlite
    }

    @Test fun refusesAFileWithoutPaddhuTables() {
        val out = JdbcBackupSource(TestDb.create(File(tmp.root, "x"), 48, { }, skipTables = setOf("transactions"))).use { importer.read(it) }
        assertTrue(assertIs<ImportOutcome.Refused>(out).reason.contains("transactions"))
    }

    // ---- bad rows are listed, not guessed ----------------------------------------------------------

    @Test fun unknownTypeCodeLeavesThatRowOutAndSaysSo() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE transactions SET type=9 WHERE transaction_pk='t4'")
        }
        assertEquals(7, r.book.transactions.size)
        assertEquals(1, r.report.errors)
        assertTrue(r.report.issues.single { it.severity == Severity.ERROR }.message.contains("type code 9"))
    }

    @Test fun orphanReferencesAreWarnedAbout() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE transactions SET category_fk='missing' WHERE transaction_pk='t4'")
        }
        assertTrue(r.report.issues.any { it.id == "t4" && it.message.contains("missing") })
        assertEquals(8, r.book.transactions.size) // kept, only flagged
    }

    @Test fun accountWithOtherDecimalsIsFlagged() {
        val r = ready { c -> TestDb.sampleBook(c); TestDb.exec(c, "UPDATE wallets SET decimals=3 WHERE wallet_pk='w2'") }
        assertTrue(r.report.issues.any { it.table == "wallets" && it.id == "w2" && it.message.contains("3 decimals") })
    }

    @Test fun cashewSharedBudgetColumnsAreCountedNotSilentlyDropped() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE transactions SET shared_key='k' WHERE transaction_pk IN ('t1','t2')")
            TestDb.exec(c, "UPDATE budgets SET shared_key='k'")
        }
        assertEquals(2, r.report.notCarriedOver["transactions: Cashew shared-budget columns"])
        assertEquals(1, r.report.notCarriedOver["budgets: Cashew shared-budget columns"])
    }

    // ---- settings blob -----------------------------------------------------------------------------

    @Test fun loansDepositsAndCardDuesComeOutOfTheSettingsBlob() {
        val b = ready().book
        val loan = b.loanTerms.single()
        assertEquals("o1", loan.objectiveId)
        assertEquals(4_500_000L, loan.principal)
        assertEquals("12.5", loan.annualRatePct)
        assertEquals(401_050L, loan.emi)
        assertEquals(45_000L, loan.fee)
        assertEquals("18", loan.gstPct)
        assertEquals(listOf(1, 2, 3), loan.paidInstalments)
        assertEquals(1_790_000_000_000L, loan.updatedAtMs)
        val fd = b.fixedDeposits.single()
        assertEquals("fd1", fd.id)
        assertEquals(10_000_000L, fd.principal)
        assertEquals("7.25", fd.ratePct)
        assertEquals(10_745_055L, fd.maturityAmount)
        assertEquals(4, fd.compoundingPerYear)
        assertTrue(fd.fromSms)
        val dues = b.cardDues.associateBy { it.id }
        assertEquals(149_900L, dues.getValue("cd1").amount)
        assertEquals(50_025L, dues.getValue("cd2").amount)
        assertEquals(2, dues.size)
    }

    @Test fun deletedMarkersAreNotImported() {
        val b = ready().book
        assertEquals(listOf("o1"), b.loanTerms.map { it.objectiveId })
        assertEquals(listOf("fd1"), b.fixedDeposits.map { it.id })
    }

    @Test fun localTimesAreReadInTheConfiguredZone() {
        val b = ready().book
        // 2026-05-05T00:00 in Asia/Kolkata (+05:30) is 2026-05-04T18:30:00Z
        assertEquals(java.time.Instant.parse("2026-05-04T18:30:00Z").toEpochMilli(), b.loanTerms.single().firstEmiMs)
        // an explicit Z stays UTC
        assertEquals(java.time.Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(), b.cardDues.first { it.id == "cd2" }.dateMs)
    }

    @Test fun deviceOnlySettingsAreDroppedAndTheRestIsKeptVerbatim() {
        val r = ready()
        val keys = r.book.settings.map { it.key }
        assertEquals(listOf("accentColor", "theme"), keys)
        assertEquals("\"dark\"", r.book.settings.first { it.key == "theme" }.json)
        assertEquals(4, r.report.notCarriedOver["settings: device-only keys"]) // hasSignedIn, lastBackup, householdId, settingsModified
    }

    @Test fun brokenSettingsBlobIsAnErrorNotACrash() {
        val r = ready { c -> TestDb.sampleBook(c); TestDb.exec(c, "UPDATE app_settings SET settings_j_s_o_n='not json'") }
        assertTrue(r.report.issues.any { it.table == "app_settings" && it.severity == Severity.ERROR })
        assertTrue(r.book.loanTerms.isEmpty())
    }

    @Test fun aLoanWithoutADateIsListedNotLost() {
        val r = ready { c ->
            TestDb.sampleBook(c)
            TestDb.exec(c, "UPDATE app_settings SET settings_j_s_o_n=?", """{"loanTerms":{"o1":{"principal":1.0,"months":6}}}""")
        }
        assertTrue(r.book.loanTerms.isEmpty())
        assertTrue(r.report.issues.any { it.table == "settings.loanTerms" && it.id == "o1" })
    }

    // ---- read-only guarantee -----------------------------------------------------------------------

    @Test fun theBackupFileIsNeverChanged() {
        val f = TestDb.create(File(tmp.root, "ro.sqlite"))
        fun sha() = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        val before = sha()
        JdbcBackupSource(f).use { importer.read(it) }
        assertEquals(before, sha())
        assertFalse(File(f.path + "-journal").exists())
        assertFalse(File(f.path + "-wal").exists())
    }
}
