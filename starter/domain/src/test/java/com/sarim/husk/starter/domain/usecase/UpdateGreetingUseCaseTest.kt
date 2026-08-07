package com.sarim.husk.starter.domain.usecase

import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateGreetingUseCaseTest {
    @Test
    fun `blank names use the Android default`() =
        runTest {
            // Given
            val repository = RecordingGreetingRepository()
            val useCase = UpdateGreetingUseCase(repository)

            // When
            useCase("   ")

            // Then
            assertEquals(Greeting("Hello, Android!"), repository.savedGreeting)
        }

    @Test
    fun `names are trimmed before the greeting is saved`() =
        runTest {
            // Given
            val repository = RecordingGreetingRepository()
            val useCase = UpdateGreetingUseCase(repository)

            // When
            useCase("  Ada  ")

            // Then
            assertEquals(Greeting("Hello, Ada!"), repository.savedGreeting)
        }
}

private class RecordingGreetingRepository : GreetingRepository {
    private val current = MutableStateFlow(Greeting("unused"))
    override val greeting: Flow<Greeting> = current
    var savedGreeting: Greeting? = null
        private set

    override suspend fun save(greeting: Greeting) {
        savedGreeting = greeting
        current.value = greeting
    }
}
