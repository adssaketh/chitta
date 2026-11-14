package com.mansaketh.chitta.core.model

/**
 * A sinking fund (Phase 8e): a yearly or occasional cost spread over the months before it falls due, for example insurance of 12,000 in March.
 * Nothing moves between accounts; the monthly share is only reserved on paper and taken off Safe to spend.
 *
 * [startMs] is the first day the fund counts from, and [targetDateMs] the day the cost falls due. [categoryId] is the category whose entries
 * draw the fund down (a main category also covers its sub-categories); null means nothing draws it down automatically.
 */
data class SinkingFund(
    val id: String,
    val name: String,
    val targetPaise: Long,
    val startMs: Long,
    val targetDateMs: Long,
    val categoryId: String?,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
)
