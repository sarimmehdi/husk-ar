package com.sarim.husk.starter.domain.usecase

import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository

/** Applies the starter greeting rule and stores the resulting message. */
class UpdateGreetingUseCase(
    private val repository: GreetingRepository,
) {
    /** Normalizes [name] and saves its formatted greeting. */
    suspend operator fun invoke(name: String) {
        val resolvedName = name.trim().ifEmpty { DEFAULT_NAME }
        repository.save(Greeting(GREETING_PREFIX + resolvedName + GREETING_SUFFIX))
    }

    private companion object {
        const val DEFAULT_NAME = "Android"
        const val GREETING_PREFIX = "Hello, "
        const val GREETING_SUFFIX = "!"
    }
}
