package com.sarim.husk.session.di

import android.graphics.BitmapFactory
import androidx.room.Room
import com.sarim.husk.ar.MarkerImage
import com.sarim.husk.session.data.database.MIGRATION_1_2
import com.sarim.husk.session.data.database.SessionDatabase
import com.sarim.husk.session.data.fitting.SolverShellFitter
import com.sarim.husk.session.data.repository.RoomSessionRepositoryImpl
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.AdjustShellUseCase
import com.sarim.husk.session.domain.usecase.CaptureObservationUseCase
import com.sarim.husk.session.domain.usecase.CreateSessionUseCase
import com.sarim.husk.session.domain.usecase.DeleteObjectUseCase
import com.sarim.husk.session.domain.usecase.DeleteSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionsUseCase
import com.sarim.husk.session.domain.usecase.RenameSessionUseCase
import com.sarim.husk.session.domain.usecase.StartObjectUseCase
import com.sarim.husk.session.presentation.CaptureScreenUseCase
import com.sarim.husk.session.presentation.CaptureViewModel
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
                ).addMigrations(MIGRATION_1_2)
                .build()
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
        factory { AdjustShellUseCase(get()) }
        factory { RenameSessionUseCase(get()) }
        factory { DeleteSessionUseCase(get()) }

        factory { SessionListScreenUseCase(get(), get(), get(), get()) }

        // Until the marker library exists there is one marker, named here rather than invented
        // deeper down where it would be harder to find and replace.
        viewModel { SessionListViewModel(get(), MarkerId(DEFAULT_MARKER_ID)) }

        factory { SessionDetailScreenUseCase(get(), get(), get(), get()) }
        factory { CaptureScreenUseCase(get()) }

        // Loaded once. Decoding a nine hundred pixel square JPEG per screen would stutter the
        // camera at exactly the moment someone is trying to hold the phone still.
        single {
            MarkerImage(
                name = DEFAULT_MARKER_NAME,
                bitmap =
                    androidContext().assets.open(DEFAULT_MARKER_ASSET).use {
                        BitmapFactory.decodeStream(it)
                    },
                widthMetres = DEFAULT_MARKER_WIDTH_METRES,
            )
        }

        viewModel { (sessionId: String, objectId: String, label: String) ->
            CaptureViewModel(
                useCases = get(),
                sessionId = SessionId(sessionId),
                objectId = ObjectId(objectId),
                label = label,
                newId = { UUID.randomUUID().toString() },
                clock = { Instant.now() },
            )
        }

        // The session id comes from the back stack entry, so it is a runtime parameter rather than
        // something the graph can supply.
        viewModel { (sessionId: String) -> SessionDetailViewModel(get(), SessionId(sessionId)) }
    }

private const val DATABASE_NAME = "husk-sessions.db"

/** The bundled marker, from ARCore's own augmented images guide. It scores 100 for trackability. */
private const val DEFAULT_MARKER_ASSET = "markers/earth.jpg"

/**
 * The printed width of the bundled marker, in metres.
 *
 * Every measurement in the app is scaled by this. ARCore reads the marker's apparent size and
 * believes this number for its real one, so printing the image at a different width makes every
 * result wrong by exactly that ratio, with nothing on screen to say so. Twenty centimetres is a
 * comfortable size on A4 with a margin.
 */
private const val DEFAULT_MARKER_WIDTH_METRES = 0.20f

/** The single marker every session is anchored to until the marker library arrives. */
private const val DEFAULT_MARKER_ID = "husk-default-marker"

/** The name ARCore reports when it recognises the bundled marker. */
private const val DEFAULT_MARKER_NAME = "husk-default-marker"
