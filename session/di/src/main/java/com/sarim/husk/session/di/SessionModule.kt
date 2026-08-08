package com.sarim.husk.session.di

import androidx.room.Room
import com.sarim.husk.session.data.database.SessionDatabase
import com.sarim.husk.session.data.fitting.SolverShellFitter
import com.sarim.husk.session.data.repository.RoomSessionRepositoryImpl
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.CaptureObservationUseCase
import com.sarim.husk.session.domain.usecase.CreateSessionUseCase
import com.sarim.husk.session.domain.usecase.DeleteObjectUseCase
import com.sarim.husk.session.domain.usecase.DeleteSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionsUseCase
import com.sarim.husk.session.domain.usecase.RenameSessionUseCase
import com.sarim.husk.session.domain.usecase.StartObjectUseCase
import com.sarim.husk.session.presentation.SessionDetailScreenUseCase
import com.sarim.husk.session.presentation.SessionDetailViewModel
import com.sarim.husk.session.presentation.SessionListScreenUseCase
import com.sarim.husk.session.presentation.SessionListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

/** Dependency graph for the session feature. */
val sessionModule =
    module {
        single {
            Room
                .databaseBuilder(
                    context = androidContext(),
                    klass = SessionDatabase::class.java,
                    name = DATABASE_NAME,
                ).build()
        }
        single { get<SessionDatabase>().sessionDao() }
        single { get<SessionDatabase>().measurementDao() }

        // Single, so every screen reads and writes the same connection rather than opening its own.
        single<SessionRepository> { RoomSessionRepositoryImpl(get(), get()) }

        // Identity and time enter the graph here and nowhere else, which is what lets the use case
        // be tested without either.
        factory { CreateSessionUseCase(get(), { UUID.randomUUID().toString() }, { Instant.now() }) }
        // Single, because it loads a native library. A factory would reload it per screen.
        single<ShellFitter> { SolverShellFitter() }

        factory { ObserveSessionsUseCase(get()) }
        factory { ObserveSessionUseCase(get()) }
        factory { StartObjectUseCase(get(), { UUID.randomUUID().toString() }) }
        factory { CaptureObservationUseCase(get(), get()) }
        factory { DeleteObjectUseCase(get()) }
        factory { RenameSessionUseCase(get()) }
        factory { DeleteSessionUseCase(get()) }

        factory { SessionListScreenUseCase(get(), get(), get(), get()) }

        // Until the marker library exists there is one marker, named here rather than invented
        // deeper down where it would be harder to find and replace.
        viewModel { SessionListViewModel(get(), MarkerId(DEFAULT_MARKER_ID)) }

        factory { SessionDetailScreenUseCase(get(), get(), get()) }

        // The session id comes from the back stack entry, so it is a runtime parameter rather than
        // something the graph can supply.
        viewModel { (sessionId: String) -> SessionDetailViewModel(get(), SessionId(sessionId)) }
    }

private const val DATABASE_NAME = "husk-sessions.db"

/** The single marker every session is anchored to until the marker library arrives. */
private const val DEFAULT_MARKER_ID = "husk-default-marker"
