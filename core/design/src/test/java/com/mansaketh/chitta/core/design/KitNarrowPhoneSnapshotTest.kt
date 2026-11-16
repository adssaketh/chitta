package com.mansaketh.chitta.core.design

import androidx.compose.ui.test.junit4.createComposeRule
import com.mansaketh.chitta.core.design.gallery.GalleryKitBars
import com.mansaketh.chitta.core.design.gallery.GalleryKitControls
import com.mansaketh.chitta.core.design.gallery.GalleryKitFeedback
import com.mansaketh.chitta.core.design.gallery.GalleryKitRows
import com.mansaketh.chitta.core.design.gallery.GalleryKitSheets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 11a: the kit on a 360 dp phone with text at 130% (the smallest common size and a common "larger text" setting), Paper and Ink. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h6000dp-xxhdpi")
class KitNarrowPhoneSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bars() = rule.snapshotBothThemes("kit_narrow_bars", fontScale = 1.3f) { GalleryKitBars() }
    @Test fun rows() = rule.snapshotBothThemes("kit_narrow_rows", fontScale = 1.3f) { GalleryKitRows() }
    @Test fun controls() = rule.snapshotBothThemes("kit_narrow_controls", fontScale = 1.3f) { GalleryKitControls() }
    @Test fun feedback() = rule.snapshotBothThemes("kit_narrow_feedback", fontScale = 1.3f) { GalleryKitFeedback() }
    @Test fun sheets() = rule.snapshotBothThemes("kit_narrow_sheets", fontScale = 1.3f) { GalleryKitSheets() }
}
