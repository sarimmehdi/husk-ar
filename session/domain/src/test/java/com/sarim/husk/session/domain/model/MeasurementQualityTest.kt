package com.sarim.husk.session.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The band boundaries, pinned against the numbers they came from.
 *
 * These are not taste. They were measured: at a radian of view spread a pixel of tracing error costs
 * about 2% of radius, at 0.4 about 4%, and below 0.2 it passes 50%. Moving a boundary means claiming
 * a different accuracy, so it should have to break a test.
 */
class MeasurementQualityTest {
    private fun quality(spread: Double) =
        MeasurementQuality(
            viewSpreadRadians = spread,
            conicResidual = 0.001,
            nullSpaceMargin = 0.0009,
        )

    @Test
    fun `views spread right around the object are good`() {
        assertEquals(MeasurementConfidence.GOOD, quality(1.4).confidence())
    }

    @Test
    fun `the good band starts where error passes a few percent`() {
        assertEquals(MeasurementConfidence.GOOD, quality(0.4).confidence())
        assertEquals(MeasurementConfidence.ROUGH, quality(0.39).confidence())
    }

    @Test
    fun `bunched views are rough rather than good`() {
        // Around 50% radius error here. The shape is worth drawing; the numbers are not worth
        // quoting, and calling this good would be a lie the person cannot check.
        assertEquals(MeasurementConfidence.ROUGH, quality(0.2).confidence())
    }

    @Test
    fun `no spread at all means nothing was measured`() {
        assertEquals(MeasurementConfidence.UNMEASURED, quality(0.0).confidence())
    }

    @Test
    fun `the residual does not decide the band`() {
        // A badly triangulated shell reprojects onto every outline it was fit to, so a small
        // residual says nothing about accuracy. Two qualities with the same spread and wildly
        // different residuals must land in the same band.
        val tightResidual = MeasurementQuality(0.15, 0.0001, 0.0009)
        val looseResidual = MeasurementQuality(0.15, 0.5, 0.0009)

        assertEquals(tightResidual.confidence(), looseResidual.confidence())
    }
}
