package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class PoseTest {
    @Test
    fun `the identity pose leaves a point untouched`() {
        val point = Vector3(1.0, 2.0, 3.0)

        assertVectorEquals(point, Pose.IDENTITY.transform(point))
    }

    @Test
    fun `transform rotates before translating`() {
        // Order is the whole point: translating first would put the object somewhere else entirely.
        val pose =
            Pose(
                translation = Vector3(10.0, 0.0, 0.0),
                rotation = Quaternion.fromAxisAngle(Vector3(0.0, 0.0, 1.0), PI / 2),
            )

        assertVectorEquals(Vector3(10.0, 1.0, 0.0), pose.transform(Vector3(1.0, 0.0, 0.0)))
    }

    @Test
    fun `a pose composed with its inverse is the identity`() {
        val pose =
            Pose(
                translation = Vector3(-3.0, 7.5, 2.0),
                rotation = Quaternion.fromAxisAngle(Vector3(1.0, 2.0, 3.0), 1.1),
            )
        val point = Vector3(4.0, -1.0, 0.25)

        assertVectorEquals(point, pose.inverse().transform(pose.transform(point)))
    }

    @Test
    fun `composing poses equals applying them in order`() {
        val outer =
            Pose(
                translation = Vector3(0.0, 5.0, 0.0),
                rotation = Quaternion.fromAxisAngle(Vector3(1.0, 0.0, 0.0), PI / 2),
            )
        val inner =
            Pose(
                translation = Vector3(2.0, 0.0, 0.0),
                rotation = Quaternion.fromAxisAngle(Vector3(0.0, 0.0, 1.0), PI / 2),
            )
        val point = Vector3(1.0, 1.0, 1.0)

        assertVectorEquals(outer.transform(inner.transform(point)), (outer * inner).transform(point))
    }

    @Test
    fun `a camera pose relative to a marker anchor round trips`() {
        // This is the operation every capture depends on: the marker anchor and the camera both
        // arrive in world space, and the solver needs the camera expressed in the anchor's frame.
        val anchorInWorld =
            Pose(
                translation = Vector3(1.5, 0.0, -2.0),
                rotation = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), 0.4),
            )
        val cameraInWorld =
            Pose(
                translation = Vector3(0.25, 1.2, 0.75),
                rotation = Quaternion.fromAxisAngle(Vector3(0.3, 1.0, -0.2), 2.0),
            )

        val cameraInAnchor = anchorInWorld.inverse() * cameraInWorld

        assertPoseEquals(cameraInWorld, anchorInWorld * cameraInAnchor)
    }

    private fun assertPoseEquals(
        expected: Pose,
        actual: Pose,
    ) {
        assertVectorEquals(expected.translation, actual.translation)
        // A quaternion and its negation describe the same rotation, so compare the effect.
        listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).forEach { axis ->
            assertVectorEquals(expected.rotation.rotate(axis), actual.rotation.rotate(axis))
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
