package com.sarim.husk.starter.presentation

import app.cash.turbine.test
import com.sarim.husk.starter.domain.model.Greeting
import com.sarim.husk.starter.domain.repository.GreetingRepository
import com.sarim.husk.starter.domain.usecase.ObserveGreetingUseCase
import com.sarim.husk.starter.domain.usecase.UpdateGreetingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StarterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `editing and saving produces the expected state transitions`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Given
            val repository = FakeGreetingRepository(Greeting("Hello, initial!"))
            val viewModel =
                StarterViewModel(
                    observeGreeting = ObserveGreetingUseCase(repository),
                    updateGreeting = UpdateGreetingUseCase(repository),
                )

            viewModel.uiState.test {
                assertEquals(StarterUiState(greeting = "Hello, initial!"), awaitItem())

                // When
                viewModel.onNameChanged("Ada")

                // Then
                assertEquals(
                    StarterUiState(greeting = "Hello, initial!", name = "Ada"),
                    awaitItem(),
                )

                // When
                viewModel.onSaveGreeting()

                // Then
                assertEquals(
                    StarterUiState(greeting = "Hello, Ada!", name = "Ada"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class FakeGreetingRepository(
    initial: Greeting,
) : GreetingRepository {
    private val mutableGreeting = MutableStateFlow(initial)
    override val greeting: StateFlow<Greeting> = mutableGreeting

    override suspend fun save(greeting: Greeting) {
        mutableGreeting.value = greeting
    }
}
