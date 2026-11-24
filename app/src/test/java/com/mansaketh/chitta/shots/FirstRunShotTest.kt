package com.mansaketh.chitta.shots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mansaketh.chitta.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Verification harness: launches the real app (real Hilt graph, empty book) and captures what the person sees. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", shadows = [ShadowWorkManagerForTests::class])
class FirstRunShotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun firstRun() {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("/home/claude/shots/first_run.png")
    }
}
