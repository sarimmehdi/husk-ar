package com.sarim.husk.ar

import com.sarim.husk.geometry.DualConic
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How the camera image is laid onto the preview surface.
 *
 * The preview fills its view and the overflow is cropped, which is what the camera actually does and
 * why a trace cannot be handed to the solver as drawn. Intrinsics describe the camera image; a
 * gesture arrives in preview pixels; on any phone whose preview is not the shape of its sensor
 * output, those differ by a scale and an offset.
 *
 * Getting this wrong is undetectable downstream. A mis-scaled trace is still a perfectly good
 * ellipse — of an object that was never there.
 */
data class PreviewMapping(
    /** Width of the camera image, in pixels. */
    val imageWidth: Int,
    /** Height of the camera image, in pixels. */
    val imageHeight: Int,
    /** Width of the preview surface, in pixels. */
    val previewWidth: Int,
    /** Height of the preview surface, in pixels. */
    val previewHeight: Int,
) {
    /**
     * Preview pixels per image pixel.
     *
     * The larger of the two ratios, because the image is scaled to **fill** the preview. Taking the
     * smaller would describe a letterboxed fit, and every trace would be off by the ratio between
     * the two aspect ratios — a few percent on some phones and a third on others.
     */
    val scale: Double =
        max(
            previewWidth.toDouble() / imageWidth.toDouble(),
            previewHeight.toDouble() / imageHeight.toDouble(),
        )

    private val offsetX: Double = (previewWidth - imageWidth * scale) / 2.0
    private val offsetY: Double = (previewHeight - imageHeight * scale) / 2.0

    /** Where a point on the preview falls in the camera image. */
    fun toImage(
        x: Float,
        y: Float,
    ): Pair<Double, Double> = (x - offsetX) / scale to (y - offsetY) / scale
}

/**
 * An ellipse as drawn on the preview, in preview pixels.
 *
 * [semiMajor] is always the longer axis, so a tall shape carries its orientation in
 * [rotationRadians] rather than by swapping the two over. Keeping that invariant means the rotation
 * always means the same thing.
 */
data class TracedEllipse(
    /** Centre x, in preview pixels. */
    val centreX: Float,
    /** Centre y, in preview pixels. */
    val centreY: Float,
    /** The longer semi-axis, in preview pixels. */
    val semiMajor: Float,
    /** The shorter semi-axis, in preview pixels. */
    val semiMinor: Float,
    /** Angle of the major axis from the preview's x axis. */
    val rotationRadians: Float,
) {
    /**
     * Whether this is worth handing to the solver.
     *
     * A tap that never became a drag is a miss, not a request to measure a point, and a conic with a
     * vanishing axis is exactly what the solver rejects as degenerate.
     */
    val isUsable: Boolean get() = semiMinor > MINIMUM_SEMI_AXIS_PIXELS

    /**
     * This trace as a dual conic in camera image coordinates.
     *
     * The mapping is a uniform scale and a translation, so an ellipse stays an ellipse: the centre
     * moves, both axes shrink by the same factor, and the orientation is untouched.
     */
    fun toDualConic(mapping: PreviewMapping): DualConic {
        val (imageX, imageY) = mapping.toImage(centreX, centreY)
        return DualConic.fromEllipse(
            centreX = imageX,
            centreY = imageY,
            semiMajor = semiMajor / mapping.scale,
            semiMinor = semiMinor / mapping.scale,
            rotationRadians = rotationRadians.toDouble(),
        )
    }

    /** Ways a gesture becomes a trace. */
    companion object {
        /** Below this a drag is indistinguishable from a tap. */
        private const val MINIMUM_SEMI_AXIS_PIXELS = 4f

        /** A quarter turn, for boxes taller than they are wide. */
        private const val QUARTER_TURN = (Math.PI / 2.0).toFloat()

        /**
         * The ellipse inscribed in the box dragged between two points.
         *
         * Inscribed rather than drawn through the corners: the box is the extent the person
         * indicated, and an ellipse through its corners would overstate it by a factor of root two
         * on both axes.
         *
         * Either drag direction gives the same box, since people drag up and left as readily as
         * down and right and a negative width would otherwise produce negative axes.
         */
        fun fromDragBox(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
        ): TracedEllipse {
            val halfWidth = abs(endX - startX) / 2f
            val halfHeight = abs(endY - startY) / 2f
            val isWide = halfWidth >= halfHeight
            return TracedEllipse(
                centreX = (startX + endX) / 2f,
                centreY = (startY + endY) / 2f,
                semiMajor = max(halfWidth, halfHeight),
                semiMinor = min(halfWidth, halfHeight),
                rotationRadians = if (isWide) 0f else QUARTER_TURN,
            )
        }
    }
}
