package com.mansaketh.chitta.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Set while a finger is down on something that scrolls sideways by itself (a tab strip), so the tab swipe leaves that touch alone. */
@Stable
object SwipeGuard { @Volatile var childOwns: Boolean = false }

/** Put on a sideways-scrolling strip so a swipe that starts on it scrolls the strip instead of changing tab. */
fun Modifier.ownsSideways(): Modifier = pointerInput(Unit) {
    try {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            SwipeGuard.childOwns = true
            do { val e = awaitPointerEvent(PointerEventPass.Initial) } while (e.changes.any { it.pressed })
            SwipeGuard.childOwns = false
        }
    } finally { SwipeGuard.childOwns = false }
}

/**
 * Finds a sideways swipe anywhere on a screen and calls [onSwipe] with -1 (finger moved left: next tab) or +1 (moved right: previous tab).
 *
 * The touch is watched before the lists and rows under it (nothing is taken until the finger has clearly moved sideways), then, once it has,
 * the movement is taken so the list does not scroll while the tab changes. Before, the list often won a slightly diagonal swipe and the
 * tab did not change. Direction = which way the finger moves more. A short quick flick counts as well as a long drag. Taps, long-presses
 * and ordinary up-down scrolling are untouched.
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
        var ignore = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.size > 1) ignore = true // two fingers: not a tab swipe
            if (!change.pressed) break
            tracker.addPosition(change.uptimeMillis, change.position)
            val move = change.positionChange()
            dx += move.x
            dy += move.y
            if (!sideways && !ignore && (abs(dx) > slop || abs(dy) > slop)) {
                if (abs(dx) > abs(dy) && !SwipeGuard.childOwns) sideways = true else ignore = true
            }
            if (sideways && !ignore) event.changes.forEach { it.consume() }
        }
        if (!sideways || ignore) return@awaitEachGesture
        val speed = tracker.calculateVelocity().x
        val far = abs(dx) >= minDistance
        val flick = abs(dx) >= flickDistance && abs(speed) >= flickSpeed && (speed > 0) == (dx > 0)
        if (far || flick) onSwipe(if (dx < 0) -1 else 1)
    }
}
