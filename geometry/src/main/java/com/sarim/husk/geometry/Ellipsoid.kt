package com.sarim.husk.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The shell Husk fits around a real object: a centre, three semi axes, and an orientation.
 *
 * Distances are metres, expressed in the marker anchor's frame. That frame is the invariant that
 * lets a session recorded today mean the same thing when the marker is found again next week.
 */
data class Ellipsoid(
    /** Centre in the marker anchor's frame, in metres. */
    val centre: Vector3,
    /** Semi axes in metres, along the ellipsoid's own axes. */
    val radii: Vector3,
    /** Orientation of those axes relative to the anchor. */
    val rotation: Quaternion,
) {
    /** Full widths along each axis, which is what a person reading a measurement expects. */
    fun extent(): Vector3 = radii * 2.0

    /** Enclosed volume in cubic metres. */
    fun volume(): Double = SPHERE_VOLUME_COEFFICIENT * PI * radii.x * radii.y * radii.z

    /**
     * This ellipsoid as a dual quadric, normalised so the homogeneous term is `-1`.
     *
     * Built as `Q = T · diag(a², b², c², -1) · T'` with `T = [[R, t], [0, 1]]`, which expands to
     * `[[R D R' - t t', -t], [-t', -1]]`. Note the **negated** translation in the last column: the
     * centre is `-Q(0..2, 3)`, not `+Q(0..2, 3)`. The reference implementation this port derives
     * from reads that column directly, which places the ellipsoid at its reflection through the
     * origin. That is corrected here and pinned by test.
     */
    @Suppress("MagicNumber")
    fun toDualQuadric(): Array<DoubleArray> {
        val axes =
            listOf(
                rotation.rotate(Vector3(1.0, 0.0, 0.0)) to radii.x,
                rotation.rotate(Vector3(0.0, 1.0, 0.0)) to radii.y,
                rotation.rotate(Vector3(0.0, 0.0, 1.0)) to radii.z,
            )

        val quadric = Array(4) { DoubleArray(4) }
        // R D R', the shape with no position.
        axes.forEach { (axis, radius) ->
            val squared = radius * radius
            val components = listOf(axis.x, axis.y, axis.z)
            for (row in 0..2) {
                for (column in 0..2) {
                    quadric[row][column] += squared * components[row] * components[column]
                }
            }
        }

        val translation = listOf(centre.x, centre.y, centre.z)
        for (row in 0..2) {
            for (column in 0..2) {
                quadric[row][column] -= translation[row] * translation[column]
            }
            quadric[row][3] = -translation[row]
            quadric[3][row] = -translation[row]
        }
        quadric[3][3] = -1.0
        return quadric
    }

    /** Recovery from the solver's output, and the empty result. */
    companion object {
        /** The 4/3 in the volume of an ellipsoid. */
        private const val SPHERE_VOLUME_COEFFICIENT = 4.0 / 3.0

        /** Below this a quadric carries no recoverable shape. */
        private const val DEGENERATE_EPSILON = 1e-12

        /** The ellipsoid with no position, size, or orientation. */
        val EMPTY = Ellipsoid(Vector3.ZERO, Vector3.ZERO, Quaternion.IDENTITY)

        /**
         * Recovers an ellipsoid from a dual [quadric], as produced by the solver.
         *
         * Axes come back ordered by magnitude, which need not match the order they were built in.
         * A quadric with no recoverable shape yields [EMPTY] rather than NaN, so a refused solve
         * cannot silently propagate.
         */
        @Suppress("MagicNumber")
        fun fromDualQuadric(quadric: Array<DoubleArray>): Ellipsoid {
            val homogeneous = quadric[3][3]
            if (abs(homogeneous) < DEGENERATE_EPSILON) return EMPTY

            // Normalise so the homogeneous term is -1; the centre is then the negated last column.
            val scale = -1.0 / homogeneous
            val centre =
                Vector3(
                    -quadric[0][3] * scale,
                    -quadric[1][3] * scale,
                    -quadric[2][3] * scale,
                )

            // The upper block is R D R' - t t', so adding t t' back leaves the shape alone. Note the
            // sign: subtracting here instead would drive the eigenvalues negative and collapse every
            // axis to zero.
            val translation = doubleArrayOf(centre.x, centre.y, centre.z)
            val shape =
                Array(3) { row ->
                    DoubleArray(3) { column ->
                        quadric[row][column] * scale + translation[row] * translation[column]
                    }
                }

            val (eigenvalues, eigenvectors) = SymmetricEigen.decompose(shape)
            val radii =
                Vector3(
                    sqrt(eigenvalues[0].coerceAtLeast(0.0)),
                    sqrt(eigenvalues[1].coerceAtLeast(0.0)),
                    sqrt(eigenvalues[2].coerceAtLeast(0.0)),
                )
            return Ellipsoid(centre, radii, SymmetricEigen.toRotation(eigenvectors))
        }
    }
}
