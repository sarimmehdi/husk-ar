package com.sarim.husk.starter.domain.usecase

import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.Flow

/** Exposes the current greeting without leaking its persistence mechanism. */
class ObserveGreetingUseCase(
    private val repository: GreetingRepository,
) {
    /** Returns the observable greeting stream. */
    operator fun invoke(): Flow<Greeting> = repository.greeting
}
