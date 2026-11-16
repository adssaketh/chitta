package com.mansaketh.chitta.ui.ledger

import com.mansaketh.chitta.core.domain.SearchSyntax
import com.mansaketh.chitta.core.domain.SmartFilter
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * One chip for a typed filter in the Ledger search box (PDF p.18: "Food & dining", "Over ₹200", "November").
 * [ranges] are the character ranges of the search text that make the filter; removing the chip cuts exactly those.
 */
internal data class TypedChip(val label: String, val ranges: List<IntRange>)

/**
 * Turns the typed filters in the search text into chips and cuts them out again (Screen 10). It reads each word with the same
 * [SearchSyntax] the list uses, so a chip appears only for a word that really filters. Nothing here changes what is searched.
 */
internal object TypedFilterChips {
    private data class Tok(val range: IntRange, val text: String, val quoted: Boolean)
    private data class Piece(val tok: Tok, val filter: SmartFilter, val key: String)

    private val TOKEN = Regex("^(-?)([A-Za-z]+)(>=|<=|:|>|<)(.*)$")
    private val LAST = Regex("^(\\d{1,4})([dwmyDWMY])$")
    private val RUPEES = Regex("^\\d{1,12}(\\.\\d{1,2})?$")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** Words of the search text with their place in it. Quotes group words and are not part of the text, as in [SearchSyntax]. */
    private fun tokens(input: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < input.length) {
            while (i < input.length && input[i].isWhitespace()) i++
            if (i >= input.length) break
            val start = i
            val sb = StringBuilder()
            val startsQuoted = input[i] == '"'
            var inQuote = false
            while (i < input.length && (inQuote || !input[i].isWhitespace())) {
                if (input[i] == '"') inQuote = !inQuote else sb.append(input[i])
                i++
            }
            if (sb.isNotBlank()) out += Tok(start until i, sb.toString(), startsQuoted)
        }
        return out
    }

    fun chips(input: String, categories: List<Category>, accounts: List<Account>, nowMs: Long, zone: ZoneId): List<TypedChip> {
        if (input.isBlank()) return emptyList()
        val pieces = ArrayList<Piece>()
        for (t in tokens(input)) {
            if (t.quoted) continue
            val m = TOKEN.matchEntire(t.text) ?: continue
            val parsed = SearchSyntax.parse(t.text, categories, accounts, nowMs, zone)
            if (parsed.problems.isNotEmpty() || parsed.text.isNotEmpty() || !parsed.filter.isActive) continue
            pieces += Piece(t, parsed.filter, canonicalKey(m.groupValues[2]))
        }
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

        // from: and to: that together cover exactly one calendar month read as one chip, "November" (PDF p.18).
        val froms = pieces.filter { it.key == "from" }
        val tos = pieces.filter { it.key == "to" }
        var month: Triple<Piece, Piece, String>? = null
        if (froms.size == 1 && tos.size == 1) {
            val start = Instant.ofEpochMilli(froms[0].filter.fromMs ?: 0L).atZone(zone).toLocalDate()
            val monthEnd = start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (start.dayOfMonth == 1 && tos[0].filter.toMs == monthEnd) {
                val name = start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                month = Triple(froms[0], tos[0], if (start.year == today.year) name else "$name ${start.year}")
            }
        }

        val out = ArrayList<TypedChip>()
        for (p in pieces) {
            val m = month
            if (m != null && (p === m.first || p === m.second)) {
                if (p === m.first) out += TypedChip(m.third, listOf(m.first.tok.range, m.second.tok.range))
                continue
            }
            out += TypedChip(label(p, categories, accounts, today, zone), listOf(p.tok.range))
        }
        return out
    }

    /** The search text without this chip's words. */
    fun without(input: String, chip: TypedChip): String {
        val sb = StringBuilder(input)
        for (r in chip.ranges.sortedByDescending { it.first }) {
            var s = r.first
            var e = r.last + 1
            while (e < sb.length && sb[e].isWhitespace()) e++
            if (e >= sb.length) while (s > 0 && sb[s - 1].isWhitespace()) s--
            sb.delete(s, e)
        }
        return sb.toString().trim()
    }

    private fun canonicalKey(raw: String) = when (raw.lowercase()) {
        "amount" -> "amt"
        "category" -> "cat"
        "account" -> "acc"
        else -> raw.lowercase()
    }

    private fun label(p: Piece, categories: List<Category>, accounts: List<Account>, today: LocalDate, zone: ZoneId): String {
        val m = TOKEN.matchEntire(p.tok.text) ?: return p.tok.text
        val neg = m.groupValues[1] == "-"
        val op = m.groupValues[3]
        val value = m.groupValues[4].trim()
        val f = p.filter
        val not = if (neg) "Not " else ""
        return when (p.key) {
            "amt" -> amountLabel(op, value) ?: p.tok.text
            "cat" -> {
                val ids = (if (neg) f.excludeCategoryIds else f.categoryIds).orEmpty()
                // A main category brings its sub-categories into the filter; name the main one only.
                val names = categories.filter { it.id in ids && (it.mainCategoryId == null || it.mainCategoryId !in ids) }.map { it.name }.distinct()
                not + names.joinToString(", ").ifEmpty { value }
            }
            "acc" -> {
                val ids = (if (neg) f.excludeAccountIds else f.accountIds).orEmpty()
                not + accounts.filter { it.id in ids }.map { it.name }.distinct().joinToString(", ").ifEmpty { value }
            }
            "type" -> {
                val types = (if (neg) f.excludeTypes else f.types).orEmpty()
                not + types.joinToString(", ") { it.word.replaceFirstChar { c -> c.uppercase() } }
            }
            "note" -> {
                val words = (if (neg) f.excludeNotes else f.notes).joinToString(", ")
                if (neg) "Not in note: $words" else "Note: $words"
            }
            "last" -> lastLabel(value) ?: p.tok.text
            "from" -> "From " + dateLabel(f.fromMs, today, zone, p.tok.text)
            "to" -> "Until " + dateLabel(f.toMs?.let { it - 1 }, today, zone, p.tok.text)
            else -> p.tok.text
        }
    }

    private fun rupees(text: String): String? {
        val t = text.trim().removePrefix("₹").replace(",", "")
        if (!RUPEES.matches(t)) return null
        return IndianFormat.rupees(Paise(BigDecimal(t).movePointRight(2).longValueExact()))
    }

    private fun amountLabel(op: String, value: String): String? = when (op) {
        ">" -> rupees(value)?.let { "Over $it" }
        ">=" -> rupees(value)?.let { "$it or more" }
        "<" -> rupees(value)?.let { "Under $it" }
        "<=" -> rupees(value)?.let { "$it or less" }
        else -> {
            val parts = value.split('-')
            if (parts.size == 2) {
                val lo = rupees(parts[0]); val hi = rupees(parts[1])
                if (lo != null && hi != null) "$lo to $hi" else null
            } else rupees(value)?.let { "Exactly $it" }
        }
    }

    private fun lastLabel(value: String): String? {
        val l = LAST.matchEntire(value) ?: return null
        val n = l.groupValues[1].toInt()
        return when (l.groupValues[2].lowercase()) {
            "d" -> if (n == 1) "Today" else "Last $n days"
            "w" -> if (n == 1) "Last week" else "Last $n weeks"
            "m" -> if (n == 1) "Last month" else "Last $n months"
            else -> if (n == 1) "Last year" else "Last $n years"
        }
    }

    private fun dateLabel(ms: Long?, today: LocalDate, zone: ZoneId, fallback: String): String {
        ms ?: return fallback
        // The stored bound is a start-of-day in the phone's zone, so the date is read back in the same zone.
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return d.format(if (d.year == today.year) DAY_MONTH else DAY_MONTH_YEAR)
    }
}
