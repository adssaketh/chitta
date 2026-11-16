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

/** Phase 11a: every v8 kit section in Paper and Ink on a 393 dp phone (PDF pages 3 to 5, 16 and 36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h6000dp-xxhdpi")
class KitSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bars() = rule.snapshotBothThemes("kit_bars") { GalleryKitBars() }
    @Test fun rows() = rule.snapshotBothThemes("kit_rows") { GalleryKitRows() }
    @Test fun controls() = rule.snapshotBothThemes("kit_controls") { GalleryKitControls() }
    @Test fun feedback() = rule.snapshotBothThemes("kit_feedback") { GalleryKitFeedback() }
    @Test fun sheets() = rule.snapshotBothThemes("kit_sheets") { GalleryKitSheets() }
}
