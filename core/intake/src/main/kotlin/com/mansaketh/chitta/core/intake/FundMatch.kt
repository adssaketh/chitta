package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import java.time.LocalDate

/** How one fund was matched to an AMFI scheme. */
sealed interface FundMatchResult {
    /** One scheme, certain: by the scheme code or ISIN already on the fund, or by a name that leaves exactly one scheme. */
    data class Sure(val scheme: AmfiScheme) : FundMatchResult

    /** Several schemes fit, or only a near name was found. The person picks; nothing is guessed. */
    data class Pick(val candidates: List<AmfiScheme>) : FundMatchResult
    data object None : FundMatchResult
}

/**
 * Matches the funds you hold to AMFI schemes (master section 8: by ISIN, then scheme code; a statement has neither, so then by name).
 *
 * A name match is Sure only when the fund's name, once the plan and option words are taken out, equals one scheme's name, and the plan
 * (Direct or Regular) and the option (Growth, IDCW payout, IDCW reinvestment) both agree and are both stated, and exactly one scheme is left.
 * Anything else is a [FundMatchResult.Pick] list of near names, or None. A choice made from the list is saved on the fund as its scheme code,
 * so it is never asked again.
 */
class FundMatchIndex(val schemes: List<AmfiScheme>) {
    private val byCode = schemes.groupBy { it.code }
    private val byIsin = HashMap<String, MutableList<AmfiScheme>>().also { m -> schemes.forEach { s -> s.isins.forEach { m.getOrPut(it.uppercase()) { ArrayList() } += s } } }
    private val profiles = schemes.map { it to NameProfile.of(it.name, it.plan, it.option) }
    private val byCore = profiles.groupBy { it.second.core }

    fun byCode(code: String): AmfiScheme? = byCode[code.trim()]?.singleOrNull()
    fun byIsin(isin: String): AmfiScheme? = byIsin[isin.trim().uppercase()]?.singleOrNull()

    fun match(f: MutualFund): FundMatchResult {
        if (f.isNps) return FundMatchResult.None
        if (f.schemeCode.isNotBlank()) byCode(f.schemeCode)?.let { return FundMatchResult.Sure(it) }
        if (f.isin.isNotBlank()) byIsin(f.isin)?.let { return FundMatchResult.Sure(it) }
        val want = NameProfile.of(f.name, "", "")
        if (want.core.isEmpty()) return FundMatchResult.None
        val exact = byCore[want.core].orEmpty().filter { want.fits(it.second) }
        if (exact.size == 1 && want.plan != NameProfile.Plan.UNKNOWN && want.option != NameProfile.Option.UNKNOWN) return FundMatchResult.Sure(exact.single().first)
        if (exact.isNotEmpty()) return FundMatchResult.Pick(exact.map { it.first }.take(MAX_PICKS))
        val near = profiles.map { (s, p) -> Triple(s, p, want.similarity(p)) }.filter { (_, p, sim) -> sim >= NEAR && want.fits(p) }
            .sortedWith(compareByDescending<Triple<AmfiScheme, NameProfile, Double>> { it.third }.thenBy { it.first.name })
        return if (near.isEmpty()) FundMatchResult.None else FundMatchResult.Pick(near.take(MAX_PICKS).map { it.first })
    }

    companion object {
        const val MAX_PICKS = 5
        private const val NEAR = 0.6
    }
}

/** A fund name reduced to what tells schemes apart: the plain name, the plan and the option. */
internal data class NameProfile(val core: String, val tokens: Set<String>, val plan: Plan, val option: Option) {
    enum class Plan { DIRECT, REGULAR, UNKNOWN }
    enum class Option { GROWTH, PAYOUT, REINVEST, IDCW, UNKNOWN }

    /** True when this (the wanted fund) does not contradict [other]: a stated plan or option must agree; an unstated one fits anything. */
    fun fits(other: NameProfile): Boolean =
        (plan == Plan.UNKNOWN || plan == other.plan) && (option == Option.UNKNOWN || option == other.option)

    fun similarity(other: NameProfile): Double {
        if (tokens.isEmpty() || other.tokens.isEmpty()) return 0.0
        val common = tokens.intersect(other.tokens).size
        return common.toDouble() / tokens.union(other.tokens).size
    }

    companion object {
        private val noise = setOf("plan", "option", "direct", "regular", "growth", "idcw", "dividend", "payout", "reinvestment", "reinvest", "reinvestmen")

        fun of(name: String, plan: String, option: String): NameProfile {
            val all = tokens(name) + tokens(plan) + tokens(option)
            val p = when { "direct" in all -> Plan.DIRECT; "regular" in all -> Plan.REGULAR; else -> Plan.UNKNOWN }
            val o = when {
                "growth" in all -> Option.GROWTH
                "payout" in all -> Option.PAYOUT
                all.any { it.startsWith("reinvest") } -> Option.REINVEST
                "idcw" in all || "dividend" in all -> Option.IDCW
                else -> Option.UNKNOWN
            }
            val kept = tokens(name).filter { it !in noise }
            return NameProfile(kept.joinToString(""), kept.toSet(), p, o)
        }

        /** Lower case words; "&" is "and"; anything in brackets is dropped (series dates, "erstwhile" notes). */
        private fun tokens(text: String): List<String> =
            text.lowercase().replace("&", " and ").replace(Regex("""\([^)]*\)"""), " ").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    }
}

/** What a price fetch would change. Built from the funds and the file; applying it is the app's job. */
data class PriceUpdate(
    val fund: MutualFund,
    val scheme: AmfiScheme,
    /** False when the fund already has a newer price (a statement dated after the file): only the match is kept. */
    val applyPrice: Boolean,
)

data class PricePlan(val updates: List<PriceUpdate>, val needPick: List<MutualFund>, val noMatch: List<MutualFund>, val fileDate: LocalDate)

object FundPrices {
    /** NPS rows are left alone (their prices come from the statement). A fund with no match keeps its old price. */
    fun plan(funds: List<MutualFund>, reading: AmfiReading.Ready): PricePlan {
        val index = FundMatchIndex(reading.schemes)
        val updates = ArrayList<PriceUpdate>(); val pick = ArrayList<MutualFund>(); val none = ArrayList<MutualFund>()
        for (f in funds.filter { !it.isNps }) {
            when (val m = index.match(f)) {
                is FundMatchResult.Sure -> updates += PriceUpdate(f, m.scheme, isNewer(m.scheme, f))
                is FundMatchResult.Pick -> pick += f
                FundMatchResult.None -> none += f
            }
        }
        return PricePlan(updates, pick, none, reading.newestDate)
    }

    /** The scheme's price replaces the fund's when the fund has none or the scheme's date is the same or later. */
    fun isNewer(scheme: AmfiScheme, f: MutualFund): Boolean {
        val have = f.navDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
        return !scheme.date.isBefore(have)
    }

    /** The ISIN to keep on a fund that has none: the first one on the scheme's line. */
    fun isinFor(scheme: AmfiScheme): String = scheme.isins.firstOrNull().orEmpty()
}
