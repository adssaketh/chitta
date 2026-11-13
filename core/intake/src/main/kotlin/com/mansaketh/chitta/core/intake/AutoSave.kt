package com.mansaketh.chitta.core.intake

enum class AutoVerdict { Save, Review }

data class AutoDecision(val verdict: AutoVerdict, /** A short sentence, for the review list and the activity log. */ val reason: String) {
    val isSave: Boolean get() = verdict == AutoVerdict.Save
}

/**
 * Decides what happens to a bank SMS that Ping handed over by itself: save it straight away, or put it in the review list.
 * "Confident" means the message tells us everything that matters and nothing needs guessing: the account is one you named (and is not
 * closed) and exists in your book, the SMS names a merchant (or is a salary / interest / cashback credit), and a category is known: one you
 * picked for this title before, or a keyword rule that matches a category in your book. A port of the old `autoSmsPolicy.dart`.
 */
object AutoSave {
    fun decide(row: IntakeRow, learnedCategoryId: String?, labelCategoryId: String?, accountInBook: Boolean): AutoDecision {
        if (!row.knownAccount) return AutoDecision(AutoVerdict.Review, "the account is not in your list")
        if (row.closed) return AutoDecision(AutoVerdict.Review, "a closed account")
        if (!row.tickByDefault) return AutoDecision(AutoVerdict.Review, "a pension / provident fund row or a credit card bill payment")
        if (!accountInBook) return AutoDecision(AutoVerdict.Review, "the account is not in your book yet")
        val incomeLabel = !row.isExpense && row.categoryLabel == "income"
        if (row.merchant.isBlank() && !incomeLabel) return AutoDecision(AutoVerdict.Review, "the SMS does not name a merchant")
        if (learnedCategoryId == null && labelCategoryId == null) {
            return AutoDecision(AutoVerdict.Review, "no category is known for it (maybe a transfer to a person)")
        }
        return AutoDecision(AutoVerdict.Save, if (learnedCategoryId != null) "a category you chose before" else "a known keyword (merchant, salary, interest...)")
    }
}
