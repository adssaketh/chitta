package com.mansaketh.chitta.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in with Google and how Drive backup is doing. Device-only: it is never part of the book, so a restore or an import
 * cannot sign anyone out or change this phone's backup switch (DECISIONS D173).
 */
@Singleton
class CloudPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta.cloud", Context.MODE_PRIVATE)

    private val _email = MutableStateFlow(p.getString("email", null))
    private val _auto = MutableStateFlow(p.getBoolean("auto", true))
    private val _last = MutableStateFlow(p.getLong("lastBackupMs", 0L).takeIf { it > 0 })
    private val _error = MutableStateFlow(p.getString("lastError", null))

    val email: StateFlow<String?> = _email
    val autoBackup: StateFlow<Boolean> = _auto
    val lastBackupMs: StateFlow<Long?> = _last
    val lastError: StateFlow<String?> = _error

    fun signedIn(email: String) {
        p.edit().putString("email", email).apply()
        _email.value = email
    }

    /** Forgets the account and the backup state on this phone. Drive is not touched: the backups stay in the person's own Drive. */
    fun signedOut() {
        p.edit().remove("email").remove("lastBackupMs").remove("lastError").apply()
        _email.value = null; _last.value = null; _error.value = null
    }

    fun setAuto(v: Boolean) { p.edit().putBoolean("auto", v).apply(); _auto.value = v }

    fun backupDone(atMs: Long) {
        p.edit().putLong("lastBackupMs", atMs).remove("lastError").apply()
        _last.value = atMs; _error.value = null
    }

    fun setError(message: String?) {
        p.edit().apply { if (message == null) remove("lastError") else putString("lastError", message) }.apply()
        _error.value = message
    }
}
