package com.sarim.husk.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Working out how a marker should be printed.
 *
 * The printed width is the scale of everything the app measures: ARCore reads the marker's apparent
 * size and believes the number it was given for the real one. Print it 4% small and every
 * measurement is 4% small, with nothing on screen to say so.
 *
 * So the layout is computed rather than written into a help page, and it carries a reference bar
 * whose length is known, which is what turns a silent scaling error into one a ruler can catch.
 */
class MarkerPrintLayoutTest {
    /** The bundled marker: very nearly square. */
    private fun squareish(targetWidth: Double = 200.0) =
        printLayoutFor(
            paper = PaperSize.A4,
            imagePixelWidth = 899,
            imagePixelHeight = 900,
            targetWidthMillimetres = targetWidth,
        )

    @Test
    fun `the image keeps its aspect ratio`() {
        val layout = squareish()

        assertEquals(200.0 * 900.0 / 899.0, layout.imageHeightMillimetres, TOLERANCE)
    }

    @Test
    fun `side margins are what is left over, halved`() {
        // A4 is 210 wide. A 200mm image leaves 10mm, so 5mm each side.
        val layout = squareish()

        assertEquals(5.0, layout.horizontalPaddingMillimetres, TOLERANCE)
    }

    @Test
    fun `top and bottom margins are what is left over, halved`() {
        val layout = squareish()

        val expected = (297.0 - 200.0 * 900.0 / 899.0) / 2.0
        assertEquals(expected, layout.verticalPaddingMillimetres, TOLERANCE)
    }

    @Test
    fun `a marker that fits says so`() {
        assertTrue(squareish(targetWidth = 150.0).fitsOnPage)
    }

    @Test
    fun `a marker wider than the paper does not fit`() {
        // Better to say so than to print something silently cropped, which would change the marker
        // ARCore is matching against as well as its size.
        assertFalse(squareish(targetWidth = 250.0).fitsOnPage)
    }

    @Test
    fun `a marker too tall for the paper does not fit either`() {
        val layout =
            printLayoutFor(
                paper = PaperSize.A4,
                imagePixelWidth = 100,
                imagePixelHeight = 400,
                targetWidthMillimetres = 100.0,
            )

        assertFalse("400mm tall cannot fit 297mm of paper", layout.fitsOnPage)
    }

    @Test
    fun `nothing is placed inside the printer's unprintable border`() {
        // Most printers cannot reach the last few millimetres. A layout that ignored that would be
        // silently cropped, and cropping changes the marker's size as well as its content.
        val layout = squareish(targetWidth = 208.0)

        assertFalse("208mm leaves 1mm a side, inside the unreachable border", layout.fitsOnPage)
    }

    @Test
    fun `letter paper gives different margins from A4`() {
        val layout =
            printLayoutFor(
                paper = PaperSize.LETTER,
                imagePixelWidth = 899,
                imagePixelHeight = 900,
                targetWidthMillimetres = 190.0,
            )

        assertEquals((215.9 - 190.0) / 2.0, layout.horizontalPaddingMillimetres, TOLERANCE)
    }

    @Test
    fun `the largest marker that fits leaves room for the printer border`() {
        val widest = widestThatFits(PaperSize.A4, imagePixelWidth = 899, imagePixelHeight = 900)

        assertTrue("must fit", printLayoutFor(PaperSize.A4, 899, 900, widest).fitsOnPage)
        assertFalse(
            "must be the largest that does",
            printLayoutFor(PaperSize.A4, 899, 900, widest + 1.0).fitsOnPage,
        )
    }

    @Test
    fun `a tall image is limited by the height of the paper, not its width`() {
        val widest = widestThatFits(PaperSize.A4, imagePixelWidth = 100, imagePixelHeight = 200)

        val layout = printLayoutFor(PaperSize.A4, 100, 200, widest)
        assertTrue(layout.fitsOnPage)
        // Twice as tall as wide, so height runs out first and the width lands near half the usable
        // height rather than near the usable width.
        assertTrue("expected height to be the limit, got $widest", widest < 150.0)
    }

    @Test
    fun `the reference bar is a round number a ruler can read`() {
        val layout = squareish()

        assertEquals(100.0, layout.referenceBarMillimetres, TOLERANCE)
    }

    @Test
    fun `a small marker gets a reference bar that still fits beside it`() {
        // The bar is only useful if it is printed. On a narrow marker a 100mm bar would run off the
        // page, so it shrinks to something the sheet can hold and still states its own length.
        val layout =
            printLayoutFor(
                paper = PaperSize.A4,
                imagePixelWidth = 899,
                imagePixelHeight = 900,
                targetWidthMillimetres = 60.0,
            )

        assertTrue("the bar must fit the paper", layout.referenceBarMillimetres <= 200.0)
        assertTrue("the bar must be measurable", layout.referenceBarMillimetres >= 50.0)
    }

    @Test
    fun `a measured print corrects the width it was meant to have`() {
        // The step that makes the whole thing safe. Whatever the printer did, measuring the bar and
        // saying what it reads recovers the true scale.
        val intended = 200.0
        val barShouldBe = 100.0
        val barActuallyIs = 96.0

        val corrected = correctedWidthMillimetres(intended, barShouldBe, barActuallyIs)

        assertEquals(192.0, corrected, TOLERANCE)
    }

    @Test
    fun `a correctly printed sheet needs no correction`() {
        assertEquals(200.0, correctedWidthMillimetres(200.0, 100.0, 100.0), TOLERANCE)
    }

    @Test
    fun `a nonsense measurement is refused rather than scaling everything to zero`() {
        // A mistyped zero would otherwise make every future measurement zero, and the shells would
        // simply vanish with no explanation.
        assertEquals(200.0, correctedWidthMillimetres(200.0, 100.0, 0.0), TOLERANCE)
        assertEquals(200.0, correctedWidthMillimetres(200.0, 100.0, -5.0), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
