package com.sarim.husk.session.domain.fitting

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.Observation

/**
 * Why a shell could not be recovered.
 *
 * Each case is something a person can act on, which is the point of naming them rather than
 * returning null. "Move further around it" and "that outline is not an ellipse" call for entirely
 * different responses.
 */
enum class FitRefusal {
    /** Fewer than three views. Two outlines do not pin an ellipsoid down. */
    TOO_FEW_VIEWS,

    /** The views were taken from too nearly the same place for the answer to mean anything. */
    VIEWS_TOO_CLOSE,

    /** One of the traced outlines is not a usable ellipse. */
    UNUSABLE_OUTLINE,

    /** The recovered surface was unbounded rather than a shell. */
    NOT_AN_ELLIPSOID,

    /** The fitter refused for a reason this build does not recognise. */
    UNKNOWN,
}

/** The outcome of trying to recover a shell. */
sealed interface ShellFit {
    /** A shell worth showing. */
    data class Fitted(
        /** The recovered shell, in the marker's frame. */
        val shell: Ellipsoid,
        /** How far it can be trusted. */
        val quality: MeasurementQuality,
    ) : ShellFit

    /** No shell, and why not. */
    data class Refused(
        /** What to tell the person holding the phone. */
        val reason: FitRefusal,
    ) : ShellFit
}

/**
 * Recovers a shell from the views taken of it.
 *
 * A port, implemented outside the domain. The real fitter carries a native library, and the domain
 * describes what was measured without depending on what measured it.
 */
interface ShellFitter {
    /** Fits a shell to [observations]. */
    fun fit(observations: List<Observation>): ShellFit
}
