package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CameraIntrinsicsTest {
    private val intrinsics =
        CameraIntrinsics(
            focalLengthX = 800.0,
            focalLengthY = 800.0,
            principalPointX = 640.0,
            principalPointY = 360.0,
        )

    /** Where [point] lands in the image, and how far in front of the camera it sits. */
    private fun project(
        point: Vector3,
        cameraInAnchor: Pose,
    ): Triple<Double, Double, Double> {
        val p = intrinsics.projectionFrom(cameraInAnchor)
        val homogeneous = doubleArrayOf(point.x, point.y, point.z, 1.0)
        val image = DoubleArray(3) { row -> (0..3).sumOf { p[row][it] * homogeneous[it] } }
        return Triple(image[0] / image[2], image[1] / image[2], image[2])
    }

    @Test
    fun `a point on the camera axis lands on the principal point`() {
        // An ARCore camera at rest looks down its own negative z, so this point is dead ahead.
        val (x, y, depth) = project(Vector3(0.0, 0.0, -2.0), Pose.IDENTITY)

        assertEquals(640.0, x, TOLERANCE)
        assertEquals(360.0, y, TOLERANCE)
        assertTrue("a point ahead of the camera must have positive depth", depth > 0.0)
    }

    @Test
    fun `a point to the right of the axis lands right of the principal point`() {
        val (x, _, _) = project(Vector3(0.5, 0.0, -2.0), Pose.IDENTITY)

        assertEquals(640.0 + 800.0 * 0.25, x, TOLERANCE)
    }

    @Test
    fun `a point above the axis lands above the principal point`() {
        // The one place the two conventions genuinely disagree. ARCore counts y upward, image rows
        // count downward, so raising the point has to lower the row. Dropping the flip would put
        // every traced ellipse on the wrong side of the frame and yield a mirrored ellipsoid.
        val (_, y, _) = project(Vector3(0.0, 0.5, -2.0), Pose.IDENTITY)

        assertEquals(360.0 - 800.0 * 0.25, y, TOLERANCE)
        assertTrue("up in the world has to mean up in the image", y < 360.0)
    }

    @Test
    fun `a point behind the camera has negative depth`() {
        val (_, _, depth) = project(Vector3(0.0, 0.0, 2.0), Pose.IDENTITY)

        assertTrue("a point behind the camera must be rejectable by its sign", depth < 0.0)
    }

    @Test
    fun `moving the camera back halves the apparent offset`() {
        val near = project(Vector3(0.5, 0.0, -2.0), Pose.IDENTITY)
        val far =
            project(
                Vector3(0.5, 0.0, -2.0),
                Pose(Vector3(0.0, 0.0, 2.0), Quaternion.IDENTITY),
            )

        assertEquals(640.0 + 800.0 * 0.25, near.first, TOLERANCE)
        assertEquals(640.0 + 800.0 * 0.125, far.first, TOLERANCE)
    }

    @Test
    fun `turning the camera sweeps the world the other way`() {
        // Yaw the camera left about the anchor's up axis and a point that was dead ahead has to
        // appear to its right.
        val turnedLeft =
            Pose(
                translation = Vector3.ZERO,
                rotation = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 6.0),
            )

        val (x, _, depth) = project(Vector3(0.0, 0.0, -2.0), turnedLeft)

        assertTrue("the point stays in front of the camera", depth > 0.0)
        assertTrue("turning left pushes a fixed point right in the image", x > 640.0)
    }

    @Test
    fun `a displaced camera measures from its own position`() {
        // Camera stepped one metre right; a point one metre right of the origin is now dead ahead.
        val steppedRight = Pose(Vector3(1.0, 0.0, 0.0), Quaternion.IDENTITY)

        val (x, y, _) = project(Vector3(1.0, 0.0, -2.0), steppedRight)

        assertEquals(640.0, x, TOLERANCE)
        assertEquals(360.0, y, TOLERANCE)
    }

    @Test
    fun `focal length scales how far off axis a point appears`() {
        val longLens = intrinsics.copy(focalLengthX = 1600.0)
        val p = longLens.projectionFrom(Pose.IDENTITY)
        val homogeneous = doubleArrayOf(0.5, 0.0, -2.0, 1.0)
        val image = DoubleArray(3) { row -> (0..3).sumOf { p[row][it] * homogeneous[it] } }

        assertEquals(640.0 + 1600.0 * 0.25, image[0] / image[2], TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
