package com.mansaketh.chitta.core.importer

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DryRunTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ready(): ImportOutcome.Ready {
        val f = TestDb.create(File(tmp.root, "d-${System.nanoTime()}.sqlite"), 48, { TestDb.sampleBook(it) }, emptySet())
        JdbcBackupSource(f).use { return assertIs(PaddhuImporter(ZoneId.of("Asia/Kolkata")).read(it)) }
    }

    @Test fun aSoundBookMatchesEverywhere() {
        val r = ready()
        val d = DryRun.compare(r.report, r.book, emptyList())
        assertTrue(d.allMatch, d.rows.toString())
        assertEquals("Account balances", d.rows[0].title)
        assertEquals("Entries", d.rows[1].title)
        assertEquals("Categories", d.rows.last().title)
        assertEquals(r.book.transactions.size, d.entryCount)
    }

    @Test fun monthSpendIsFoundAndMatchesOnASoundBook() {
        val m = assertNotNull(ready().report.monthSpend)
        assertTrue(m.matches)
        assertTrue(m.sourcePaise >= 0)
    }

    @Test fun aDifferingAccountFailsTheRunAndNamesTheAccount() {
        val r = ready()
        val report = r.report.copy(accountChecks = listOf(AccountCheck("a", "Axis", 1, 10_000, 10_100)))
        val d = DryRun.compare(report, r.book, emptyList())
        assertFalse(d.allMatch)
        assertFalse(d.rows[0].ok)
        assertTrue(d.rows[0].sub.contains("Axis"), d.rows[0].sub)
    }

    @Test fun aMergeThatWasNotAppliedFailsTheCategoriesRow() {
        val r = ready()
        val from = r.book.categories.first()
        val into = r.book.categories.first { it.id != from.id && it.mainCategoryId != from.id && it.mainCategoryId == null }
        val pair = SimilarPair(from.id, from.name, into.id, into.name)
        assertFalse(DryRun.compare(r.report, r.book, listOf(pair)).allMatch)
        val merged = CategoryMerge.apply(r.book, listOf(pair), 1L)
        val d = DryRun.compare(r.report, merged, listOf(pair))
        assertTrue(d.rows.last().ok, d.rows.last().toString())
        assertTrue(merged.transactions.none { it.categoryId == from.id })
        assertEquals(r.book.transactions.size, merged.transactions.size)
    }
}
