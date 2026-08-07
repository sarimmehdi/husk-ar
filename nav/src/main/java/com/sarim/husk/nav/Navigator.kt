package com.sarim.husk.nav

import androidx.compose.runtime.mutableStateListOf

/** Owns the observable application navigation back stack. */
class Navigator(
    startDestination: Route,
) {
    /** Ordered destinations from the root through the currently visible route. */
    val backStack = mutableStateListOf<Route>(startDestination)

    /** Adds [route] as the current destination. */
    fun navigateTo(route: Route) {
        backStack.add(route)
    }

    /** Clears the current history and installs [route] as the new root. */
    fun resetTo(route: Route) {
        backStack.clear()
        backStack.add(route)
    }

    /** Returns to the previous destination without removing the root. */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    /** Applies a typed [event] to the navigation back stack. */
    fun onNavigate(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.NavigateForward -> navigateTo(event.route)
            NavigationEvent.NavigateBackward -> pop()
            is NavigationEvent.ResetTo -> resetTo(event.route)
        }
    }
}
