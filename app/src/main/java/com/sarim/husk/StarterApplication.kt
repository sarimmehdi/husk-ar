package com.sarim.husk

import android.app.Application
import com.sarim.husk.marker.di.BUNDLED_MARKER_ID
import com.sarim.husk.marker.di.bundledMarker
import com.sarim.husk.marker.di.markerModule
import com.sarim.husk.marker.domain.repository.MarkerRepository
import com.sarim.husk.nav.Route
import com.sarim.husk.nav.navModule
import com.sarim.husk.session.di.sessionModule
import com.sarim.husk.starter.di.starterModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.time.Instant

/** Initializes dependency injection for the generated starter application. */
class StarterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@StarterApplication)
            modules(navModule(Route.Home), starterModule, sessionModule, markerModule)
        }
        seedBundledMarker()
    }

    /**
     * Makes sure the bundled marker is in the library.
     *
     * Only written when nothing is stored under its id, so a width someone corrected after measuring
     * their printed sheet is not overwritten on the next launch — which would silently restore the
     * scaling error they had just fixed.
     */
    private fun seedBundledMarker() {
        val repository: MarkerRepository = get()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (repository.observeMarker(BUNDLED_MARKER_ID).first() == null) {
                repository.put(bundledMarker(Instant.now()))
            }
        }
    }
}
