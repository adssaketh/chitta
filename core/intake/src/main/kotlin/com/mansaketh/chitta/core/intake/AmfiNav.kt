package com.mansaketh.chitta.core.intake

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One scheme line of AMFI's public NAV file. [nav] is exact decimal (AMFI gives up to 4 places); never a Double. */
data class AmfiScheme(
    val code: String,
    /** The ISINs on the line (payout/growth first, reinvestment second), without the "-" placeholders. */
    val isins: List<String>,
    val name: String,
    val plan: String,
    val option: String,
    val nav: BigDecimal,
    val date: LocalDate,
)

sealed interface AmfiReading {
    /** [skipped] counts scheme lines left out (no price such as "N.A.", or an unreadable date). */
    data class Ready(val schemes: List<AmfiScheme>, val skipped: Int, val newestDate: LocalDate) : AmfiReading
    data class Refused(val reason: String) : AmfiReading
}

/**
 * Reads AMFI's "NAVAll" file (https://portal.amfiindia.com/spages/NAVAll.txt). Plain Kotlin.
 *
 * The layout was read from the live file on 14 Nov 2026: semicolon separated; a header line `Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div
 * Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date`; then blocks of a category line, a fund-house line and scheme lines (the category and
 * fund-house lines have no semicolon and are ignored); dates look like `13-Nov-2026`; CRLF line ends. AMFI has changed this file's layout before,
 * so columns are found by their header names, not by position, and a file whose header lacks the code, name, price or date column is refused
 * with nothing changed. An older layout without the Plan and Option columns still reads: those parts then stay inside the name.
 */
object AmfiNav {
    private val dateFmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    /** Fewer scheme lines than this means the file is not the NAV file (the real one has over 14,000). */
    const val MIN_SCHEMES = 1_000

    fun read(text: String, minSchemes: Int = MIN_SCHEMES): AmfiReading {
        val lines = text.lineSequence().map { it.trim('\r', '\n', ' ', '\uFEFF') }
        var header: List<String>? = null
        val schemes = ArrayList<AmfiScheme>()
        var skipped = 0
        var idx: Cols? = null
        for (line in lines) {
            if (idx == null) {
                if (line.startsWith("Scheme Code", ignoreCase = true) && ';' in line) {
                    header = line.split(';').map { it.trim() }
                    idx = Cols.of(header) ?: return AmfiReading.Refused("The price file has a different layout than expected (a column is missing). Nothing was changed.")
                }
                continue
            }
            if (';' !in line) continue
            val p = line.split(';')
            val code = p.getOrNull(idx.code)?.trim().orEmpty()
            if (code.isEmpty() || !code.all { it.isDigit() }) continue
            if (p.size < idx.minSize) { skipped++; continue }
            val nav = p[idx.nav].trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            val date = runCatching { LocalDate.parse(p[idx.date].trim(), dateFmt) }.getOrNull()
            if (nav == null || date == null) { skipped++; continue }
            schemes += AmfiScheme(
                code = code,
                isins = idx.isins.map { p[it].trim() }.filter { it.isNotEmpty() && it != "-" },
                name = p[idx.name].trim(),
                plan = idx.plan?.let { p[it].trim() }.orEmpty(),
                option = idx.option?.let { p[it].trim() }.orEmpty(),
                nav = nav, date = date,
            )
        }
        if (header == null) return AmfiReading.Refused("This does not look like the price file: no header line was found. Nothing was changed.")
        if (schemes.size < minSchemes) return AmfiReading.Refused("The price file had only ${schemes.size} readable schemes, so it was not used. Nothing was changed.")
        return AmfiReading.Ready(schemes, skipped, schemes.maxOf { it.date })
    }

    private class Cols(val code: Int, val name: Int, val nav: Int, val date: Int, val isins: List<Int>, val plan: Int?, val option: Int?) {
        val minSize = maxOf(code, name, nav, date, plan ?: 0, option ?: 0, isins.maxOrNull() ?: 0) + 1

        companion object {
            fun of(h: List<String>): Cols? {
                fun at(label: String) = h.indexOfFirst { it.equals(label, ignoreCase = true) }.takeIf { it >= 0 }
                val code = at("Scheme Code") ?: return null
                val name = at("Scheme Name") ?: return null
                val nav = at("Net Asset Value") ?: return null
                val date = at("Date") ?: return null
                return Cols(code, name, nav, date, h.indices.filter { h[it].startsWith("ISIN", ignoreCase = true) }, at("Plan"), at("Option"))
            }
        }
    }
}
