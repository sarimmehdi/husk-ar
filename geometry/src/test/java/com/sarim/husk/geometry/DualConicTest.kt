package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class DualConicTest {
    @Test
    fun `an axis aligned ellipse reports the centre and semi axes it was built from`() {
        val conic = DualConic.fromAxisAligned(centreX = 320.0, centreY = 240.0, semiX = 100.0, semiY = 50.0)

        val parameters = conic.toParameters()

        assertEquals(320.0, parameters.centreX, TOLERANCE)
        assertEquals(240.0, parameters.centreY, TOLERANCE)
        assertEquals(100.0, parameters.semiMajor, TOLERANCE)
        assertEquals(50.0, parameters.semiMinor, TOLERANCE)
        assertEquals(0.0, parameters.rotationRadians, TOLERANCE)
    }

    @Test
    fun `semi axes are reported major first regardless of which input was larger`() {
        val tall = DualConic.fromAxisAligned(centreX = 0.0, centreY = 0.0, semiX = 30.0, semiY = 80.0)

        val parameters = tall.toParameters()

        assertEquals(80.0, parameters.semiMajor, TOLERANCE)
        assertEquals(30.0, parameters.semiMinor, TOLERANCE)
        // A tall ellipse is a wide one turned a quarter turn.
        assertEquals(PI / 2, abs(parameters.rotationRadians), TOLERANCE)
    }

    @Test
    fun `a circle has equal semi axes and no meaningful rotation`() {
        val circle = DualConic.fromAxisAligned(centreX = -5.0, centreY = 7.0, semiX = 12.0, semiY = 12.0)

        val parameters = circle.toParameters()

        assertEquals(12.0, parameters.semiMajor, TOLERANCE)
        assertEquals(12.0, parameters.semiMinor, TOLERANCE)
    }

    @Test
    fun `the matrix is symmetric because a dual conic has only six free values`() {
        val conic = DualConic.fromAxisAligned(centreX = 3.0, centreY = -2.0, semiX = 9.0, semiY = 4.0)

        assertEquals(conic.m01, conic.m10, TOLERANCE)
        assertEquals(conic.m02, conic.m20, TOLERANCE)
        assertEquals(conic.m12, conic.m21, TOLERANCE)
    }

    @Test
    fun `a bounding box inscribes an ellipse touching each side`() {
        // Detection boxes arrive as corners; the solver consumes conics. The inscribed ellipse is
        // the one tangent to all four sides, so its semi axes are the box half extents.
        val conic = DualConic.inscribedInBox(left = 100.0, top = 50.0, right = 300.0, bottom = 250.0)

        val parameters = conic.toParameters()

        assertEquals(200.0, parameters.centreX, TOLERANCE)
        assertEquals(150.0, parameters.centreY, TOLERANCE)
        assertEquals(100.0, parameters.semiMajor, TOLERANCE)
        assertEquals(100.0, parameters.semiMinor, TOLERANCE)
    }

    @Test
    fun `a degenerate box does not produce a nan`() {
        val conic = DualConic.inscribedInBox(left = 10.0, top = 10.0, right = 10.0, bottom = 10.0)

        val parameters = conic.toParameters()

        assertTrue("centre is finite", parameters.centreX.isFinite() && parameters.centreY.isFinite())
        assertTrue("axes are finite", parameters.semiMajor.isFinite() && parameters.semiMinor.isFinite())
    }

    private fun abs(value: Double) = kotlin.math.abs(value)

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
