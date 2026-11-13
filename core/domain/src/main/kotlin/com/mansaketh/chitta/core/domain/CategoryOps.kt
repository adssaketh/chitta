package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction

/** Category housekeeping as plain functions: each returns the rows to save, so the screen only has to write them. */
object CategoryOps {
    data class MergeResult(val entries: List<Transaction>, val titles: List<TitleMemory>, val removedCategoryIds: List<String>)

    /** Moves every entry and remembered title of [source] (and its subcategories) to [target], then removes [source]. */
    fun merge(source: Category, target: Category, categories: List<Category>, entries: List<Transaction>, titles: List<TitleMemory>, nowMs: Long): MergeResult {
        require(source.id != target.id) { "cannot merge a category into itself" }
        val family = setOf(source.id) + categories.filter { it.mainCategoryId == source.id }.map { it.id }
        val movedEntries = entries.filter { it.categoryId in family || it.subCategoryId in family }.map {
            it.copy(
                categoryId = if (it.categoryId in family) target.id else it.categoryId,
                subCategoryId = if (it.subCategoryId in family) null else it.subCategoryId,
                modifiedAtMs = nowMs,
            )
        }
        val movedTitles = titles.filter { it.categoryId in family }.map { it.copy(categoryId = target.id, modifiedAtMs = nowMs) }
        return MergeResult(movedEntries, movedTitles, family.toList())
    }

    /** Why [category] cannot become a subcategory of [parent], or null when it can. */
    fun cannotConvert(category: Category, parent: Category, categories: List<Category>): String? = when {
        category.id == parent.id -> "Pick a different category."
        parent.mainCategoryId != null -> "A subcategory cannot hold subcategories."
        categories.any { it.mainCategoryId == category.id } -> "This category has subcategories. Merge or move them first."
        category.income != parent.income -> "Income and expense categories cannot be mixed."
        else -> null
    }

    fun convertToSub(category: Category, parent: Category, nowMs: Long): Category = category.copy(mainCategoryId = parent.id, modifiedAtMs = nowMs)

    /** Swaps the order of [id] with its neighbour in [siblings] (already in display order). Returns the rows whose order changed. */
    fun move(siblings: List<Category>, id: String, up: Boolean, nowMs: Long): List<Category> {
        val i = siblings.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in siblings.indices) return emptyList()
        val reordered = siblings.toMutableList().apply { add(j, removeAt(i)) }
        return reordered.mapIndexedNotNull { index, c -> if (c.sortOrder != index) c.copy(sortOrder = index, modifiedAtMs = nowMs) else null }
    }
}
