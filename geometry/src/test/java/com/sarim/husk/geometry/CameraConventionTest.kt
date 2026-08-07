package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ARCore reports camera poses in the OpenGL convention: the camera looks down **-Z**, with **+Y up**
 * and +X right. The dual-quadric solver, like OpenCV, expects the camera to look down **+Z** with
 * **+Y down** and +X right.
 *
 * Getting this wrong produces ellipsoids that are mirrored, or sitting behind the camera, and it
 * looks like the solver is broken when it is not. These tests pin the conversion by construction
 * rather than by inspection.
 */
class CameraConventionTest {
    @Test
    fun `the camera forward axis flips sign`() {
        // Straight ahead for ARCore is -Z; for the solver it is +Z.
        assertVectorEquals(
            Vector3(0.0, 0.0, 1.0),
            CameraConvention.glToCv(Vector3(0.0, 0.0, -1.0)),
        )
    }

    @Test
    fun `the camera up axis flips sign`() {
        // Up for ARCore is +Y; the solver's image rows run downward, so up becomes -Y.
        assertVectorEquals(
            Vector3(0.0, -1.0, 0.0),
            CameraConvention.glToCv(Vector3(0.0, 1.0, 0.0)),
        )
    }

    @Test
    fun `the camera right axis is unchanged`() {
        assertVectorEquals(
            Vector3(1.0, 0.0, 0.0),
            CameraConvention.glToCv(Vector3(1.0, 0.0, 0.0)),
        )
    }

    @Test
    fun `converting a direction twice returns the original`() {
        val direction = Vector3(0.3, -0.7, 1.4)

        assertVectorEquals(direction, CameraConvention.glToCv(CameraConvention.glToCv(direction)))
    }

    @Test
    fun `converting a pose does not move the camera`() {
        // The translation says where the camera sits in the anchor frame. Reinterpreting the
        // camera's own axes cannot move it, so the translation must survive untouched.
        val cameraInGl =
            Pose(
                translation = Vector3(0.4, 1.1, -2.5),
                rotation = Quaternion.fromAxisAngle(Vector3(0.2, 1.0, -0.5), 1.3),
            )

        assertVectorEquals(cameraInGl.translation, CameraConvention.glToCv(cameraInGl).translation)
    }

    @Test
    fun `the camera still looks the same way after conversion`() {
        // The invariant that matters: forward is -Z in GL axes and +Z in CV axes, and both must
        // name the same physical direction in the anchor frame. A rotation with no zero components
        // is used so a sign error cannot hide behind a zero.
        val rotation = Quaternion.fromAxisAngle(Vector3(0.3, 1.0, -0.7), 1.1)
        val cameraInGl = Pose(translation = Vector3(1.0, 2.0, 3.0), rotation = rotation)

        val cameraInCv = CameraConvention.glToCv(cameraInGl)

        assertVectorEquals(
            rotation.rotate(Vector3(0.0, 0.0, -1.0)),
            cameraInCv.rotation.rotate(Vector3(0.0, 0.0, 1.0)),
        )
    }

    @Test
    fun `the camera still points up the same way after conversion`() {
        // Up is +Y in GL axes and -Y in CV axes, again naming one physical direction.
        val rotation = Quaternion.fromAxisAngle(Vector3(0.3, 1.0, -0.7), 1.1)
        val cameraInGl = Pose(translation = Vector3.ZERO, rotation = rotation)

        val cameraInCv = CameraConvention.glToCv(cameraInGl)

        assertVectorEquals(
            rotation.rotate(Vector3(0.0, 1.0, 0.0)),
            cameraInCv.rotation.rotate(Vector3(0.0, -1.0, 0.0)),
        )
    }

    @Test
    fun `converting a pose twice returns the original`() {
        val pose =
            Pose(
                translation = Vector3(-1.0, 0.5, 4.0),
                rotation = Quaternion.fromAxisAngle(Vector3(1.0, 2.0, -1.0), 0.9),
            )

        val roundTripped = CameraConvention.glToCv(CameraConvention.glToCv(pose))

        assertVectorEquals(pose.translation, roundTripped.translation)
        listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).forEach { axis ->
            assertVectorEquals(pose.rotation.rotate(axis), roundTripped.rotation.rotate(axis))
        }
    }

    private fun assertVectorEquals(
        expected: Vector3,
        actual: Vector3,
    ) {
        assertEquals("x", expected.x, actual.x, TOLERANCE)
        assertEquals("y", expected.y, actual.y, TOLERANCE)
        assertEquals("z", expected.z, actual.z, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
