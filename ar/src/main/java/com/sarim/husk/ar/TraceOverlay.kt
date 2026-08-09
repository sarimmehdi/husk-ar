package com.sarim.husk.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/**
 * The surface a person drags on to outline an object.
 *
 * Drag out a box; the ellipse inscribed in it is the trace. A box is far easier to place accurately
 * than a freehand curve, and it cannot produce a shape the solver has to reject.
 *
 * Traces are reported in preview pixels. Turning them into something the solver can use needs the
 * camera image's dimensions, which this has no business knowing — see [TracedEllipse.toDualConic].
 */
@Composable
fun TraceOverlay(
    listener: TraceListener,
    strokeColour: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(Unit) {
                    // Written out rather than using detectDragGestures, which reports the position
                    // at which touch slop was crossed rather than where the finger went down. That
                    // would anchor every box a slop's distance from where the person aimed, biased
                    // in whichever direction they happened to start moving.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position
                        start = origin
                        current = origin
                        // Reported once, when the finger lands. A caller that has to infer the
                        // start from the first movement would re-mark it on every movement, and
                        // anything comparing the frame then against the frame now would always be
                        // comparing two frames a few milliseconds apart.
                        listener.onStarted()

                        drag(down.id) { change ->
                            change.consume()
                            current = change.position
                            listener.onChanged(traceBetween(origin, change.position))
                        }

                        val trace = traceBetween(origin, current ?: origin)
                        // Only a real drag is committed. A tap that never moved is a miss, and
                        // committing it would spend one of the three views the solver needs on a
                        // view of nothing.
                        if (trace.isUsable) listener.onCommitted(trace)
                        start = null
                        current = null
                    }
                },
    ) {
        val from = start
        val to = current
        if (from != null && to != null) {
            drawTrace(from, to, strokeColour)
        }
    }
}

private fun traceBetween(
    from: Offset,
    to: Offset,
) = TracedEllipse.fromDragBox(
    startX = from.x,
    startY = from.y,
    endX = to.x,
    endY = to.y,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrace(
    from: Offset,
    to: Offset,
    colour: Color,
) {
    val topLeft = Offset(minOf(from.x, to.x), minOf(from.y, to.y))
    val size = Size(abs(to.x - from.x), abs(to.y - from.y))
    drawOval(
        color = colour,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = STROKE_WIDTH_PIXELS),
    )
}

private const val STROKE_WIDTH_PIXELS = 4f
