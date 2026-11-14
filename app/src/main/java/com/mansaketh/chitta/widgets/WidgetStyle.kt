package com.mansaketh.chitta.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.mansaketh.chitta.MainActivity
import com.mansaketh.chitta.core.design.tokens.Palette

/** Widget colours: the Paper tokens by day and the Ink tokens by night (master section 3). */
internal object WidgetColors {
    private fun pair(day: Long, night: Long) = ColorProvider(day = Color(day), night = Color(night))
    val background = pair(Palette.PAPER_BACKGROUND, Palette.INK_BACKGROUND)
    val surface = pair(Palette.PAPER_SURFACE, Palette.INK_SURFACE)
    val ink = pair(Palette.PAPER_INK, Palette.INK_INK)
    val muted = pair(Palette.PAPER_MUTED, Palette.INK_MUTED)
    val rule = pair(Palette.PAPER_RULE, Palette.INK_RULE)
    val sindoor = pair(Palette.PAPER_SINDOOR, Palette.INK_SINDOOR)
    val onSindoor = pair(Palette.PAPER_ON_SINDOOR, Palette.INK_ON_SINDOOR)
}

/** The links the app already understands (`chitta://add`, `chitta://open`), aimed at MainActivity so no chooser can appear. */
internal object WidgetIntents {
    private const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun link(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClassName(context.packageName, MainActivity::class.java.name).addFlags(FLAGS)

    fun add(context: Context, type: String) = link(context, "chitta://add?type=$type")

    fun open(context: Context): Intent = Intent(context, MainActivity::class.java).addFlags(FLAGS)
}
