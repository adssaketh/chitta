package com.mansaketh.chitta.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.ThemeMode

/**
 * Renders [content] in Paper, then in Ink, and writes `<name>_paper.png` and `<name>_ink.png` to
 * src/test/snapshots. [fontScale] 2.0 is the 200% text test. Roborazzi compares against these files in
 * `verifyRoborazziDebug` and rewrites them in `recordRoborazziDebug`.
 */
internal fun ComposeContentTestRule.snapshotBothThemes(
    name: String,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    var mode by mutableStateOf(ThemeMode.Paper)
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            ChittaTheme(mode = mode) {
                Box(Modifier.testTag("shot").fillMaxWidth().background(ChittaTheme.colors.background)) { content() }
            }
        }
    }
    waitForIdle()
    onNodeWithTag("shot").captureRoboImage("src/test/snapshots/${name}_paper.png")
    mode = ThemeMode.Ink
    waitForIdle()
    onNodeWithTag("shot").captureRoboImage("src/test/snapshots/${name}_ink.png")
}
