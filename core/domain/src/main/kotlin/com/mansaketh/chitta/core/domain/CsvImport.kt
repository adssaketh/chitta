package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalQuery
import java.util.Locale

/** The date layouts a bank or app CSV is likely to use. Day-first comes before month-first because this is an Indian book. */
enum class DatePattern(val label: String, val pattern: String) {
    Iso("2026-11-13", "uuuu-MM-dd"),
    DayMonthYearSlash("13/11/2026", "dd/MM/uuuu"),
    DayMonthYearDash("13-11-2026", "dd-MM-uuuu"),
    DayMonthName("13 Nov 2026", "d MMM uuuu"),
    DayMonthYear2("13/11/26", "dd/MM/uu"),
    MonthDayYearSlash("11/13/2026", "MM/dd/uuuu");

    private val formatters: List<DateTimeFormatter> by lazy {
        listOf("", " HH:mm", " HH:mm:ss", "'T'HH:mm:ss", "'T'HH:mm").map {
            DateTimeFormatter.ofPattern(pattern + it, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
        }
    }

    /** Epoch milliseconds, or null when [text] does not fit this layout. A date with no time is placed at noon. */
    fun parse(text: String, zone: ZoneId): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        for (f in formatters) {
            val parsed: TemporalAccessor = try { f.parseBest(t, TemporalQuery { LocalDateTime.from(it) }, TemporalQuery { LocalDate.from(it) }) } catch (e: Exception) { continue }
            val dt = when (parsed) {
                is LocalDateTime -> parsed
                is LocalDate -> parsed.atTime(LocalTime.NOON)
                else -> continue
            }
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    companion object {
        /** The first layout that reads every non-blank sample, or null. */
        fun detect(samples: List<String>, zone: ZoneId): DatePattern? {
            val real = samples.filter { it.isNotBlank() }
            if (real.isEmpty()) return null
            return entries.firstOrNull { p -> real.all { p.parse(it, zone) != null } }
        }
    }
}

/** Which CSV column holds what. Column numbers start at 0; null means the file has no such column. */
data class CsvMapping(
    val date: Int? = null,
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
    val title: Int? = null,
    val category: Int? = null,
    val subcategory: Int? = null,
    val account: Int? = null,
    val note: Int? = null,
    val type: Int? = null,
    val datePattern: DatePattern = DatePattern.Iso,
    /** Credit-card statements often show spending as positive numbers. */
    val positiveIsExpense: Boolean = false,
    val hasHeader: Boolean = true,
    /** The account used for rows that name none. */
    val defaultAccountId: String? = null,
) {
    /** What is still missing before rows can be read. Empty when the mapping is usable. */
    fun problems(): List<String> = buildList {
        if (date == null) add("Choose the date column.")
        if (amount == null && debit == null && credit == null) add("Choose the amount column, or the debit and credit columns.")
    }
}

/** A saved mapping, kept by name so the next file from the same bank needs no setup. */
data class CsvTemplate(val name: String, val mapping: CsvMapping) {
    fun encode(): String {
        val m = mapping
        fun n(v: Int?) = v?.toString() ?: ""
        val body = listOf(
            n(m.date), n(m.amount), n(m.debit), n(m.credit), n(m.title), n(m.category), n(m.subcategory), n(m.account), n(m.note), n(m.type),
            m.datePattern.name, if (m.positiveIsExpense) "1" else "0", if (m.hasHeader) "1" else "0",
        ).joinToString(";")
        return Escape.write(name) + "|" + body
    }

    companion object {
        fun decode(text: String): CsvTemplate? {
            val bar = text.indexOf('|')
            if (bar < 0) return null
            val f = text.substring(bar + 1).split(';')
            if (f.size < 13) return null
            fun n(i: Int) = f[i].toIntOrNull()
            val pattern = DatePattern.entries.firstOrNull { it.name == f[10] } ?: return null
            return CsvTemplate(
                Escape.read(text.substring(0, bar)),
                CsvMapping(n(0), n(1), n(2), n(3), n(4), n(5), n(6), n(7), n(8), n(9), pattern, f[11] == "1", f[12] == "1"),
            )
        }
    }
}

/** Why a row starts unticked in the review list. */
enum class CsvReason(val text: String) {
    PossibleDuplicate("Looks like an entry you already have"),
    NoCategory("Pick a category"),
    UnknownCategory("Category not in your book"),
    NoAccount("Pick an account"),
    UnknownAccount("Account not in your book"),
    Rounded("Amount had more than two decimals and was rounded"),
}

/** One readable row of the file. [amountPaise] is signed: money out is negative. */
data class CsvRow(
    val line: Int,
    val dateMs: Long,
    val amountPaise: Long,
    val title: String,
    val note: String,
    val accountId: String?,
    val accountLabel: String,
    val categoryId: String?,
    val subCategoryId: String?,
    val categoryLabel: String,
    val reasons: List<CsvReason>,
) {
    val income: Boolean get() = amountPaise > 0
    /** Ready to save without a second look. */
    val ready: Boolean get() = reasons.isEmpty()
}

/** A row that cannot be turned into an entry at all, with its line number in the file. */
data class CsvSkipped(val line: Int, val reason: String)

data class CsvParse(val rows: List<CsvRow>, val skipped: List<CsvSkipped>)

/** What the importer needs to know about the book to match names and spot duplicates. */
data class CsvContext(
    val accounts: List<Account>,
    val categories: List<Category>,
    val existing: List<Transaction>,
    val titleMemory: List<TitleMemory>,
    val zone: ZoneId,
)

/**
 * Reads CSV rows into candidates for the review list. Nothing here saves anything: the person reviews the candidates and only the
 * ticked ones are turned into entries (master section 2, rule 5).
 */
object CsvImport {
    class Amount(val paise: Long, val rounded: Boolean)

    /** "₹1,234.50", "(500)", "1,234.50 Dr", "Rs. 99", "-12.5" and similar. Null when it is not a number. More than two decimals round half-even and say so. */
    fun parseAmount(text: String): Amount? {
        var s = text.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        val lower = s.lowercase(Locale.ENGLISH)
        if (lower.endsWith("dr") || lower.endsWith("dr.")) { negative = true; s = s.substring(0, s.length - (if (lower.endsWith("dr.")) 3 else 2)) }
        else if (lower.endsWith("cr") || lower.endsWith("cr.")) { s = s.substring(0, s.length - (if (lower.endsWith("cr.")) 3 else 2)) }
        s = s.replace("₹", "").replace(" ", "").replace(" ", "").replace(",", "")
        s = s.replace(Regex("(?i)^(rs\\.?|inr)"), "").replace(Regex("(?i)(rs\\.?|inr)$"), "")
        if (s.endsWith("-")) { negative = !negative; s = s.dropLast(1) }
        if (s.startsWith("-")) { negative = !negative; s = s.drop(1) } else if (s.startsWith("+")) s = s.drop(1)
        if (!Regex("^\\d+(\\.\\d+)?$").matches(s)) return null
        val exact = BigDecimal(s)
        val rounded = exact.stripTrailingZeros().scale() > 2
        val paise = try { exact.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact() } catch (e: ArithmeticException) { return null }
        return Amount(if (negative) -paise else paise, rounded)
    }

    /** A first guess at the mapping from the header row, so most files need no setup. */
    fun guessMapping(header: List<String>): CsvMapping {
        fun find(vararg words: String, not: List<String> = emptyList()): Int? {
            for (w in words) {
                val i = header.indexOfFirst { h -> val x = h.trim().lowercase(Locale.ENGLISH); x.contains(w) && not.none { n -> x.contains(n) } }
                if (i >= 0) return i
            }
            return null
        }
        val debit = find("debit", "withdraw", "paid out", "money out")
        val credit = find("credit", "deposit", "paid in", "money in")
        val amount = find("amount", not = listOf("balance"))
        val sub = find("subcategory", "sub category", "sub-category")
        return CsvMapping(
            date = find("date"),
            amount = amount,
            debit = if (amount == null) debit else null,
            credit = if (amount == null) credit else null,
            title = find("title", "description", "narration", "particulars", "payee", "merchant", "details"),
            category = header.indexOfFirst { it.trim().lowercase(Locale.ENGLISH).let { x -> x.contains("category") && !x.contains("sub") } }.takeIf { it >= 0 },
            subcategory = sub,
            account = find("account", not = listOf("balance", "number", "no")),
            note = find("note", "memo", "remark", "comment"),
            type = header.indexOfFirst { it.trim().equals("type", ignoreCase = true) }.takeIf { it >= 0 },
        )
    }

    private val INCOME_WORDS = setOf("income", "credit", "cr", "deposit", "received")
    private val EXPENSE_WORDS = setOf("expense", "debit", "dr", "withdrawal", "paid", "spent")

    private fun cell(row: List<String>, i: Int?): String = if (i == null) "" else row.getOrNull(i)?.trim().orEmpty()

    /** Reads every data row. [rows] is the whole file as parsed by [Csv.parse]; the header row is skipped when the mapping says so. */
    fun read(rows: List<List<String>>, mapping: CsvMapping, ctx: CsvContext): CsvParse {
        val out = ArrayList<CsvRow>()
        val skipped = ArrayList<CsvSkipped>()
        val firstData = if (mapping.hasHeader) 1 else 0
        for (index in firstData until rows.size) {
            val row = rows[index]
            if (row.all { it.isBlank() }) continue
            val line = index + 1
            val dateText = cell(row, mapping.date)
            val dateMs = mapping.datePattern.parse(dateText, ctx.zone)
            if (dateMs == null) { skipped.add(CsvSkipped(line, if (dateText.isEmpty()) "No date." else "The date \"$dateText\" does not look like ${mapping.datePattern.label}.")); continue }

            var rounded = false
            val typeText = cell(row, mapping.type).lowercase(Locale.ENGLISH)
            val signed: Long
            if (mapping.amount == null) {
                val d = cell(row, mapping.debit); val c = cell(row, mapping.credit)
                val dv = if (d.isEmpty()) null else parseAmount(d)
                val cv = if (c.isEmpty()) null else parseAmount(c)
                when {
                    dv != null && dv.paise != 0L -> { signed = -Math.abs(dv.paise); rounded = dv.rounded }
                    cv != null && cv.paise != 0L -> { signed = Math.abs(cv.paise); rounded = cv.rounded }
                    else -> { skipped.add(CsvSkipped(line, "No amount.")); continue }
                }
            } else {
                val text = cell(row, mapping.amount)
                val a = parseAmount(text)
                if (a == null) { skipped.add(CsvSkipped(line, if (text.isEmpty()) "No amount." else "The amount \"$text\" is not a number.")); continue }
                if (a.paise == 0L) { skipped.add(CsvSkipped(line, "The amount is zero.")); continue }
                rounded = a.rounded
                signed = when {
                    typeText in INCOME_WORDS -> Math.abs(a.paise)
                    typeText in EXPENSE_WORDS -> -Math.abs(a.paise)
                    typeText.isEmpty() -> if (mapping.positiveIsExpense) -a.paise else a.paise
                    else -> { skipped.add(CsvSkipped(line, "Entries of type \"${cell(row, mapping.type)}\" are not imported from CSV. Add transfers with Transfer.")); continue }
                }
            }
            if (mapping.amount == null && typeText.isNotEmpty() && typeText !in INCOME_WORDS && typeText !in EXPENSE_WORDS) {
                skipped.add(CsvSkipped(line, "Entries of type \"${cell(row, mapping.type)}\" are not imported from CSV.")); continue
            }

            val title = cell(row, mapping.title)
            val reasons = ArrayList<CsvReason>()
            if (rounded) reasons.add(CsvReason.Rounded)

            // account
            val accountText = cell(row, mapping.account)
            var accountId: String? = null
            var accountLabel = accountText
            if (accountText.isNotEmpty()) {
                val a = ctx.accounts.filter { it.name.equals(accountText, ignoreCase = true) }.let { m -> m.firstOrNull { !it.archived } ?: m.firstOrNull() }
                if (a != null) { accountId = a.id; accountLabel = a.name } else reasons.add(CsvReason.UnknownAccount)
            } else {
                val d = ctx.accounts.firstOrNull { it.id == mapping.defaultAccountId }
                if (d != null) { accountId = d.id; accountLabel = d.name } else reasons.add(CsvReason.NoAccount)
            }

            // category: the named one, else what the titles memory remembers for this title
            val income = signed > 0
            val categoryText = cell(row, mapping.category)
            val subText = cell(row, mapping.subcategory)
            var categoryId: String? = null
            var subId: String? = null
            var categoryLabel = categoryText
            if (categoryText.isNotEmpty()) {
                val named = ctx.categories.filter { it.mainCategoryId == null && !it.archived && it.name.equals(categoryText, ignoreCase = true) }
                val main = named.firstOrNull { it.income == income } ?: named.firstOrNull()
                if (main != null) {
                    categoryId = main.id; categoryLabel = main.name
                    if (subText.isNotEmpty()) {
                        val sub = ctx.categories.firstOrNull { it.mainCategoryId == main.id && !it.archived && it.name.equals(subText, ignoreCase = true) }
                        if (sub != null) { subId = sub.id; categoryLabel = main.name + " > " + sub.name }
                    }
                } else reasons.add(CsvReason.UnknownCategory)
            } else {
                val remembered = TitleSuggest.categoryFor(title, ctx.titleMemory)?.let { id -> ctx.categories.firstOrNull { it.id == id } }
                if (remembered != null) {
                    if (remembered.mainCategoryId != null) { categoryId = remembered.mainCategoryId; subId = remembered.id } else categoryId = remembered.id
                    categoryLabel = remembered.name
                } else reasons.add(CsvReason.NoCategory)
            }
            out.add(CsvRow(line, dateMs, signed, title, cell(row, mapping.note), accountId, accountLabel, categoryId, subId, categoryLabel, reasons))
        }
        return CsvParse(markDuplicates(out, ctx), skipped)
    }

    private fun norm(s: String) = s.lowercase(Locale.ENGLISH).filter { it.isLetterOrDigit() }

    private fun similar(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return true
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 3 && long.contains(short)
    }

    /**
     * Adds [CsvReason.PossibleDuplicate] to rows that match an entry already in the book: same amount, same day, same account when known,
     * and a similar title. Each existing entry can flag only one row, so two real purchases of the same amount on the same day in
     * one file are both kept when the book has none of them, and only one is flagged when the book has one.
     */
    fun markDuplicates(rows: List<CsvRow>, ctx: CsvContext): List<CsvRow> {
        val used = HashSet<String>()
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(ctx.zone).toLocalDate()
        return rows.map { r ->
            val match = ctx.existing.firstOrNull { e ->
                e.id !in used && e.amount == r.amountPaise && day(e.dateMs) == day(r.dateMs) &&
                    (r.accountId == null || e.accountId == r.accountId) && similar(e.name, r.title)
            }
            if (match != null) { used.add(match.id); r.copy(reasons = r.reasons + CsvReason.PossibleDuplicate) } else r
        }
    }

    /** One row of the review list: the row, whether it is ticked, and the account and category the person settled on. */
    data class Choice(val row: CsvRow, val ticked: Boolean, val accountId: String?, val categoryId: String?, val subCategoryId: String?) {
        /** A row can be saved only with an account and a category. */
        val saveable: Boolean get() = accountId != null && categoryId != null
    }

    fun initialChoices(parse: CsvParse): List<Choice> = parse.rows.map { Choice(it, it.ready, it.accountId, it.categoryId, it.subCategoryId) }

    /** Entries for the ticked, saveable rows only. */
    fun toEntries(choices: List<Choice>, accounts: List<Account>, nowMs: Long, newId: () -> String): List<Transaction> =
        choices.filter { it.ticked && it.saveable }.map { c ->
            val r = c.row
            val account = accounts.first { it.id == c.accountId }
            Transaction(
                id = newId(), pairedTransactionId = null, name = r.title, amount = r.amountPaise, currency = account.currency, note = r.note,
                categoryId = c.categoryId!!, subCategoryId = c.subCategoryId, accountId = account.id, dateMs = r.dateMs, modifiedAtMs = nowMs,
                originalDueMs = null, income = r.income, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
                kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = MethodAdded.CSV, objectiveId = null,
                objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
            )
        }
}
