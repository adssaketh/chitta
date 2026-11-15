package com.mansaketh.chitta.cloud

import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.HouseholdSharing
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How the Ledger should look at the household right now. [active] is false until the person has chosen what to share (PDF p.33). */
data class HouseholdLens(val active: Boolean = false, val view: String = HouseholdSharing.VIEW_EVERYONE, val myUid: String = "", val canDelete: Boolean = false)

/**
 * Which entries a list shows. Outside a household, or before sharing is chosen, this is exactly the phone's own entries, so every
 * screen behaves as before. Once sharing is on, the Ledger can switch between Everyone and Mine ([HouseholdSharing.visible]).
 * Home, Insights and net worth do not use this: they stay personal (DECISIONS D212).
 */
@Singleton
class HouseholdView @Inject constructor(
    private val ledger: LedgerRepository,
    private val household: HouseholdPrefs,
    private val sharing: HouseholdSharingPrefs,
    private val session: FirebaseSession,
) {
    val lens: Flow<HouseholdLens> = combine(household.state, sharing.state) { h, s ->
        val active = h.inHousehold && s.ready
        HouseholdLens(active, s.view, session.uid.orEmpty(), s.canDelete)
    }.distinctUntilChanged()

    /** Entries for the Ledger, with the lens that was used to pick them. */
    fun entries(): Flow<Pair<HouseholdLens, List<Transaction>>> = lens.let { l ->
        combine(l, ledger.observeEverything()) { lens, all ->
            if (!lens.active) lens to all.filter { !it.isMemberCopy() }
            else lens to all.filter { HouseholdSharing.visible(true, lens.view, it.ownerUid, it.isPrivate, lens.myUid) }
        }
    }

    fun setView(view: String) = sharing.setView(view)

    private fun Transaction.isMemberCopy() = HouseholdSharing.isPlaceholderAccountId(accountId)
}
