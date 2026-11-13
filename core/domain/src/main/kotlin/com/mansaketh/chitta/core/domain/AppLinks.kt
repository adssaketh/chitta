package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.time.LocalDate
import java.time.ZoneId

/** The pre-filled Quick Add a link asks for, and plain-language notes about anything in the link that was ignored. */
data class AppLinkResult(val draft: QuickAddDraft, val notes: List<String>)

/**
 * App links (master section 7): `chitta://add?amount=250&title=Tea&category=Food&account=HDFC&type=expense&note=...&date=2026-11-13`.
 * A link only fills in the Quick Add sheet; it never saves. The person checks it and taps Save. Anything that does not
 * match (an unknown category, a bad amount) is left blank and listed in [AppLinkResult.notes].
 */
object AppLinks {
    fun parse(params: Map<String, String>, accounts: List<Account>, categories: List<Category>, nowMs: Long, zone: ZoneId): AppLinkResult {
        val notes = ArrayList<String>()
        val type = when (params["type"]?.trim()?.lowercase()) {
            null, "", "expense" -> EntryType.Expense
            "income" -> EntryType.Income
            else -> { notes += "Type \"${params["type"]}\" is not supported here; used expense."; EntryType.Expense }
        }
        val amount = params["amount"]?.trim().orEmpty()
        val amountRaw = if (amount.isEmpty()) "" else if (AmountExpression.evaluate(amount) != null) amount else { notes += "The amount \"$amount\" is not a number; left empty."; "" }

        val live = accounts.filter { !it.archived }
        val account = params["account"]?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
            live.firstOrNull { it.name.equals(n, ignoreCase = true) }.also { if (it == null) notes += "No account called \"$n\"; the usual one is used." }
        }
        val category = params["category"]?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
            categories.firstOrNull { !it.archived && it.mainCategoryId == null && it.income == (type == EntryType.Income) && it.name.equals(n, ignoreCase = true) }
                .also { if (it == null) notes += "No ${if (type == EntryType.Income) "income" else "expense"} category called \"$n\"; pick one." }
        }
        val dateMs = params["date"]?.trim()?.takeIf { it.isNotEmpty() }?.let { d ->
            runCatching { LocalDate.parse(d).atTime(java.time.LocalTime.now(zone).withNano(0)).atZone(zone).toInstant().toEpochMilli() }
                .getOrElse { notes += "The date \"$d\" should look like 2026-11-13; used now."; null }
        } ?: nowMs

        return AppLinkResult(
            QuickAddDraft(type = type, amountRaw = amountRaw, title = params["title"]?.trim().orEmpty(), categoryId = category?.id, accountId = account?.id, dateMs = dateMs, note = params["note"]?.trim().orEmpty()),
            notes,
        )
    }
}
