package com.sarim.husk.ar

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Pointing at a shell that is not on screen.
 *
 * The interesting case is behind the camera. Projection divides by depth, which for anything behind
 * is negative, and that mirrors the answer — so an arrow built from a projection sends someone
 * turning away from the very thing they are looking for.
 */
class OffscreenArrowTest {
    private val lens = CameraIntrinsics(800.0, 800.0, 320.0, 240.0)
    private val atOrigin = Pose(Vector3.ZERO, Quaternion.IDENTITY)

    private fun arrowTo(
        point: Vector3,
        camera: Pose = atOrigin,
    ) = offscreenArrowTo(point, camera, lens, imageWidth = 640, imageHeight = 480)

    /** Angles are compared as directions, so that pi and minus pi count as the same way round. */
    private fun assertPointsAt(
        expected: Double,
        actual: Float,
    ) {
        val difference = Math.toDegrees(atan2Difference(expected, actual.toDouble()))
        assertTrue("expected about $expected, got $actual (off by $difference degrees)", abs(difference) < 1.0)
    }

    private fun atan2Difference(
        a: Double,
        b: Double,
    ): Double {
        var difference = a - b
        while (difference > PI) difference -= 2 * PI
        while (difference < -PI) difference += 2 * PI
        return difference
    }

    @Test
    fun `something straight ahead needs no arrow`() {
        assertNull(arrowTo(Vector3(0.0, 0.0, -2.0)))
    }

    @Test
    fun `something just inside the frame needs no arrow`() {
        // At two metres the half width of the view is 2 * 320 / 800 = 0.8m, so this is inside.
        assertNull(arrowTo(Vector3(0.7, 0.0, -2.0)))
    }

    @Test
    fun `something off to the right points right`() {
        val arrow = requireNotNull(arrowTo(Vector3(5.0, 0.0, -2.0)))

        assertPointsAt(0.0, arrow.angleRadians)
        assertFalse(arrow.isBehind)
    }

    @Test
    fun `something off to the left points left`() {
        assertPointsAt(PI, requireNotNull(arrowTo(Vector3(-5.0, 0.0, -2.0))).angleRadians)
    }

    @Test
    fun `something above points up the screen`() {
        // Screen y counts downward, so up the screen is a negative angle.
        assertPointsAt(-PI / 2.0, requireNotNull(arrowTo(Vector3(0.0, 5.0, -2.0))).angleRadians)
    }

    @Test
    fun `something below points down the screen`() {
        assertPointsAt(PI / 2.0, requireNotNull(arrowTo(Vector3(0.0, -5.0, -2.0))).angleRadians)
    }

    @Test
    fun `something behind and to the right still points right`() {
        // The case a projection gets exactly backwards. Dividing by a negative depth would put this
        // on the left of the screen and turn the holder away from what they are looking for.
        val arrow = requireNotNull(arrowTo(Vector3(1.0, 0.0, 5.0)))

        assertPointsAt(0.0, arrow.angleRadians)
        assertTrue("it is behind, and the screen should be able to say so", arrow.isBehind)
    }

    @Test
    fun `something behind and to the left still points left`() {
        assertPointsAt(PI, requireNotNull(arrowTo(Vector3(-1.0, 0.0, 5.0))).angleRadians)
    }

    @Test
    fun `something directly behind is reported as behind`() {
        assertTrue(requireNotNull(arrowTo(Vector3(0.0, 0.0, 5.0))).isBehind)
    }

    @Test
    fun `the arrow follows where the camera is pointing`() {
        // Turned to face it, the same shell needs no arrow at all.
        val turnedToFaceIt =
            Pose(Vector3.ZERO, Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), -PI / 2.0))

        assertNull(arrowTo(Vector3(2.0, 0.0, 0.0), camera = turnedToFaceIt))
        requireNotNull(arrowTo(Vector3(2.0, 0.0, 0.0), camera = atOrigin))
    }

    @Test
    fun `an arrow is measured from where the camera is, not from the marker`() {
        val steppedRight = Pose(Vector3(4.0, 0.0, 0.0), Quaternion.IDENTITY)

        // The shell is at the marker's origin, which is now to the camera's left.
        assertPointsAt(PI, requireNotNull(arrowTo(Vector3.ZERO, camera = steppedRight)).angleRadians)
    }
}
