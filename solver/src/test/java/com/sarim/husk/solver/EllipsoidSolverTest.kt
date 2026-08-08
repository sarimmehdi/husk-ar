package com.sarim.husk.solver

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the conversion either side of the native call, with the call itself replaced.
 *
 * The arithmetic is already pinned by the C++ host tests; what is unproven here is whether the right
 * numbers reach it and the right ones come back. That is pure JVM work, so it runs without a device.
 */
class EllipsoidSolverTest {
    /** Records what it was handed and replays a canned result. */
    private class RecordingEstimator(
        private val code: Int = 0,
        private val result: DoubleArray = resolvedResult(),
    ) : NativeEstimator {
        var projections: DoubleArray? = null
        var conics: DoubleArray? = null
        var viewCount: Int = -1

        override fun estimate(
            projections: DoubleArray,
            conics: DoubleArray,
            viewCount: Int,
            out: DoubleArray,
        ): Int {
            this.projections = projections.copyOf()
            this.conics = conics.copyOf()
            this.viewCount = viewCount
            result.copyInto(out)
            return code
        }
    }

    private val intrinsics = CameraIntrinsics(800.0, 800.0, 640.0, 360.0)

    private fun observation(
        translation: Vector3 = Vector3.ZERO,
        outline: DualConic = DualConic(4.0, 0.0, 0.0, 9.0, 0.0, -1.0),
    ) = EllipseObservation(
        cameraInAnchor = Pose(translation, Quaternion.IDENTITY),
        intrinsics = intrinsics,
        outline = outline,
    )

    @Test
    fun `every view gets twelve doubles and every outline six`() {
        val estimator = RecordingEstimator()

        EllipsoidSolver(estimator).solve(List(4) { observation() })

        assertEquals(4, estimator.viewCount)
        assertEquals(4 * 12, estimator.projections?.size)
        assertEquals(4 * 6, estimator.conics?.size)
    }

    @Test
    fun `each outline is packed as the upper triangle in row order`() {
        // Six distinct values, so a transposed or reordered pack cannot pass by coincidence.
        val estimator = RecordingEstimator()
        val outline = DualConic(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

        EllipsoidSolver(estimator).solve(listOf(observation(outline = outline)))

        assertArrayEqualsWithin(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), estimator.conics!!)
    }

    @Test
    fun `views are packed in the order they were given`() {
        // Each camera sits at a different height, which shows up in the projection's last column. If
        // the strides were wrong these would arrive interleaved.
        val estimator = RecordingEstimator()
        val observations = (0..2).map { observation(translation = Vector3(0.0, it.toDouble(), 0.0)) }

        EllipsoidSolver(estimator).solve(observations)

        val packed = estimator.projections!!
        val heights = (0..2).map { view -> packed[view * 12 + 7] }
        assertTrue(
            "each view must carry its own camera height, got $heights",
            heights[0] != heights[1] && heights[1] != heights[2],
        )
    }

    @Test
    fun `the projection each view carries is the one the intrinsics build`() {
        val estimator = RecordingEstimator()
        val pose = Pose(Vector3(0.3, -0.2, 1.5), Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), 0.4))
        val expected = intrinsics.projectionFrom(pose)

        EllipsoidSolver(estimator).solve(
            listOf(EllipseObservation(pose, intrinsics, DualConic(4.0, 0.0, 0.0, 9.0, 0.0, -1.0))),
        )

        val packed = estimator.projections!!
        for (row in 0..2) {
            for (column in 0..3) {
                assertEquals(
                    "row $row column $column",
                    expected[row][column],
                    packed[row * 4 + column],
                    TOLERANCE,
                )
            }
        }
    }

    @Test
    fun `a resolved result becomes an ellipsoid with its diagnostics`() {
        val estimator = RecordingEstimator(code = 0, result = resolvedResult())

        val outcome = EllipsoidSolver(estimator).solve(List(3) { observation() })

        val resolved = outcome as SolveOutcome.Resolved
        assertEquals(0.1, resolved.ellipsoid.centre.x, TOLERANCE)
        assertEquals(0.2, resolved.ellipsoid.centre.y, TOLERANCE)
        assertEquals(0.3, resolved.ellipsoid.centre.z, TOLERANCE)
        assertEquals(0.4, resolved.ellipsoid.radii.x, TOLERANCE)
        assertEquals(0.5, resolved.ellipsoid.radii.y, TOLERANCE)
        assertEquals(0.6, resolved.ellipsoid.radii.z, TOLERANCE)
        assertEquals(0.0123, resolved.quality.nullSpaceMargin, TOLERANCE)
        assertEquals(0.0456, resolved.quality.conicResidual, TOLERANCE)
        assertEquals(1.25, resolved.quality.viewSpreadRadians, TOLERANCE)
    }

    @Test
    fun `an identity rotation in the packed result survives the round trip`() {
        val estimator = RecordingEstimator()

        val outcome = EllipsoidSolver(estimator).solve(List(3) { observation() })

        val rotation = (outcome as SolveOutcome.Resolved).ellipsoid.rotation
        assertEquals(1.0, rotation.w, TOLERANCE)
        assertEquals(0.0, rotation.x, TOLERANCE)
        assertEquals(0.0, rotation.y, TOLERANCE)
        assertEquals(0.0, rotation.z, TOLERANCE)
    }

    @Test
    fun `a quarter turn in the packed result comes back as a quarter turn`() {
        // Reading the rotation transposed would turn this the other way, which no assertion on the
        // radii would notice.
        val result = resolvedResult()
        // A rotation of ninety degrees about the anchor's y axis, row-major.
        doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0).copyInto(result, 6)
        val estimator = RecordingEstimator(result = result)

        val outcome = EllipsoidSolver(estimator).solve(List(3) { observation() })

        val turned = (outcome as SolveOutcome.Resolved).ellipsoid.rotation.rotate(Vector3(1.0, 0.0, 0.0))
        assertEquals(0.0, turned.x, TOLERANCE)
        assertEquals(0.0, turned.y, TOLERANCE)
        assertEquals(-1.0, turned.z, TOLERANCE)
    }

    @Test
    fun `each refusal code becomes its own reason`() {
        // Pinned to literals on both sides of the boundary. Renumbering the C++ enum without
        // updating these would otherwise relabel every refusal the app shows.
        val expected =
            mapOf(
                1 to RefusalReason.TOO_FEW_VIEWS,
                2 to RefusalReason.INCONSISTENT_INPUT,
                3 to RefusalReason.DEGENERATE_CONIC,
                4 to RefusalReason.NULL_SPACE_AMBIGUOUS,
                5 to RefusalReason.NOT_AN_ELLIPSOID,
            )

        expected.forEach { (code, reason) ->
            val outcome =
                EllipsoidSolver(RecordingEstimator(code = code)).solve(List(3) { observation() })
            assertEquals("code $code", reason, (outcome as SolveOutcome.Refused).reason)
        }
    }

    @Test
    fun `an unrecognised code is reported rather than mistaken for success`() {
        val outcome = EllipsoidSolver(RecordingEstimator(code = 99)).solve(List(3) { observation() })

        assertEquals(RefusalReason.UNKNOWN, (outcome as SolveOutcome.Refused).reason)
    }

    @Test
    fun `a refusal still carries the diagnostics that explain it`() {
        val estimator = RecordingEstimator(code = 4)

        val outcome = EllipsoidSolver(estimator).solve(List(3) { observation() })

        val refused = outcome as SolveOutcome.Refused
        assertEquals(0.0123, refused.quality.nullSpaceMargin, TOLERANCE)
        assertNotNull("the overlay needs something to show for a refusal", refused.quality)
    }

    @Test
    fun `an empty list is handed across as zero views rather than short circuited`() {
        // The native side owns deciding what is too few, so that rule lives in one place.
        val estimator = RecordingEstimator(code = 1)

        EllipsoidSolver(estimator).solve(emptyList())

        assertEquals(0, estimator.viewCount)
        assertEquals(0, estimator.projections?.size)
    }

    private fun assertArrayEqualsWithin(
        expected: DoubleArray,
        actual: DoubleArray,
    ) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals("slot $it", expected[it], actual[it], TOLERANCE) }
    }

    private companion object {
        const val TOLERANCE = 1e-9

        /** A packed result with a distinct value in every slot, grouped as the layout reads. */
        fun resolvedResult() =
            doubleArrayOf(0.1, 0.2, 0.3) +
                doubleArrayOf(0.4, 0.5, 0.6) +
                doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0) +
                doubleArrayOf(0.0123, 0.0456, 1.25)
    }
}
