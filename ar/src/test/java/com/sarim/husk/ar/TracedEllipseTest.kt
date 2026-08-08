package com.sarim.husk.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning what someone drew on the screen into what the solver measures.
 *
 * The trace happens in preview pixels; the camera intrinsics describe the camera image. Those are
 * different coordinate systems whenever the preview is not the same shape as the sensor output,
 * which is almost always. Nothing downstream can detect the mistake — a trace mapped with the wrong
 * scale still produces a perfectly valid ellipse, just of the wrong object.
 */
class TracedEllipseTest {
    /** A 16:9 preview showing a 4:3 image, centre-cropped: the usual case on a phone. */
    private val cropped =
        PreviewMapping(
            imageWidth = 640,
            imageHeight = 480,
            previewWidth = 1080,
            previewHeight = 2400,
        )

    /** Preview and image the same shape, so nothing is cropped and only scale differs. */
    private val sameShape =
        PreviewMapping(
            imageWidth = 640,
            imageHeight = 480,
            previewWidth = 1280,
            previewHeight = 960,
        )

    @Test
    fun `the preview centre is the image centre`() {
        // Centre-cropping trims equally from both sides, so whatever else moves, the middle does
        // not. If this fails nothing else about the mapping is worth checking.
        val (x, y) = cropped.toImage(1080f / 2f, 2400f / 2f)

        assertEquals(320.0, x, TOLERANCE)
        assertEquals(240.0, y, TOLERANCE)
    }

    @Test
    fun `a plain rescale maps corners to corners`() {
        val (x, y) = sameShape.toImage(0f, 0f)

        assertEquals(0.0, x, TOLERANCE)
        assertEquals(0.0, y, TOLERANCE)
    }

    @Test
    fun `a plain rescale halves preview coordinates`() {
        val (x, y) = sameShape.toImage(640f, 480f)

        assertEquals(320.0, x, TOLERANCE)
        assertEquals(240.0, y, TOLERANCE)
    }

    @Test
    fun `the image is scaled to fill the preview rather than fit inside it`() {
        // Filling is what the camera preview actually does. Fitting would leave bars, and mapping as
        // though it fitted puts every trace off by the ratio of the two aspect ratios.
        assertEquals(2400.0 / 480.0, cropped.scale, TOLERANCE)
    }

    @Test
    fun `cropping happens on the axis with room to spare`() {
        // A tall preview showing a 4:3 image crops the sides, not the top. A trace at the very left
        // of the preview therefore lands inside the image, not before it.
        val (x, _) = cropped.toImage(0f, 1200f)

        assertTrue("expected the left edge to land inside the image, got $x", x > 0.0)
        assertTrue("expected the left edge to land left of centre, got $x", x < 320.0)
    }

    @Test
    fun `an ellipse traced in the middle of the preview is centred in the image`() {
        val traced =
            TracedEllipse(
                centreX = 540f,
                centreY = 1200f,
                semiMajor = 200f,
                semiMinor = 100f,
                rotationRadians = 0f,
            )

        val parameters = traced.toDualConic(cropped).toParameters()

        assertEquals(320.0, parameters.centreX, TOLERANCE)
        assertEquals(240.0, parameters.centreY, TOLERANCE)
    }

    @Test
    fun `axes shrink by the same factor as positions`() {
        // Scaling the centre but not the axes is the classic version of this mistake, and it draws
        // an overlay in the right place at the wrong size.
        val traced =
            TracedEllipse(
                centreX = 540f,
                centreY = 1200f,
                semiMajor = 200f,
                semiMinor = 100f,
                rotationRadians = 0f,
            )

        val parameters = traced.toDualConic(cropped).toParameters()

        assertEquals(200.0 / cropped.scale, parameters.semiMajor, TOLERANCE)
        assertEquals(100.0 / cropped.scale, parameters.semiMinor, TOLERANCE)
    }

    @Test
    fun `a uniform scale leaves the orientation alone`() {
        val traced =
            TracedEllipse(
                centreX = 540f,
                centreY = 1200f,
                semiMajor = 200f,
                semiMinor = 100f,
                rotationRadians = 0.7f,
            )

        val parameters = traced.toDualConic(cropped).toParameters()

        assertEquals(0.7, parameters.rotationRadians, 1e-6)
    }

    @Test
    fun `an off centre trace stays off centre in the same direction`() {
        val traced =
            TracedEllipse(
                centreX = 540f + 100f,
                centreY = 1200f - 200f,
                semiMajor = 50f,
                semiMinor = 50f,
                rotationRadians = 0f,
            )

        val parameters = traced.toDualConic(cropped).toParameters()

        assertEquals(320.0 + 100.0 / cropped.scale, parameters.centreX, TOLERANCE)
        assertEquals(240.0 - 200.0 / cropped.scale, parameters.centreY, TOLERANCE)
    }

    @Test
    fun `a trace from a drag box inscribes the box`() {
        // Dragging out a box is the gesture; the ellipse is inscribed in it, not drawn through its
        // corners, so the shell matches the extent the person indicated.
        val traced = TracedEllipse.fromDragBox(startX = 100f, startY = 300f, endX = 500f, endY = 700f)

        assertEquals(300f, traced.centreX, FLOAT_TOLERANCE)
        assertEquals(500f, traced.centreY, FLOAT_TOLERANCE)
        assertEquals(200f, traced.semiMajor, FLOAT_TOLERANCE)
        assertEquals(200f, traced.semiMinor, FLOAT_TOLERANCE)
    }

    @Test
    fun `a drag box works in any direction`() {
        // People drag up and to the left as readily as down and to the right, and a negative width
        // would otherwise produce an ellipse with negative axes.
        val downRight = TracedEllipse.fromDragBox(startX = 100f, startY = 300f, endX = 500f, endY = 700f)
        val upLeft = TracedEllipse.fromDragBox(startX = 500f, startY = 700f, endX = 100f, endY = 300f)

        assertEquals(downRight.centreX, upLeft.centreX, FLOAT_TOLERANCE)
        assertEquals(downRight.centreY, upLeft.centreY, FLOAT_TOLERANCE)
        assertEquals(downRight.semiMajor, upLeft.semiMajor, FLOAT_TOLERANCE)
        assertEquals(downRight.semiMinor, upLeft.semiMinor, FLOAT_TOLERANCE)
    }

    @Test
    fun `a wide drag box keeps its major axis along the drag`() {
        val traced = TracedEllipse.fromDragBox(startX = 0f, startY = 0f, endX = 400f, endY = 100f)

        assertEquals(200f, traced.semiMajor, FLOAT_TOLERANCE)
        assertEquals(50f, traced.semiMinor, FLOAT_TOLERANCE)
        assertEquals(0f, traced.rotationRadians, FLOAT_TOLERANCE)
    }

    @Test
    fun `a tall drag box turns a quarter so its major axis is vertical`() {
        // semiMajor is the longer one by definition, so a tall box has to carry its orientation in
        // the rotation rather than by swapping the axes over.
        val traced = TracedEllipse.fromDragBox(startX = 0f, startY = 0f, endX = 100f, endY = 400f)

        assertEquals(200f, traced.semiMajor, FLOAT_TOLERANCE)
        assertEquals(50f, traced.semiMinor, FLOAT_TOLERANCE)
        assertEquals(HALF_PI, kotlin.math.abs(traced.rotationRadians), FLOAT_TOLERANCE)
    }

    @Test
    fun `a tap with no drag is not a usable trace`() {
        // Tapping without dragging is a miss, not a request to measure a point, and a zero axis
        // conic is exactly what the solver rejects as degenerate.
        val traced = TracedEllipse.fromDragBox(startX = 200f, startY = 200f, endX = 200f, endY = 200f)

        assertTrue("a zero sized drag should not be usable", !traced.isUsable)
    }

    @Test
    fun `a real drag is usable`() {
        val traced = TracedEllipse.fromDragBox(startX = 100f, startY = 100f, endX = 300f, endY = 260f)

        assertTrue(traced.isUsable)
    }

    private companion object {
        const val TOLERANCE = 1e-9
        const val FLOAT_TOLERANCE = 1e-4f
        const val HALF_PI = (Math.PI / 2.0).toFloat()
    }
}
