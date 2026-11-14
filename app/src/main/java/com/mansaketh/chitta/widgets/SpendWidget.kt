package com.mansaketh.chitta.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mansaketh.chitta.core.domain.WidgetLines

/**
 * Safe to spend and what was spent today (Phase 8j). The figure is the one Home leads with; amounts are masked when hide-amounts or the
 * lock is on. Tapping opens the app (behind the lock when it is on). Glance cannot load the bundled Fraunces, so the figure uses the system serif.
 */
class SpendWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lines = WidgetData.lines(context)
        provideContent { SpendContent(context, lines) }
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}

@Composable
private fun SpendContent(context: Context, lines: WidgetLines?) {
    val shown = lines ?: WidgetLines("Safe to spend", "—", "Open the app to refresh", "", "Safe to spend is not available. Open the app to refresh.")
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(20.dp).padding(16.dp)
            .clickable(actionStartActivity(WidgetIntents.open(context)))
            .semantics { contentDescription = shown.spoken },
    ) {
        Text(shown.title, style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp))
        Text(shown.main, style = TextStyle(color = WidgetColors.ink, fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium))
        Text(shown.sub, style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp))
        Spacer(GlanceModifier.defaultWeight())
        // A hairline, then today: the ledger's rule and day total in small.
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.rule))
        if (shown.today.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Text(shown.today, style = TextStyle(color = WidgetColors.ink, fontSize = 13.sp))
        }
    }
}
