package com.sarim.husk.session.domain.model

import com.sarim.husk.geometry.Ellipsoid

/** Identifies something measured within a session. */
data class ObjectId(
    /** The identifier itself. */
    val value: String,
)

/**
 * How far a measurement can be trusted, in words rather than radians.
 *
 * The bands come from measuring the solver against synthetic captures: with a pixel of tracing
 * error, radius error runs about 2% at a radian of view spread, 4% at 0.4, and then climbs sharply —
 * past 50% by 0.2 and beyond 1000% below 0.05. The boundaries below sit where that curve turns.
 */
enum class MeasurementConfidence {
    /** Not enough separated views yet for a shell at all. */
    UNMEASURED,

    /** Views close together. The shape is indicative; the numbers are not worth quoting. */
    ROUGH,

    /** Views spread well around the object. Within a few percent. */
    GOOD,

    /** Adjusted by hand, so it is whatever the person decided rather than what was measured. */
    ADJUSTED,
}

/**
 * How far a measurement can be trusted.
 *
 * Deliberately a copy of what the solver reports rather than a reference to it. The solver is
 * infrastructure — it carries a native library — and the domain describes what was measured without
 * depending on what measured it.
 */
data class MeasurementQuality(
    /**
     * The widest angle between any two lines of sight to the object, in radians.
     *
     * The only one of these three that tracks accuracy. Around a radian, a pixel of tracing error
     * costs a couple of percent of radius; below about a fifth of a radian it degrades sharply.
     */
    val viewSpreadRadians: Double,
    /**
     * How closely the shell reprojects onto the traced outlines.
     *
     * Catches gross mistakes such as two views of different objects. Not an accuracy measure: badly
     * triangulated views fit every outline closely and are still the wrong size.
     */
    val conicResidual: Double,
    /** How clearly one shell stood apart from the alternatives when it was solved. */
    val nullSpaceMargin: Double,
) {
    /**
     * This measurement's confidence band.
     *
     * Judged on view spread alone. The residual looks like the obvious candidate and is the wrong
     * one: measured across a sweep of capture geometries it stayed flat while radius error ran from
     * 1% to 5000%, because a badly triangulated shell still reprojects onto every outline it was fit
     * to.
     */
    fun confidence(): MeasurementConfidence =
        when {
            viewSpreadRadians >= GOOD_SPREAD_RADIANS -> MeasurementConfidence.GOOD
            viewSpreadRadians > 0.0 -> MeasurementConfidence.ROUGH
            else -> MeasurementConfidence.UNMEASURED
        }

    private companion object {
        /** Below this, a pixel of tracing error costs more than a few percent of radius. */
        const val GOOD_SPREAD_RADIANS = 0.4
    }
}

/**
 * One thing measured in a session: a shell, and the captures it was derived from.
 *
 * The observations are kept rather than discarded once solved, because they are what makes a
 * measurement reviewable — a shell alone cannot be checked, re-solved after a correction, or
 * replayed.
 */
data class MeasuredObject(
    /** Stable identity. */
    val id: ObjectId,
    /** What the person called it. */
    val label: String,
    /** The recovered shell, in the marker's frame. */
    val shell: Ellipsoid,
    /** The captures it was solved from, in the order they were taken. */
    val observations: List<Observation> = emptyList(),
    /** How far the shell can be trusted, or null when it was placed rather than solved. */
    val quality: MeasurementQuality? = null,
    /**
     * Whether the shell has been adjusted by hand since it was fitted.
     *
     * Kept because the solver's confidence stops describing an adjusted shell the moment it is
     * moved: the number was earned by views that no longer match what is on screen. Reporting it
     * afterwards would be the app vouching for a figure a person typed in.
     */
    val isHandAdjusted: Boolean = false,
)
