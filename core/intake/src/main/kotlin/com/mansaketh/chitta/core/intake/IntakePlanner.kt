package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import java.time.ZoneId

enum class IntakeReason(val text: String) {
    NoAccount("Pick an account"),
    NoCategory("Pick a category"),
    PossibleDuplicate("Looks like an entry you already have"),
    UnknownAccount("Account is not in your list"),
    ClosedAccount("A closed account"),
    NotTicked("Pension / provident fund or card bill payment"),
    NoMerchant("The SMS does not name a merchant"),
    Unconfirmed("The amount could not be read with certainty"),
}

/** What the planner needs to know about your book. */
data class IntakeContext(
    val accounts: List<Account>,
    val categories: List<Category>,
    val existing: List<Transaction>,
    val titleMemory: List<TitleMemory>,
    val zone: ZoneId,
)

/** One row of the review list. [autoSave] is true when the row may be saved without a look (the "confident" rule). */
data class IntakeCandidate(
    val row: IntakeRow,
    val accountId: String?,
    val categoryId: String?,
    val subCategoryId: String?,
    val reasons: List<IntakeReason>,
    val autoSave: Boolean,
    val autoReason: String,
) {
    val ready: Boolean get() = reasons.isEmpty()
    val saveable: Boolean get() = accountId != null && categoryId != null
}

/** A payment already in the book as the other half of this row (bank SMS vs UPI page): only its note gets the row's details. */
data class IntakeClaim(val entryId: String, val row: IntakeRow)

data class IntakePlan(val candidates: List<IntakeCandidate>, val alreadyImported: Int, val claims: List<IntakeClaim> = emptyList()) {
    val toAutoSave: List<IntakeCandidate> get() = candidates.filter { it.autoSave }
    val toReview: List<IntakeCandidate> get() = candidates.filter { !it.autoSave }
}

/** One row of the review list after the person has looked at it. */
data class IntakeChoice(val candidate: IntakeCandidate, val ticked: Boolean, val accountId: String?, val categoryId: String?, val subCategoryId: String?) {
    val saveable: Boolean get() = accountId != null && categoryId != null
}

object IntakePlanner {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun similar(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (s, l) = if (x.length <= y.length) x to y else y to x
        return s.length >= 3 && l.contains(s)
    }

    /** Ping messages (and UPI pages) already in the book carry their markers in the note. Returns true when this row is one of them. */
    private fun alreadyThere(row: IntakeRow, existing: List<Transaction>): Boolean {
        val idMark = "$PING_NOTE_MARKER${row.sourceId}"
        val keyMark = "$PING_KEY_MARKER${row.contentKey}"
        return existing.any { e ->
            val n = e.note
            // the id must end there, so "#12" does not match "#123"
            Regex(Regex.escape(idMark) + "(?![\\w-])").containsMatchIn(n) || (!row.isUpi && Regex(Regex.escape(keyMark) + "(?![\\w-])").containsMatchIn(n))
        }
    }

    fun plan(rows: List<IntakeRow>, ctx: IntakeContext): IntakePlan {
        val fresh = rows.filter { !alreadyThere(it, ctx.existing) }
        val merged = Twins.merge(fresh, ctx.zone)
        val usedExisting = HashSet<String>()
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(ctx.zone).toLocalDate()
        val claims = ArrayList<IntakeClaim>()
        val out = merged.mapNotNull { r ->
            val twin = twinInBook(r, ctx.existing, usedExisting, ::day)
            if (twin != null) { usedExisting.add(twin.id); claims.add(IntakeClaim(twin.id, r)); return@mapNotNull null }
            val account = ctx.accounts.firstOrNull { !it.archived && norm(it.name) == norm(r.walletName) }
            val learned = ctx.titleMemory.firstOrNull { it.title.equals(r.title, ignoreCase = true) }?.categoryId
                ?.takeIf { id -> ctx.categories.any { it.id == id && !it.archived } }
            val byLabel = CategoryResolver.forLabel(r.categoryLabel, !r.isExpense, ctx.categories)
            val categoryId = learned ?: byLabel?.id
            val signed = if (r.isExpense) -r.amountPaise else r.amountPaise
            val dup = ctx.existing.firstOrNull { e ->
                e.id !in usedExisting && e.amount == signed && day(e.dateMs) == day(r.dateMs) &&
                    (account == null || e.accountId == account.id) && similar(e.name, r.title)
            }
            if (dup != null) usedExisting.add(dup.id)

            val reasons = ArrayList<IntakeReason>()
            if (account == null) reasons.add(IntakeReason.NoAccount)
            if (categoryId == null) reasons.add(IntakeReason.NoCategory)
            if (dup != null) reasons.add(IntakeReason.PossibleDuplicate)
            if (!r.knownAccount) reasons.add(IntakeReason.UnknownAccount)
            if (r.closed) reasons.add(IntakeReason.ClosedAccount)
            if (!r.tickByDefault) reasons.add(IntakeReason.NotTicked)
            if (r.amountUnconfirmed) reasons.add(IntakeReason.Unconfirmed)

            val decision = AutoSave.decide(r, learned, byLabel?.id, account != null)
            val auto = decision.isSave && dup == null && !r.amountUnconfirmed && !r.isUpi
            IntakeCandidate(r, account?.id, categoryId, null, reasons, auto, decision.reason)
        }
        return IntakePlan(out, rows.size - fresh.size, claims)
    }

    private val realSmsMarker = Regex("Ping SMS #(?!pp-)")

    /**
     * The entry already in the book that is the other half of this payment: same direction, amount and day, one made from a bank SMS and
     * the other from a UPI page. An entry already matched once is not matched again, so two payments of the same amount on one day stay two.
     */
    private fun twinInBook(r: IntakeRow, existing: List<Transaction>, used: Set<String>, day: (Long) -> java.time.LocalDate): Transaction? {
        val signed = if (r.isExpense) -r.amountPaise else r.amountPaise
        return existing.firstOrNull { e ->
            e.id !in used && e.amount == signed && day(e.dateMs) == day(r.dateMs) &&
                if (r.isUpi) realSmsMarker.containsMatchIn(e.note) && !e.note.contains("Ping SMS #pp-")
                else e.note.contains("Ping SMS #pp-") && !realSmsMarker.containsMatchIn(e.note)
        }
    }

    fun initialChoices(plan: IntakePlan): List<IntakeChoice> =
        plan.toReview.map { IntakeChoice(it, it.ready, it.accountId, it.categoryId, it.subCategoryId) }

    /** Entries for the rows to save: the confident ones (all of them) or the ticked, saveable review rows. */
    fun toEntries(choices: List<IntakeChoice>, accounts: List<Account>, nowMs: Long, newId: () -> String): List<Transaction> =
        choices.filter { it.ticked && it.saveable }.map { c ->
            val r = c.candidate.row
            val account = accounts.first { it.id == c.accountId }
            entry(r, account, c.categoryId!!, c.subCategoryId, nowMs, newId())
        }

    fun autoEntries(plan: IntakePlan, accounts: List<Account>, nowMs: Long, newId: () -> String): List<Transaction> =
        plan.toAutoSave.filter { it.saveable }.map { c ->
            entry(c.row, accounts.first { it.id == c.accountId }, c.categoryId!!, c.subCategoryId, nowMs, newId())
        }

    private fun entry(r: IntakeRow, account: Account, categoryId: String, subCategoryId: String?, nowMs: Long, id: String) = Transaction(
        id = id, pairedTransactionId = null, name = r.title, amount = if (r.isExpense) -r.amountPaise else r.amountPaise, currency = account.currency,
        note = r.note, categoryId = categoryId, subCategoryId = subCategoryId, accountId = account.id, dateMs = r.dateMs, modifiedAtMs = nowMs,
        originalDueMs = null, income = !r.isExpense, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
        kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = MethodAdded.SHARED, objectiveId = null,
        objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
    )
}
