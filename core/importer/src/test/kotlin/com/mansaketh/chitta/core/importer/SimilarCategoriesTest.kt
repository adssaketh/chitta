package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimilarCategoriesTest {
    private fun cat(id: String, name: String, order: Int = 0, income: Boolean = false, main: String? = null, transfer: Boolean = false) =
        Category(id, name, null, null, null, 0L, null, order, income, null, main, countsAsTransfer = transfer)

    @Test fun foodAndFoodAndDiningArePaired_shorterIntoLonger() {
        val p = SimilarCategories.find(listOf(cat("a", "Food", 0), cat("b", "Food & dining", 1), cat("c", "Rent", 2)))
        assertEquals(listOf(SimilarPair("a", "Food", "b", "Food & dining")), p)
    }

    @Test fun sameWordsDifferingByCasePunctuationOrAmpersandArePairedOnce() {
        val p = SimilarCategories.find(listOf(cat("a", "Food & Dining", 0), cat("b", "food and dining!", 1)))
        assertEquals(1, p.size)
        // Same words: the later one in the list is merged into the earlier one.
        assertEquals("b", p[0].fromId); assertEquals("a", p[0].intoId)
    }

    @Test fun unrelatedNamesSubcategoriesTransfersAndMixedIncomeAreNotPaired() {
        val p = SimilarCategories.find(listOf(
            cat("a", "Food"), cat("b", "Dining out"), cat("s", "Food & dining", main = "x"),
            cat("t", "Food stuff", transfer = true), cat("i", "Food & dining", income = true),
        ))
        assertTrue(p.isEmpty())
    }

    @Test fun eachCategoryIsInOnePairOnly() {
        val p = SimilarCategories.find(listOf(cat("a", "Food", 0), cat("b", "Food out", 1), cat("c", "Food out late", 2)))
        assertEquals(1, p.size)
        assertEquals("a", p[0].fromId); assertEquals("b", p[0].intoId)
    }
}
