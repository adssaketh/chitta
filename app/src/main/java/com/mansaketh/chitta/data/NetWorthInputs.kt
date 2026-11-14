package com.mansaketh.chitta.data

import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.NetWorthExtras
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Loan terms, fixed deposits, mutual funds, NPS and their switches, as the one input Net worth needs beyond entries (Phases 8b and 8d). */
@Singleton
class NetWorthInputs @Inject constructor(plan: PlanRepository, assets: AssetsRepository, prefs: UiPrefs) {
    private data class Switches(val deposits: Boolean, val instalments: Boolean, val funds: Boolean, val nps: Boolean)

    private val switches = combine(prefs.netWorthDeposits, prefs.netWorthInstalments, prefs.netWorthFunds, prefs.netWorthNps) { d, i, f, n -> Switches(d, i, f, n) }

    val extras: Flow<NetWorthExtras> = combine(plan.observeLoanTerms(), assets.observeDeposits(), assets.observeFunds(), switches) { t, d, funds, sw ->
        NetWorthExtras(
            terms = t, deposits = d, includeDeposits = sw.deposits, includeInstalments = sw.instalments,
            funds = funds.filter { !it.isNps }, npsSchemes = funds.filter { it.isNps }, includeFunds = sw.funds, includeNps = sw.nps,
        )
    }
}
