package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.Category

/**
 * Finds the category in your book for a rule-based label ("dining", "groceries"...). The old app's default categories had ids 1 to 11, and
 * a Paddhu book keeps them, so the id is tried first; a book started fresh in Chitta has other ids, so the name is tried next. Never creates one.
 */
object CategoryResolver {
    private val oldId = mapOf(
        "dining" to "1", "groceries" to "2", "shopping" to "3", "transit" to "4", "entertainment" to "5", "bills_fees" to "6",
        "gifts" to "7", "beauty" to "8", "work" to "9", "travel" to "10", "income" to "11",
    )
    private val names = mapOf(
        "dining" to listOf("dining", "food", "fooddining", "foodanddining", "restaurants"),
        "groceries" to listOf("groceries", "grocery"),
        "shopping" to listOf("shopping"),
        "transit" to listOf("transit", "transport", "transportation"),
        "entertainment" to listOf("entertainment"),
        "bills_fees" to listOf("billsfees", "billsandfees", "bills", "billsutilities"),
        "gifts" to listOf("gifts", "gift"),
        "beauty" to listOf("beauty"),
        "work" to listOf("work"),
        "travel" to listOf("travel"),
        "income" to listOf("income", "salary"),
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun forLabel(label: String, income: Boolean, categories: List<Category>): Category? {
        if (label !in SmsParser.LABELS_WITH_CATEGORY) return null
        val live = categories.filter { !it.archived && it.mainCategoryId == null && it.income == income }
        oldId[label]?.let { id -> live.firstOrNull { it.id == id }?.let { return it } }
        val wanted = names[label].orEmpty()
        return live.firstOrNull { norm(it.name) in wanted }
    }
}
