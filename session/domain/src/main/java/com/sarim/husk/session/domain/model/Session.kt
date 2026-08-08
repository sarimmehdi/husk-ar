package com.sarim.husk.session.domain.model

import java.time.Instant

/** Identifies a session. */
data class SessionId(
    /** The identifier itself. Opaque; nothing may be inferred from its shape. */
    val value: String,
)

/** Identifies the marker image a session is anchored to. */
data class MarkerId(
    /** The identifier itself. */
    val value: String,
)

/**
 * A sitting of measurements, all anchored to one marker.
 *
 * The marker is what makes a session mean anything later. Every pose and every shell inside is
 * expressed in that marker's frame, so putting the marker back where it was restores the whole
 * session in place — which is the entire reason measurements are worth keeping.
 */
data class Session(
    /** Stable identity. */
    val id: SessionId,
    /** What the person called it. Not unique, and not an identifier. */
    val name: String,
    /** The marker everything here is measured against. */
    val markerId: MarkerId,
    /** When the session was started. */
    val createdAt: Instant,
    /** What was measured, in the order it was captured. */
    val objects: List<MeasuredObject> = emptyList(),
)
