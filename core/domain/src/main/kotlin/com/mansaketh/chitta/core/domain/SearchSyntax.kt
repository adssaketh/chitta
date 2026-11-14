package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** The kinds of entry `type:` can ask for. */
enum class SearchType(val word: String) {
    Spending("spending"), Income("income"), Upcoming("upcoming"), Lent("lent"), Borrowed("borrowed"),
    Subscription("subscription"), Repeating("repeating"), Transfer("transfer");

    companion object {
        fun fromWord(w: String): SearchType? = entries.firstOrNull { it.word == w.lowercase() }
        val words: String get() = entries.joinToString(", ") { it.word }
    }
}

/**
 * What the typed filters in a Ledger search ask for (Phase 8f, master section 8 "Smart search syntax"). Null or empty means "no such filter".
 * Different filters must all hold. Inside one filter, any value may hold (`cat:food,fuel`, or `cat:food cat:fuel`). A minus in front
 * (`-cat:food`) leaves entries out. Amounts are positive paise of the absolute value, as in [LedgerFilter]. [toMs] is exclusive.
 */
data class SmartFilter(
    val categoryIds: Set<String>? = null,
    val excludeCategoryIds: Set<String> = emptySet(),
    val accountIds: Set<String>? = null,
    val excludeAccountIds: Set<String> = emptySet(),
    val types: Set<SearchType>? = null,
    val excludeTypes: Set<SearchType> = emptySet(),
    /** Lower-case words; an entry's note must contain at least one. */
    val notes: List<String> = emptyList(),
    val excludeNotes: List<String> = emptyList(),
    val minAmount: Paise? = null,
    val maxAmount: Paise? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
) {
    val isActive: Boolean
        get() = categoryIds != null || excludeCategoryIds.isNotEmpty() || accountIds != null || excludeAccountIds.isNotEmpty() ||
            types != null || excludeTypes.isNotEmpty() || notes.isNotEmpty() || excludeNotes.isNotEmpty() ||
            minAmount != null || maxAmount != null || fromMs != null || toMs != null
}

/**
 * The result of reading a search box. [text] is what is left for plain text search (the words that were not filters, in order, one space
 * apart). [problems] are plain sentences about parts that could not be used; those parts are ignored, and the screen shows the sentences
 * so an ignored filter is never silent.
 */
data class SearchParse(val text: String, val filter: SmartFilter, val problems: List<String>)

/**
 * Reads typed filters such as `amt>500 cat:food acc:hdfc last:30d`:
 *
 *  - `amt>500`, `amt>=500`, `amt<200`, `amt<=200`, `amt:100-500`, `amt:500`: rupees, commas allowed, up to two decimals. `>` and `<` are strict
 *    to the paisa;
 *  - `cat:food`, `acc:hdfc`: a name, case ignored. An exact name wins; otherwise any name that contains the words. A main category brings its
 *    sub-categories. `cat:food,fuel` is either;
 *  - `last:30d` (today and the 29 days before), `last:2w` (14 days), `last:3m` and `last:1y` (calendar months and years, ending today);
 *  - `from:2026-10-01` and `to:2026-10-31` (the last day is included);
 *  - `type:` one of [SearchType.words], `note:word` in the note;
 *  - a minus in front of `cat`, `acc`, `type` or `note` leaves matches out;
 *  - a word in quotes is always plain text, and quotes also let a value hold spaces: `cat:"eating out"`.
 *
 * Words that are not filters stay plain text, so a search with no filters behaves as it always did. A word like `foo:bar` whose key is not a
 * filter is reported, not searched, because searching for it would quietly return nothing.
 */
object SearchSyntax {
    private val KEYS = mapOf(
        "amt" to "amt", "amount" to "amt", "cat" to "cat", "category" to "cat", "acc" to "acc", "account" to "acc",
        "last" to "last", "from" to "from", "to" to "to", "type" to "type", "note" to "note",
    )
    private val TOKEN = Regex("^(-?)([A-Za-z]+)(>=|<=|:|>|<)(.*)$")
    private val LAST = Regex("^(\\d{1,4})([dwmyDWMY])$")
    private val RANGE = Regex("^([\\d,.₹]+)-([\\d,.₹]+)$")
    private val RUPEES = Regex("^\\d{1,12}(\\.\\d{1,2})?$")

    private data class Token(val text: String, val startsQuoted: Boolean)

    private fun tokens(input: String): List<Token> {
        val out = ArrayList<Token>()
        val sb = StringBuilder()
        var inQuote = false
        var startsQuoted = false
        var open = false
        for (ch in input) {
            when {
                ch == '"' -> { if (!open) { open = true; startsQuoted = true }; inQuote = !inQuote }
                ch.isWhitespace() && !inQuote -> if (open) { out += Token(sb.toString(), startsQuoted); sb.setLength(0); open = false; startsQuoted = false }
                else -> { if (!open) { open = true; startsQuoted = false }; sb.append(ch) }
            }
        }
        if (open) out += Token(sb.toString(), startsQuoted)
        return out.filter { it.text.isNotBlank() }
    }

    /** Rupees to paise, exactly. Null when the text is not an amount. */
    internal fun paise(text: String): Paise? {
        val t = text.trim().removePrefix("₹").replace(",", "")
        if (!RUPEES.matches(t)) return null
        return Paise(BigDecimal(t).movePointRight(2).longValueExact())
    }

    /** Category ids a name stands for: exact names win, else names that contain it; a main category adds its sub-categories. Empty when none fit. */
    internal fun categoryIds(name: String, categories: List<Category>): Set<String> {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return emptySet()
        val exact = categories.filter { it.name.trim().lowercase() == q }
        val hits = exact.ifEmpty { categories.filter { it.name.lowercase().contains(q) } }
        val ids = LinkedHashSet<String>()
        for (c in hits) {
            ids += c.id
            if (c.mainCategoryId == null) categories.filter { it.mainCategoryId == c.id }.forEach { ids += it.id }
        }
        return ids
    }

    internal fun accountIds(name: String, accounts: List<Account>): Set<String> {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return emptySet()
        val exact = accounts.filter { it.name.trim().lowercase() == q }
        return exact.ifEmpty { accounts.filter { it.name.lowercase().contains(q) } }.map { it.id }.toSet()
    }

    fun parse(input: String, categories: List<Category>, accounts: List<Account>, nowMs: Long, zone: ZoneId): SearchParse {
        val problems = ArrayList<String>()
        val text = ArrayList<String>()
        var cats: MutableSet<String>? = null
        val notCats = LinkedHashSet<String>()
        var accs: MutableSet<String>? = null
        val notAccs = LinkedHashSet<String>()
        var types: MutableSet<SearchType>? = null
        val notTypes = LinkedHashSet<SearchType>()
        val notes = ArrayList<String>()
        val notNotes = ArrayList<String>()
        var min: Long? = null
        var max: Long? = null
        var from: Long? = null
        var to: Long? = null
        val today = Periods.date(nowMs, zone)

        fun raiseMin(v: Long) { min = maxOf(min ?: v, v) }
        fun lowerMax(v: Long) { max = minOf(max ?: v, v) }
        fun raiseFrom(v: Long) { from = maxOf(from ?: v, v) }
        fun lowerTo(v: Long) { to = minOf(to ?: v, v) }

        for (tok in tokens(input)) {
            val m = if (tok.startsQuoted) null else TOKEN.matchEntire(tok.text)
            if (m == null) { text += tok.text; continue }
            val neg = m.groupValues[1] == "-"
            val rawKey = m.groupValues[2]
            val op = m.groupValues[3]
            val value = m.groupValues[4].trim()
            val key = KEYS[rawKey.lowercase()]
            if (key == null) {
                // only "word:" is read as a filter attempt; "a<b" or "x>y" stay plain text
                if (op == ":") problems += "\"$rawKey:\" is not a filter. Filters are amt, cat, acc, last, from, to, type and note. Put the word in quotes to search for it."
                else text += tok.text
                continue
            }
            if (neg && key !in setOf("cat", "acc", "type", "note")) { problems += "A minus works with cat, acc, type and note, not $key."; continue }
            if (key != "amt" && op != ":") { problems += "$key needs a colon, like $key:${if (key == "last") "30d" else "value"}."; continue }
            if (value.isEmpty()) { problems += "$key: needs a value."; continue }

            when (key) {
                "amt" -> when (op) {
                    ">", ">=", "<", "<=" -> {
                        val v = paise(value)
                        if (v == null) problems += "amt$op needs an amount in rupees, like amt${op}500. \"$value\" is not one."
                        else when (op) {
                            ">" -> raiseMin(v.value + 1)
                            ">=" -> raiseMin(v.value)
                            "<" -> lowerMax(v.value - 1)
                            else -> lowerMax(v.value)
                        }
                    }
                    else -> {
                        val r = RANGE.matchEntire(value)
                        if (r != null) {
                            val lo = paise(r.groupValues[1]); val hi = paise(r.groupValues[2])
                            if (lo == null || hi == null) problems += "amt: needs amounts in rupees, like amt:100-500. \"$value\" is not one."
                            else if (lo > hi) problems += "amt:$value starts above where it ends."
                            else { raiseMin(lo.value); lowerMax(hi.value) }
                        } else {
                            val v = paise(value)
                            if (v == null) problems += "amt: needs an amount in rupees, like amt:500 or amt:100-500. \"$value\" is not one."
                            else { raiseMin(v.value); lowerMax(v.value) }
                        }
                    }
                }
                "cat" -> {
                    val ids = LinkedHashSet<String>()
                    for (name in value.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                        val found = categoryIds(name, categories)
                        if (found.isEmpty()) problems += "No category is called \"$name\"." else ids += found
                    }
                    if (ids.isNotEmpty()) { if (neg) notCats += ids else cats = (cats ?: LinkedHashSet<String>()).also { it += ids } }
                }
                "acc" -> {
                    val ids = LinkedHashSet<String>()
                    for (name in value.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                        val found = accountIds(name, accounts)
                        if (found.isEmpty()) problems += "No account is called \"$name\"." else ids += found
                    }
                    if (ids.isNotEmpty()) { if (neg) notAccs += ids else accs = (accs ?: LinkedHashSet<String>()).also { it += ids } }
                }
                "type" -> {
                    val found = LinkedHashSet<SearchType>()
                    for (w in value.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                        val t = SearchType.fromWord(w)
                        if (t == null) problems += "\"$w\" is not a type. Types are ${SearchType.words}." else found += t
                    }
                    if (found.isNotEmpty()) { if (neg) notTypes += found else types = (types ?: LinkedHashSet<SearchType>()).also { it += found } }
                }
                "note" -> {
                    val words = value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    if (neg) notNotes += words else notes += words
                }
                "last" -> {
                    val l = LAST.matchEntire(value)
                    val n = l?.groupValues?.get(1)?.toInt()
                    if (l == null || n == null || n < 1) problems += "last: needs a number and d, w, m or y, like last:30d. \"$value\" is not that."
                    else {
                        val first: LocalDate = when (l.groupValues[2].lowercase()) {
                            "d" -> today.minusDays(n - 1L)
                            "w" -> today.minusDays(n * 7L - 1)
                            "m" -> today.minusMonths(n.toLong()).plusDays(1)
                            else -> today.minusYears(n.toLong()).plusDays(1)
                        }
                        raiseFrom(Periods.startOfDay(first, zone))
                        lowerTo(Periods.startOfDay(today.plusDays(1), zone))
                    }
                }
                "from", "to" -> {
                    val d = runCatching { LocalDate.parse(value) }.getOrNull()
                    if (d == null) problems += "$key: needs a date like $key:2026-10-01. \"$value\" is not one."
                    else if (key == "from") raiseFrom(Periods.startOfDay(d, zone)) else lowerTo(Periods.startOfDay(d.plusDays(1), zone))
                }
            }
        }

        val lo = min; val hi = max
        if (lo != null && hi != null && lo > hi) { problems += "The amounts leave nothing between them."; min = null; max = null }
        val f = from; val t = to
        if (f != null && t != null && f >= t) { problems += "The dates leave nothing between them."; from = null; to = null }

        val filter = SmartFilter(
            categoryIds = cats, excludeCategoryIds = notCats, accountIds = accs, excludeAccountIds = notAccs,
            types = types, excludeTypes = notTypes, notes = notes, excludeNotes = notNotes,
            minAmount = min?.let { Paise(it) }, maxAmount = max?.let { Paise(it) }, fromMs = from, toMs = to,
        )
        return SearchParse(text.joinToString(" "), filter, problems)
    }
}
