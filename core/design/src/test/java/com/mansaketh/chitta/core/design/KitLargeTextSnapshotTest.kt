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

/** Phase 11a: the kit at 200% text (nothing may clip), Paper and Ink. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h6000dp-xxhdpi")
class KitLargeTextSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun barsAt200Percent() = rule.snapshotBothThemes("kit_bars_200", fontScale = 2f) { GalleryKitBars() }
    @Test fun rowsAt200Percent() = rule.snapshotBothThemes("kit_rows_200", fontScale = 2f) { GalleryKitRows() }
    @Test fun controlsAt200Percent() = rule.snapshotBothThemes("kit_controls_200", fontScale = 2f) { GalleryKitControls() }
    @Test fun feedbackAt200Percent() = rule.snapshotBothThemes("kit_feedback_200", fontScale = 2f) { GalleryKitFeedback() }
    @Test fun sheetsAt200Percent() = rule.snapshotBothThemes("kit_sheets_200", fontScale = 2f) { GalleryKitSheets() }
}
