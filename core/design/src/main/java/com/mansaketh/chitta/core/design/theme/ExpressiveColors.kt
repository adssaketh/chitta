package com.mansaketh.chitta.core.design.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mansaketh.chitta.core.design.tokens.ChittaColors
import com.mansaketh.chitta.core.design.tokens.Palette
import com.mansaketh.chitta.core.design.tokens.ensureContrast

/**
 * Material 3 Expressive, colours only (shapes, type and layout stay Chitta's own).
 * Android 12+ takes the colours from the wallpaper; older phones get Material's fixed baseline palette.
 */
internal fun expressiveScheme(context: Context, dark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
}

/**
 * Maps a Material scheme onto Chitta's own colour roles so every existing component recolours without a layout change.
 * Text roles are nudged until they pass 4.5:1 against the background, because wallpaper colours can be any colour.
 */
internal fun ColorScheme.toChittaColors(dark: Boolean): ChittaColors {
    val bg = background
    val ink = onBackground
    val green = if (dark) Color(Palette.INK_GREEN) else Color(Palette.PAPER_GREEN)
    return ChittaColors(
        isDark = dark,
        background = bg,
        surface = surfaceContainerLow,
        ink = ink,
        inkMuted = ensureContrast(onSurfaceVariant, bg, 4.5f, towards = ink),
        rule = outlineVariant,
        mark = outline,
        sindoor = primary,
        onSindoor = onPrimary,
        warningText = ensureContrast(error, bg, 4.5f, towards = ink),
        warningOnInverse = errorContainer,
        green = ensureContrast(green, bg, 4.5f, towards = ink),
        scrim = scrim.copy(alpha = if (dark) 0.72f else 0.40f),
        category = (if (dark) ChittaColors.Ink else ChittaColors.Paper).category,
    )
}
