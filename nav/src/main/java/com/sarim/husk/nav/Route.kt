package com.sarim.husk.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Marker for destinations that may be stored in the application back stack. */
sealed interface Route : NavKey {
    /** Default destination displayed by a generated application shell. */
    @Serializable
    data object Home : Route
}
