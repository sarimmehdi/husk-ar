package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class EllipsoidTest {
    @Test
    fun `an ellipsoid keeps the centre and radii it was built from`() {
        val ellipsoid =
            Ellipsoid(
                centre = Vector3(1.0, -2.0, 3.0),
                radii = Vector3(0.1, 0.2, 0.3),
                rotation = Quaternion.IDENTITY,
            )

        assertVectorEquals(Vector3(1.0, -2.0, 3.0), ellipsoid.centre)
        assertVectorEquals(Vector3(0.1, 0.2, 0.3), ellipsoid.radii)
    }

    @Test
    fun `extent reports full widths rather than radii`() {
        // The UI shows an object as 18.4 x 24.1 cm, which is the full extent, not the semi axes.
        val ellipsoid =
            Ellipsoid(
                centre = Vector3.ZERO,
                radii = Vector3(0.092, 0.1205, 0.0895),
                rotation = Quaternion.IDENTITY,
            )

        assertVectorEquals(Vector3(0.184, 0.241, 0.179), ellipsoid.extent())
    }

    @Test
    fun `the dual quadric centre is the negated last column`() {
        // The reference implementation this port derives from reads the centre as +Q(0..2,3). For a
        // dual quadric normalised to Q(3,3) = -1 the centre is the negation of that column, and
        // reading the sign the wrong way puts the ellipsoid at its own reflection through the
        // origin. Pinning it here is the whole reason this test exists.
        val centre = Vector3(0.4, -1.1, 2.6)
        val ellipsoid =
            Ellipsoid(
                centre = centre,
                radii = Vector3(0.05, 0.05, 0.05),
                rotation = Quaternion.IDENTITY,
            )

        val quadric = ellipsoid.toDualQuadric()

        assertEquals(-1.0, quadric[3][3], TOLERANCE)
        assertEquals(-centre.x, quadric[0][3], TOLERANCE)
        assertEquals(-centre.y, quadric[1][3], TOLERANCE)
        assertEquals(-centre.z, quadric[2][3], TOLERANCE)
    }

    @Test
    fun `a dual quadric round trips back to the ellipsoid that produced it`() {
        val original =
            Ellipsoid(
                centre = Vector3(-0.3, 1.4, 0.85),
                radii = Vector3(0.12, 0.07, 0.2),
                rotation = Quaternion.fromAxisAngle(Vector3(0.4, 1.0, -0.2), 0.9),
            )

        val recovered = Ellipsoid.fromDualQuadric(original.toDualQuadric())

        assertVectorEquals(original.centre, recovered.centre)
        assertRadiiEqualAsASet(original.radii, recovered.radii)
    }

    @Test
    fun `an axis aligned dual quadric recovers its radii`() {
        val original =
            Ellipsoid(
                centre = Vector3(2.0, 0.0, -1.0),
                radii = Vector3(0.3, 0.1, 0.2),
                rotation = Quaternion.IDENTITY,
            )

        val recovered = Ellipsoid.fromDualQuadric(original.toDualQuadric())

        assertRadiiEqualAsASet(original.radii, recovered.radii)
    }

    @Test
    fun `a rotated ellipsoid keeps its volume through the round trip`() {
        val original =
            Ellipsoid(
                centre = Vector3.ZERO,
                radii = Vector3(0.25, 0.1, 0.05),
                rotation = Quaternion.fromAxisAngle(Vector3(1.0, 1.0, 1.0), PI / 3),
            )

        val recovered = Ellipsoid.fromDualQuadric(original.toDualQuadric())

        assertEquals(original.volume(), recovered.volume(), 1e-9)
    }

    @Test
    fun `a degenerate quadric does not produce a nan`() {
        val zero = Array(4) { DoubleArray(4) }

        val recovered = Ellipsoid.fromDualQuadric(zero)

        assertTrue("centre is finite", recovered.centre.length().isFinite())
        assertTrue("radii are finite", recovered.radii.length().isFinite())
    }

    private fun assertRadiiEqualAsASet(
        expected: Vector3,
        actual: Vector3,
    ) {
        // Recovery orders the axes by magnitude, which need not match construction order.
        val expectedSorted = listOf(expected.x, expected.y, expected.z).sorted()
        val actualSorted = listOf(actual.x, actual.y, actual.z).sorted()
        expectedSorted.zip(actualSorted).forEach { (e, a) -> assertEquals(e, a, TOLERANCE) }
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
