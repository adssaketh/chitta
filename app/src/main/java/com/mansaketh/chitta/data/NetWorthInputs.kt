package com.mansaketh.chitta.data

import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.NetWorthExtras
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Loan terms, fixed deposits, mutual funds, NPS, gold, EPF, PPF and their switches, as the one input Net worth needs beyond entries (Phases 8b and 8d). */
@Singleton
class NetWorthInputs @Inject constructor(plan: PlanRepository, assets: AssetsRepository, prefs: UiPrefs) {
    private data class Switches(val deposits: Boolean, val instalments: Boolean, val funds: Boolean, val nps: Boolean)

    private val switches = combine(prefs.netWorthDeposits, prefs.netWorthInstalments, prefs.netWorthFunds, prefs.netWorthNps) { d, i, f, n -> Switches(d, i, f, n) }

    private val base: Flow<NetWorthExtras> = combine(plan.observeLoanTerms(), assets.observeDeposits(), assets.observeFunds(), switches) { t, d, funds, sw ->
        NetWorthExtras(
            terms = t, deposits = d, includeDeposits = sw.deposits, includeInstalments = sw.instalments,
            funds = funds.filter { !it.isNps }, npsSchemes = funds.filter { it.isNps }, includeFunds = sw.funds, includeNps = sw.nps,
        )
    }

    /** Gold, EPF and PPF (Phase 8i) with their three switches, added to the rest. */
    private val manual = combine(prefs.netWorthGold, prefs.netWorthEpf, prefs.netWorthPpf) { g, e, p -> Triple(g, e, p) }

    val extras: Flow<NetWorthExtras> = combine(base, assets.observeAssets(), assets.observeAssetValues(), manual) { b, a, v, sw ->
        b.copy(assets = a, assetValues = v, includeGold = sw.first, includeEpf = sw.second, includePpf = sw.third)
    }
}
