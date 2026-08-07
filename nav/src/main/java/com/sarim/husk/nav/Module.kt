package com.sarim.husk.nav

import org.koin.dsl.module

/** Creates the navigation dependency graph rooted at [startDestination]. */
fun navModule(startDestination: Route) =
    module {
        single { Navigator(startDestination) }
    }
