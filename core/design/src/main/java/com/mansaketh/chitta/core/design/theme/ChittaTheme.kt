package com.mansaketh.chitta.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mansaketh.chitta.core.design.tokens.ChittaColors
import com.mansaketh.chitta.core.design.tokens.ChittaType
import com.mansaketh.chitta.core.money.IndianFormat

/** Paper (light), Ink (dark), follow the phone, or Material 3 Expressive colours (wallpaper-based, follows the phone's light/dark). */
enum class ThemeMode { Paper, Ink, System, Expressive }

private val LocalColors = staticCompositionLocalOf { ChittaColors.Paper }
private val LocalType = staticCompositionLocalOf { ChittaType() }

/** Masks every amount (privacy in public). Components read this; nothing else needs to know. */
val LocalHideAmounts = compositionLocalOf { false }

object ChittaTheme {
    val colors: ChittaColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val type: ChittaType
        @Composable @ReadOnlyComposable get() = LocalType.current

    val hideAmounts: Boolean
        @Composable @ReadOnlyComposable get() = LocalHideAmounts.current
}

/**
 * @param accent optional custom accent (replaces sindoor); null keeps the default palette.
 * Material 3 is only the base: our own components draw on top of it (master section 9).
 */
@Composable
fun ChittaTheme(
    mode: ThemeMode = ThemeMode.System,
    hideAmounts: Boolean = false,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Paper -> false
        ThemeMode.Ink -> true
        ThemeMode.System, ThemeMode.Expressive -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    // Expressive: wallpaper colours win over a custom accent (the accent picker only tints Paper and Ink).
    val expressive = remember(mode, dark, context) { if (mode == ThemeMode.Expressive) expressiveScheme(context, dark) else null }
    val colors = remember(dark, accent, expressive) {
        if (expressive != null) expressive.toChittaColors(dark) else {
            val base = if (dark) ChittaColors.Ink else ChittaColors.Paper
            if (accent != null) base.withAccent(accent) else base
        }
    }
    val type = remember { ChittaType() }
    val scheme = remember(colors, expressive) {
        if (expressive != null) expressive else if (colors.isDark) {
            darkColorScheme(
                primary = colors.sindoor, onPrimary = colors.onSindoor,
                background = colors.background, onBackground = colors.ink,
                surface = colors.surface, onSurface = colors.ink,
                surfaceVariant = colors.surface, onSurfaceVariant = colors.inkMuted,
                outline = colors.mark, outlineVariant = colors.rule,
                error = colors.warningText, onError = colors.background,
                scrim = colors.scrim, inverseSurface = colors.ink, inverseOnSurface = colors.background,
            )
        } else {
            lightColorScheme(
                primary = colors.sindoor, onPrimary = colors.onSindoor,
                background = colors.background, onBackground = colors.ink,
                surface = colors.surface, onSurface = colors.ink,
                surfaceVariant = colors.surface, onSurfaceVariant = colors.inkMuted,
                outline = colors.mark, outlineVariant = colors.rule,
                error = colors.warningText, onError = colors.background,
                scrim = colors.scrim, inverseSurface = colors.ink, inverseOnSurface = colors.background,
            )
        }
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides type,
        LocalHideAmounts provides hideAmounts,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Draws every rupee sign in the amount font. Hanken Grotesk, the interface font, has no rupee glyph,
 * and Android would otherwise swap in some other font for it.
 */
@Composable
@ReadOnlyComposable
fun String.rupeeAware(): AnnotatedString {
    val family = ChittaTheme.type.rupeeFamily
    val text = this
    return buildAnnotatedString {
        text.forEach { ch ->
            if (ch == IndianFormat.RUPEE[0]) withStyle(SpanStyle(fontFamily = family)) { append(ch) } else append(ch)
        }
    }
}
