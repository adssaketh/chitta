package com.mansaketh.chitta.widgets

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that opens Quick Add as an expense (Phase 8j). It shows no amounts. The app's own lock still comes first when it is on.
 * On Android 14 and later the PendingIntent form of startActivityAndCollapse is required; the Intent form is used below that.
 */
class QuickAddTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Add expense"; updateTile() }
    }

    override fun onClick() {
        val intent = WidgetIntents.add(this, "expense")
        val launch = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(launch) else launch()
    }
}
