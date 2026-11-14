package com.mansaketh.chitta.data

import android.content.Context
import com.mansaketh.chitta.core.domain.LockTimeout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lock choices. Device-only: a lock is about this phone, so it never travels with a backup (DECISIONS D68). */
@Singleton
class SecurityPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta_security", Context.MODE_PRIVATE)

    private val _lock = MutableStateFlow(p.getBoolean("lock", false))
    val lockEnabled: StateFlow<Boolean> = _lock
    fun setLockEnabled(v: Boolean) { _lock.value = v; p.edit().putBoolean("lock", v).apply() }

    private val _timeout = MutableStateFlow(LockTimeout.fromKey(p.getString("timeout", null)))
    val timeout: StateFlow<LockTimeout> = _timeout
    fun setTimeout(v: LockTimeout) { _timeout.value = v; p.edit().putString("timeout", v.key).apply() }

    /** Blanks the preview in the recent-apps list (and blocks screenshots), by marking the window secure. */
    private val _hideInRecents = MutableStateFlow(p.getBoolean("hideInRecents", false))
    val hideInRecents: StateFlow<Boolean> = _hideInRecents
    fun setHideInRecents(v: Boolean) { _hideInRecents.value = v; p.edit().putBoolean("hideInRecents", v).apply() }

    /**
     * Hide-amounts mode, kept across restarts so the home-screen widgets can mask too (Phase 8j, DECISIONS D166). Widgets also mask
     * whenever the lock is on, so a locked book never shows figures on the home screen.
     */
    private val _hideAmounts = MutableStateFlow(p.getBoolean("hideAmounts", false))
    val hideAmounts: StateFlow<Boolean> = _hideAmounts
    fun setHideAmounts(v: Boolean) { _hideAmounts.value = v; p.edit().putBoolean("hideAmounts", v).apply() }
}
