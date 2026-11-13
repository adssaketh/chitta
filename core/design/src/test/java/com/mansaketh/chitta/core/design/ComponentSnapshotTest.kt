package com.mansaketh.chitta.core.design

import androidx.compose.ui.test.junit4.createComposeRule
import com.mansaketh.chitta.core.design.gallery.GalleryAmounts
import com.mansaketh.chitta.core.design.gallery.GalleryCharts
import com.mansaketh.chitta.core.design.gallery.GalleryColors
import com.mansaketh.chitta.core.design.gallery.GalleryControls
import com.mansaketh.chitta.core.design.gallery.GalleryIcons
import com.mansaketh.chitta.core.design.gallery.GalleryKeypad
import com.mansaketh.chitta.core.design.gallery.GalleryLedger
import com.mansaketh.chitta.core.design.gallery.GalleryNav
import com.mansaketh.chitta.core.design.gallery.GalleryStates
import com.mansaketh.chitta.core.design.gallery.GalleryType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every gallery section in Paper and Ink, on a 393 dp wide phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h4000dp-xxhdpi")
class ComponentSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun colours() = rule.snapshotBothThemes("colours") { GalleryColors() }
    @Test fun type() = rule.snapshotBothThemes("type") { GalleryType() }
    @Test fun amounts() = rule.snapshotBothThemes("amounts") { GalleryAmounts() }
    @Test fun ledger() = rule.snapshotBothThemes("ledger") { GalleryLedger() }
    @Test fun controls() = rule.snapshotBothThemes("controls") { GalleryControls() }
    @Test fun charts() = rule.snapshotBothThemes("charts") { GalleryCharts() }
    @Test fun keypad() = rule.snapshotBothThemes("keypad") { GalleryKeypad() }
    @Test fun navigationAndSheets() = rule.snapshotBothThemes("navigation") { GalleryNav() }
    @Test fun states() = rule.snapshotBothThemes("states") { GalleryStates() }
    @Test fun icons() = rule.snapshotBothThemes("icons") { GalleryIcons() }
}
