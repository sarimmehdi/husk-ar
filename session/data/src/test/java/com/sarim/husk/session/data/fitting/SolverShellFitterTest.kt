package com.sarim.husk.session.data.fitting

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.ObservationId
import com.sarim.husk.solver.EllipseObservation
import com.sarim.husk.solver.RefusalReason
import com.sarim.husk.solver.SolveOutcome
import com.sarim.husk.solver.SolveQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The adapter between the solver and the domain.
 *
 * Nothing numerical happens here — that is pinned by the solver's own tests — but everything about
 * which refusal a person is shown, and whether the views reach the solver at all, does. The solve
 * call is injected so all of it is testable without a device.
 */
class SolverShellFitterTest {
    private val shell =
        Ellipsoid(
            centre = Vector3(0.1, 0.2, 0.3),
            radii = Vector3(0.05, 0.06, 0.07),
            rotation = Quaternion.IDENTITY,
        )

    private fun observation(
        id: String,
        translation: Vector3 = Vector3.ZERO,
    ) = Observation(
        id = ObservationId(id),
        cameraInAnchor = Pose(translation, Quaternion.IDENTITY),
        intrinsics = CameraIntrinsics(800.0, 800.0, 640.0, 360.0),
        outline = DualConic(4.0, 0.0, 0.0, 9.0, 0.0, -1.0),
        capturedAt = Instant.parse("2026-08-08T10:00:00Z"),
    )

    private fun fitter(
        outcome: SolveOutcome,
        record: (List<EllipseObservation>) -> Unit = {},
    ) = SolverShellFitter { observations ->
        record(observations)
        outcome
    }

    private fun resolved(
        margin: Double = 0.0009,
        residual: Double = 0.0012,
        spread: Double = 1.4,
    ) = SolveOutcome.Resolved(shell, SolveQuality(margin, residual, spread))

    private fun refused(reason: RefusalReason) = SolveOutcome.Refused(reason, SolveQuality(0.0, 0.0, 0.0))

    @Test
    fun `a resolved solve becomes a fitted shell`() {
        val fit = fitter(resolved()).fit(listOf(observation("v1")))

        assertEquals(shell, (fit as ShellFit.Fitted).shell)
    }

    @Test
    fun `the quality travels across unchanged`() {
        // Three separate doubles in the same order on both sides. Transposing two would be invisible
        // until someone read the debug overlay and believed it.
        val fit = fitter(resolved(margin = 0.5, residual = 0.25, spread = 1.75)).fit(listOf(observation("v1")))

        val quality = (fit as ShellFit.Fitted).quality
        assertEquals(0.5, quality.nullSpaceMargin, TOLERANCE)
        assertEquals(0.25, quality.conicResidual, TOLERANCE)
        assertEquals(1.75, quality.viewSpreadRadians, TOLERANCE)
    }

    @Test
    fun `every observation reaches the solver in order`() {
        var seen: List<EllipseObservation> = emptyList()
        val observations =
            listOf(
                observation("v1", Vector3(0.0, 0.0, 0.0)),
                observation("v2", Vector3(1.0, 0.0, 0.0)),
                observation("v3", Vector3(2.0, 0.0, 0.0)),
            )

        fitter(resolved(), record = { seen = it }).fit(observations)

        assertEquals(3, seen.size)
        assertEquals(
            observations.map { it.cameraInAnchor.translation.x },
            seen.map { it.cameraInAnchor.translation.x },
        )
    }

    @Test
    fun `each pose keeps the intrinsics and outline it was captured with`() {
        // A pose paired with another frame's lens or outline puts the object somewhere it never was,
        // and the solver has no way to notice.
        var seen: List<EllipseObservation> = emptyList()
        val observation = observation("v1")

        fitter(resolved(), record = { seen = it }).fit(listOf(observation))

        assertEquals(observation.intrinsics, seen.single().intrinsics)
        assertEquals(observation.outline, seen.single().outline)
        assertEquals(observation.cameraInAnchor, seen.single().cameraInAnchor)
    }

    @Test
    fun `too few views is reported as such`() {
        val fit = fitter(refused(RefusalReason.TOO_FEW_VIEWS)).fit(emptyList())

        assertEquals(FitRefusal.TOO_FEW_VIEWS, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `an ambiguous solve is reported as views taken too close together`() {
        // The solver's own words describe the linear algebra. This is the only place they are turned
        // into something a person can act on, and "move further around it" is the whole point.
        val fit = fitter(refused(RefusalReason.NULL_SPACE_AMBIGUOUS)).fit(listOf(observation("v1")))

        assertEquals(FitRefusal.VIEWS_TOO_CLOSE, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `a degenerate conic is reported as an unusable outline`() {
        val fit = fitter(refused(RefusalReason.DEGENERATE_CONIC)).fit(listOf(observation("v1")))

        assertEquals(FitRefusal.UNUSABLE_OUTLINE, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `an unbounded surface is reported as not an ellipsoid`() {
        val fit = fitter(refused(RefusalReason.NOT_AN_ELLIPSOID)).fit(listOf(observation("v1")))

        assertEquals(FitRefusal.NOT_AN_ELLIPSOID, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `inconsistent input is not dressed up as advice`() {
        // This one means the app packed the buffers wrong. Telling someone to move the phone would
        // send them chasing a fault that is not theirs.
        val fit = fitter(refused(RefusalReason.INCONSISTENT_INPUT)).fit(listOf(observation("v1")))

        assertEquals(FitRefusal.UNKNOWN, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `an unrecognised refusal stays unrecognised`() {
        val fit = fitter(refused(RefusalReason.UNKNOWN)).fit(listOf(observation("v1")))

        assertEquals(FitRefusal.UNKNOWN, (fit as ShellFit.Refused).reason)
    }

    @Test
    fun `every solver refusal has a mapping`() {
        // A new RefusalReason added upstream would otherwise fall through to UNKNOWN and quietly
        // lose whatever advice it carried.
        RefusalReason.entries.forEach { reason ->
            val fit = fitter(refused(reason)).fit(listOf(observation("v1")))
            assertEquals(
                "no mapping decided for $reason",
                expectedRefusals.getValue(reason),
                (fit as ShellFit.Refused).reason,
            )
        }
    }

    private val expectedRefusals =
        mapOf(
            RefusalReason.TOO_FEW_VIEWS to FitRefusal.TOO_FEW_VIEWS,
            RefusalReason.NULL_SPACE_AMBIGUOUS to FitRefusal.VIEWS_TOO_CLOSE,
            RefusalReason.DEGENERATE_CONIC to FitRefusal.UNUSABLE_OUTLINE,
            RefusalReason.NOT_AN_ELLIPSOID to FitRefusal.NOT_AN_ELLIPSOID,
            RefusalReason.INCONSISTENT_INPUT to FitRefusal.UNKNOWN,
            RefusalReason.UNKNOWN to FitRefusal.UNKNOWN,
        )

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
