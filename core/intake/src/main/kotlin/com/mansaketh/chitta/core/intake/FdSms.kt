package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.FixedDeposit
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Reads a bank "FD booked / renewed" SMS into a [FixedDeposit]. Behaviour of Paddhu's `parseFixedDepositSms`, rebuilt on paise.
 *
 * Nothing is guessed: no amount, or no maturity (neither a date nor a tenure), gives null and the message stays parked. A deposit read
 * here never creates an entry; the bank's separate debit SMS does that. The reading is only as good as the message wording the old app
 * was written against (the wording in DECISIONS D100); a bank that words it differently is simply not read.
 */
object FdSms {
    private val bankKeywords: List<Pair<String, String>> = listOf(
        "axis" to "Axis Bank", "hdfc" to "HDFC Bank", "icici" to "ICICI Bank", "sbi" to "State Bank of India", "kotak" to "Kotak Mahindra Bank",
        "idfc" to "IDFC FIRST Bank", "yes bank" to "YES Bank", "yesbnk" to "YES Bank", "indusind" to "IndusInd Bank", "federal" to "Federal Bank",
        "canara" to "Canara Bank", "pnb" to "Punjab National Bank", "bob" to "Bank of Baroda", "union bank" to "Union Bank of India",
        "au small" to "AU Small Finance Bank", "aubank" to "AU Small Finance Bank", "ausfb" to "AU Small Finance Bank", "bajaj" to "Bajaj Finance",
        "equitas" to "Equitas Small Finance Bank", "ujjivan" to "Ujjivan Small Finance Bank",
    )

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private const val DATE = """(\d{1,2}[-/. ][A-Za-z]{3}[a-z]*[-/., ]+\d{2,4}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|[A-Za-z]{3}[a-z]*\s+\d{1,2},?\s+\d{4})"""

    private val rupee = Rx.of("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d+)?)""", ignoreCase = true)
    private val named = Rx.of("""(?:deposit|fd|fdr|td|amount|principal)[^\d]{0,30}?(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d+)?)""", ignoreCase = true)
    private val maturityAmountRe = Rx.of("""matur\w*\s*(?:amount|value|proceeds|amt)[^\d]{0,20}?(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d+)?)""", ignoreCase = true)
    private val ratePct = Rx.of("""(\d{1,2}(?:\.\d+)?)\s*%""", ignoreCase = true)
    private val rateWord = Rx.of("""(?:roi|rate(?: of interest)?|interest rate)[^\d]{0,12}(\d{1,2}(?:\.\d+)?)""", ignoreCase = true)
    private val maturityDateRe = Rx.of("""matur\w*\s*(?:on|date|dt|:|-)?\s*[:\-]?\s*$DATE""", ignoreCase = true)
    private val startRe = Rx.of("""(?:booked|opened|created|placed|start\w*|value date|deposit date)\s*(?:on|date|dt|:|-)?\s*[:\-]?\s*$DATE""", ignoreCase = true)
    private val tenureRe = Rx.of("""(?:tenure|period|term|for)\s*(?:of|:)?\s*(\d{1,3})\s*(month|day|year|yr)""", ignoreCase = true)
    private val numberRe = Rx.of("""(?:fd|fdr|td|deposit|receipt|a/?c|account)\s*(?:no\.?|number|#)?\s*[:\-]?\s*(?:x+|\*+)?(\d{4,})""", ignoreCase = true)

    private val dmonY = Rx.of("""^(\d{1,2})[-/. ]([A-Za-z]{3})[a-z]*[-/., ]+(\d{2,4})\z""")
    private val dmy = Rx.of("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\z""")
    private val monDY = Rx.of("""^([A-Za-z]{3})[a-z]*\s+(\d{1,2}),?\s+(\d{4})\z""")

    private fun year(y: String): Int = y.toInt().let { if (y.length <= 2) 2000 + it else it }

    private fun date(raw: String): LocalDate? {
        val s = raw.trim()
        return try {
            dmonY.find(s)?.let { m ->
                val mi = months.indexOf(m.groupValues[2].lowercase())
                if (mi >= 0) return LocalDate.of(year(m.groupValues[3]), mi + 1, m.groupValues[1].toInt())
            }
            dmy.find(s)?.let { m -> return LocalDate.of(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            monDY.find(s)?.let { m ->
                val mi = months.indexOf(m.groupValues[1].lowercase())
                if (mi >= 0) return LocalDate.of(m.groupValues[3].toInt(), mi + 1, m.groupValues[2].toInt())
            }
            null
        } catch (e: java.time.DateTimeException) {
            null // "31-02-2027": not a date, so not guessed
        }
    }

    private fun addMonths(d: LocalDate, n: Int): LocalDate {
        val ym = YearMonth.from(d).plusMonths(n.toLong())
        return ym.atDay(minOf(d.dayOfMonth, ym.lengthOfMonth()))
    }

    private fun bankFrom(sender: String, body: String): String {
        val hay = "${sender.lowercase()} ${body.lowercase()}"
        bankKeywords.firstOrNull { hay.contains(it.first) }?.let { return it.second }
        val tag = sender.replace(Regex("^[A-Za-z]{2}-"), "").trim()
        return if (tag.isEmpty()) "Bank" else tag.uppercase()
    }

    /** The deposit this message announces, or null (not a booking, or the amount or the maturity could not be read). */
    fun parse(body: String, sender: String, smsDateMs: Long, smsId: String, zone: ZoneId): FixedDeposit? {
        if (!PingExport.looksLikeFixedDepositBooking(body)) return null
        val principal = Rupees.toPaise(named.find(body)?.groupValues?.get(1)) ?: Rupees.toPaise(rupee.find(body)?.groupValues?.get(1))
        if (principal == null || principal <= 0) return null
        val stated = Rupees.toPaise(maturityAmountRe.find(body)?.groupValues?.get(1))
        val rate = (ratePct.find(body) ?: rateWord.find(body))?.groupValues?.get(1)?.let { BigDecimal(it).stripTrailingZeros().toPlainString() } ?: "0"

        val smsDay = java.time.Instant.ofEpochMilli(smsDateMs).atZone(zone).toLocalDate()
        val start = startRe.find(body)?.let { date(it.groupValues[1]) } ?: smsDay
        var maturity = maturityDateRe.find(body)?.let { date(it.groupValues[1]) }
        if (maturity == null) {
            tenureRe.find(body)?.let { t ->
                val n = t.groupValues[1].toInt()
                val u = t.groupValues[2].lowercase()
                maturity = when { u.startsWith("month") -> addMonths(start, n); u.startsWith("day") -> start.plusDays(n.toLong()); else -> addMonths(start, 12 * n) }
            }
        }
        val mat = maturity ?: return null
        if (!mat.isAfter(start)) return null

        return FixedDeposit(
            id = "sms-" + smsId.ifEmpty { start.atStartOfDay(zone).toInstant().toEpochMilli().toString() },
            bank = bankFrom(sender, body),
            fdNumber = numberRe.find(body)?.groupValues?.get(1) ?: "",
            principal = principal, ratePct = rate,
            startMs = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            maturityMs = mat.atStartOfDay(zone).toInstant().toEpochMilli(),
            compoundingPerYear = 4, maturityAmount = stated?.takeIf { it > 0 },
            accountId = null, closed = false, note = "", fromSms = true, smsId = smsId, updatedAtMs = null,
        )
    }
}
