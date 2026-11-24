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

    /** Phosphor "palette" (regular, MIT): the Settings group icon on PDF p.36. */
    val Palette: ImageVector by lazy {
        ImageVector.Builder(name = "Palette", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M200.77,53.89A103.27,103.27,0,0,0,128,24h-1.07A104,104,0,0,0,24,128c0,43,26.58,79.06,69.36,94.17A32,32,0,0,0,136,192a16,16,0,0,1,16-16h46.21a31.81,31.81,0,0,0,31.2-24.88,104.43,104.43,0,0,0,2.59-24A103.28,103.28,0,0,0,200.77,53.89Zm13,93.71A15.89,15.89,0,0,1,198.21,160H152a32,32,0,0,0-32,32,16,16,0,0,1-21.31,15.07C62.49,194.3,40,164,40,128a88,88,0,0,1,87.09-88h.9a88.35,88.35,0,0,1,88,87.25A88.86,88.86,0,0,1,213.81,147.6ZM140,76a12,12,0,1,1-12-12A12,12,0,0,1,140,76ZM96,100A12,12,0,1,1,84,88,12,12,0,0,1,96,100Zm0,56a12,12,0,1,1-12-12A12,12,0,0,1,96,156Zm88-56a12,12,0,1,1-12-12A12,12,0,0,1,184,100Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Phosphor "cloud" (regular, MIT): the Settings group icon on PDF p.36. */
    val Cloud: ImageVector by lazy {
        ImageVector.Builder(name = "Cloud", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M160,40A88.09,88.09,0,0,0,81.29,88.67,64,64,0,1,0,72,216h88a88,88,0,0,0,0-176Zm0,160H72a48,48,0,0,1,0-96c1.1,0,2.2,0,3.29.11A88,88,0,0,0,72,128a8,8,0,0,0,16,0,72,72,0,1,1,72,72Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Phosphor "database" (regular, MIT): the Settings group icon on PDF p.36. */
    val Database: ImageVector by lazy {
        ImageVector.Builder(name = "Database", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M128,24C74.17,24,32,48.6,32,80v96c0,31.4,42.17,56,96,56s96-24.6,96-56V80C224,48.6,181.83,24,128,24Zm80,104c0,9.62-7.88,19.43-21.61,26.92C170.93,163.35,150.19,168,128,168s-42.93-4.65-58.39-13.08C55.88,147.43,48,137.62,48,128V111.36c17.06,15,46.23,24.64,80,24.64s62.94-9.68,80-24.64ZM69.61,53.08C85.07,44.65,105.81,40,128,40s42.93,4.65,58.39,13.08C200.12,60.57,208,70.38,208,80s-7.88,19.43-21.61,26.92C170.93,115.35,150.19,120,128,120s-42.93-4.65-58.39-13.08C55.88,99.43,48,89.62,48,80S55.88,60.57,69.61,53.08ZM186.39,202.92C170.93,211.35,150.19,216,128,216s-42.93-4.65-58.39-13.08C55.88,195.43,48,185.62,48,176V159.36c17.06,15,46.23,24.64,80,24.64s62.94-9.68,80-24.64V176C208,185.62,200.12,195.43,186.39,202.92Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Phosphor "question" (regular, MIT): the Settings group icon on PDF p.36. */
    val Question: ImageVector by lazy {
        ImageVector.Builder(name = "Question", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M140,180a12,12,0,1,1-12-12A12,12,0,0,1,140,180ZM128,72c-22.06,0-40,16.15-40,36v4a8,8,0,0,0,16,0v-4c0-11,10.77-20,24-20s24,9,24,20-10.77,20-24,20a8,8,0,0,0-8,8v8a8,8,0,0,0,16,0v-.72c18.24-3.35,32-17.9,32-35.28C168,88.15,150.06,72,128,72Zm104,56A104,104,0,1,1,128,24,104.11,104.11,0,0,1,232,128Zm-16,0a88,88,0,1,0-88,88A88.1,88.1,0,0,0,216,128Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Phosphor "download-simple" (regular, MIT): the Settings group icon on PDF p.36. */
    val DownloadSimple: ImageVector by lazy {
        ImageVector.Builder(name = "DownloadSimple", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0Zm-101.66,5.66a8,8,0,0,0,11.32,0l40-40a8,8,0,0,0-11.32-11.32L136,124.69V32a8,8,0,0,0-16,0v92.69L93.66,98.34a8,8,0,0,0-11.32,11.32Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Phosphor "push-pin" (regular, MIT): the slanted pin the PDF draws on Quick Add and Entry detail (p.15, p.20). Replaces the upright thumbtack drawn in 11c. */
    val Pin: ImageVector by lazy {
        ImageVector.Builder(name = "Pin", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = PathParser().parsePathString("M235.32,81.37,174.63,20.69a16,16,0,0,0-22.63,0L98.37,74.49c-10.66-3.34-35-7.37-60.4,13.14a16,16,0,0,0-1.29,23.78L85,159.71,42.34,202.34a8,8,0,0,0,11.32,11.32L96.29,171l48.29,48.29A16,16,0,0,0,155.9,224c.38,0,.75,0,1.13,0a15.93,15.93,0,0,0,11.64-6.33c19.64-26.1,17.75-47.32,13.19-60L235.33,104A16,16,0,0,0,235.32,81.37ZM224,92.69h0l-57.27,57.46a8,8,0,0,0-1.49,9.22c9.46,18.93-1.8,38.59-9.34,48.62L48,100.08c12.08-9.74,23.64-12.31,32.48-12.31A40.13,40.13,0,0,1,96.81,91a8,8,0,0,0,9.25-1.51L163.32,32,224,92.68Z").toNodes(), fill = SolidColor(Color.Black))
            .build()
    }

    /** Drawn here, not from Phosphor (11c, D285): one line forking into two arrows, for Split. */
    val Split: ImageVector by lazy { stroked("Split", "M128,216V136M128,136L56,64M128,136L200,64M56,104V64H96M200,104V64H160") }
}
