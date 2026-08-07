package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class QuaternionTest {
    @Test
    fun `identity leaves a vector untouched`() {
        val v = Vector3(1.0, 2.0, 3.0)

        assertVectorEquals(v, Quaternion.IDENTITY.rotate(v))
    }

    @Test
    fun `a quarter turn about z maps x onto y`() {
        val rotation = Quaternion.fromAxisAngle(Vector3(0.0, 0.0, 1.0), PI / 2)

        assertVectorEquals(Vector3(0.0, 1.0, 0.0), rotation.rotate(Vector3(1.0, 0.0, 0.0)))
    }

    @Test
    fun `rotating by a quaternion and then its inverse returns the original vector`() {
        val rotation = Quaternion.fromAxisAngle(Vector3(1.0, 2.0, 3.0), 0.7)
        val v = Vector3(-4.0, 0.5, 2.0)

        assertVectorEquals(v, rotation.inverse().rotate(rotation.rotate(v)))
    }

    @Test
    fun `composing two rotations equals applying them in order`() {
        val first = Quaternion.fromAxisAngle(Vector3(0.0, 0.0, 1.0), PI / 2)
        val second = Quaternion.fromAxisAngle(Vector3(1.0, 0.0, 0.0), PI / 2)
        val v = Vector3(1.0, 0.0, 0.0)

        assertVectorEquals(second.rotate(first.rotate(v)), (second * first).rotate(v))
    }

    @Test
    fun `an unnormalised quaternion still rotates without scaling the vector`() {
        // Poses arriving from tracking are not guaranteed to be unit length, and a rotation that
        // silently scales its input would corrupt every downstream measurement.
        val scaled = Quaternion(0.0, 0.0, sqrt(0.5) * 3.0, sqrt(0.5) * 3.0)

        val rotated = scaled.rotate(Vector3(1.0, 0.0, 0.0))

        assertEquals(1.0, rotated.length(), TOLERANCE)
        assertVectorEquals(Vector3(0.0, 1.0, 0.0), rotated)
    }

    @Test
    fun `normalising a zero quaternion yields the identity rather than a nan`() {
        val normalised = Quaternion(0.0, 0.0, 0.0, 0.0).normalized()

        assertTrue("a degenerate quaternion must not produce NaN", !normalised.w.isNaN())
        assertEquals(Quaternion.IDENTITY, normalised)
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
