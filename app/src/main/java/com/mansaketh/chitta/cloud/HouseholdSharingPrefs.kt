package com.mansaketh.chitta.cloud

import android.content.Context
import com.mansaketh.chitta.core.domain.HouseholdSharing
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What this phone remembers about entry sharing. Device-only, like [HouseholdPrefs]: never part of the book, so a restore cannot switch sharing on. */
data class SharingState(
    /** The person has chosen what to share (PDF p.33). Until then nothing is uploaded and nothing is downloaded. */
    val ready: Boolean = false,
    /** "everyone" or "mine": which entries lists and totals show. */
    val view: String = HouseholdSharing.VIEW_EVERYONE,
    /** The owner allowed this person to delete others' entries. */
    val canDelete: Boolean = false,
    /** Entries deleted here after this time are removed from the household. Earlier deletions were made before sharing started. */
    val deleteCursorMs: Long = 0L,
    /** Fingerprint of the categories as last uploaded or downloaded: a different one means there is something to upload. */
    val categoriesHash: String = "",
    val lastSyncMs: Long = 0L,
    /** The last thing that went wrong, in words; empty when the last round worked. */
    val problem: String = "",
)

@Singleton
class HouseholdSharingPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta.household.sharing", Context.MODE_PRIVATE)

    private fun read() = SharingState(
        ready = p.getBoolean("ready", false), view = HouseholdSharing.cleanView(p.getString("view", null)), canDelete = p.getBoolean("canDelete", false),
        deleteCursorMs = p.getLong("deleteCursor", 0L), categoriesHash = p.getString("categoriesHash", "").orEmpty(), lastSyncMs = p.getLong("lastSync", 0L),
        problem = p.getString("problem", "").orEmpty(),
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SharingState> = _state

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        _state.value = read()
    }

    fun markReady(nowMs: Long) = edit { putBoolean("ready", true).putLong("deleteCursor", nowMs).putString("categoriesHash", "").remove("problem") }
    fun setView(view: String) = edit { putString("view", HouseholdSharing.cleanView(view)) }
    fun setCanDelete(value: Boolean) { if (_state.value.canDelete != value) edit { putBoolean("canDelete", value) } }
    fun setDeleteCursor(ms: Long) = edit { putLong("deleteCursor", ms) }
    fun setCategoriesHash(hash: String) { if (_state.value.categoriesHash != hash) edit { putString("categoriesHash", hash) } }
    fun synced(nowMs: Long) = edit { putLong("lastSync", nowMs).remove("problem") }
    fun problem(text: String) { if (_state.value.problem != text) edit { putString("problem", text) } }

    /** Leaving, being removed, or signing out. The view choice is kept; everything else about sharing starts again. */
    fun reset() = edit { putBoolean("ready", false).putBoolean("canDelete", false).putLong("deleteCursor", 0L).putString("categoriesHash", "").remove("problem").remove("lastSync") }
}
