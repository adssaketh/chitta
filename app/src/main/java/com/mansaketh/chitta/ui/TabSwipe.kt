package com.mansaketh.chitta.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Finds a sideways swipe anywhere on a screen and calls [onSwipe] with -1 (finger moved left: next tab) or +1 (moved right: previous tab).
 *
 * Why this and not detectHorizontalDragGestures: that one only starts after the rows and lists under the finger have had their say, so a
 * slightly diagonal swipe was often taken by the list and lost. Here the touch is watched first (nothing is consumed, so taps, long-presses
 * and scrolling still work) and the direction is decided by which way the finger moves more. A short quick flick counts as well as a long drag.
 * If a child that scrolls sideways (a tab strip) takes the movement, the swipe is dropped.
 */
suspend fun PointerInputScope.detectTabSwipe(onSwipe: (direction: Int) -> Unit) {
    val slop = viewConfiguration.touchSlop
    val minDistance = 56.dp.toPx()
    val flickDistance = 24.dp.toPx()
    val flickSpeed = 450.dp.toPx() // per second
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dx = 0f
        var dy = 0f
        var sideways = false
        var vertical = false
        var takenByChild = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.size > 1) { vertical = true } // two fingers: leave it to zoom or scroll
            if (!change.pressed) break
            tracker.addPosition(change.uptimeMillis, change.position)
            val move = change.positionChange()
            dx += move.x
            dy += move.y
            if (!sideways && !vertical) {
                if (abs(dx) > slop || abs(dy) > slop) {
                    if (abs(dx) > abs(dy) * 1.2f) sideways = true else vertical = true
                }
            }
            if (sideways) {
                // After the children have seen it: if one of them used the movement, it owns this gesture.
                val after = awaitPointerEvent(PointerEventPass.Final)
                if (after.changes.any { it.isConsumed }) takenByChild = true
                if (after.changes.none { it.pressed }) break
            }
        }
        if (!sideways || vertical || takenByChild) return@awaitEachGesture
        val speed = tracker.calculateVelocity().x
        val far = abs(dx) >= minDistance
        val flick = abs(dx) >= flickDistance && abs(speed) >= flickSpeed && (speed > 0) == (dx > 0)
        if (far || flick) onSwipe(if (dx < 0) -1 else 1)
    }
}
