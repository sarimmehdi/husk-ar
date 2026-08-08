package com.sarim.husk.session.di

import com.sarim.husk.session.data.repository.InMemorySessionRepositoryImpl
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.CreateSessionUseCase
import com.sarim.husk.session.domain.usecase.DeleteSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionsUseCase
import com.sarim.husk.session.domain.usecase.RenameSessionUseCase
import com.sarim.husk.session.presentation.SessionListScreenUseCase
import com.sarim.husk.session.presentation.SessionListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

/** Dependency graph for the session feature. */
val sessionModule =
    module {
        // Single, because the in-memory store is the only copy of the data. A factory would hand
        // every screen its own empty repository.
        single<SessionRepository> { InMemorySessionRepositoryImpl() }

        // Identity and time enter the graph here and nowhere else, which is what lets the use case
        // be tested without either.
        factory { CreateSessionUseCase(get(), { UUID.randomUUID().toString() }, { Instant.now() }) }
        factory { ObserveSessionsUseCase(get()) }
        factory { RenameSessionUseCase(get()) }
        factory { DeleteSessionUseCase(get()) }

        factory { SessionListScreenUseCase(get(), get(), get(), get()) }

        // Until the marker library exists there is one marker, named here rather than invented
        // deeper down where it would be harder to find and replace.
        viewModel { SessionListViewModel(get(), MarkerId(DEFAULT_MARKER_ID)) }
    }

/** The single marker every session is anchored to until the marker library arrives. */
private const val DEFAULT_MARKER_ID = "husk-default-marker"
