package com.sarim.husk.utils.state

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

/** Stores immutable screen state in a [SavedStateHandle] across process death. */
class SavedStateStore<State : Parcelable>(
    private val savedStateHandle: SavedStateHandle,
    private val key: String,
    initialState: State,
) {
    /** Current state restored from the handle when available. */
    val state: StateFlow<State> =
        savedStateHandle.getStateFlow(
            key = key,
            initialValue = initialState,
        )

    /** Applies [reducer] to the current state and persists a changed result. */
    fun update(reducer: (State) -> State) {
        val oldState = state.value
        val newState = reducer(oldState)

        if (oldState != newState) {
            savedStateHandle[key] = newState
        }
    }

    /** Removes the state owned by this store from the saved-state handle. */
    fun clear() {
        savedStateHandle.remove<State>(key)
    }
}
