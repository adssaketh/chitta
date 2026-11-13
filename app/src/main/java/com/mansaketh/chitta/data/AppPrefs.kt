package com.mansaketh.chitta.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small switches that are not money: first run done, and the import marker that allows Undo for 30 days. */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta", Context.MODE_PRIVATE)
    var firstRunDone: Boolean
        get() = p.getBoolean("firstRunDone", false)
        set(v) { p.edit().putBoolean("firstRunDone", v).apply() }
    var importedAtMs: Long
        get() = p.getLong("importedAtMs", 0L)
        set(v) { p.edit().putLong("importedAtMs", v).apply() }
    var importBackupPath: String?
        get() = p.getString("importBackupPath", null)
        set(v) { p.edit().putString("importBackupPath", v).apply() }

    fun clearImportMarker() { p.edit().remove("importedAtMs").remove("importBackupPath").apply() }

    companion object { const val UNDO_IMPORT_DAYS = 30L }
}
