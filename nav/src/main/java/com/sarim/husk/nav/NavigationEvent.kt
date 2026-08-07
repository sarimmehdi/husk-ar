package com.sarim.husk.nav

/** Describes an application-level navigation request. */
sealed interface NavigationEvent {
    /** Adds [route] to the current navigation back stack. */
    data class NavigateForward(
        /** Destination appended to the back stack. */
        val route: Route,
    ) : NavigationEvent

    /** Removes the current destination when a previous destination exists. */
    data object NavigateBackward : NavigationEvent

    /** Replaces the complete back stack with [route]. */
    data class ResetTo(
        /** Destination that becomes the new root. */
        val route: Route,
    ) : NavigationEvent
}
