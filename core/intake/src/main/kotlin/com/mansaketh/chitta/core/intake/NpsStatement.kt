package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.model.NpsAccount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** One scheme row of the statement's "Investment Details" table: units now held, the latest price and the value at that price. */
data class NpsHolding(val name: String, val units: BigDecimal, val nav: BigDecimal, val valuePaise: Long)

sealed interface NpsReading {
    /**
     * [tier] is "Tier I" or "Tier II". [priceDate] is the date of "Investment Details as on". [netContributionsPaise] is Total Contribution minus
     * Total Withdrawal. Scheme values were checked against the statement's own total.
     */
    data class Ready(
        val tier: String,
        val priceDate: LocalDate,
        val contributionsPaise: Long,
        val withdrawalsPaise: Long,
        val statedValuePaise: Long,
        val schemes: List<NpsHolding>,
    ) : NpsReading {
        val netContributionsPaise: Long get() = contributionsPaise - withdrawalsPaise
    }

    data class Refused(val reason: String) : NpsReading
}

/**
 * Reads the text of a CRA (KFintech / Protean / CAMS) NPS "Transaction Statement". Only the summary row and the Investment Details table are
 * read: units now held per scheme, the latest price, and what has been paid in. The PRAN, name, address, nominees and every transaction line
 * are ignored and never reach a result. A picture-only statement has no text and is refused before this point.
 *
 * Nothing is guessed: if the summary row or the table is missing, or the scheme values do not add to the table's Total (or to the current
 * valuation), the file is refused and nothing changes.
 */
object NpsStatement {
    private const val MONEY = """[\d,]+\.\d{2}"""
    private val summary = Rx.of(
        """\d+\s+($MONEY)\s+($MONEY)\s+$MONEY\s+($MONEY)\s+-?$MONEY\s+Investment\s+Details\s+as\s+on\s+(\d{2})-(\d{2})-(\d{4})""",
    )
    private val tierRe = Rx.of("""Transaction\s+Statement\s*-\s*(Tier\s+I{1,2})""", ignoreCase = true)
    private val row = Rx.of("""([\d,]+\.\d{3,4})\s+([\d,]+\.\d{4})\s+($MONEY)(?:\s+-?\d+\.\d+%)?(?=\s|\z)""")
    private val header = Rx.of("""Scheme\s+Name\s+Total\s+Units\s+Latest\s+NAV\s+Value\s+at\s+NAV\s+XIRR""", ignoreCase = true)
    private val totalRe = Rx.of("""\bTotal\s+($MONEY)""")

    private fun paise(text: String): Long = BigDecimal(text.replace(",", "")).movePointRight(2).longValueExact()

    /** True when the text is an NPS statement of this kind, so one file picker can serve both statement types. */
    fun looksLike(text: String): Boolean {
        val flat = text.replace(Regex("""\s+"""), " ")
        return flat.contains("NATIONAL PENSION SYSTEM", ignoreCase = true) && flat.contains("Investment Details as on", ignoreCase = true)
    }

    fun read(text: String): NpsReading {
        val flat = text.replace(' ', ' ').replace(Regex("""\s+"""), " ")
        val s = summary.find(flat) ?: return NpsReading.Refused("The summary line (contributions, withdrawals, current valuation) was not found. Nothing was changed.")
        val priceDate = try { LocalDate.of(s.groupValues[6].toInt(), s.groupValues[5].toInt(), s.groupValues[4].toInt()) } catch (e: Exception) {
            return NpsReading.Refused("The statement's price date could not be read. Nothing was changed.")
        }
        val tier = tierRe.find(flat)?.groupValues?.get(1)?.replace(Regex("""\s+"""), " ")?.let { t -> if (t.endsWith("II", true)) "Tier II" else "Tier I" } ?: "Tier I"
        // The table sits after the summary line and ends at "Changes made during..." (or the end of the text).
        val from = s.range.last + 1
        val to = flat.indexOf("Changes made during", from).let { if (it < 0) flat.length else it }
        val section = flat.substring(from, to)
        val afterHeader = header.find(section)?.let { section.substring(it.range.last + 1) }
            ?: return NpsReading.Refused("The scheme table was not found. Nothing was changed.")
        val rows = row.findAll(afterHeader).toList()
        if (rows.isEmpty()) return NpsReading.Refused("No scheme rows were found in the table. Nothing was changed.")
        val schemes = ArrayList<NpsHolding>()
        var cursor = 0
        for (m in rows) {
            val name = afterHeader.substring(cursor, m.range.first).replace(Regex("""\s+"""), " ").trim()
            cursor = m.range.last + 1
            if (name.isEmpty()) return NpsReading.Refused("A scheme name could not be read. Nothing was changed.")
            schemes += NpsHolding(name, BigDecimal(m.groupValues[1].replace(",", "")), BigDecimal(m.groupValues[2].replace(",", "")), paise(m.groupValues[3]))
        }
        val statedValue = paise(s.groupValues[3])
        val tableTotal = totalRe.find(afterHeader, cursor)?.groupValues?.get(1)?.let { paise(it) }
        val sum = schemes.sumOf { it.valuePaise }
        if (sum != statedValue || (tableTotal != null && tableTotal != sum)) {
            return NpsReading.Refused("The scheme values do not add up to the statement's own total, so something was misread. Nothing was changed.")
        }
        return NpsReading.Ready(tier, priceDate, paise(s.groupValues[1]), paise(s.groupValues[2]), statedValue, schemes.filter { it.units.signum() > 0 })
    }
}

/** What an NPS statement import would do. Only schemes that came from a statement are ever replaced. */
data class NpsPlan(
    val account: NpsAccount,
    val accountIsNew: Boolean,
    val add: List<MutualFund>,
    val update: List<MutualFund>,
    val remove: List<MutualFund>,
    /** Schemes left out because a scheme of the same name was typed by hand in this account: never counted twice. */
    val leftOutAsTypedByHand: List<NpsHolding>,
)

object NpsImport {
    const val SOURCE = "nps-statement"
    const val ACCOUNT_PREFIX = "NPS "

    fun plan(
        accounts: List<NpsAccount>, schemes: List<MutualFund>, r: NpsReading.Ready, importedAtMs: Long, zone: ZoneId, newId: () -> String,
    ): NpsPlan {
        val accountName = ACCOUNT_PREFIX + r.tier
        val existing = accounts.firstOrNull { it.name.equals(accountName, ignoreCase = true) }
        val priceMs = r.priceDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val account = NpsAccount(
            id = existing?.id ?: newId(), name = existing?.name ?: accountName, netContributionsPaise = r.netContributionsPaise,
            asOfMs = priceMs, note = existing?.note ?: "", updatedAtMs = importedAtMs,
        )
        val own = schemes.filter { it.groupId == account.id }
        val fromStatement = own.filter { it.source == SOURCE }.associateBy { CasImport.normalName(it.name) }
        val byHand = own.filter { it.source != SOURCE }.map { CasImport.normalName(it.name) }.toSet()
        val add = ArrayList<MutualFund>(); val update = ArrayList<MutualFund>(); val left = ArrayList<NpsHolding>()
        val seen = HashSet<String>()
        for (h in r.schemes) {
            val k = CasImport.normalName(h.name)
            if (k in byHand) { left += h; continue }
            seen += k
            val old = fromStatement[k]
            val fund = MutualFund(
                id = old?.id ?: newId(), name = h.name, isin = "", schemeCode = "", units = h.units.stripTrailingZeros().toPlainString(), investedPaise = null,
                source = SOURCE, unitsAsOfMs = priceMs, navText = h.nav.stripTrailingZeros().toPlainString(), navDate = r.priceDate.toString(),
                note = old?.note ?: "", updatedAtMs = importedAtMs, folio = "", groupId = account.id,
            )
            if (old == null) add += fund else update += fund
        }
        return NpsPlan(account, existing == null, add, update, fromStatement.filterKeys { it !in seen }.values.toList(), left)
    }
}
