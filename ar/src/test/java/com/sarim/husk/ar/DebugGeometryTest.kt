package com.sarim.husk.ar

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * The geometry the debug view draws.
 *
 * All of it is the inverse of projection, and every sign in it is one that produces a plausible
 * picture when written the wrong way round — a ray pointing where the camera never looked, or a
 * frustum nothing could have seen through. Drawn wrong it is worse than not drawn, because it would
 * be used to explain a bad measurement.
 */
class DebugGeometryTest {
    private val lens = CameraIntrinsics(800.0, 800.0, 320.0, 240.0)

    @Test
    fun `the principal point looks straight ahead`() {
        val direction = rayDirection(lens, 320.0, 240.0)

        assertEquals(0.0, direction.x, TOLERANCE)
        assertEquals(0.0, direction.y, TOLERANCE)
        assertEquals(-1.0, direction.z, TOLERANCE)
    }

    @Test
    fun `a pixel right of centre looks right`() {
        assertTrue(rayDirection(lens, 520.0, 240.0).x > 0.0)
    }

    @Test
    fun `a pixel above centre looks up`() {
        // Image rows count downward and the camera's y counts up, so a smaller row is higher. Miss
        // this and every debug ray is mirrored vertically.
        assertTrue(rayDirection(lens, 320.0, 40.0).y > 0.0)
    }

    @Test
    fun `a ray is unit length`() {
        val direction = rayDirection(lens, 500.0, 100.0)
        val length =
            kotlin.math.sqrt(
                direction.x * direction.x + direction.y * direction.y + direction.z * direction.z,
            )

        assertEquals(1.0, length, TOLERANCE)
    }

    @Test
    fun `the frustum has eight corners`() {
        assertEquals(8, frustumCorners(lens, 640, 480, 0.1, 2.0).size)
    }

    @Test
    fun `near corners sit on the near plane, not on a sphere around the camera`() {
        // Scaling along the ray instead would bow the plane towards the camera at the corners and
        // draw a volume nothing could have seen through.
        val corners = frustumCorners(lens, 640, 480, 0.5, 2.0)

        corners.take(4).forEach { assertEquals(-0.5, it.z, TOLERANCE) }
        corners.drop(4).forEach { assertEquals(-2.0, it.z, TOLERANCE) }
    }

    @Test
    fun `the frustum widens with distance`() {
        val corners = frustumCorners(lens, 640, 480, 0.5, 2.0)

        val nearWidth = kotlin.math.abs(corners[1].x - corners[0].x)
        val farWidth = kotlin.math.abs(corners[5].x - corners[4].x)
        assertTrue("far plane must be wider, got $nearWidth then $farWidth", farWidth > nearWidth)
    }

    @Test
    fun `the frustum is centred on the axis for a centred principal point`() {
        val corners = frustumCorners(lens, 640, 480, 1.0, 2.0)

        assertEquals(0.0, corners.take(4).sumOf { it.x } / 4.0, TOLERANCE)
        assertEquals(0.0, corners.take(4).sumOf { it.y } / 4.0, TOLERANCE)
    }

    @Test
    fun `an off centre principal point shifts the frustum off the axis`() {
        // Phone lenses are not perfectly centred, and a frustum drawn as though they were would
        // disagree with where the shells actually appear.
        val offCentre = CameraIntrinsics(800.0, 800.0, 400.0, 240.0)

        val corners = frustumCorners(offCentre, 640, 480, 1.0, 2.0)

        assertTrue("expected the volume to lean left", corners.take(4).sumOf { it.x } / 4.0 < 0.0)
    }

    @Test
    fun `axes are drawn in the frame of the pose they belong to`() {
        // The marker's axes have to turn with the marker. Drawn in world axes they would stay put
        // while everything else moved, which is exactly the confusion a debug view should remove.
        val turned = Pose(Vector3(1.0, 0.0, 0.0), Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 2.0))

        val ends = axisEnds(turned, lengthMetres = 1.0)

        // The pose's local x, a quarter turn about y, points along the world's negative z.
        assertEquals(1.0, ends[0].x, TOLERANCE)
        assertEquals(-1.0, ends[0].z, TOLERANCE)
    }

    @Test
    fun `axes start from the pose's position`() {
        val ends = axisEnds(Pose(Vector3(2.0, 3.0, 4.0), Quaternion.IDENTITY), lengthMetres = 0.5)

        assertEquals(2.5, ends[0].x, TOLERANCE)
        assertEquals(3.5, ends[1].y, TOLERANCE)
        assertEquals(4.5, ends[2].z, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
