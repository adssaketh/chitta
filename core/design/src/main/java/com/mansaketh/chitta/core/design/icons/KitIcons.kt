package com.mansaketh.chitta.core.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

    private fun stroked(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 16f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    /** Phosphor "push-pin" (regular, MIT): the slanted pin the PDF draws on Quick Add and Entry detail (p.15, p.20). Replaces the upright thumbtack drawn in 11c. */
    val Pin: ImageVector by lazy {
        ImageVector.Builder(name = "Pin", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M235.32,81.37,174.63,20.69a16,16,0,0,0-22.63,0L98.37,74.49c-10.66-3.34-35-7.37-60.4,13.14a16,16,0,0,0-1.29,23.78L85,159.71,42.34,202.34a8,8,0,0,0,11.32,11.32L96.29,171l48.29,48.29A16,16,0,0,0,155.9,224c.38,0,.75,0,1.13,0a15.93,15.93,0,0,0,11.64-6.33c19.64-26.1,17.75-47.32,13.19-60L235.33,104A16,16,0,0,0,235.32,81.37ZM224,92.69h0l-57.27,57.46a8,8,0,0,0-1.49,9.22c9.46,18.93-1.8,38.59-9.34,48.62L48,100.08c12.08-9.74,23.64-12.31,32.48-12.31A40.13,40.13,0,0,1,96.81,91a8,8,0,0,0,9.25-1.51L163.32,32,224,92.68Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Drawn here, not from Phosphor (11c, D285): one line forking into two arrows, for Split. */
    val Split: ImageVector by lazy { stroked("Split", "M128,216V136M128,136L56,64M128,136L200,64M56,104V64H96M200,104V64H160") }
}
