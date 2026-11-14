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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Two buttons that open Quick Add as an expense or as income (Phase 8j), through the same `chitta://add?type=` link the app already handles.
 * Nothing is saved from the widget. It shows no amounts, so hide-amounts and the lock do not change it.
 */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickAddContent(context) }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickAddContent(context: Context) {
    Column(GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(20.dp).padding(16.dp)) {
        Text("Add an entry", style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp))
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth().height(56.dp)) {
            // The one primary action is the expense: it is what is logged most.
            Button(context, "Expense", "expense", WidgetColors.sindoor, WidgetColors.onSindoor, "Add expense", GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Button(context, "Income", "income", WidgetColors.surface, WidgetColors.ink, "Add income", GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun Button(
    context: Context, label: String, type: String,
    fill: androidx.glance.unit.ColorProvider, text: androidx.glance.unit.ColorProvider, description: String, modifier: GlanceModifier,
) {
    Box(
        modifier = modifier.height(56.dp).background(fill).cornerRadius(14.dp)
            .clickable(actionStartActivity(WidgetIntents.add(context, type)))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium))
    }
}
