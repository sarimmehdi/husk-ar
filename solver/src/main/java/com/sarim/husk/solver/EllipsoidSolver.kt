package com.sarim.husk.solver

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3

/** One capture: where the camera was, what lens it had, and the ellipse traced on that frame. */
data class EllipseObservation(
    /** The camera's pose in the marker anchor's frame, as ARCore reports it. */
    val cameraInAnchor: Pose,
    /** The lens that took this frame, in the pixels the outline was traced in. */
    val intrinsics: CameraIntrinsics,
    /** The traced outline, in dual form. */
    val outline: DualConic,
)

/** How much to trust a resolved ellipsoid. */
data class SolveQuality(
    /**
     * How clearly one answer stood apart from the alternatives.
     *
     * A pass or fail signal rather than a grade: it collapses when the views cannot determine an
     * ellipsoid at all, and barely moves between a good capture and a mediocre one.
     */
    val nullSpaceMargin: Double,
    /**
     * How closely the ellipsoid reprojects onto the traced outlines, unitless.
     *
     * Catches gross mistakes such as a view of a different object. Explicitly **not** an accuracy
     * measure: poorly triangulated views give an ellipsoid that matches every outline closely and is
     * still the wrong size. Judge accuracy by [viewSpreadRadians].
     */
    val conicResidual: Double,
    /**
     * The widest angle between any two lines of sight to the object.
     *
     * The one number here that tracks accuracy. Around a radian a pixel of tracing error costs a
     * couple of percent of radius; below about a fifth of a radian it degrades sharply.
     */
    val viewSpreadRadians: Double,
)

/** Why the solver declined to produce an ellipsoid. */
enum class RefusalReason {
    /** Fewer than three views. Two outlines do not constrain an ellipsoid. */
    TOO_FEW_VIEWS,

    /** The buffers handed across did not describe the number of views claimed. */
    INCONSISTENT_INPUT,

    /** An outline was not a proper ellipse, so it had no centre to normalise around. */
    DEGENERATE_CONIC,

    /** More than one ellipsoid fits. Usually a camera that barely moved between captures. */
    NULL_SPACE_AMBIGUOUS,

    /** The recovered surface was unbounded — a hyperboloid rather than an ellipsoid. */
    NOT_AN_ELLIPSOID,

    /** The native layer returned a code this build does not know. */
    UNKNOWN,
}

/** What came back from a solve. */
sealed interface SolveOutcome {
    /** The diagnostics, which are reported whether or not an ellipsoid came out. */
    val quality: SolveQuality

    /** An ellipsoid the solver stands behind. */
    data class Resolved(
        /** The recovered shell, in the marker anchor's frame. */
        val ellipsoid: Ellipsoid,
        override val quality: SolveQuality,
    ) : SolveOutcome

    /** No ellipsoid, and why. */
    data class Refused(
        /** What went wrong. */
        val reason: RefusalReason,
        override val quality: SolveQuality,
    ) : SolveOutcome
}

/**
 * Recovers a 3D ellipsoid from ellipses traced across several views.
 *
 * The arithmetic runs in C++; this converts to and from it. Calls are synchronous and take well
 * under a millisecond for a handful of views, so there is nothing to schedule off the main thread
 * beyond whatever the caller is already doing.
 */
class EllipsoidSolver internal constructor(
    private val estimator: NativeEstimator,
) {
    constructor() : this(JniEstimator)

    /**
     * Solves for the ellipsoid the given [observations] all look at.
     *
     * Order does not matter, but each observation's pose and outline have to describe the same frame
     * as each other, and every observation the same object.
     */
    fun solve(observations: List<EllipseObservation>): SolveOutcome {
        val projections = DoubleArray(observations.size * PROJECTION_STRIDE)
        val outlines = DoubleArray(observations.size * CONIC_STRIDE)

        observations.forEachIndexed { index, observation ->
            val matrix = observation.intrinsics.projectionFrom(observation.cameraInAnchor)
            var slot = index * PROJECTION_STRIDE
            for (row in matrix.indices) {
                for (column in matrix[row].indices) {
                    projections[slot++] = matrix[row][column]
                }
            }

            val outline = observation.outline
            val upperTriangle =
                listOf(outline.m00, outline.m01, outline.m02, outline.m11, outline.m12, outline.m22)
            upperTriangle.forEachIndexed { slot, value ->
                outlines[index * CONIC_STRIDE + slot] = value
            }
        }

        val packed = DoubleArray(PACKED_SIZE)
        val code = estimator.estimate(projections, outlines, observations.size, packed)
        val quality =
            SolveQuality(
                nullSpaceMargin = packed[MARGIN_SLOT],
                conicResidual = packed[RESIDUAL_SLOT],
                viewSpreadRadians = packed[SPREAD_SLOT],
            )

        if (code != CODE_RESOLVED) return SolveOutcome.Refused(reasonOf(code), quality)

        return SolveOutcome.Resolved(
            ellipsoid =
                Ellipsoid(
                    centre = vectorAt(packed, CENTRE_SLOT),
                    radii = vectorAt(packed, RADII_SLOT),
                    rotation = Quaternion.fromRotationMatrix(rotationOf(packed)),
                ),
            quality = quality,
        )
    }

    private fun vectorAt(
        packed: DoubleArray,
        slot: Int,
    ): Vector3 = Vector3(packed[slot], packed[slot + 1], packed[slot + 2])

    private fun rotationOf(packed: DoubleArray): Array<DoubleArray> =
        Array(ROTATION_ROWS) { row ->
            DoubleArray(ROTATION_ROWS) { column ->
                packed[ROTATION_SLOT + row * ROTATION_ROWS + column]
            }
        }

    private fun reasonOf(code: Int): RefusalReason =
        when (code) {
            CODE_TOO_FEW_VIEWS -> RefusalReason.TOO_FEW_VIEWS
            CODE_INCONSISTENT_INPUT -> RefusalReason.INCONSISTENT_INPUT
            CODE_DEGENERATE_CONIC -> RefusalReason.DEGENERATE_CONIC
            CODE_NULL_SPACE_AMBIGUOUS -> RefusalReason.NULL_SPACE_AMBIGUOUS
            CODE_NOT_AN_ELLIPSOID -> RefusalReason.NOT_AN_ELLIPSOID
            else -> RefusalReason.UNKNOWN
        }

    /** Layout of the buffers shared with the native solver. */
    internal companion object {
        /** Doubles per view: a 3x4 projection matrix, row-major. */
        const val PROJECTION_STRIDE = 12

        /** Doubles per outline: the upper triangle of a symmetric 3x3. */
        const val CONIC_STRIDE = 6

        /** Doubles in a packed result: centre, radii, rotation, then three diagnostics. */
        const val PACKED_SIZE = 18

        /** First slot of the centre. */
        const val CENTRE_SLOT = 0

        /** First slot of the radii. */
        const val RADII_SLOT = 3

        /** First slot of the row-major rotation. */
        const val ROTATION_SLOT = 6

        /** Rows and columns in the packed rotation. */
        const val ROTATION_ROWS = 3

        /** Slot holding the null space margin. */
        const val MARGIN_SLOT = 15

        /** Slot holding the conic residual. */
        const val RESIDUAL_SLOT = 16

        /** Slot holding the view spread, in radians. */
        const val SPREAD_SLOT = 17

        // Mirrors of the codes in core/bridge.h. An enum crosses JNI as nothing richer than an
        // integer, so both sides pin these literals in their own tests; changing one without the
        // other fails a test rather than quietly relabelling every refusal.

        /** The solve produced an ellipsoid. */
        const val CODE_RESOLVED = 0

        /** Mirror of `kCodeTooFewViews`. */
        const val CODE_TOO_FEW_VIEWS = 1

        /** Mirror of `kCodeInconsistentInput`. */
        const val CODE_INCONSISTENT_INPUT = 2

        /** Mirror of `kCodeDegenerateConic`. */
        const val CODE_DEGENERATE_CONIC = 3

        /** Mirror of `kCodeNullSpaceAmbiguous`. */
        const val CODE_NULL_SPACE_AMBIGUOUS = 4

        /** Mirror of `kCodeNotAnEllipsoid`. */
        const val CODE_NOT_AN_ELLIPSOID = 5
    }
}
