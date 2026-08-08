package com.sarim.husk.session.data.fitting

import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.solver.EllipseObservation
import com.sarim.husk.solver.EllipsoidSolver
import com.sarim.husk.solver.RefusalReason
import com.sarim.husk.solver.SolveOutcome

/**
 * The domain's [ShellFitter], backed by the native solver.
 *
 * This is where the solver's vocabulary is translated into something a person can act on. The solver
 * reports what the linear algebra did; the app has to say what to do about it, and those are not the
 * same sentence.
 *
 * [solve] is injected so the translation can be tested without a device. The solver itself runs
 * through JNI, and everything decided here — which refusal is shown, whether the views arrive intact
 * — is ordinary Kotlin that has no business needing an emulator to check.
 */
class SolverShellFitter(
    private val solve: (List<EllipseObservation>) -> SolveOutcome = EllipsoidSolver()::solve,
) : ShellFitter {
    override fun fit(observations: List<Observation>): ShellFit =
        when (val outcome = solve(observations.map(Observation::toSolverObservation))) {
            is SolveOutcome.Resolved ->
                ShellFit.Fitted(
                    shell = outcome.ellipsoid,
                    quality =
                        MeasurementQuality(
                            viewSpreadRadians = outcome.quality.viewSpreadRadians,
                            conicResidual = outcome.quality.conicResidual,
                            nullSpaceMargin = outcome.quality.nullSpaceMargin,
                        ),
                )

            is SolveOutcome.Refused -> ShellFit.Refused(outcome.reason.toFitRefusal())
        }
}

private fun Observation.toSolverObservation() =
    EllipseObservation(
        cameraInAnchor = cameraInAnchor,
        intrinsics = intrinsics,
        outline = outline,
    )

/**
 * The solver's reason, as advice.
 *
 * Exhaustive rather than defaulted, so a reason added upstream fails to compile here instead of
 * silently becoming [FitRefusal.UNKNOWN] and losing whatever it had to say.
 */
private fun RefusalReason.toFitRefusal(): FitRefusal =
    when (this) {
        RefusalReason.TOO_FEW_VIEWS -> FitRefusal.TOO_FEW_VIEWS
        RefusalReason.NULL_SPACE_AMBIGUOUS -> FitRefusal.VIEWS_TOO_CLOSE
        RefusalReason.DEGENERATE_CONIC -> FitRefusal.UNUSABLE_OUTLINE
        RefusalReason.NOT_AN_ELLIPSOID -> FitRefusal.NOT_AN_ELLIPSOID
        // Both mean the app made a mistake, not the person holding the phone. Telling them to move
        // would send them chasing a fault that is not theirs.
        RefusalReason.INCONSISTENT_INPUT -> FitRefusal.UNKNOWN
        RefusalReason.UNKNOWN -> FitRefusal.UNKNOWN
    }
