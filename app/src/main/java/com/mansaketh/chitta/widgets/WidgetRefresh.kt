package com.mansaketh.chitta.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetRefresh {
    /** Redraws the spend widget if one is on a home screen. A failure is not worth showing: the next update tries again. */
    suspend fun spend(context: Context) {
        runCatching {
            val app = context.applicationContext
            if (GlanceAppWidgetManager(app).getGlanceIds(SpendWidget::class.java).isNotEmpty()) SpendWidget().updateAll(app)
        }
    }
}
