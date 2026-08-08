package com.sarim.husk.ar

import kotlin.math.min

/**
 * A sheet of paper, in millimetres.
 *
 * The literals are the ISO and ANSI definitions of these sizes. Naming them would move the same
 * numbers one line further away without explaining anything the enum entry does not already say.
 */
@Suppress("MagicNumber")
enum class PaperSize(
    /** Short edge. */
    val widthMillimetres: Double,
    /** Long edge. */
    val heightMillimetres: Double,
) {
    /** 210 by 297. */
    A4(210.0, 297.0),

    /** 8.5 by 11 inches. */
    LETTER(215.9, 279.4),
}

/**
 * How a marker should sit on a printed sheet.
 *
 * The printed width is the scale of everything the app measures. ARCore reads the marker's apparent
 * size in the camera and takes the number it was given for the real one, so a marker printed 4%
 * small makes every measurement 4% small, with nothing on screen to say so.
 *
 * Which is why this carries a reference bar. Instructions alone are not enough: most print dialogs
 * default to fitting the page, and someone can follow every instruction exactly and still get a
 * rescaled sheet. A printed bar of known length turns that from an invisible error into one a ruler
 * finds in five seconds — and [correctedWidthMillimetres] turns the measurement into the fix.
 */
data class MarkerPrintLayout(
    /** The paper it is laid out for. */
    val paper: PaperSize,
    /** How wide the image should be once printed. */
    val imageWidthMillimetres: Double,
    /** How tall it will be at that width, keeping its aspect ratio. */
    val imageHeightMillimetres: Double,
    /** Gap between each side of the image and the paper's edge. */
    val horizontalPaddingMillimetres: Double,
    /** Gap between the top and bottom of the image and the paper's edges. */
    val verticalPaddingMillimetres: Double,
    /** Length the printed reference bar should measure. */
    val referenceBarMillimetres: Double,
    /** Whether it fits, allowing for the border a printer cannot reach. */
    val fitsOnPage: Boolean,
)

/**
 * The border no printer can reach.
 *
 * Ignoring it produces a layout that is silently cropped, and cropping a marker changes both its
 * size and the pattern ARCore is matching against.
 */
const val UNPRINTABLE_BORDER_MILLIMETRES = 5.0

/** How a marker of [targetWidthMillimetres] sits on [paper]. */
fun printLayoutFor(
    paper: PaperSize,
    imagePixelWidth: Int,
    imagePixelHeight: Int,
    targetWidthMillimetres: Double,
): MarkerPrintLayout {
    val height = targetWidthMillimetres * imagePixelHeight / imagePixelWidth
    val usableWidth = paper.widthMillimetres - 2 * UNPRINTABLE_BORDER_MILLIMETRES
    val usableHeight = paper.heightMillimetres - 2 * UNPRINTABLE_BORDER_MILLIMETRES
    return MarkerPrintLayout(
        paper = paper,
        imageWidthMillimetres = targetWidthMillimetres,
        imageHeightMillimetres = height,
        horizontalPaddingMillimetres = (paper.widthMillimetres - targetWidthMillimetres) / 2.0,
        verticalPaddingMillimetres = (paper.heightMillimetres - height) / 2.0,
        referenceBarMillimetres = referenceBarFor(targetWidthMillimetres),
        fitsOnPage = targetWidthMillimetres <= usableWidth && height <= usableHeight,
    )
}

/**
 * The largest marker that fits on [paper].
 *
 * Bigger markers track better and further away, so the useful default is the biggest the sheet can
 * hold rather than a round number chosen for looking tidy.
 */
fun widestThatFits(
    paper: PaperSize,
    imagePixelWidth: Int,
    imagePixelHeight: Int,
): Double {
    val usableWidth = paper.widthMillimetres - 2 * UNPRINTABLE_BORDER_MILLIMETRES
    val usableHeight = paper.heightMillimetres - 2 * UNPRINTABLE_BORDER_MILLIMETRES
    val widthLimitedByHeight = usableHeight * imagePixelWidth / imagePixelHeight
    return min(usableWidth, widthLimitedByHeight)
}

/**
 * The width the marker really was printed at.
 *
 * Whatever the print dialog did to the page, measuring the reference bar recovers the true scale:
 * the sheet was scaled uniformly, so the bar and the marker were scaled by the same factor.
 *
 * A measurement of zero or less is a slip rather than a reading, and honouring it would scale every
 * future measurement to nothing and make the shells vanish with no explanation. Such a value leaves
 * the intended width alone.
 */
fun correctedWidthMillimetres(
    intendedWidthMillimetres: Double,
    referenceBarMillimetres: Double,
    measuredBarMillimetres: Double,
): Double =
    if (measuredBarMillimetres <= 0.0) {
        intendedWidthMillimetres
    } else {
        intendedWidthMillimetres * measuredBarMillimetres / referenceBarMillimetres
    }

/**
 * A bar long enough to measure accurately and short enough to print beside the marker.
 *
 * A hundred millimetres against any ruler makes a one percent error visible. On a marker too narrow
 * to sit beside one, it shrinks rather than running off the page, since a bar that was not printed
 * catches nothing.
 */
private fun referenceBarFor(markerWidthMillimetres: Double): Double =
    min(PREFERRED_REFERENCE_BAR_MILLIMETRES, markerWidthMillimetres)
        .coerceAtLeast(MINIMUM_REFERENCE_BAR_MILLIMETRES)

private const val PREFERRED_REFERENCE_BAR_MILLIMETRES = 100.0
private const val MINIMUM_REFERENCE_BAR_MILLIMETRES = 50.0
