package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.Book
import com.mansaketh.chitta.core.model.Category

/** Two main categories that look like the same thing. Merging moves everything in [fromId] into [intoId] and removes [fromId]. */
data class SimilarPair(val fromId: String, val fromName: String, val intoId: String, val intoName: String) {
    val key: String get() = "$fromId>$intoId"
}

/**
 * Finds main categories that differ only by case, punctuation or "&" versus "and", or where one name is the other with extra words
 * after it ("Food" and "Food & dining"). Only a suggestion: nothing is merged unless the person says so (D294).
 */
object SimilarCategories {
    fun words(name: String): List<String> =
        name.lowercase().replace("&", " and ").map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }

    fun find(categories: List<Category>): List<SimilarPair> {
        val tops = categories.filter { it.mainCategoryId == null && !it.countsAsTransfer && !it.archived && words(it.name).isNotEmpty() }
        val used = HashSet<String>()
        val pairs = ArrayList<SimilarPair>()
        for (a in tops.sortedWith(compareBy({ words(it.name).size }, { it.sortOrder }, { it.id }))) {
            if (a.id in used) continue
            val wa = words(a.name)
            val best = tops.filter { b ->
                b.id != a.id && b.id !in used && b.income == a.income && words(b.name).let { wb ->
                    wb.size >= wa.size && wb.take(wa.size) == wa &&
                        // Same words: only one direction counts, so the pair is not found twice.
                        (wb.size > wa.size || compareValuesBy(a, b, { it.sortOrder }, { it.id }) > 0)
                }
            }.minWithOrNull(compareBy({ words(it.name).size }, { it.sortOrder }, { it.id })) ?: continue
            used += a.id; used += best.id
            pairs += SimilarPair(a.id, a.name, best.id, best.name)
        }
        return pairs.sortedBy { p -> tops.first { it.id == p.fromId }.sortOrder }
    }
}

/** Applies the merges the person chose to a book that has not been saved yet. Mirrors what Categories > Merge does for a saved book. */
object CategoryMerge {
    fun apply(book: Book, merges: List<SimilarPair>, nowMs: Long): Book {
        var b = book
        for (m in merges) {
            val family = setOf(m.fromId) + b.categories.filter { it.mainCategoryId == m.fromId }.map { it.id }
            b = b.copy(
                categories = b.categories.filter { it.id !in family },
                transactions = b.transactions.map { t ->
                    if (t.categoryId in family || t.subCategoryId in family) {
                        t.copy(
                            categoryId = if (t.categoryId in family) m.intoId else t.categoryId,
                            subCategoryId = if (t.subCategoryId in family) null else t.subCategoryId,
                            modifiedAtMs = nowMs,
                        )
                    } else t
                },
                titleMemory = b.titleMemory.map { if (it.categoryId in family) it.copy(categoryId = m.intoId, modifiedAtMs = nowMs) else it },
                budgets = b.budgets.map { bud ->
                    bud.copy(
                        categoryIds = bud.categoryIds?.map { if (it in family) m.intoId else it }?.distinct(),
                        excludedCategoryIds = bud.excludedCategoryIds?.map { if (it in family) m.intoId else it }?.distinct(),
                    )
                },
                categoryLimits = run {
                    val taken = b.categoryLimits.filter { it.categoryId == m.intoId }.map { it.budgetId }.toSet()
                    b.categoryLimits.filter { !(it.categoryId in family && it.budgetId in taken) }
                        .map { if (it.categoryId in family) it.copy(categoryId = m.intoId) else it }
                },
            )
        }
        return b
    }
}
