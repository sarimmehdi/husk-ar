package com.sarim.husk.starter.data.repository

import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

abstract class GreetingRepositoryContract {
    protected abstract fun createRepository(): GreetingRepository

    @Test
    fun `saved greetings are observed`() =
        runTest {
            // Given
            val repository = createRepository()
            val expected = Greeting("Hello, contract!")

            // When
            repository.save(expected)

            // Then
            assertEquals(expected, repository.greeting.first())
        }
}
