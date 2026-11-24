package com.mansaketh.chitta.shots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.mansaketh.chitta.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", shadows = [ShadowWorkManagerForTests::class])
class MainTabsShotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle(); rule.onRoot().captureRoboImage("/home/claude/shots/$name.png") }

    @Test fun tabs() {
        Seed.book()
        rule.waitForIdle(); Thread.sleep(2000)
        shot("home")
        for (tab in listOf("Ledger", "Plan", "Insights", "More")) {
            rule.onNodeWithText(tab).performClick()
            shot(tab.lowercase())
        }
    }
}
