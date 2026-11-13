package com.mansaketh.chitta.core.intake

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One UPI payment read from a PhonePe, Paytm, Google Pay, BHIM, CRED or MobiKwik page, a statement PDF's text, or a statement CSV. */
data class UpiEntry(
    /** Always positive, in paise. */
    val amountPaise: Long,
    val isExpense: Boolean,
    /** Who was paid, or who paid you. */
    val party: String,
    val date: LocalDateTime,
    val txnId: String = "",
    val utr: String = "",
    val upiId: String = "",
    val message: String = "",
    /** Digits of the account that was debited / credited, as far as they are shown. */
    val accountDigits: String = "",
    val app: String = "PhonePe",
    /** The amount was taken from a line of digits because no rupee sign was found; the rupee sign is easily read as a digit, so check it. */
    val amountUnconfirmed: Boolean = false,
)

/**
 * Reads payments from the text of a screenshot (read on the phone by ML Kit), a statement PDF, or a CSV. The text-reading itself is on
 * the Android side; everything here is plain Kotlin so it is tested with invented text. A port of `phonePeParser.dart`, including the
 * lessons the old app learned from real screenshots (a rupee sign read as "7", amounts shown twice, several OCR passes).
 */
object UpiParser {
    private val MAX_PAISE = 1_000_000_000L // ₹1,00,00,000: a bigger "amount" is an id or UTR read as money

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private fun month(s: String): Int? = months.indexOf(s.take(3).lowercase()).let { if (it < 0) null else it + 1 }

    private fun plausible(s: String?): Long? {
        val v = Rupees.toPaise(s?.replace(",", "")?.trim()) ?: return null
        return if (v in 1..MAX_PAISE) v else null
    }

    private val amountRe = Rx.of("(?:₹|\\b(?:Rs\\.?|INR))\\s*([\\d,]+(?:\\.\\d{1,2})?)", ignoreCase = true)
    private val upiRe = Rx.of("\\b([\\w.\\-]{2,}@[a-z][a-z0-9]{1,})\\b", ignoreCase = true)
    private val utrRe = Rx.of("UTR\\s*(?:No\\.?|Number)?\\s*[:\\-]?\\s*(\\d{9,})", ignoreCase = true)
    private val accountRe = Rx.of("(?:Debited from|Credited to|Paid by|Received in|Credited in)\\s*[:\\-]?\\s*[\\n ]*([Xx*•]{2,}\\s*\\d{3,8})", ignoreCase = true)

    private fun digitsOf(s: String?) = (s ?: "").filter { it.isDigit() }
    private fun tidy(s: String) = s.replace(Rx.WHITESPACE_RUN, " ").trim()

    private fun titleCase(s: String): String =
        if (s == s.uppercase() || s == s.lowercase()) s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.take(1).uppercase() + it.drop(1).lowercase() } else s

    private fun time(h: String?, m: String?, ap: String?): Pair<Int, Int>? {
        if (h == null || m == null) return null
        var hour = h.toInt()
        val minute = m.toInt()
        if (ap != null) {
            val pm = ap.lowercase() == "pm"
            if (hour == 12) hour = 0
            if (pm) hour += 12
        }
        return if (hour > 23 || minute > 59) null else hour to minute
    }

    private fun dateOf(year: Int, mo: Int, day: Int, h: Int, mi: Int): LocalDateTime? =
        try { LocalDateTime.of(year, mo, day, h, mi) } catch (e: Exception) { null }

    /** The amount written with ₹ / Rs / INR. A payment page shows it more than once, so the value seen most often wins; the first seen decides a tie. */
    fun amountWithRupeeSign(t: String): Long? {
        val counts = LinkedHashMap<Long, Int>()
        for (m in amountRe.findAll(t)) {
            val v = plausible(m.groupValues[1]) ?: continue
            counts[v] = (counts[v] ?: 0) + 1
        }
        if (counts.isEmpty()) return null
        var best = counts.keys.first()
        for ((v, c) in counts) if (c > counts.getValue(best)) best = v
        return best
    }

    /** True when the signed amounts disagree: more than one value and none seen twice. Then no reading can be trusted. */
    private fun amountsDisagree(t: String): Boolean {
        val counts = HashMap<Long, Int>()
        for (m in amountRe.findAll(t)) plausible(m.groupValues[1])?.let { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.size > 1 && counts.values.all { it < 2 }
    }

    private val looseRe = Rx.of("^[^\\dA-Za-z]?[A-Za-z]?\\s*(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d{1,7}(?:\\.\\d{1,2})?)\\z")
    private val afterLabelRe = Rx.of("^(message|phonepe transaction id|utr|bank reference|order id)", ignoreCase = true)
    private val sevenRe = Regex("^\\d{4,}(?:\\.\\d{1,2})?\\z")

    /** The ₹ sign read as another character ("T950", "?950"): lines that are only a short number. A bare run of 4+ digits starting with 7 has its 7 dropped. */
    private fun amountFromLooseLines(lines: List<String>): Long? {
        val counts = LinkedHashMap<Long, Int>()
        for (i in lines.indices) {
            if (i > 0 && afterLabelRe.containsMatchIn(lines[i - 1])) continue
            var raw = looseRe.find(lines[i])?.groupValues?.get(1)
            if (raw != null && raw.startsWith("7") && sevenRe.matches(raw)) raw = raw.substring(1)
            plausible(raw)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
        if (counts.isEmpty()) return null
        var best = counts.keys.first()
        for ((v, c) in counts) if (c > counts.getValue(best)) best = v
        return best
    }

    // ---------------------------------------------------------------- PhonePe screenshot
    private val shotDateRe = Rx.of("(\\d{1,2}):(\\d{2})\\s*([ap]m)?\\s+on\\s+(\\d{1,2})\\s+([A-Za-z]{3})[a-z]*\\s+(\\d{4})", ignoreCase = true)
    private val avatarRe = Regex("^[A-Z]{1,3}\\z")
    private val moneyOnlyRe = Rx.of("^(?:₹|Rs\\.?|INR)?\\s*[\\d,]+(?:\\.\\d+)?\\z", ignoreCase = true)
    private val labelRe = Rx.of("^(transfer details|message|phonepe transaction id|debited from|credited to|paid to|received from|utr|powered by|transaction (?:successful|failed|pending)|bank reference)", ignoreCase = true)

    private fun linesOf(t: String) = t.split('\n').map { tidy(it) }.filter { it.isNotEmpty() }

    /** Reads a PhonePe "Transaction Successful" screen. Null when it is not one or when the amount or date cannot be found; nothing is guessed. */
    fun parsePhonePeScreenshot(text: String): UpiEntry? {
        val t = text.replace("\r", "")
        if (!Rx.of("transaction successful|payment successful|money sent|paid to|received from", ignoreCase = true).containsMatchIn(t)) return null
        if (Rx.of("transaction (?:failed|pending)|payment (?:failed|pending)", ignoreCase = true).containsMatchIn(t) && !Rx.of("successful", ignoreCase = true).containsMatchIn(t)) return null

        val dm = shotDateRe.find(t) ?: return null
        val mo = month(dm.groupValues[5]) ?: return null
        val tm = time(dm.groupValues[1], dm.groupValues[2], dm.groupValues[3].ifEmpty { null })
        val date = dateOf(dm.groupValues[6].toInt(), mo, dm.groupValues[4].toInt(), tm?.first ?: 0, tm?.second ?: 0) ?: return null

        val lines = linesOf(t)
        var amount = amountWithRupeeSign(t)
        val unconfirmed = amount == null || amountsDisagree(t)
        if (amount == null) amount = amountFromLooseLines(lines)
        if (amount == null || amount <= 0) return null

        val received = Rx.of("received from|credited to", ignoreCase = true).containsMatchIn(t) && !Rx.of("paid to|debited from", ignoreCase = true).containsMatchIn(t)

        var party = ""
        val partyRe = Rx.of("^(?:paid to|received from)\\s*(.*)\\z", ignoreCase = true)
        var i = 0
        while (i < lines.size && party.isEmpty()) {
            val m = partyRe.find(lines[i])
            if (m != null) {
                val cands = buildList { if (m.groupValues[1].isNotEmpty()) add(m.groupValues[1]); addAll(lines.drop(i + 1).take(5)) }
                for (c0 in cands) {
                    val c = c0.replace(amountRe, "").trim()
                    if (c.isEmpty() || avatarRe.matches(c) || moneyOnlyRe.matches(c) || labelRe.containsMatchIn(c)) continue
                    if (upiRe.containsMatchIn(c) && c.contains('@') && !c.contains(' ')) continue
                    party = c
                    break
                }
            }
            i++
        }

        val upi = upiRe.find(t)?.groupValues?.get(1) ?: ""
        var message = ""
        for (j in lines.indices) {
            if (Rx.of("^message\\z", ignoreCase = true).matches(lines[j]) && j + 1 < lines.size) {
                val next = lines[j + 1]
                if (!labelRe.containsMatchIn(next)) message = next
                break
            }
        }
        val txn = Rx.of("PhonePe Transaction ID\\s*[:\\-]?\\s*\\n?\\s*([A-Z0-9]{10,})", ignoreCase = true).find(t)?.groupValues?.get(1)
            ?: Regex("\\b(T\\d{16,})\\b").find(t)?.groupValues?.get(1) ?: ""
        return UpiEntry(
            amountPaise = amount, isExpense = !received, party = titleCase(party), date = date, txnId = txn,
            utr = utrRe.find(t)?.groupValues?.get(1) ?: "", upiId = upi, message = message,
            accountDigits = digitsOf(accountRe.find(t)?.groupValues?.get(1)), amountUnconfirmed = unconfirmed,
        )
    }

    // ---------------------------------------------------------------- other apps
    private val anyDateRe = Rx.of("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?[ \\t\\-/]*([A-Za-z]{3})[a-z]*\\.?,?[ \\t]*['’]?[ \\t]*(\\d{4}|\\d{2})\\b")
    private val anyTimeRe = Rx.of("(\\d{1,2}):(\\d{2})\\s*([ap]\\.?m)?", ignoreCase = true)
    private val anyRefRe = Rx.of("(?:UPI\\s*Ref(?:erence)?\\s*(?:No\\.?|Number)?|UPI\\s*transaction\\s*ID|Transaction\\s*ID)[\\s:\\-]*(\\d{9,})", ignoreCase = true)
    private val maskedAccountRe = Rx.of("[Xx*•]{2,}\\s*(\\d{2,8})\\b")
    private val bankDashAccountRe = Rx.of("\\bbank\\s*[-–—]\\s*(\\d{4})\\b", ignoreCase = true)

    private fun appOf(t: String): String {
        val l = t.lowercase()
        return when {
            "paytm" in l -> "Paytm"
            "google pay" in l || "google transaction" in l -> "Google Pay"
            "bhim" in l -> "BHIM"
            "mobikwik" in l -> "MobiKwik"
            Rx.of("\\bcred\\b", ignoreCase = true).containsMatchIn(t) -> "CRED"
            "amazon pay" in l -> "Amazon Pay"
            else -> "UPI app"
        }
    }

    private val stopsRe = Rx.of("^(upi|from|completed|paid|banking|transaction|remarks|debited|message|google)", ignoreCase = true)
    private fun stopsName(l: String) = '@' in l || amountRe.containsMatchIn(l) || moneyOnlyRe.matches(l) || stopsRe.containsMatchIn(l)

    private fun partyOfOtherApp(lines: List<String>): String {
        for (i in lines.indices) if (Rx.of("^banking name\\z", ignoreCase = true).matches(lines[i]) && i + 1 < lines.size) return lines[i + 1]
        val toRe = Rx.of("^To(?=[\\s:]|\\z)\\s*:?\\s*(.*)\\z")
        for (i in lines.indices) {
            val m = toRe.find(lines[i]) ?: continue
            if (Rx.of("^upi\\b", ignoreCase = true).containsMatchIn(m.groupValues[1])) continue
            val parts = ArrayList<String>()
            if (m.groupValues[1].isNotEmpty()) parts.add(m.groupValues[1])
            var extra = 0
            var j = i + 1
            while (j < lines.size && extra < 3) {
                val l = lines[j]
                j++
                if (avatarRe.matches(l)) continue
                if (stopsName(l)) break
                parts.add(l); extra++
            }
            val name = parts.joinToString(" ").trim()
            if (name.isNotEmpty()) return name
        }
        var last = ""
        for (l in lines) {
            if (amountRe.containsMatchIn(l) || moneyOnlyRe.matches(l)) break
            if (Regex("[A-Za-z]{3,}").containsMatchIn(l) && '@' !in l) last = l
        }
        return last.replace(Regex("[.…]+\\z"), "").trim()
    }

    fun parseOtherAppScreenshot(text: String): UpiEntry? {
        val t = text.replace("\r", "")
        val success = Rx.of("successful|completed|\\bpaid\\b|payment (?:received|sent)", ignoreCase = true).containsMatchIn(t)
        val failed = Rx.of("failed|declined|reversed|pending", ignoreCase = true).containsMatchIn(t)
        if (!success || (failed && !Rx.of("successful|completed", ignoreCase = true).containsMatchIn(t))) return null
        if (!Rx.of("\\bupi\\b|@", ignoreCase = true).containsMatchIn(t)) return null

        var day: LocalDate? = null
        for (m in anyDateRe.findAll(t)) {
            val mo = month(m.groupValues[2]) ?: continue
            var year = m.groupValues[3].toInt()
            if (year < 100) year += 2000
            day = try { LocalDate.of(year, mo, m.groupValues[1].toInt()) } catch (e: Exception) { null }
            if (day != null) break
        }
        if (day == null) return null
        var tm: Pair<Int, Int>? = null
        for (m in anyTimeRe.findAll(t)) {
            if (m.groupValues[3].isEmpty()) continue
            tm = time(m.groupValues[1], m.groupValues[2], m.groupValues[3].replace(".", ""))
            if (tm != null) break
        }
        val date = day.atTime(tm?.first ?: 0, tm?.second ?: 0)

        val lines = linesOf(t)
        var amount = amountWithRupeeSign(t)
        val unconfirmed = amount == null || amountsDisagree(t)
        if (amount == null) amount = amountFromLooseLines(lines)
        if (amount == null || amount <= 0) return null

        val received = Rx.of("received from|money received", ignoreCase = true).containsMatchIn(t)
        val account = digitsOf((maskedAccountRe.find(t) ?: bankDashAccountRe.find(t))?.groupValues?.get(1))
        return UpiEntry(
            amountPaise = amount, isExpense = !received, party = titleCase(partyOfOtherApp(lines)), date = date,
            // two-column pages (BHIM) put other labels between "Transaction ID" and the number
            utr = anyRefRe.find(t)?.groupValues?.get(1) ?: Rx.of("^(\\d{12})\\z", multiline = true).find(t)?.groupValues?.get(1) ?: "",
            upiId = upiRe.find(t)?.groupValues?.get(1) ?: "", accountDigits = account, app = appOf(t), amountUnconfirmed = unconfirmed,
        )
    }

    /** PhonePe's own page first, then the other apps' pages. */
    fun parseScreenshot(text: String): UpiEntry? = parsePhonePeScreenshot(text) ?: parseOtherAppScreenshot(text)

    /**
     * The best of several readings of the same picture (the original and its prepared copies). Readings with a rupee sign beat those without;
     * among those the amount seen most often wins, and on a tie the earlier reading.
     */
    fun chooseBestReading(readings: List<UpiEntry?>): UpiEntry? {
        val all = readings.filterNotNull()
        if (all.isEmpty()) return null
        val confirmed = all.filter { !it.amountUnconfirmed }
        val pool = confirmed.ifEmpty { all }
        val votes = HashMap<Long, Int>()
        for (r in pool) votes[r.amountPaise] = (votes[r.amountPaise] ?: 0) + 1
        var best = pool.first()
        for (r in pool) if (votes.getValue(r.amountPaise) > votes.getValue(best.amountPaise)) best = r
        return best
    }

    // ---------------------------------------------------------------- statement text (PDF)
    private val stmtDateRe = Rx.of("(?:^|\\n)\\s*(?:([A-Za-z]{3})[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})|(\\d{1,2})\\s+([A-Za-z]{3})[a-z]*,?\\s+(\\d{4}))")
    private val stmtTimeRe = Rx.of("(\\d{1,2}):(\\d{2})\\s*([AP]M)", ignoreCase = true)
    private val stmtTypeRe = Regex("\\b(DEBIT|CREDIT)\\b")
    private val stmtPartyRe = Rx.of("(Paid to|Received from|Payment to|Paid for|Refund from|Money sent to|Mobile recharged|Bill paid|Added to wallet|Sent to)\\s*(.*)", ignoreCase = true)

    /** Reads the text of a PhonePe statement. Each payment starts with its date; type, amount, "Paid to ...", ids and the account follow in any order. */
    fun parseStatementText(text: String): List<UpiEntry> {
        val t = "\n" + text.replace("\r", "")
        val starts = stmtDateRe.findAll(t).toList()
        val out = ArrayList<UpiEntry>()
        for (i in starts.indices) {
            val s = starts[i]
            val block = t.substring(s.range.first, if (i + 1 < starts.size) starts[i + 1].range.first else t.length)
            val g = s.groupValues
            val (mo, day, year) = if (g[1].isNotEmpty()) Triple(month(g[1]), g[2].toInt(), g[3].toInt()) else Triple(month(g[5]), g[4].toInt(), g[6].toInt())
            if (mo == null) continue
            val tmM = stmtTimeRe.find(block)
            val tm = tmM?.let { time(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            val date = dateOf(year, mo, day, tm?.first ?: 0, tm?.second ?: 0) ?: continue
            if (Rx.of("\\bFAILED\\b|\\bPENDING\\b", ignoreCase = true).containsMatchIn(block)) continue

            val ty = stmtTypeRe.find(block)
            val amount = amountRe.findAll(block).firstNotNullOfOrNull { plausible(it.groupValues[1]) }
                ?: plausible(Rx.of("\\b(?:DEBIT|CREDIT)\\b\\s*[^\\d\\s]?\\s*([\\d,]+(?:\\.\\d{1,2})?)").find(block)?.groupValues?.get(1))
            if (amount == null || amount <= 0) continue

            val pm = stmtPartyRe.find(block)
            val prefix = (pm?.groupValues?.get(1) ?: "").lowercase()
            val isExpense = if (ty != null) ty.groupValues[1] == "DEBIT" else !(prefix.startsWith("received") || prefix.startsWith("refund") || prefix.startsWith("added"))
            var party = ""
            if (pm != null) {
                party = pm.groupValues[2].split('\n').first()
                    .replace(Regex("\\b(DEBIT|CREDIT)\\b.*\\z"), "")
                    .replace(Rx.of("Transaction ID.*\\z", ignoreCase = true), "")
                    .replace(amountRe, "").trim()
            }
            if (party.isEmpty() && pm != null) party = pm.groupValues[1]
            out.add(
                UpiEntry(
                    amountPaise = amount, isExpense = isExpense, party = titleCase(tidy(party)), date = date,
                    txnId = Rx.of("Transaction ID\\s*[:\\-]?\\s*([A-Z0-9]{8,})", ignoreCase = true).find(block)?.groupValues?.get(1) ?: "",
                    utr = utrRe.find(block)?.groupValues?.get(1) ?: "", accountDigits = digitsOf(accountRe.find(block)?.groupValues?.get(1)),
                ),
            )
        }
        return out
    }

    // ---------------------------------------------------------------- statement CSV
    private fun splitCsv(text: String): List<List<String>> {
        val firstLine = text.split('\n').firstOrNull { it.isNotBlank() } ?: ""
        val tabs = firstLine.split('\t').size; val commas = firstLine.split(',').size; val semis = firstLine.split(';').size
        val delim = if (tabs > commas) '\t' else if (semis > commas) ';' else ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quoted -> if (c == '"') { if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else quoted = false } else cell.append(c)
                c == '"' -> quoted = true
                c == delim -> { row.add(cell.toString()); cell.setLength(0) }
                c == '\n' -> { row.add(cell.toString()); cell.setLength(0); if (row.any { it.isNotBlank() }) rows.add(row); row = ArrayList() }
                else -> cell.append(c)
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    private fun csvDate(raw: String): LocalDateTime? {
        val s = tidy(raw)
        var h = 0; var mi = 0
        Rx.of("(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*([AaPp][Mm])?").find(s)?.let { t ->
            time(t.groupValues[1], t.groupValues[2], t.groupValues[3].ifEmpty { null })?.let { h = it.first; mi = it.second }
        }
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.let { return dateOf(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt(), h, mi) }
        Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})").find(s)?.let {
            var y = it.groupValues[3].toInt(); if (y < 100) y += 2000
            return dateOf(y, it.groupValues[2].toInt(), it.groupValues[1].toInt(), h, mi) // day first, as in India
        }
        Rx.of("([A-Za-z]{3})[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})").find(s)?.let { m ->
            month(m.groupValues[1])?.let { return dateOf(m.groupValues[3].toInt(), it, m.groupValues[2].toInt(), h, mi) }
        }
        Rx.of("(\\d{1,2})[\\s\\-/]+([A-Za-z]{3})[a-z]*[\\s\\-/,]+(\\d{4})").find(s)?.let { m ->
            month(m.groupValues[2])?.let { return dateOf(m.groupValues[3].toInt(), it, m.groupValues[1].toInt(), h, mi) }
        }
        return null
    }

    private fun col(header: List<String>, names: List<Regex>): Int {
        for (n in names) for (i in header.indices) if (n.containsMatchIn(header[i])) return i
        return -1
    }

    private fun paiseOf(raw: String) = plausible(raw.filter { it.isDigit() || it == '.' || it == ',' })

    /** Reads a PhonePe statement saved as CSV. Columns are found by name, since the exact layout is not fixed. */
    fun parseCsv(text: String): List<UpiEntry> {
        val rows = splitCsv(text.removePrefix("\uFEFF"))
        if (rows.size < 2) return emptyList()
        var headerAt = -1
        for (i in 0 until minOf(rows.size, 15)) {
            val j = rows[i].joinToString("|").lowercase()
            if ("date" in j && ("amount" in j || "debit" in j || "credit" in j)) { headerAt = i; break }
        }
        if (headerAt < 0) return emptyList()
        val h = rows[headerAt].map { it.trim().lowercase() }
        val cDate = col(h, listOf(Regex("^date"), Regex("date")))
        val cTime = col(h, listOf(Regex("^time")))
        val cDetails = col(h, listOf(Regex("details|description|narration|merchant|paid to|particulars|name")))
        val cType = col(h, listOf(Regex("^type\\z|dr\\s*/?\\s*cr|debit.?credit|transaction type|cr/dr")))
        val cAmount = col(h, listOf(Regex("^amount|amount \\(")))
        val cDebit = col(h, listOf(Regex("^debit|withdraw|paid out|amount debited")))
        val cCredit = col(h, listOf(Regex("^credit|deposit|paid in|amount credited")))
        val cTxn = col(h, listOf(Regex("transaction id|txn id|phonepe")))
        val cUtr = col(h, listOf(Regex("utr|bank reference|reference")))
        val cStatus = col(h, listOf(Regex("status")))
        val cAccount = col(h, listOf(Regex("account|paid by|bank")))
        if (cDate < 0 || (cAmount < 0 && cDebit < 0 && cCredit < 0)) return emptyList()
        fun at(r: List<String>, i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""

        val out = ArrayList<UpiEntry>()
        for (r in rows.drop(headerAt + 1)) {
            val status = at(r, cStatus).lowercase()
            if (status.isNotEmpty() && "success" !in status && "complete" !in status) continue
            var date = csvDate(at(r, cDate)) ?: continue
            if (cTime >= 0) {
                val t = Rx.of("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").find(at(r, cTime))
                val tm = t?.let { time(it.groupValues[1], it.groupValues[2], it.groupValues[3].ifEmpty { null }) }
                if (tm != null) date = date.withHour(tm.first).withMinute(tm.second)
            }
            var amount: Long? = null
            var expense: Boolean? = null
            if (cDebit >= 0 || cCredit >= 0) {
                val d = paiseOf(at(r, cDebit)); val c = paiseOf(at(r, cCredit))
                if (d != null && d > 0) { amount = d; expense = true } else if (c != null && c > 0) { amount = c; expense = false }
            }
            if (amount == null && cAmount >= 0) {
                val raw = at(r, cAmount)
                amount = paiseOf(raw)
                val ty = at(r, cType).lowercase()
                if ("debit" in ty || ty == "dr" || "paid" in ty || "sent" in ty) expense = true
                if ("credit" in ty || ty == "cr" || "received" in ty) expense = false
                if (expense == null && raw.trim().startsWith("-")) expense = true
            }
            if (amount == null || amount <= 0) continue
            var details = at(r, cDetails)
            val isExpense = expense ?: !Rx.of("^(received|refund)", ignoreCase = true).containsMatchIn(details)
            details = details.replace(Rx.of("^(paid to|received from|payment to|sent to|refund from)\\s*", ignoreCase = true), "")
            val utrDigits = digitsOf(at(r, cUtr))
            out.add(
                UpiEntry(
                    amountPaise = amount, isExpense = isExpense, party = titleCase(tidy(details)), date = date, txnId = at(r, cTxn),
                    utr = if (utrDigits.length >= 9) utrDigits else "", accountDigits = digitsOf(at(r, cAccount)),
                ),
            )
        }
        return out
    }

    // ---------------------------------------------------------------- to review rows
    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun noteOf(e: UpiEntry): String {
        val parts = ArrayList<String>()
        parts.add(e.app)
        if (e.amountUnconfirmed) parts.add("CHECK THE AMOUNT (no ₹ sign was read; a leading 7 is taken to be the ₹ sign)")
        if (e.message.isNotEmpty()) parts.add(e.message)
        if (e.utr.isNotEmpty()) parts.add("UTR ${e.utr}")
        if (e.txnId.isNotEmpty()) parts.add("Txn ${e.txnId}")
        return parts.joinToString(" · ")
    }

    /** One review row. The id is made from the transaction id / UTR, so the same payment is recognised when imported twice. */
    fun toRow(e: UpiEntry, known: KnownAccounts, zone: ZoneId): IntakeRow {
        val d = e.date
        val ref = when {
            e.txnId.isNotEmpty() -> e.txnId
            e.utr.isNotEmpty() -> e.utr
            else -> "${d.year}${two(d.monthValue)}${two(d.dayOfMonth)}${two(d.hour)}${two(d.minute)}-${e.amountPaise}-${PingExport.stableHash(e.party)}"
        }
        val k = known.byDigits(e.accountDigits)
        val merchant = e.party
        val last4 = e.accountDigits.takeLast(4)
        return IntakeRow(
            sourceId = "pp-$ref", contentKey = "pp-${e.utr.ifEmpty { ref }}", sender = e.app,
            dateMs = d.atZone(zone).toInstant().toEpochMilli(), amountPaise = e.amountPaise, isExpense = e.isExpense,
            title = merchant.ifEmpty { if (e.isExpense) "Payment" else "Money received" }, merchant = merchant,
            categoryLabel = SmsParser.suggestCategoryLabel(e.isExpense, merchant),
            accountName = k?.name ?: if (e.accountDigits.isEmpty()) null else "Account ••$last4",
            walletName = if (k != null && k.wallet.isNotEmpty()) k.wallet else "Unassigned",
            closed = k?.closed ?: false, tickByDefault = !e.amountUnconfirmed, knownAccount = k != null,
            extraNote = noteOf(e), amountUnconfirmed = e.amountUnconfirmed,
        )
    }

    /** All payments as review rows, newest first, the same payment only once. */
    fun toIntake(entries: List<UpiEntry>, known: KnownAccounts, zone: ZoneId): PingIntake {
        val seen = HashSet<String>()
        val rows = ArrayList<IntakeRow>()
        var skipped = 0
        for (e in entries) {
            val r = toRow(e, known, zone)
            if (!seen.add(r.sourceId)) { skipped++; continue }
            rows.add(r)
        }
        rows.sortByDescending { it.dateMs }
        return PingIntake(rows, skipped, rows.lastOrNull()?.dateMs, rows.firstOrNull()?.dateMs, emptyList(), emptyList())
    }
}
