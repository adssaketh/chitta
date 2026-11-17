package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.Book
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import java.time.format.TextStyle
import java.util.Locale

/** One line of the dry run: [ok] draws a tick, otherwise a cross, with [sub] saying what differs. */
data class DryRunRow(val title: String, val sub: String, val ok: Boolean)

data class DryRunResult(val rows: List<DryRunRow>, val entryCount: Int) {
    val allMatch: Boolean get() = rows.all { it.ok }
}

/**
 * The dry run (PDF p.12): the backup's own figures against what would be saved, for every account, the entry count, one month's
 * spending and the categories. Nothing is written. [book] is the book as it would be saved, after any merges the person chose.
 * It compares with the backup file (a copy of Paddhu's data), not with the live Paddhu app.
 */
object DryRun {
    private fun n(i: Int) = IndianFormat.group(i.toString())
    private fun money(paise: Long) = IndianFormat.rupees(Paise(paise))

    fun compare(report: Report, book: Book, merges: List<SimilarPair>): DryRunResult {
        val rows = ArrayList<DryRunRow>()

        val checks = report.accountChecks
        val bad = checks.filter { !it.matches }
        rows += DryRunRow(
            "Account balances",
            if (bad.isEmpty()) "${n(checks.size)} of ${n(checks.size)}, to the paisa"
            else "${n(checks.size - bad.size)} of ${n(checks.size)}. ${bad.first().accountName}: backup ${money(bad.first().sourceSumPaise)}, imported ${money(bad.first().importedSumPaise)}" +
                (if (bad.size > 1) " and ${bad.size - 1} more" else ""),
            bad.isEmpty(),
        )

        val tx = report.counts.firstOrNull { it.table == "transactions" }
        val read = tx?.read ?: 0
        val imported = tx?.imported ?: 0
        rows += DryRunRow("Entries", "${n(imported)} of ${n(read)}", imported == read)

        report.monthSpend?.let { m ->
            val name = m.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            rows += DryRunRow(
                "$name spending",
                if (m.matches) "${money(m.sourcePaise)} in both" else "backup ${money(m.sourcePaise)}, imported ${money(m.importedPaise)}",
                m.matches,
            )
        }

        val cat = report.counts.firstOrNull { it.table == "categories" }
        val ids = book.categories.map { it.id }.toSet()
        val mergeOk = merges.all { it.fromId !in ids && it.intoId in ids && book.transactions.none { t -> t.categoryId == it.fromId } }
        val catSub = when {
            merges.isEmpty() -> "${n(cat?.imported ?: 0)} of ${n(cat?.read ?: 0)}"
            merges.size == 1 -> "“${merges[0].fromName}” merged into “${merges[0].intoName}”"
            else -> "${merges.size} merged, “${merges[0].fromName}” into “${merges[0].intoName}” and ${merges.size - 1} more"
        }
        rows += DryRunRow("Categories", catSub, (cat == null || cat.read == cat.imported) && mergeOk)

        return DryRunResult(rows, book.transactions.size)
    }
}
