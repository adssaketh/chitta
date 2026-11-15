package com.mansaketh.chitta.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the sync between this person's phones is doing, as far as the screen needs to know. Device-only and kept outside the book
 * (so a restore cannot change it), like [CloudPrefs]. The real position (cursor, device id, waiting changes) lives in the database.
 */
@Singleton
class SyncPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _last = MutableStateFlow(p.getLong("lastSyncMs", 0L).takeIf { it > 0 })
    private val _error = MutableStateFlow(p.getString("lastError", null))
    private val _needsChoice = MutableStateFlow(p.getBoolean("needsChoice", false))
    private val _running = MutableStateFlow(false)

    val lastSyncMs: StateFlow<Long?> = _last
    val lastError: StateFlow<String?> = _error
    val needsChoice: StateFlow<Boolean> = _needsChoice
    val running: StateFlow<Boolean> = _running

    /** Set when the whole book was replaced on this phone (a restore, or "Delete everything"): the cloud must follow this phone, not the other way round. */
    var bookReplaced: Boolean
        get() = p.getBoolean(KEY_BOOK_REPLACED, false)
        set(v) { p.edit().putBoolean(KEY_BOOK_REPLACED, v).apply() }

    /** The Firebase account this phone last synced with. A different one starts the sync over. */
    var syncUid: String?
        get() = p.getString("syncUid", null)
        set(v) { p.edit().apply { if (v == null) remove("syncUid") else putString("syncUid", v) }.apply() }

    /** When old delete markers were last cleared out of the cloud. */
    var lastCleanMs: Long
        get() = p.getLong("lastCleanMs", 0L)
        set(v) { p.edit().putLong("lastCleanMs", v).apply() }

    fun done(atMs: Long) {
        p.edit().putLong("lastSyncMs", atMs).remove("lastError").apply()
        _last.value = atMs; _error.value = null
    }

    fun setError(message: String?) {
        p.edit().apply { if (message == null) remove("lastError") else putString("lastError", message) }.apply()
        _error.value = message
    }

    fun setNeedsChoice(v: Boolean) { p.edit().putBoolean("needsChoice", v).apply(); _needsChoice.value = v }

    fun setRunning(v: Boolean) { _running.value = v }

    companion object {
        const val FILE = "chitta.sync"
        const val KEY_BOOK_REPLACED = "bookReplaced"
    }
}
