package com.mansaketh.chitta.core.intake

enum class AutoVerdict { Save, Review }

/** [needsCheck]: saved on a guess (account created, category guessed or no merchant), so the entry carries a Check tag until you look at it. */
data class AutoDecision(val verdict: AutoVerdict, /** A short sentence, for the review list and the activity log. */ val reason: String, val needsCheck: Boolean = false) {
    val isSave: Boolean get() = verdict == AutoVerdict.Save
}

/**
 * Decides what happens to a bank SMS that Ping handed over by itself: save it straight away, or wait in the bell (review list).
 * Phase 10: messages are saved even when part of them is a guess (new account, fallback category, no merchant), marked Check.
 * Only the ones that could double-count money or that cannot be placed at all wait: a closed account, a pension / provident fund row, a
 * card bill payment, and a message that does not say which account it is (no account in the book and none to make).
 */
object AutoSave {
    fun decide(row: IntakeRow, learnedCategoryId: String?, labelCategoryId: String?, accountInBook: Boolean, canCreateAccount: Boolean, fallbackCategoryAvailable: Boolean): AutoDecision {
        if (row.closed) return AutoDecision(AutoVerdict.Review, "a closed account")
        if (!row.tickByDefault) return AutoDecision(AutoVerdict.Review, "a pension / provident fund row or a credit card bill payment")
        if (!accountInBook && !canCreateAccount) return AutoDecision(AutoVerdict.Review, "the SMS does not say which account it is")
        val incomeLabel = !row.isExpense && row.categoryLabel == "income"
        val categoryKnown = learnedCategoryId != null || labelCategoryId != null
        if (!categoryKnown && !fallbackCategoryAvailable) return AutoDecision(AutoVerdict.Review, "no category is known for it")
        val check = !row.knownAccount || !categoryKnown || (row.merchant.isBlank() && !incomeLabel)
        val why = when {
            check -> "saved on a best guess, marked Check"
            learnedCategoryId != null -> "a category you chose before"
            else -> "a known keyword (merchant, salary, interest...)"
        }
        return AutoDecision(AutoVerdict.Save, why, check)
    }
}
