package com.mansaketh.chitta.core.domain

/** An icon a category can wear. [key] is the Phosphor name stored on the category as "ph:<key>". */
data class IconChoice(val key: String, val label: String, val keywords: List<String> = emptyList())

object IconSearch {
    const val PREFIX = "ph:"

    /** The Phosphor key a category's icon name points to, or null for an imported (Paddhu) name or none. */
    fun keyOf(iconName: String?): String? = iconName?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }

    fun nameFor(key: String): String = PREFIX + key

    /**
     * Icons that match every word typed, in the label or the keywords (case-insensitive; a word matches the start of a word, so
     * "cof" finds Coffee but "ee" does not). An empty search lists everything in catalog order.
     */
    fun search(query: String, choices: List<IconChoice>): List<IconChoice> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return choices
        return choices.filter { c ->
            val haystack = (listOf(c.label) + c.keywords).flatMap { it.lowercase().split(Regex("[\\s-]+")) }
            words.all { w -> haystack.any { it.startsWith(w) } }
        }
    }
}
