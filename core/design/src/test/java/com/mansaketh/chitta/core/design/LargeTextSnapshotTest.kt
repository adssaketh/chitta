package com.mansaketh.chitta.core.design

import androidx.compose.ui.test.junit4.createComposeRule
import com.mansaketh.chitta.core.design.gallery.GalleryControls
import com.mansaketh.chitta.core.design.gallery.GalleryLedger
import com.mansaketh.chitta.core.design.gallery.GalleryNav
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Definition of done: text at 200% must not clip. The busiest sections at font scale 2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h6000dp-xxhdpi")
class LargeTextSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun ledgerAt200Percent() = rule.snapshotBothThemes("ledger_200", fontScale = 2f) { GalleryLedger() }
    @Test fun controlsAt200Percent() = rule.snapshotBothThemes("controls_200", fontScale = 2f) { GalleryControls() }
    @Test fun navigationAt200Percent() = rule.snapshotBothThemes("navigation_200", fontScale = 2f) { GalleryNav() }
}
