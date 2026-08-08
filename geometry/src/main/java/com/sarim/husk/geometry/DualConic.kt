package com.sarim.husk.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A conic in dual form: the symmetric 3x3 matrix whose tangent lines satisfy `l' C l = 0`.
 *
 * The solver works in the dual space because the projection of a dual quadric is a dual conic —
 * `C = P Q P'` — which is what makes the ellipsoid recoverable by a linear system. Storing the
 * primal form instead would require inverting at every step.
 *
 * The matrix is symmetric, so only six of the nine values are free. It is stored in full anyway
 * because the arithmetic reads more clearly that way, and [toParameters] is the only place the
 * redundancy matters.
 */
data class DualConic(
    /** Row 0, column 0. */
    val m00: Double,
    /** Row 0, column 1; mirrored at [m10]. */
    val m01: Double,
    /** Row 0, column 2; mirrored at [m20]. */
    val m02: Double,
    /** Row 1, column 1. */
    val m11: Double,
    /** Row 1, column 2; mirrored at [m21]. */
    val m12: Double,
    /** Row 2, column 2; the homogeneous scale. */
    val m22: Double,
) {
    /** Mirror of [m01]; a dual conic is symmetric. */
    val m10: Double get() = m01

    /** Mirror of [m02]; a dual conic is symmetric. */
    val m20: Double get() = m02

    /** Mirror of [m12]; a dual conic is symmetric. */
    val m21: Double get() = m12

    /**
     * The ellipse this conic describes, in image pixels.
     *
     * A conic that has degenerated to a point reports zero axes rather than dividing by a vanishing
     * scale, so a bad trace cannot poison everything downstream with NaN.
     */
    fun toParameters(): EllipseParameters {
        if (abs(m22) < DEGENERATE_EPSILON) return EllipseParameters(0.0, 0.0, 0.0, 0.0, 0.0)

        // Normalise so the homogeneous term is -1. The centre is then the negated last column, and
        // the leading block is the shape displaced by the centre's outer product.
        val scale = -1.0 / m22
        val centreX = -m02 * scale
        val centreY = -m12 * scale

        // Adding the outer product back leaves the shape about the origin. Subtracting instead
        // would drive the eigenvalues negative and report every ellipse as a point.
        val a = m00 * scale + centreX * centreX
        val b = m01 * scale + centreX * centreY
        val c = m11 * scale + centreY * centreY

        // Eigenvalues of the symmetric 2x2 [[a, b], [b, c]] are the squared semi axes.
        val mean = (a + c) / 2.0
        val spread = hypot((a - c) / 2.0, b)

        return EllipseParameters(
            centreX = centreX,
            centreY = centreY,
            semiMajor = sqrt((mean + spread).coerceAtLeast(0.0)),
            semiMinor = sqrt((mean - spread).coerceAtLeast(0.0)),
            rotationRadians = if (spread < DEGENERATE_EPSILON) 0.0 else atan2(2.0 * b, a - c) / 2.0,
        )
    }

    /** Constructors for the shapes detections and traces produce. */
    companion object {
        /**
         * Below this a conic has collapsed and its shape is no longer recoverable.
         *
         * Chosen relative to the homogeneous scale rather than to pixels, so it does not depend on
         * image resolution.
         */
        private const val DEGENERATE_EPSILON = 1e-12

        /**
         * The ellipse with the given centre, semi axes and orientation.
         *
         * Built as `C = T · diag(a², b², -1) · T'` with `T = [[R, t], [0, 1]]`, which expands to
         * `[[R D R' - t t', -t], [-t', -1]]`. The minus one is what makes this a dual conic rather
         * than merely a symmetric matrix: tangency, `l' C l = 0`, only holds with it, and tangency
         * is the entire reason the solver can write `C = P Q P'`.
         */
        fun fromEllipse(
            centreX: Double,
            centreY: Double,
            semiMajor: Double,
            semiMinor: Double,
            rotationRadians: Double,
        ): DualConic {
            val cosine = cos(rotationRadians)
            val sine = sin(rotationRadians)
            val major = semiMajor * semiMajor
            val minor = semiMinor * semiMinor

            // R · diag(a², b²) · R', the shape with no position.
            val shapeXx = major * cosine * cosine + minor * sine * sine
            val shapeXy = (major - minor) * cosine * sine
            val shapeYy = major * sine * sine + minor * cosine * cosine

            return DualConic(
                m00 = shapeXx - centreX * centreX,
                m01 = shapeXy - centreX * centreY,
                m02 = -centreX,
                m11 = shapeYy - centreY * centreY,
                m12 = -centreY,
                m22 = -1.0,
            )
        }

        /** The ellipse centred at ([centreX], [centreY]) with the given semi axes and no rotation. */
        fun fromAxisAligned(
            centreX: Double,
            centreY: Double,
            semiX: Double,
            semiY: Double,
        ): DualConic =
            fromEllipse(
                centreX = centreX,
                centreY = centreY,
                semiMajor = semiX,
                semiMinor = semiY,
                rotationRadians = 0.0,
            )

        /**
         * The ellipse tangent to all four sides of a detection box.
         *
         * Detections arrive as boxes and the solver consumes conics, so this is the bridge. It is
         * the inscribed ellipse, not the circumscribed one: the box is the object's extent, and an
         * ellipse through the corners would overstate it by a factor of the square root of two on
         * both axes.
         */
        fun inscribedInBox(
            left: Double,
            top: Double,
            right: Double,
            bottom: Double,
        ): DualConic =
            fromAxisAligned(
                centreX = (left + right) / 2.0,
                centreY = (top + bottom) / 2.0,
                semiX = abs(right - left) / 2.0,
                semiY = abs(bottom - top) / 2.0,
            )
    }
}

/**
 * An ellipse in image pixels.
 *
 * [semiMajor] is always the larger axis, and [rotationRadians] turns it away from the image x axis.
 */
data class EllipseParameters(
    /** Centre x in image pixels. */
    val centreX: Double,
    /** Centre y in image pixels. */
    val centreY: Double,
    /** Larger semi axis in pixels. */
    val semiMajor: Double,
    /** Smaller semi axis in pixels. */
    val semiMinor: Double,
    /** Turn of the major axis away from the image x axis. */
    val rotationRadians: Double,
)
