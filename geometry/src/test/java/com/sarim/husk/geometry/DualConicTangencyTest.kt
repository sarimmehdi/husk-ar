package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The property that makes a dual conic a dual conic.
 *
 * Every other test here builds a conic and reads it back, which only proves the two halves agree
 * with each other. A dual conic is defined by its tangent lines: `l' C l = 0` for every line `l`
 * touching the ellipse. That is the property the solver relies on, since `C = P Q P'` holds for
 * tangency and for nothing else, so it is the one worth checking directly.
 */
class DualConicTangencyTest {
    /** `l' C l`, which must vanish when the line is tangent. */
    private fun tangency(
        conic: DualConic,
        a: Double,
        b: Double,
        c: Double,
    ): Double {
        val l = doubleArrayOf(a, b, c)
        val m =
            arrayOf(
                doubleArrayOf(conic.m00, conic.m01, conic.m02),
                doubleArrayOf(conic.m10, conic.m11, conic.m12),
                doubleArrayOf(conic.m20, conic.m21, conic.m22),
            )
        var sum = 0.0
        for (row in 0..2) {
            for (column in 0..2) {
                sum += l[row] * m[row][column] * l[column]
            }
        }
        return sum
    }

    /** Scale-free, since a conic is only defined up to one. */
    private fun assertTangent(
        conic: DualConic,
        a: Double,
        b: Double,
        c: Double,
    ) {
        val magnitude =
            maxOf(
                1.0,
                listOf(conic.m00, conic.m01, conic.m02, conic.m11, conic.m12, conic.m22)
                    .maxOf { kotlin.math.abs(it) },
            )
        assertEquals(
            "line ($a, $b, $c) should touch the ellipse",
            0.0,
            tangency(conic, a, b, c) / magnitude,
            TOLERANCE,
        )
    }

    @Test
    fun `the vertical tangents of a centred circle touch it`() {
        val circle = DualConic.fromAxisAligned(centreX = 0.0, centreY = 0.0, semiX = 2.0, semiY = 2.0)

        // x = 2 and x = -2, written as lines a*x + b*y + c = 0.
        assertTangent(circle, 1.0, 0.0, -2.0)
        assertTangent(circle, 1.0, 0.0, 2.0)
    }

    @Test
    fun `the horizontal tangents of a centred circle touch it`() {
        val circle = DualConic.fromAxisAligned(centreX = 0.0, centreY = 0.0, semiX = 2.0, semiY = 2.0)

        assertTangent(circle, 0.0, 1.0, -2.0)
        assertTangent(circle, 0.0, 1.0, 2.0)
    }

    @Test
    fun `an offset circle is touched by tangents either side of its centre`() {
        // Offset is what separates a real dual conic from a matrix that merely round-trips: the
        // centre enters the leading block subtracted, and getting that sign wrong is invisible to
        // any test that only reads its own construction back.
        val circle = DualConic.fromAxisAligned(centreX = 3.0, centreY = 0.0, semiX = 2.0, semiY = 2.0)

        assertTangent(circle, 1.0, 0.0, -5.0)
        assertTangent(circle, 1.0, 0.0, -1.0)
    }

    @Test
    fun `an offset ellipse is touched on every side`() {
        val ellipse = DualConic.fromAxisAligned(centreX = 10.0, centreY = -4.0, semiX = 3.0, semiY = 5.0)

        assertTangent(ellipse, 1.0, 0.0, -13.0)
        assertTangent(ellipse, 1.0, 0.0, -7.0)
        assertTangent(ellipse, 0.0, 1.0, -1.0)
        assertTangent(ellipse, 0.0, 1.0, 9.0)
    }

    @Test
    fun `a box inscribed ellipse touches all four sides of the box`() {
        val ellipse = DualConic.inscribedInBox(left = 100.0, top = 40.0, right = 300.0, bottom = 200.0)

        assertTangent(ellipse, 1.0, 0.0, -100.0)
        assertTangent(ellipse, 1.0, 0.0, -300.0)
        assertTangent(ellipse, 0.0, 1.0, -40.0)
        assertTangent(ellipse, 0.0, 1.0, -200.0)
    }

    @Test
    fun `a rotated ellipse is touched by its rotated tangents`() {
        val angle = 0.6
        val ellipse =
            DualConic.fromEllipse(
                centreX = 5.0,
                centreY = 2.0,
                semiMajor = 4.0,
                semiMinor = 2.0,
                rotationRadians = angle,
            )

        // The tangent perpendicular to the major axis, at the far end of it. Its normal is the
        // major axis direction and its offset is that axis projected through the centre.
        val normalX = cos(angle)
        val normalY = sin(angle)
        val centreProjection = normalX * 5.0 + normalY * 2.0
        assertTangent(ellipse, normalX, normalY, -(centreProjection + 4.0))
        assertTangent(ellipse, normalX, normalY, -(centreProjection - 4.0))
    }

    @Test
    fun `a rotated ellipse is touched across its minor axis too`() {
        val angle = 0.6
        val ellipse =
            DualConic.fromEllipse(
                centreX = 5.0,
                centreY = 2.0,
                semiMajor = 4.0,
                semiMinor = 2.0,
                rotationRadians = angle,
            )

        val normalX = -sin(angle)
        val normalY = cos(angle)
        val centreProjection = normalX * 5.0 + normalY * 2.0
        assertTangent(ellipse, normalX, normalY, -(centreProjection + 2.0))
        assertTangent(ellipse, normalX, normalY, -(centreProjection - 2.0))
    }

    @Test
    fun `parameters read back match what was built`() {
        val ellipse =
            DualConic.fromEllipse(
                centreX = 10.0,
                centreY = -4.0,
                semiMajor = 5.0,
                semiMinor = 3.0,
                rotationRadians = 0.4,
            )

        val parameters = ellipse.toParameters()

        assertEquals(10.0, parameters.centreX, TOLERANCE)
        assertEquals(-4.0, parameters.centreY, TOLERANCE)
        assertEquals(5.0, parameters.semiMajor, TOLERANCE)
        assertEquals(3.0, parameters.semiMinor, TOLERANCE)
        assertEquals(0.4, parameters.rotationRadians, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
