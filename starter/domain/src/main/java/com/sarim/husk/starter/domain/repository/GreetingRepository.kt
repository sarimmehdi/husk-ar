package com.sarim.husk.starter.domain.repository

import com.sarim.husk.starter.domain.model.Greeting
import kotlinx.coroutines.flow.Flow

/** Persistence-independent contract for observing and updating the greeting. */
interface GreetingRepository {
    /** Observable greeting shown by the starter feature. */
    val greeting: Flow<Greeting>

    /** Replaces the currently stored [greeting]. */
    suspend fun save(greeting: Greeting)
}
