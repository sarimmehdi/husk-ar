package com.sarim.husk.ar

import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/**
 * Re-expressing the camera in the marker's frame.
 *
 * ARCore reports both the camera and the marker relative to a session origin that moves between
 * runs. Husk stores everything relative to the marker, which is what lets a session recorded today
 * mean the same thing tomorrow, so every capture has to be converted on the way in.
 *
 * A mistake here is not a crash. It produces a shell in a plausible place that is simply not where
 * the object was, and no later step can tell.
 */
class CameraSnapshotTest {
    private fun assertPoseEquals(
        expected: Pose,
        actual: Pose,
    ) {
        assertEquals(expected.translation.x, actual.translation.x, TOLERANCE)
        assertEquals(expected.translation.y, actual.translation.y, TOLERANCE)
        assertEquals(expected.translation.z, actual.translation.z, TOLERANCE)
        // Compare what the rotation does, since a quaternion and its negation are the same turn.
        listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).forEach {
            val want = expected.rotation.rotate(it)
            val got = actual.rotation.rotate(it)
            assertEquals(want.x, got.x, TOLERANCE)
            assertEquals(want.y, got.y, TOLERANCE)
            assertEquals(want.z, got.z, TOLERANCE)
        }
    }

    @Test
    fun `a camera sitting on the marker is at the marker frame's origin`() {
        val markerInWorld = Pose(Vector3(3.0, -1.0, 7.0), Quaternion.IDENTITY)

        val cameraInMarker = cameraInMarkerFrame(cameraInWorld = markerInWorld, markerInWorld = markerInWorld)

        assertPoseEquals(Pose.IDENTITY, cameraInMarker)
    }

    @Test
    fun `the session origin drops out of the answer`() {
        // The whole point. Two runs place the same physical arrangement at different world
        // coordinates; both must produce the same pose in the marker's frame.
        val firstRun =
            cameraInMarkerFrame(
                cameraInWorld = Pose(Vector3(1.0, 0.0, 0.0), Quaternion.IDENTITY),
                markerInWorld = Pose(Vector3(0.0, 0.0, 0.0), Quaternion.IDENTITY),
            )
        val secondRun =
            cameraInMarkerFrame(
                cameraInWorld = Pose(Vector3(101.0, 50.0, -20.0), Quaternion.IDENTITY),
                markerInWorld = Pose(Vector3(100.0, 50.0, -20.0), Quaternion.IDENTITY),
            )

        assertPoseEquals(firstRun, secondRun)
    }

    @Test
    fun `a camera offset from the marker keeps that offset`() {
        val cameraInMarker =
            cameraInMarkerFrame(
                cameraInWorld = Pose(Vector3(0.0, 0.0, 2.0), Quaternion.IDENTITY),
                markerInWorld = Pose(Vector3(0.0, 0.0, 0.0), Quaternion.IDENTITY),
            )

        assertPoseEquals(Pose(Vector3(0.0, 0.0, 2.0), Quaternion.IDENTITY), cameraInMarker)
    }

    @Test
    fun `a turned marker turns the camera's offset with it`() {
        // The offset is expressed in the marker's axes, not the world's. Skipping the rotation and
        // subtracting positions alone would be right only while the marker faced world forward.
        val quarterTurnAboutY = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 2.0)

        val cameraInMarker =
            cameraInMarkerFrame(
                cameraInWorld = Pose(Vector3(0.0, 0.0, 2.0), Quaternion.IDENTITY),
                markerInWorld = Pose(Vector3.ZERO, quarterTurnAboutY),
            )

        // Turning the frame a quarter turn about y sends world +z onto the frame's -x.
        assertEquals(-2.0, cameraInMarker.translation.x, TOLERANCE)
        assertEquals(0.0, cameraInMarker.translation.y, TOLERANCE)
        assertEquals(0.0, cameraInMarker.translation.z, TOLERANCE)
    }

    @Test
    fun `converting back through the marker recovers the world pose`() {
        val markerInWorld =
            Pose(Vector3(1.5, -0.5, 2.0), Quaternion.fromAxisAngle(Vector3(0.3, 1.0, -0.2), 0.9))
        val cameraInWorld =
            Pose(Vector3(-2.0, 1.0, 0.25), Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.4), 2.2))

        val cameraInMarker = cameraInMarkerFrame(cameraInWorld, markerInWorld)

        assertPoseEquals(cameraInWorld, markerInWorld * cameraInMarker)
    }

    @Test
    fun `an ARCore pose becomes a geometry pose without reordering the quaternion`() {
        // ARCore hands back a float array ordered x, y, z, w. Reading it as w first would leave a
        // unit quaternion describing an entirely different turn.
        val pose =
            poseFrom(
                translation = floatArrayOf(1f, 2f, 3f),
                rotationQuaternion = floatArrayOf(0.1f, 0.2f, 0.3f, 0.9f),
            )

        assertEquals(1.0, pose.translation.x, FLOAT_TOLERANCE)
        assertEquals(2.0, pose.translation.y, FLOAT_TOLERANCE)
        assertEquals(3.0, pose.translation.z, FLOAT_TOLERANCE)
        assertEquals(0.1, pose.rotation.x, FLOAT_TOLERANCE)
        assertEquals(0.2, pose.rotation.y, FLOAT_TOLERANCE)
        assertEquals(0.3, pose.rotation.z, FLOAT_TOLERANCE)
        assertEquals(0.9, pose.rotation.w, FLOAT_TOLERANCE)
    }

    @Test
    fun `a snapshot maps a preview of its own shape without cropping`() {
        val snapshot =
            CameraSnapshot(
                cameraInMarker = Pose.IDENTITY,
                intrinsics =
                    com.sarim.husk.geometry
                        .CameraIntrinsics(800.0, 800.0, 640.0, 360.0),
                imageWidth = 640,
                imageHeight = 480,
            )

        val mapping = snapshot.previewMapping(previewWidth = 1280, previewHeight = 960)

        assertEquals(2.0, mapping.scale, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
        const val FLOAT_TOLERANCE = 1e-6
    }
}
