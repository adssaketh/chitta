package com.mansaketh.chitta.core.intake

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reads a mutual fund "Holdings report" kept as an Excel sheet (rows from [XlsxReader.firstSheet]): a title line "Holdings report as on dd-mm-yyyy",
 * a Summary with the report's own Total Invested value and Total Current value, then a table with Fund Name, ISIN, Total Units, Invested Value,
 * Current Value and Folio Number. Name and PAN on the sheet are never read into a result.
 *
 * The sheet has no price per unit, so the price shown is the current value divided by the units (four decimals) and the date is the report's date.
 * Nothing is guessed: a row that is not a number, or rows that do not add up to the report's own totals, refuse the whole file.
 */
object HoldingsReport {
    private val dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val asOnRe = Rx.of("""as\s+on\s+(\d{1,2}-\d{1,2}-\d{4})""", ignoreCase = true)

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    private fun decimal(s: String): BigDecimal? = s.trim().replace(",", "").toBigDecimalOrNull()
    private fun paise(d: BigDecimal): Long = d.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    /** True when the first rows look like this report, so one file picker can serve it. */
    fun looksLike(rows: List<List<String>>): Boolean =
        rows.take(20).any { r -> r.any { norm(it) == "fundname" } && r.any { norm(it) == "isin" } && r.any { norm(it) == "totalunits" } }

    fun read(rows: List<List<String>>): CasReading {
        val headerIndex = rows.indexOfFirst { r -> r.any { norm(it) == "fundname" } && r.any { norm(it) == "isin" } }
        if (headerIndex < 0) return CasReading.Refused("This sheet has no Fund Name and ISIN columns, so it is not a holdings report. Nothing was changed.")
        val header = rows[headerIndex].map(::norm)
        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val cName = col("fundname"); val cIsin = col("isin"); val cUnits = col("totalunits", "units"); val cInvested = col("investedvalue", "investedamount")
        val cCurrent = col("currentvalue", "currentamount", "marketvalue"); val cFolio = col("folionumber", "folio", "folionumber")
        if (listOf(cName, cIsin, cUnits, cInvested, cCurrent).any { it < 0 }) {
            return CasReading.Refused("The holdings report needs the columns Fund Name, ISIN, Total Units, Invested Value and Current Value. Nothing was changed.")
        }
        val asOf = rows.take(headerIndex).firstNotNullOfOrNull { r -> r.firstNotNullOfOrNull { asOnRe.find(it)?.groupValues?.get(1) } }
            ?.let { runCatching { LocalDate.parse(it.split('-').joinToString("-") { p -> p.padStart(2, '0') }, dateFmt) }.getOrNull() }
        // The report's own totals, from the Summary block above the table.
        fun summary(label: String): BigDecimal? = rows.take(headerIndex).firstNotNullOfOrNull { r ->
            val i = r.indexOfFirst { norm(it) == label }
            if (i >= 0) r.drop(i + 1).firstNotNullOfOrNull { decimal(it) } else null
        }
        val statedInvested = summary("totalinvestedvalue")
        val statedCurrent = summary("totalcurrentvalue")

        val date = asOf ?: return CasReading.Refused("The report's date (\"Holdings report as on ...\") was not found. Nothing was changed.")
        val all = ArrayList<CasHolding>()
        for (r in rows.drop(headerIndex + 1)) {
            if (r.all { it.isBlank() }) continue
            fun cell(i: Int) = r.getOrNull(i).orEmpty().trim()
            val name = cell(cName)
            if (name.isEmpty()) continue
            val units = decimal(cell(cUnits)); val invested = decimal(cell(cInvested)); val current = decimal(cell(cCurrent))
            if (units == null || invested == null || current == null || units.signum() < 0) {
                return CasReading.Refused("The row for \"$name\" has a unit or amount that is not a number. Nothing was changed.")
            }
            val nav = if (units.signum() > 0) current.divide(units, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            all += CasHolding(
                folio = if (cFolio >= 0) cell(cFolio) else "", name = name, investedPaise = paise(invested), units = units, navDate = date, nav = nav,
                valuePaise = paise(current), isin = cell(cIsin).uppercase(),
            )
        }
        if (all.isEmpty()) return CasReading.Refused("No fund rows were found under the table heading. Nothing was changed.")
        if (statedCurrent != null && paise(statedCurrent) != all.sumOf { it.valuePaise }) {
            return CasReading.Refused("The rows add up to a different current value than the report's own total, so something was misread. Nothing was changed.")
        }
        if (statedInvested != null && paise(statedInvested) != all.sumOf { it.investedPaise }) {
            return CasReading.Refused("The rows add up to a different invested value than the report's own total, so something was misread. Nothing was changed.")
        }
        val held = all.filter { it.units.signum() > 0 }
        return CasReading.Ready(date, held, all.size - held.size)
    }
}
