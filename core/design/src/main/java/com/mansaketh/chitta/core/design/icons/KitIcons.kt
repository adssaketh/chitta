package com.mansaketh.chitta.core.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Icons the v8 kit needs that Phosphor 2.1.1 ships as the same glyph turned: caret-left and caret-up are caret-right and caret-down
 * rotated 180 degrees about the centre of the 256 x 256 viewport. Same paths as [ChittaIcons.CaretRight] and [ChittaIcons.CaretDown], so
 * the stroke and the corners match exactly. [ChittaIcons] itself is generated (tools/gen_icons.py) and is not edited by hand.
 */
object KitIcons {
    private const val CARET_RIGHT = "M181.66,133.66l-80,80a8,8,0,0,1-11.32-11.32L164.69,128,90.34,53.66a8,8,0,0,1,11.32-11.32l80,80A8,8,0,0,1,181.66,133.66Z"
    private const val CARET_DOWN = "M213.66,101.66l-80,80a8,8,0,0,1-11.32,0l-80-80A8,8,0,0,1,53.66,90.34L128,164.69l74.34-74.35a8,8,0,0,1,11.32,11.32Z"

    private const val ARROW_LEFT = "M224,128a8,8,0,0,1-8,8H59.31l58.35,58.34a8,8,0,0,1-11.32,11.32l-72-72a8,8,0,0,1,0-11.32l72-72a8,8,0,0,1,11.32,11.32L59.31,120H216A8,8,0,0,1,224,128Z"

    private fun turned(name: String, pathData: String, degrees: Float = 180f): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .group(rotate = degrees, pivotX = 128f, pivotY = 128f) {
                addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            }
            .build()

    /** phosphor: caret-left (caret-right turned) */
    val CaretLeft: ImageVector by lazy { turned("CaretLeft", CARET_RIGHT) }

    /** phosphor: arrow-up (arrow-left turned a quarter clockwise); the Up button of Customise */
    val ArrowUp: ImageVector by lazy { turned("ArrowUp", ARROW_LEFT, 90f) }

    /** phosphor: arrow-down (arrow-left turned a quarter anticlockwise); the Down button of Customise */
    val ArrowDown: ImageVector by lazy { turned("ArrowDown", ARROW_LEFT, -90f) }

    /** phosphor: caret-up (caret-down turned) */
    val CaretUp: ImageVector by lazy { turned("CaretUp", CARET_DOWN) }
}
