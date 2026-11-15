package com.mansaketh.chitta.core.design

import androidx.compose.ui.test.junit4.createComposeRule
import com.mansaketh.chitta.core.design.gallery.GalleryCharts
import com.mansaketh.chitta.core.design.gallery.GalleryControls
import com.mansaketh.chitta.core.design.gallery.GalleryKeypad
import com.mansaketh.chitta.core.design.gallery.GalleryLedger
import com.mansaketh.chitta.core.design.gallery.GalleryNav
import com.mansaketh.chitta.core.design.gallery.GalleryRows
import com.mansaketh.chitta.core.design.gallery.GalleryStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 9c: the same sections on a narrow phone (360 dp wide, the smallest common size) with the system text at 130%, a common
 * "larger text" setting. Catches wrapping and clipping that the 393 dp and 200% runs would not. These are the shared components;
 * the full app screens are built from them and tied to their view models, so they are checked on the phone (phase notes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h6000dp-xxhdpi")
class NarrowPhoneSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun ledger() = rule.snapshotBothThemes("narrow_ledger", fontScale = 1.3f) { GalleryLedger() }
    @Test fun rows() = rule.snapshotBothThemes("narrow_rows", fontScale = 1.3f) { GalleryRows() }
    @Test fun controls() = rule.snapshotBothThemes("narrow_controls", fontScale = 1.3f) { GalleryControls() }
    @Test fun charts() = rule.snapshotBothThemes("narrow_charts", fontScale = 1.3f) { GalleryCharts() }
    @Test fun keypad() = rule.snapshotBothThemes("narrow_keypad", fontScale = 1.3f) { GalleryKeypad() }
    @Test fun navigation() = rule.snapshotBothThemes("narrow_navigation", fontScale = 1.3f) { GalleryNav() }
    @Test fun states() = rule.snapshotBothThemes("narrow_states", fontScale = 1.3f) { GalleryStates() }
}
