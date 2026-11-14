package com.mansaketh.chitta.widgets

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mansaketh.chitta.R

/**
 * Long-press the app icon: Add expense, Add income, Ledger (Phase 8j). Dynamic rather than a static XML file because a static shortcut cannot name the
 * debug build's package (the id has a suffix), and the same list then works for the release build and after the rename to Paddhu Pusthakam.
 */
object Shortcuts {
    fun publish(context: Context) {
        runCatching {
            val items = listOf(
                ShortcutInfoCompat.Builder(context, "add_expense").setShortLabel("Add expense").setLongLabel("Add an expense")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_expense)).setIntent(WidgetIntents.add(context, "expense")).setRank(0).build(),
                ShortcutInfoCompat.Builder(context, "add_income").setShortLabel("Add income").setLongLabel("Add income")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_income)).setIntent(WidgetIntents.add(context, "income")).setRank(1).build(),
                ShortcutInfoCompat.Builder(context, "ledger").setShortLabel("Ledger").setLongLabel("Open the ledger")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_ledger)).setIntent(WidgetIntents.link(context, "chitta://open?tab=ledger")).setRank(2).build(),
            )
            ShortcutManagerCompat.setDynamicShortcuts(context, items)
        }
    }
}
