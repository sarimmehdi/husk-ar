package com.sarim.husk.starter.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.starter.domain.usecase.ObserveGreetingUseCase
import com.sarim.husk.starter.domain.usecase.UpdateGreetingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** User-visible state rendered by the starter screen. */
data class StarterUiState(
    /** Persisted greeting currently displayed to the user. */
    val greeting: String = "",
    /** Editable name used to construct the next greeting. */
    val name: String = "",
)

/** Coordinates greeting observation, editing, and persistence for the starter screen. */
class StarterViewModel(
    observeGreeting: ObserveGreetingUseCase,
    private val updateGreeting: UpdateGreetingUseCase,
) : ViewModel() {
    private val name = MutableStateFlow("")

    /** Lifecycle-independent stream of state rendered by the starter screen. */
    val uiState: StateFlow<StarterUiState> =
        combine(observeGreeting(), name) { greeting, currentName ->
            StarterUiState(greeting = greeting.message, name = currentName)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StarterUiState(),
        )

    /** Updates the editable greeting [value]. */
    fun onNameChanged(value: String) {
        name.value = value
    }

    /** Persists a greeting derived from the current name. */
    fun onSaveGreeting() {
        viewModelScope.launch {
            updateGreeting(name.value)
        }
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
