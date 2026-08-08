package com.sarim.husk

import android.app.Application
import com.sarim.husk.session.di.sessionModule
import com.sarim.husk.starter.di.starterModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Initializes dependency injection for the generated starter application. */
class StarterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@StarterApplication)
            modules(starterModule, sessionModule)
        }
    }
}
