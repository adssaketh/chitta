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
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private val connected = MutableStateFlow(false)
    private val snapshot = MutableStateFlow<HouseholdSnapshot?>(null)
    private val code = MutableStateFlow<String?>(null)
    private val myUid = MutableStateFlow("")
    private val nickname = MutableStateFlow("")

    private data class Bits(val busy: Boolean, val notice: String?, val connected: Boolean, val snapshot: HouseholdSnapshot?, val code: String?)
    private val bits = combine(busy, notice, connected, snapshot, code) { b, n, c, s, k -> Bits(b, n, c, s, k) }
    private val ids = combine(myUid, nickname) { u, n -> u to n }

    val ui: StateFlow<HouseholdUi> = combine(bits, prefs.state, cloud.email, ids) { b, l, e, i ->
        HouseholdUi(e, b.connected, l, b.snapshot, i.first, b.busy, b.notice, b.code, i.second)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HouseholdUi())

    fun dismissNotice() { notice.value = null }

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

    fun create(name: String, nick: String) = act("Household made.") { service.create(name, nick) }
    fun join(codeText: String, nick: String) = act("You joined the household.") { service.join(codeText, nick) }
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
