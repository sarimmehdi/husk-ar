package com.sarim.husk.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Marker for destinations that may be stored in the application back stack. */
sealed interface Route : NavKey {
    /** Default destination displayed by a generated application shell. */
    @Serializable
    data object Home : Route

    /**
     * One session and everything measured in it.
     *
     * Carries the id rather than the session itself, so a back stack restored after the process was
     * killed reads the current state instead of a snapshot taken before it died.
     */
    @Serializable
    data class SessionDetail(
        /** Which session to show. */
        val sessionId: String,
    ) : Route

    /** Outlining one object through the camera. */
    @Serializable
    data class Capture(
        /** Which session the object belongs to. */
        val sessionId: String,
        /** Which object is being measured. */
        val objectId: String,
        /** What it is called, so the screen can say so without waiting for a read. */
        val label: String,
    ) : Route
}
