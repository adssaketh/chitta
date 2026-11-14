package com.mansaketh.chitta.core.intake

import java.time.LocalDate

/** Where the suggested amount came from. A total line is trustworthy; the largest amount is only a guess and is labelled so. */
enum class AmountSource { TOTAL_LINE, LARGEST }

/**
 * What the text of a shop receipt seems to say. Every field can be null: a field is left empty rather than guessed.
 * These are suggestions only (DECISIONS D151): the app shows them and the person taps to use each one.
 */
data class ReceiptReading(
    val amountPaise: Long? = null,
    val amountSource: AmountSource? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
) {
    val isEmpty get() = amountPaise == null && date == null && merchant == null
}

/**
 * Reads the amount, date and shop name out of the text of a receipt photo (the text itself is read on the phone by ML Kit; this part is
 * plain Kotlin and is tested with invented receipts). Written for printed Indian bills: day-first dates, lakh commas, GST lines.
 * ML Kit's recogniser reads Latin script only, so a Telugu or Hindi shop name is not read; the digits usually are.
 */
object ReceiptParser {
    private const val MAX_PAISE = 1_000_000_000L // ₹1,00,00,000: a bigger number is a phone, GSTIN or bill number read as money

    // ----- amount -----

    private const val NUM = "\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?"
    private val amountRe = Rx.of("(?:₹|Rs\\.?|INR)?\\s*($NUM)", ignoreCase = true)
    private val onlyAmount = Rx.of("^(?:₹|Rs\\.?|INR)?\\s*($NUM)\\s*$", ignoreCase = true)
    private val parenRe = Rx.of("\\([^)]*\\)")

    /** Words that make a "total" line something else: a part of the bill, not what was paid. */
    private val notTheTotal = Rx.of("sub\\s*-?\\s*total|tax|gst|vat|cess|discount|saving|change|tender|round|qty|quantity|item|point|tip|charge|fee|cash\\s*back", ignoreCase = true)
    private val strongTotal = Rx.of("grand\\s*total|net\\s*(?:amount|payable|total)|amount\\s*(?:payable|due|paid)|total\\s*(?:amount|payable|due|paid)|balance\\s*due|bill\\s*amount|you\\s*pay|to\\s*pay", ignoreCase = true)
    private val plainTotal = Rx.of("\\btotal\\b", ignoreCase = true)

    /** Lines whose numbers are ids and phone numbers, ignored when the largest amount is used as a last resort. */
    private val idLine = Rx.of("gstin|fssai|\\bph\\b|phone|tel\\b|mob|invoice|bill\\s*no|order|\\bid\\b|\\bref|\\bno\\.|token|table|\\bcin\\b|\\bpan\\b|\\bpin\\b", ignoreCase = true)

    private fun paiseOf(text: String): Long? {
        val v = Rupees.toPaise(text) ?: return null
        return if (v in 1..MAX_PAISE) v else null
    }

    /** The first amount in [text], or null. A rupee sign or "Rs" in front is allowed. */
    private fun firstAmount(text: String): Long? {
        for (m in amountRe.findAll(text)) paiseOf(m.groupValues[1])?.let { return it }
        return null
    }

    /** The amount on the label's own line after the label, else the next line when it starts with an amount. */
    private fun amountAfterLabel(lines: List<String>, i: Int, label: Regex): Long? {
        val line = lines[i]
        val hit = label.find(line)
        val after = if (hit != null) line.substring(hit.range.last + 1) else line
        firstAmount(after)?.let { return it }
        val next = lines.getOrNull(i + 1)?.trim() ?: return null
        // The next line counts only when it is just an amount (maybe with a rupee sign), so a following item line is never read as the total.
        return if (onlyAmount.matches(next)) firstAmount(next) else null
    }

    private fun readAmount(lines: List<String>): Pair<Long, AmountSource>? {
        var strong: Long? = null
        var plain: Long? = null
        for (i in lines.indices) {
            val line = lines[i]
            // The label is judged without brackets: "Total (incl. GST)" is still the total.
            val judged = line.replace(parenRe, " ")
            if (notTheTotal.containsMatchIn(judged)) continue
            when {
                strongTotal.containsMatchIn(judged) -> amountAfterLabel(lines, i, strongTotal)?.let { strong = it }
                plainTotal.containsMatchIn(judged) -> amountAfterLabel(lines, i, plainTotal)?.let { plain = it }
            }
        }
        // The last line of the strongest kind wins: totals sit at the bottom, after the items.
        strong?.let { return it to AmountSource.TOTAL_LINE }
        plain?.let { return it to AmountSource.TOTAL_LINE }
        val largest = lines.filterNot { idLine.containsMatchIn(it) }
            .flatMap { l -> amountRe.findAll(l).mapNotNull { m ->
                // Without a total line only amounts that look like money count: a decimal part or a rupee sign.
                val money = m.groupValues[1].contains('.') || m.value.contains('₹') || m.value.contains("rs", ignoreCase = true) || m.value.contains("inr", ignoreCase = true)
                if (money) paiseOf(m.groupValues[1]) else null
            }.toList() }
            .maxOrNull()
        return largest?.let { it to AmountSource.LARGEST }
    }

    // ----- date -----

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private fun monthOf(name: String): Int? = months.indexOf(name.take(3).lowercase()).let { if (it < 0) null else it + 1 }
    private fun year(s: String): Int = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    private val numericDay = Rx.of("(?<!\\d)(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4}|\\d{2})(?!\\d)")
    private val isoDay = Rx.of("(?<!\\d)(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})(?!\\d)")
    private val dayMonthName = Rx.of("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-/.]*([A-Za-z]{3,9})[\\s\\-/.,]*(\\d{4}|\\d{2})(?!\\d)")
    private val monthNameDay = Rx.of("\\b([A-Za-z]{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})(?!\\d)")

    private fun valid(y: Int, m: Int, d: Int, today: LocalDate): LocalDate? {
        val day = try { LocalDate.of(y, m, d) } catch (e: Exception) { return null }
        // A receipt is from the past; one day ahead allows for a phone clock in another zone. Before 2000 is a misread.
        return if (day.year >= 2000 && !day.isAfter(today.plusDays(1))) day else null
    }

    /**
     * The first date that appears in the text and makes sense. Numbers are read day first, as Indian bills print them; when the
     * second number cannot be a month (11/25/2026) the date is read month first. "12 Nov 2026" and "Nov 12, 2026" are read as written.
     */
    private fun readDate(text: String, today: LocalDate): LocalDate? {
        val found = ArrayList<Pair<Int, LocalDate>>()
        for (m in numericDay.findAll(text)) {
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = year(m.groupValues[3])
            val d = valid(y, b, a, today) ?: if (b > 12) valid(y, a, b, today) else null
            if (d != null) found += m.range.first to d
        }
        for (m in isoDay.findAll(text)) valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), today)?.let { found += m.range.first to it }
        for (m in dayMonthName.findAll(text)) {
            val mo = monthOf(m.groupValues[2]) ?: continue
            valid(year(m.groupValues[3]), mo, m.groupValues[1].toInt(), today)?.let { found += m.range.first to it }
        }
        for (m in monthNameDay.findAll(text)) {
            val mo = monthOf(m.groupValues[1]) ?: continue
            valid(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt(), today)?.let { found += m.range.first to it }
        }
        return found.minByOrNull { it.first }?.second
    }

    // ----- shop name -----

    private val notAName = Rx.of(
        "invoice|receipt|\\bbill\\b|tax|gstin|\\bgst\\b|cash\\s*memo|thank|date|time|www\\.|http|@|\\btel\\b|phone|\\bph\\b|mob|order|table|customer|token|original|duplicate|\\bcopy\\b|fssai|\\bcin\\b|\\bpan\\b|address|\\bcash\\b|\\bqty\\b|\\bitem|\\bamount|\\btotal",
        ignoreCase = true,
    )
    private val welcome = Rx.of("^welcome\\s+to\\s+", ignoreCase = true)

    private fun readMerchant(lines: List<String>): String? {
        for (raw in lines.take(8)) {
            val line = raw.replace(welcome, "").trim()
            val letters = line.count { it.isLetter() }
            val visible = line.count { !it.isWhitespace() }
            if (letters < 3 || visible == 0 || letters.toDouble() / visible < 0.6) continue
            if (notAName.containsMatchIn(line)) continue
            val cleaned = line.trim { !it.isLetterOrDigit() }.replace(Rx.WHITESPACE_RUN, " ").take(40).trim()
            if (cleaned.length < 3) continue
            // A name printed all in capitals is shown in title case; a mixed-case name is kept as printed.
            return if (cleaned == cleaned.uppercase() || cleaned == cleaned.lowercase())
                cleaned.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.take(1).uppercase() + it.drop(1).lowercase() }
            else cleaned
        }
        return null
    }

    /** Reads one piece of recognised text. [today] bounds the date (a receipt is not from the future). */
    fun parse(text: String, today: LocalDate): ReceiptReading {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ReceiptReading()
        val amount = readAmount(lines)
        return ReceiptReading(amount?.first, amount?.second, readDate(text, today), readMerchant(lines))
    }

    /**
     * Combines two readings of the same photo (as taken, and with more contrast): each field comes from the first reading that has it,
     * except that an amount from a total line beats an amount that is only the largest one seen.
     */
    fun merge(readings: List<ReceiptReading>): ReceiptReading {
        val amount = readings.firstOrNull { it.amountSource == AmountSource.TOTAL_LINE } ?: readings.firstOrNull { it.amountPaise != null }
        return ReceiptReading(
            amount?.amountPaise, amount?.amountSource,
            readings.firstNotNullOfOrNull { it.date },
            readings.firstNotNullOfOrNull { it.merchant },
        )
    }
}
