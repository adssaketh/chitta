package com.mansaketh.chitta.data

import com.mansaketh.chitta.core.domain.LockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the lock screen is up. A fresh start with the lock on begins locked; after that the rule is [LockPolicy]: lock when the app
 * comes back after being away longer than the chosen time. It lives in a singleton so rotating the phone does not unlock or relock.
 */
@Singleton
class AppLock @Inject constructor(private val prefs: SecurityPrefs) {
    private val _locked = MutableStateFlow(prefs.lockEnabled.value)
    val locked: StateFlow<Boolean> = _locked
    private var leftAtMs: Long? = null

    /** The app left the screen (not a rotation). Only counts while unlocked. */
    fun onLeft(nowMs: Long) { if (!_locked.value) leftAtMs = nowMs }

    /** The app is back in front: decide whether the lock comes up. */
    fun onReturned(nowMs: Long) {
        _locked.value = LockPolicy.shouldLock(prefs.lockEnabled.value, _locked.value, leftAtMs, nowMs, prefs.timeout.value)
        leftAtMs = null
    }

    fun unlocked() { _locked.value = false; leftAtMs = null }
}
