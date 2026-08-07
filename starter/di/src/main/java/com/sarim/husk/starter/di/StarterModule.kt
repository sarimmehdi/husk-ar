package com.sarim.husk.starter.di

import com.sarim.husk.starter.data.repository.InMemoryGreetingRepositoryImpl
import com.sarim.husk.starter.domain.repository.GreetingRepository
import com.sarim.husk.starter.domain.usecase.ObserveGreetingUseCase
import com.sarim.husk.starter.domain.usecase.UpdateGreetingUseCase
import com.sarim.husk.starter.presentation.StarterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency graph for the in-memory starter feature. */
val starterModule =
    module {
        single<GreetingRepository> { InMemoryGreetingRepositoryImpl() }
        factory { ObserveGreetingUseCase(get()) }
        factory { UpdateGreetingUseCase(get()) }
        viewModel { StarterViewModel(get(), get()) }
    }
