package app.silverbox.ui.print

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

/** How close the print in front is brought up, and which part of it is in the middle. */
@Stable
internal class Zoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    val zoomed get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Scales by [k] round [centroid] and moves by [pan], keeping the spot under the fingers under them. */
    fun change(k: Float, pan: Offset, centroid: Offset, size: IntSize) {
        val s = (scale * k).coerceIn(1f, MAX)
        val c = centroid - Offset(size.width / 2f, size.height / 2f)
        set(s, c - (c - offset) * (s / scale) + pan, size)
    }

    /** A double tap: up close on that spot, or back to the whole print. */
    fun toggle(at: Offset, size: IntSize) {
        if (zoomed) return reset()
        val c = at - Offset(size.width / 2f, size.height / 2f)
        set(TAP, c - c * TAP, size)
    }

    private fun set(s: Float, o: Offset, size: IntSize) {
        // Never so far that an edge of the print comes away from the edge of its place.
        val mx = (s - 1) * size.width / 2f
        val my = (s - 1) * size.height / 2f
        scale = s
        offset = Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    companion object {
        const val MAX = 5f
        const val TAP = 2.5f
    }
}

/**
 * Two fingers bring the print up close; once close, one finger moves round it. A double tap goes close or
 * back, a single tap still turns the print over. At its own size a one-finger swipe is left to the pager.
 */
internal fun Modifier.zoomable(zoom: Zoom, onTap: () -> Unit): Modifier = this
    .pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                // The moment the last finger lifts there is nothing left to measure from.
                val centroid = event.calculateCentroid(useCurrent = true)
                if ((fingers >= 2 || zoom.zoomed) && centroid.isSpecified) {
                    zoom.change(event.calculateZoom(), event.calculatePan(), centroid, size)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.toggle(it, size) }, onTap = { if (!zoom.zoomed) onTap() }) }
    .graphicsLayer {
        scaleX = zoom.scale
        scaleY = zoom.scale
        translationX = zoom.offset.x
        translationY = zoom.offset.y
    }
