package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first

/**
 * Records one traced view of an object and re-fits its shell.
 *
 * Recording and fitting are one operation rather than two because they are one thing a person does:
 * they trace an outline and expect the overlay to answer. Splitting them would leave the rule about
 * what happens after a capture sitting in a view model, where it could differ between screens.
 */
class CaptureObservationUseCase(
    private val repository: SessionRepository,
    private val fitter: ShellFitter,
) {
    /**
     * Adds [observation] to an object and returns what the fitter made of the views so far.
     *
     * The observation is kept whether or not the fit succeeds — a view is evidence even when it is
     * not yet enough, and discarding it would mean a capture that refuses at three views could never
     * reach four.
     *
     * A refused fit leaves any shell already on the object untouched. Overwriting it would blank the
     * overlay the person is looking at, on the strength of one view that happened not to help.
     */
    suspend operator fun invoke(
        sessionId: SessionId,
        objectId: ObjectId,
        observation: Observation,
    ): ShellFit {
        val measured =
            repository
                .observeSession(sessionId)
                .first()
                ?.objects
                ?.firstOrNull { it.id == objectId }
                ?: return ShellFit.Refused(FitRefusal.UNKNOWN)

        val observations = measured.observations + observation
        val fit = fitter.fit(observations)

        val updated =
            when (fit) {
                is ShellFit.Fitted ->
                    measured.copy(
                        observations = observations,
                        shell = fit.shell,
                        quality = fit.quality,
                    )

                is ShellFit.Refused -> measured.copy(observations = observations)
            }
        repository.putObject(sessionId, updated)
        return fit
    }
}
