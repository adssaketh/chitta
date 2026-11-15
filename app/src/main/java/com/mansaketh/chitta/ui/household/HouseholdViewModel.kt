package com.mansaketh.chitta.ui.household

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.cloud.CloudPrefs
import com.mansaketh.chitta.cloud.FirebaseSession
import com.mansaketh.chitta.cloud.GoogleAccount
import com.mansaketh.chitta.cloud.HResult
import com.mansaketh.chitta.cloud.HouseholdLocal
import com.mansaketh.chitta.cloud.HouseholdPrefs
import com.mansaketh.chitta.cloud.HouseholdService
import com.mansaketh.chitta.cloud.HouseholdSharingPrefs
import com.mansaketh.chitta.cloud.HouseholdSync
import com.mansaketh.chitta.cloud.SharingState
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.HouseholdStore
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.DiagnosticRow
import com.mansaketh.chitta.core.domain.HouseholdDiagnostics
import com.mansaketh.chitta.core.domain.WhoSpent
import com.mansaketh.chitta.core.domain.WhoSpentResult
import com.mansaketh.chitta.core.domain.HouseholdSharing
import com.mansaketh.chitta.cloud.HouseholdSnapshot
import com.mansaketh.chitta.cloud.SignInResult
import com.mansaketh.chitta.core.domain.Households
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HouseholdUi(
    /** The Google account used for Drive backup (null: not signed in at all). */
    val email: String? = null,
    /** Firebase has this person signed in, so the household can be read. Checked when the page opens. */
    val connected: Boolean = false,
    val local: HouseholdLocal = HouseholdLocal("", "", false, "", false),
    val snapshot: HouseholdSnapshot? = null,
    val myUid: String = "",
    val busy: Boolean = false,
    val notice: String? = null,
    /** The invite code shown in the invite sheet (null: none made yet). */
    val code: String? = null,
    /** A starting nickname for the create and join sheets. */
    val suggestedNickname: String = "",
    /** Entry sharing on this phone (Phase 9b step 3): chosen or not, last send, last problem. */
    val sharing: SharingState = SharingState(),
    /** How many entries are shared, waiting to go up, and came from other members. Read when the page opens and after each action. */
    val counts: HouseholdStore.Counts? = null,
    /** Set right after creating or joining: the page opens "Choose what to share" (PDF p.33) once. */
    val askToShare: Boolean = false,
)

/** The diagnostics view (Phase 9b step 4): one row per question, the waiting note, and whether the server could be read. */
data class DiagnosticsUi(
    val loading: Boolean = false,
    val rows: List<DiagnosticRow> = emptyList(),
    val waiting: Int = 0,
    val error: String? = null,
)

/**
 * Household page logic (Phase 9b, step 2). Every action goes through [HouseholdService]; the page shows what the server says, and the
 * copy on this phone ([HouseholdPrefs]) only tells it which household to ask about.
 */
@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val account: GoogleAccount,
    private val service: HouseholdService,
    private val prefs: HouseholdPrefs,
    private val cloud: CloudPrefs,
    private val fb: FirebaseSession,
    private val sync: HouseholdSync,
    sharingPrefs: HouseholdSharingPrefs,
    ledger: LedgerRepository,
    categoryRepo: CategoryRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private val connected = MutableStateFlow(false)
    private val snapshot = MutableStateFlow<HouseholdSnapshot?>(null)
    private val code = MutableStateFlow<String?>(null)
    private val myUid = MutableStateFlow("")
    private val nickname = MutableStateFlow("")
    private val counts = MutableStateFlow<HouseholdStore.Counts?>(null)
    private val askToShare = MutableStateFlow(false)

    private data class Bits(val busy: Boolean, val notice: String?, val connected: Boolean, val snapshot: HouseholdSnapshot?, val code: String?)
    private val bits = combine(busy, notice, connected, snapshot, code) { b, n, c, s, k -> Bits(b, n, c, s, k) }
    private val ids = combine(myUid, nickname) { u, n -> u to n }

    private val sharingBits = combine(sharingPrefs.state, counts, askToShare) { s, c, a -> Triple(s, c, a) }

    val ui: StateFlow<HouseholdUi> = combine(bits, prefs.state, cloud.email, ids, sharingBits) { b, l, e, i, s ->
        HouseholdUi(e, b.connected, l, b.snapshot, i.first, b.busy, b.notice, b.code, i.second, s.first, s.second, s.third)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HouseholdUi())

    fun dismissNotice() { notice.value = null }

    // ---- Who spent how much (PDF p.34)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _month = MutableStateFlow(YearMonth.now(zone))
    val month: StateFlow<YearMonth> = _month

    /** The month's numbers, or null while the member list has not been read yet. Shared entries only (see [WhoSpent]). */
    val spent: StateFlow<WhoSpentResult?> = combine(ledger.observeEverything(), categoryRepo.observeAll(), snapshot, _month, myUid) { entries, cats, snap, month, uid ->
        snap?.let { WhoSpent.compute(entries, cats, WhoSpent.monthPeriod(month, zone), it.members, uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun earlierMonth() { _month.value = _month.value.minusMonths(1) }
    fun laterMonth() { if (_month.value < YearMonth.now(zone)) _month.value = _month.value.plusMonths(1) }

    // ---- diagnostics: what the phone and the server each hold
    private val _diagnostics = MutableStateFlow(DiagnosticsUi())
    val diagnostics: StateFlow<DiagnosticsUi> = _diagnostics

    fun loadDiagnostics() {
        if (_diagnostics.value.loading) return
        _diagnostics.value = _diagnostics.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val phone = sync.phoneHolds()
            if (phone == null) { _diagnostics.value = DiagnosticsUi(error = "Sign in with Google to read the household."); return@launch }
            when (val server = sync.serverHolds()) {
                is HResult.Ok -> _diagnostics.value = DiagnosticsUi(rows = HouseholdDiagnostics.rows(phone, server.value), waiting = phone.waiting)
                is HResult.Fail -> _diagnostics.value = DiagnosticsUi(waiting = phone.waiting, error = server.message)
            }
        }
    }

    /**
     * The page opened. If Firebase already has this person signed in, re-check the household on the server. Otherwise the page shows a
     * "Continue with Google" button (a phone that signed in during Phase 9a has the Google account but no Firebase sign-in yet).
     */
    fun opened() {
        syncIdentity()
        if (connected.value) reload()
    }

    private fun syncIdentity() {
        val uid = fb.uid
        myUid.value = uid.orEmpty()
        nickname.value = Households.defaultNickname(fb.displayName, fb.email ?: cloud.email.value)
        connected.value = uid != null && fb.email.equals(cloud.email.value, ignoreCase = true)
    }

    fun signIn(activity: Activity) = work {
        when (val r = account.ensureFirebase(activity)) {
            is SignInResult.Ok -> {
                syncIdentity()
                if (!connected.value) notice.value = "Signed in with Google, but the household sign-in did not finish. Check your connection and try again."
                else refreshAndLoad()
            }
            SignInResult.Cancelled -> Unit
            is SignInResult.Failed -> notice.value = r.message
        }
    }

    private suspend fun refreshAndLoad() {
        counts.value = sync.counts()
        service.refresh()?.let { notice.value = it; return }
        if (prefs.state.value.inHousehold) {
            when (val r = service.load()) {
                is HResult.Ok -> snapshot.value = r.value
                is HResult.Fail -> { notice.value = r.message; snapshot.value = null }
            }
        } else snapshot.value = null
    }

    fun reload() = work { refreshAndLoad() }

    /** Runs one action, shows its error if any, and re-reads the household. */
    private fun act(done: String? = null, block: suspend () -> String?) = work {
        val error = block()
        if (error != null) notice.value = error else if (done != null) notice.value = done
        refreshAndLoad()
    }

    fun create(name: String, nick: String) = act("Household made.") { service.create(name, nick).also { if (it == null) askToShare.value = true } }
    fun join(codeText: String, nick: String) = act("You joined the household.") { service.join(codeText, nick).also { if (it == null) askToShare.value = true } }
    fun askHandled() { askToShare.value = false }

    // ---- entry sharing (PDF p.33 and the "Your sharing" row)
    fun chooseSharing(choice: HouseholdSharing.ShareChoice) = act("Sharing is on.") { sync.adopt(choice) }
    fun shareEarlier() = act("Your earlier entries will go up.") { sync.shareEarlierEntries() }
    fun sendNow() = act("Sent.") { sync.syncNow() }
    fun leave() = act("You left the household. Your entries stay on this phone.") { service.leave() }
    fun remove(uid: String) = act("Removed. Their entries stay on their phone.") { service.remove(uid) }
    fun makeOwner(uid: String) = act("Handed over. The new owner makes a new invite code.") { service.transfer(uid) }
    fun setCanDelete(uid: String, on: Boolean) = act { service.setCanDelete(uid, on) }
    fun setShowBalances(on: Boolean) = act { service.setShowBalances(on) }
    fun rename(name: String) = act { service.rename(name) }
    fun setNickname(name: String) = act { service.setMyNickname(name) }

    // ---- invite sheet
    fun loadCode() = work { code.value = service.currentCode() }

    fun newCode() = work {
        when (val r = service.newCode()) {
            is HResult.Ok -> { code.value = r.value; refreshAndLoad() }
            is HResult.Fail -> notice.value = r.message
        }
    }

    fun stopJoining() = work {
        val error = service.revokeCode()
        if (error != null) notice.value = error else { code.value = null; notice.value = "Nobody can join with that code now." }
    }

    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { try { block() } finally { busy.value = false } }
    }
}
