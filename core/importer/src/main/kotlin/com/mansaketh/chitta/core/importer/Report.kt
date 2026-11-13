package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.Book

enum class Severity { WARNING, ERROR }

/** Something the user should see before importing. An ERROR row is left out of the book and listed here. */
data class Issue(val severity: Severity, val table: String, val id: String?, val message: String)

data class TableCount(val table: String, val read: Int, val imported: Int)

/**
 * Conversion check for one account: the sum of its transaction amounts as Paddhu stored them (exact decimal
 * sum, rounded once at the end) against the sum of the imported paise. They must be equal.
 * This checks the double-to-paise step; it does not recompute Paddhu's on-screen balance rules.
 */
data class AccountCheck(
    val accountId: String,
    val accountName: String,
    val transactions: Int,
    val sourceSumPaise: Long,
    val importedSumPaise: Long,
) {
    val matches get() = sourceSumPaise == importedSumPaise
}

data class Report(
    val schemaVersion: Int,
    val counts: List<TableCount>,
    val firstTransactionMs: Long?,
    val lastTransactionMs: Long?,
    /** Amounts that were not an exact number of paise and were rounded (HALF_EVEN). */
    val movedAmounts: Int,
    /** Items that exist in the file but are not carried over, with how many, e.g. "transactions: shared budget columns". */
    val notCarriedOver: Map<String, Int>,
    val accountChecks: List<AccountCheck>,
    val issues: List<Issue>,
) {
    val errors get() = issues.count { it.severity == Severity.ERROR }
    val warnings get() = issues.count { it.severity == Severity.WARNING }
    val balancesMatch get() = accountChecks.all { it.matches }
}

sealed interface ImportOutcome {
    /** The file cannot be imported at all (wrong version, not a Paddhu file). Nothing was read further. */
    data class Refused(val reason: String) : ImportOutcome

    /** [book] holds everything that could be read; [report] lists everything that needs a look. Nothing is saved yet. */
    data class Ready(val book: Book, val report: Report) : ImportOutcome
}
