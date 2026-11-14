package com.mansaketh.chitta.core.intake

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One line of the ledger. [debit] and [credit] are paise; one of them is zero. */
data class LedgerLine(val date: LocalDate, val particulars: String, val voucher: String, val costCentre: String, val debit: Long, val credit: Long, val balance: Long)

/** A Zerodha Console ledger that has been read and checked. Money is paise. */
data class ZerodhaLedger(val from: LocalDate?, val to: LocalDate?, val opening: Long, val closing: Long, val lines: List<LedgerLine>) {
    /**
     * Rows for the CSV import: Date, Particulars, Voucher Type, Debit, Credit. The opening balance leads as its own row, dated the first day of the
     * ledger (or of its first line), so an account that starts empty ends on the file's closing balance. The person can untick it.
     */
    fun toCsvRows(): List<List<String>> {
        val rows = ArrayList<List<String>>()
        rows.add(listOf("Date", "Particulars", "Voucher Type", "Debit", "Credit"))
        val first = from ?: lines.firstOrNull()?.date
        if (opening != 0L && first != null) {
            rows.add(listOf(first.toString(), "Opening balance (from the ledger file)", "Opening", if (opening < 0) text(-opening) else "", if (opening > 0) text(opening) else ""))
        }
        for (l in lines) rows.add(listOf(l.date.toString(), l.particulars, l.voucher, if (l.debit != 0L) text(l.debit) else "", if (l.credit != 0L) text(l.credit) else ""))
        return rows
    }

    private fun text(paise: Long) = BigDecimal.valueOf(paise, 2).toPlainString()

    sealed interface Result {
        data class Ready(val ledger: ZerodhaLedger) : Result
        /** Not read, nothing guessed. [looksLikeLedger] is false when the sheet is some other kind of table. */
        data class Refused(val reason: String, val looksLikeLedger: Boolean) : Result
    }

    companion object {
        /** Zerodha shows up to four decimals; a running balance may differ from the sum of the rounded lines by a paisa. */
        private val TOLERANCE = BigDecimal("0.01")

        fun read(rows: List<List<String>>): Result {
            val headerIdx = rows.indexOfFirst { r ->
                val l = r.map { it.trim().lowercase() }
                "particulars" in l && "debit" in l && "credit" in l && l.any { it.contains("balance") }
            }
            if (headerIdx < 0) return Result.Refused("This sheet has no ledger table (Particulars, Debit, Credit, Net Balance).", false)
            val header = rows[headerIdx].map { it.trim().lowercase() }
            val cParticulars = header.indexOf("particulars")
            val cDate = header.indexOfFirst { it.contains("date") }
            val cCentre = header.indexOfFirst { it.contains("cost") }
            val cVoucher = header.indexOfFirst { it.contains("voucher") }
            val cDebit = header.indexOf("debit")
            val cCredit = header.indexOf("credit")
            val cBalance = header.indexOfFirst { it.contains("balance") }
            if (cDate < 0) return Result.Refused("The ledger table has no date column.", true)

            var from: LocalDate? = null
            var to: LocalDate? = null
            for (r in rows.take(headerIdx)) for (cell in r) {
                val m = Regex("""Ledger for .*? from (\d{4}-\d{2}-\d{2}) to (\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE).find(cell) ?: continue
                from = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull(); to = runCatching { LocalDate.parse(m.groupValues[2]) }.getOrNull()
            }

            fun cell(r: List<String>, i: Int) = if (i < 0) "" else r.getOrNull(i)?.trim().orEmpty()
            fun number(s: String): BigDecimal? = if (s.isBlank()) BigDecimal.ZERO else s.replace(",", "").toBigDecimalOrNull()
            fun paise(v: BigDecimal): Long = v.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()

            var opening: BigDecimal? = null
            var closing: BigDecimal? = null
            val lines = ArrayList<LedgerLine>()
            var running: BigDecimal? = null
            for (idx in headerIdx + 1 until rows.size) {
                val r = rows[idx]
                val label = cell(r, cParticulars).lowercase()
                val rowNo = idx + 1
                when {
                    r.all { it.isBlank() } -> continue
                    label.startsWith("opening bal") -> {
                        opening = number(cell(r, cBalance)) ?: return Result.Refused("Row $rowNo: the opening balance is not a number.", true)
                        running = opening
                    }
                    label.startsWith("closing bal") -> {
                        closing = number(cell(r, cBalance)) ?: return Result.Refused("Row $rowNo: the closing balance is not a number.", true)
                    }
                    else -> {
                        val dateText = cell(r, cDate)
                        if (dateText.isEmpty()) continue
                        val date = parseDate(dateText) ?: return Result.Refused("Row $rowNo: \"$dateText\" is not a date I can read.", true)
                        val debit = number(cell(r, cDebit)) ?: return Result.Refused("Row $rowNo: the debit is not a number.", true)
                        val credit = number(cell(r, cCredit)) ?: return Result.Refused("Row $rowNo: the credit is not a number.", true)
                        val balance = number(cell(r, cBalance)) ?: return Result.Refused("Row $rowNo: the net balance is not a number.", true)
                        val before = running ?: return Result.Refused("Row $rowNo comes before the opening balance.", true)
                        val expected = before - debit + credit
                        if ((expected - balance).abs() > TOLERANCE) {
                            return Result.Refused("Row $rowNo does not add up: the balance before it, less the debit, plus the credit, is ${expected.setScale(2, RoundingMode.HALF_EVEN)}, but the file says ${balance.setScale(2, RoundingMode.HALF_EVEN)}.", true)
                        }
                        running = balance
                        if (debit.signum() == 0 && credit.signum() == 0) continue
                        lines += LedgerLine(date, cell(r, cParticulars), cell(r, cVoucher), cell(r, cCentre), paise(debit), paise(credit), paise(balance))
                    }
                }
            }
            val open = opening ?: return Result.Refused("The ledger has no opening balance row.", true)
            val close = closing ?: return Result.Refused("The ledger has no closing balance row, so I cannot check it.", true)
            if (((running ?: open) - close).abs() > TOLERANCE) {
                return Result.Refused("The last balance (${(running ?: open).setScale(2, RoundingMode.HALF_EVEN)}) does not match the closing balance (${close.setScale(2, RoundingMode.HALF_EVEN)}).", true)
            }
            if (lines.isEmpty()) return Result.Refused("The ledger has no entries between the opening and closing balance.", true)
            return Result.Ready(ZerodhaLedger(from, to, paise(open), paise(close), lines))
        }

        /** yyyy-MM-dd as Zerodha writes it, dd-MM-yyyy or dd/MM/yyyy as a person might, or an Excel serial number. */
        internal fun parseDate(text: String): LocalDate? {
            val t = text.trim()
            Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(t)?.let { return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull() }
            Regex("""^(\d{2})[-/](\d{2})[-/](\d{4})$""").find(t)?.let { return runCatching { LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }.getOrNull() }
            val serial = t.toBigDecimalOrNull()?.toLong()
            if (serial != null && serial in 20_000..80_000) return LocalDate.of(1899, 12, 30).plusDays(serial)
            return null
        }
    }
}
