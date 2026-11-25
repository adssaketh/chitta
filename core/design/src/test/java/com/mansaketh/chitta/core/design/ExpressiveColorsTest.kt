package com.mansaketh.chitta.core.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mansaketh.chitta.core.design.theme.toChittaColors
import com.mansaketh.chitta.core.design.tokens.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveColorsTest {
    @Test fun lightSchemeMapsAndKeepsTextReadable() {
        val s = lightColorScheme()
        val c = s.toChittaColors(dark = false)
        assertFalse(c.isDark)
        assertEquals(s.primary, c.sindoor)
        assertEquals(s.background, c.background)
        assertTrue(contrast(c.inkMuted, c.background) >= 4.5f)
        assertTrue(contrast(c.warningText, c.background) >= 4.5f)
        assertTrue(contrast(c.green, c.background) >= 4.5f)
    }

    @Test fun darkSchemeMapsAndKeepsTextReadable() {
        val c = darkColorScheme().toChittaColors(dark = true)
        assertTrue(c.isDark)
        assertTrue(contrast(c.ink, c.background) >= 4.5f)
        assertTrue(contrast(c.inkMuted, c.background) >= 4.5f)
        assertTrue(contrast(c.warningText, c.background) >= 4.5f)
    }

    @Test fun oddWallpaperColoursStillPass() {
        // A pale wallpaper-derived muted text and an orange error must be pulled to 4.5:1.
        val s = lightColorScheme(onSurfaceVariant = Color(0xFFBBBBBB), error = Color(0xFFFFB74D))
        val c = s.toChittaColors(dark = false)
        assertTrue(contrast(c.inkMuted, c.background) >= 4.5f)
        assertTrue(contrast(c.warningText, c.background) >= 4.5f)
    }
}
