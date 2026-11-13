package com.mansaketh.chitta.data

import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.NetWorthExtras
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Loan terms, fixed deposits and the two switches, as the one input Net worth needs beyond entries (Phase 8b). */
@Singleton
class NetWorthInputs @Inject constructor(plan: PlanRepository, assets: AssetsRepository, prefs: UiPrefs) {
    val extras: Flow<NetWorthExtras> = combine(plan.observeLoanTerms(), assets.observeDeposits(), prefs.netWorthDeposits, prefs.netWorthInstalments) { t, d, deposits, instalments ->
        NetWorthExtras(terms = t, deposits = d, includeDeposits = deposits, includeInstalments = instalments)
    }
}
