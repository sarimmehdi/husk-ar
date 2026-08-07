package com.sarim.husk.starter.data.repository

import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local [GreetingRepository] used by the dependency-light demo preset. */
class InMemoryGreetingRepositoryImpl : GreetingRepository {
    private val mutableGreeting = MutableStateFlow(Greeting(DEFAULT_GREETING))

    override val greeting: StateFlow<Greeting> = mutableGreeting.asStateFlow()

    override suspend fun save(greeting: Greeting) {
        mutableGreeting.emit(greeting)
    }

    private companion object {
        const val DEFAULT_GREETING = "Hello, Android!"
    }
}
