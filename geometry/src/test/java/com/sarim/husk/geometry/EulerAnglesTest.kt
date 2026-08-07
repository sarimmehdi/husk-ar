package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Euler angles exist only where a person edits a rotation. Everything else stores quaternions, so
 * these tests care about one property above all: converting a quaternion to angles and back must
 * describe the same rotation, even where the angles themselves are not unique.
 */
class EulerAnglesTest {
    @Test
    fun `the identity rotation has no angles`() {
        val angles = EulerAngles.from(Quaternion.IDENTITY)

        assertEquals(0.0, angles.pitchRadians, TOLERANCE)
        assertEquals(0.0, angles.yawRadians, TOLERANCE)
        assertEquals(0.0, angles.rollRadians, TOLERANCE)
    }

    @Test
    fun `yaw turns about the up axis`() {
        // ARCore's up is +Y, so heading is yaw about Y. A quarter turn takes +X onto -Z.
        val rotation = EulerAngles(pitchRadians = 0.0, yawRadians = PI / 2, rollRadians = 0.0).toQuaternion()

        assertVectorEquals(Vector3(0.0, 0.0, -1.0), rotation.rotate(Vector3(1.0, 0.0, 0.0)))
    }

    @Test
    fun `pitch turns about the right axis`() {
        val rotation = EulerAngles(pitchRadians = PI / 2, yawRadians = 0.0, rollRadians = 0.0).toQuaternion()

        assertVectorEquals(Vector3(0.0, 0.0, 1.0), rotation.rotate(Vector3(0.0, 1.0, 0.0)))
    }

    @Test
    fun `roll turns about the forward axis`() {
        val rotation = EulerAngles(pitchRadians = 0.0, yawRadians = 0.0, rollRadians = PI / 2).toQuaternion()

        assertVectorEquals(Vector3(0.0, 1.0, 0.0), rotation.rotate(Vector3(1.0, 0.0, 0.0)))
    }

    @Test
    fun `each single axis rotation round trips through angles`() {
        listOf(
            EulerAngles(pitchRadians = 0.4, yawRadians = 0.0, rollRadians = 0.0),
            EulerAngles(pitchRadians = 0.0, yawRadians = -1.2, rollRadians = 0.0),
            EulerAngles(pitchRadians = 0.0, yawRadians = 0.0, rollRadians = 2.5),
        ).forEach { original ->
            val recovered = EulerAngles.from(original.toQuaternion())

            assertEquals("pitch", original.pitchRadians, recovered.pitchRadians, TOLERANCE)
            assertEquals("yaw", original.yawRadians, recovered.yawRadians, TOLERANCE)
            assertEquals("roll", original.rollRadians, recovered.rollRadians, TOLERANCE)
        }
    }

    @Test
    fun `a combined rotation round trips as a rotation`() {
        // The recovered angles need not equal the originals, because several triples describe one
        // rotation. What must hold is that the rotation itself survives.
        val original = EulerAngles(pitchRadians = 0.6, yawRadians = -0.9, rollRadians = 1.4)

        assertRotationEquals(original.toQuaternion(), EulerAngles.from(original.toQuaternion()).toQuaternion())
    }

    @Test
    fun `an arbitrary quaternion round trips as a rotation`() {
        val rotation = Quaternion.fromAxisAngle(Vector3(0.3, -1.0, 0.6), 2.2)

        assertRotationEquals(rotation, EulerAngles.from(rotation).toQuaternion())
    }

    @Test
    fun `looking straight up does not produce a nan`() {
        // Pitch of ±90° is gimbal lock: yaw and roll stop being separable. The conversion must pick
        // a representative rather than divide by a vanishing cosine.
        listOf(PI / 2, -PI / 2).forEach { pitch ->
            val rotation = EulerAngles(pitchRadians = pitch, yawRadians = 0.8, rollRadians = 0.0).toQuaternion()

            val recovered = EulerAngles.from(rotation)

            assertTrue("pitch is finite", recovered.pitchRadians.isFinite())
            assertTrue("yaw is finite", recovered.yawRadians.isFinite())
            assertTrue("roll is finite", recovered.rollRadians.isFinite())
            assertRotationEquals(rotation, recovered.toQuaternion())
        }
    }

    private fun assertRotationEquals(
        expected: Quaternion,
        actual: Quaternion,
    ) {
        listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).forEach { axis ->
            assertVectorEquals(expected.rotate(axis), actual.rotate(axis))
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
