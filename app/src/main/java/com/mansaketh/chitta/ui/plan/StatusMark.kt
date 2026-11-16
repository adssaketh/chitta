package com.mansaketh.chitta.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.BillStatus

/** The five states a bill can show on the calendar. Drawn, not typed, so they look the same on every phone font. */
enum class Mark { Due, Overdue, Paid, Skipped, Later }

fun markOf(status: BillStatus, projected: Boolean): Mark = when {
    projected -> Mark.Later
    status == BillStatus.Overdue -> Mark.Overdue
    status == BillStatus.Upcoming -> Mark.Due
    status == BillStatus.Paid -> Mark.Paid
    else -> Mark.Skipped
}

/** A small drawn mark: filled dot (due), triangle (overdue), tick (paid), dash (skipped), ring (comes later). The words next to it say what it means. */
@Composable
fun StatusMark(mark: Mark, color: Color, modifier: Modifier = Modifier) {
    val stroke = Dimens.MarkStroke
    Canvas(modifier.size(Dimens.IconSmall).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val sw = stroke.toPx()
        when (mark) {
            Mark.Due -> drawCircle(color, radius = w * 0.3f, center = Offset(w / 2, h / 2))
            Mark.Later -> drawCircle(color, radius = w * 0.3f - sw / 2, center = Offset(w / 2, h / 2), style = Stroke(sw))
            Mark.Overdue -> drawPath(Path().apply { moveTo(w / 2, h * 0.18f); lineTo(w * 0.85f, h * 0.8f); lineTo(w * 0.15f, h * 0.8f); close() }, color)
            Mark.Paid -> drawPath(Path().apply { moveTo(w * 0.2f, h * 0.55f); lineTo(w * 0.42f, h * 0.75f); lineTo(w * 0.8f, h * 0.28f) }, color, style = Stroke(sw))
            Mark.Skipped -> drawRect(color, topLeft = Offset(w * 0.2f, h / 2 - sw / 2), size = Size(w * 0.6f, sw))
        }
    }
}
