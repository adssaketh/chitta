package com.mansaketh.chitta.shots

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.mansaketh.chitta.ChittaApp
import com.mansaketh.chitta.MainActivity
import org.junit.Rule
import java.io.File

/**
 * Helpers for walking the real app and saving what is on screen. Theme comes from the SHOT_THEME environment variable ("paper" or "ink");
 * images go to /home/claude/shots/<theme>/. A step that cannot find its button is written to MISSING.txt instead of stopping the walk,
 * so one renamed label does not hide every screen after it.
 */
abstract class ShotBase {
    @get:Rule val rule: AndroidComposeTestRule<androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity>, MainActivity> = createAndroidComposeRule<MainActivity>()
    protected val theme: String = System.getenv("SHOT_THEME") ?: "paper"
    private val dir = File("/home/claude/shots/$theme").also { it.mkdirs() }

    protected fun prepare(seed: Boolean = true) {
        val app = ApplicationProvider.getApplicationContext<ChittaApp>()
        app.uiPrefs.setThemeMode(theme)
        app.uiPrefs.setAnimations(false)
        if (seed) Seed.book()
        settle(2000)
    }

    protected fun settle(ms: Long = 1200) { rule.waitForIdle(); Thread.sleep(ms); rule.waitForIdle() }

    protected fun shot(name: String) { settle(); rule.onRoot().captureRoboImage(File(dir, "$name.png").path) }

    private fun note(msg: String) = File(dir, "MISSING.txt").appendText(msg + "\n")

    /** Tap the first thing with this text, or this content description (substring, any case). */
    protected fun tap(label: String, index: Int = 0): Boolean {
        val m = hasText(label, substring = false, ignoreCase = true) or hasContentDescription(label, substring = true, ignoreCase = true)
        return try {
            val nodes = rule.onAllNodes(m, useUnmergedTree = false).fetchSemanticsNodes()
            val i = if (index < 0) nodes.size + index else index
            if (i < 0 || nodes.size <= i) { note("no '$label' (index $index of ${nodes.size})"); false } else { rule.onAllNodes(m)[i].performClick(); settle(700); true }
        } catch (e: Throwable) { note("tap '$label' failed: ${e.message?.take(120)}"); false }
    }

    protected fun type(text: String) {
        try { rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput(text); settle(500) } catch (e: Throwable) { note("type '$text' failed: ${e.message?.take(120)}") }
    }

    protected fun back() { rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(700) }
}
