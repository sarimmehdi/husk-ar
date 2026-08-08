package com.sarim.husk.session.domain.model

import com.sarim.husk.geometry.Ellipsoid

/** Identifies something measured within a session. */
data class ObjectId(
    /** The identifier itself. */
    val value: String,
)

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
)

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
)
