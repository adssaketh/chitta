package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of a statement: a fund in a folio with the statement's own units, cost, price and value. Money is in paise. */
data class CasHolding(
    val folio: String,
    val name: String,
    val investedPaise: Long,
    val units: BigDecimal,
    val navDate: LocalDate,
    val nav: BigDecimal,
    val valuePaise: Long,
)

sealed interface CasReading {
    /** [holdings] has only funds with units; [exitedRows] counts the zero-unit rows left out. The row values were checked against the statement's own total. */
    data class Ready(val asOf: LocalDate?, val holdings: List<CasHolding>, val exitedRows: Int) : CasReading
    data class Refused(val reason: String) : CasReading
}

/**
 * Reads the text of a CAMS / KFintech / MFCentral "Consolidated Account Summary" PDF (the SoA holdings table). Plain Kotlin, tested with
 * invented text in the order PDFBox gives (the table's cells in the order the report wrote them).
 *
 * The statement carries no ISIN, so a fund is told from another by its folio and name. Nothing is guessed: if no row is recognised, or the
 * rows do not add up to the statement's own Total, the file is refused and nothing changes. Name, PAN, address and phone in the text are
 * never read into a result.
 */
object CasSummary {
    private const val MONEY = """[\d,]+\.\d{2}"""
    private val dateFmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    /** invested, market value, gain, (pct), units, NAV date, folio, NAV: the cells of one row once the text is joined with spaces. */
    private val row = Rx.of(
        """($MONEY)\s+($MONEY)\s+\(?-?$MONEY\)?\s+\([+\-]?[\d.,]+%\)\s+([\d,]+\.\d{3})\s+(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(\S+)\s+([\d,]+\.\d{2,4})(?=\s|\z)""",
    )
    private val total = Rx.of("""\bTotal\s+($MONEY)""")
    private val asOfRe = Rx.of("""As on Date:\s*(\d{1,2}-[A-Za-z]{3}-\d{4})""", ignoreCase = true)
    private val headerMarker = Rx.of("""Folio\s+No\.?\s+NAV""")
    private val footer = Rx.of("""MFCentral\S*\s+Page\s+\d+\s+of\s+\d+|Page\s+\d+\s+of\s+\d+""")
    private val noise = listOf(
        "Consolidated Account Summary", "SoA Holdings", "Demat Holdings", "Invested Value", "Market Value", "Gain/Loss", "(Absolute)", "(INR)",
        "Balance Units", "NAV Date", "Scheme Details",
    )

    private fun paise(text: String): Long = BigDecimal(text.replace(",", "")).movePointRight(2).longValueExact()

    private fun cleanName(segment: String): String {
        var s = segment
        headerMarker.findAll(s).lastOrNull()?.let { s = s.substring(it.range.last + 1) }
        s = footer.replace(s, " ")
        s = asOfRe.replace(s, " ")
        s = Regex("""\(\s*As on Date[^)]*\)""", RegexOption.IGNORE_CASE).replace(s, " ")
        noise.forEach { s = s.replace(it, " ") }
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    fun read(text: String): CasReading {
        val flat = text.replace(' ', ' ').replace(Regex("""\s+"""), " ")
        val matches = row.findAll(flat).toList()
        if (matches.isEmpty()) return CasReading.Refused("This does not look like a CAMS, KFintech or MFCentral account summary: no holdings table was found. Nothing was changed.")
        val all = ArrayList<CasHolding>()
        var cursor = 0
        for (m in matches) {
            val name = cleanName(flat.substring(cursor, m.range.first))
            cursor = m.range.last + 1
            val date = try { LocalDate.parse(m.groupValues[4], dateFmt) } catch (e: Exception) {
                return CasReading.Refused("A date in the table could not be read (${m.groupValues[4]}). Nothing was changed.")
            }
            if (name.isEmpty()) return CasReading.Refused("A fund name could not be read. Nothing was changed.")
            all += CasHolding(
                folio = m.groupValues[5], name = name, investedPaise = paise(m.groupValues[1]),
                units = BigDecimal(m.groupValues[3].replace(",", "")), navDate = date, nav = BigDecimal(m.groupValues[6].replace(",", "")),
                valuePaise = paise(m.groupValues[2]),
            )
        }
        // The statement's own Total must equal the sum of the rows, or something was misread.
        val stated = total.find(flat, cursor)?.groupValues?.get(1)?.let { paise(it) }
        if (stated != null && stated != all.sumOf { it.valuePaise }) {
            return CasReading.Refused("The rows add up to a different figure than the statement's Total, so something was misread. Nothing was changed.")
        }
        val asOf = asOfRe.find(flat)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() }
        val held = all.filter { it.units.signum() > 0 }
        return CasReading.Ready(asOf, held, all.size - held.size)
    }
}

/** What an import would do to the funds already in the book. Only funds from the statement source are ever touched. */
data class CasPlan(
    val add: List<MutualFund>,
    val update: List<MutualFund>,
    /** Statement-sourced funds no longer in the file with units: removed (their old copies go into Undo). */
    val remove: List<MutualFund>,
    /** Rows left out because a fund with the same name was typed by hand: never counted twice. */
    val leftOutAsTypedByHand: List<CasHolding>,
)

object CasImport {
    fun key(folio: String, name: String): String = folio.trim().lowercase() + "|" + normalName(name)

    /** Lower case, letters and digits only: "Fund - Direct Plan Growth" and "fund direct plan growth" are one name. */
    fun normalName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    fun plan(existing: List<MutualFund>, holdings: List<CasHolding>, importedAtMs: Long, newId: () -> String): CasPlan {
        val mutual = existing.filter { !it.isNps }
        val fromCas = mutual.filter { it.source == MutualFund.SOURCE_CAS }.associateBy { key(it.folio, it.name) }
        val byHand = mutual.filter { it.source != MutualFund.SOURCE_CAS }.map { normalName(it.name) }.toSet()
        val add = ArrayList<MutualFund>(); val update = ArrayList<MutualFund>(); val left = ArrayList<CasHolding>()
        val seen = HashSet<String>()
        for (h in holdings) {
            if (normalName(h.name) in byHand) { left += h; continue }
            val k = key(h.folio, h.name)
            seen += k
            val old = fromCas[k]
            val fund = MutualFund(
                id = old?.id ?: newId(), name = h.name, isin = old?.isin ?: "", schemeCode = old?.schemeCode ?: "",
                units = h.units.stripTrailingZeros().toPlainString(), investedPaise = h.investedPaise, source = MutualFund.SOURCE_CAS,
                unitsAsOfMs = importedAtMs, navText = h.nav.stripTrailingZeros().toPlainString(), navDate = h.navDate.toString(),
                note = old?.note ?: "", updatedAtMs = importedAtMs, folio = h.folio, groupId = "",
            )
            if (old == null) add += fund else update += fund
        }
        val remove = fromCas.filterKeys { it !in seen }.values.toList()
        return CasPlan(add, update, remove, left)
    }
}
