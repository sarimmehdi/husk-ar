package com.sarim.husk.utils.state

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SavedStateStoreTest {
    @Test
    fun `store exposes initial state when saved state is absent`() {
        val savedStateHandle = SavedStateHandle()

        val store = createStore(savedStateHandle, TestState(count = 1))

        assertEquals(TestState(count = 1), store.state.value)
        assertTrue(savedStateHandle.contains(KEY))
    }

    @Test
    fun `store restores saved state instead of initial state`() {
        val restored = TestState(count = 4)
        val savedStateHandle = SavedStateHandle(mapOf(KEY to restored))

        val store = createStore(savedStateHandle, TestState(count = 1))

        assertEquals(restored, store.state.value)
    }

    @Test
    fun `update stores changed state and ignores equal state`() {
        val savedStateHandle = SavedStateHandle()
        val store = createStore(savedStateHandle, TestState(count = 1))

        store.update { current -> current.copy(count = current.count + 1) }
        assertEquals(TestState(count = 2), store.state.value)
        assertEquals(TestState(count = 2), savedStateHandle.get<TestState>(KEY))

        store.update { current -> current }
        assertEquals(TestState(count = 2), store.state.value)
    }

    @Test
    fun `clear removes state from saved state handle`() {
        val savedStateHandle = SavedStateHandle()
        val store = createStore(savedStateHandle, TestState(count = 1))

        store.clear()

        assertFalse(savedStateHandle.contains(KEY))
    }

    private fun createStore(
        savedStateHandle: SavedStateHandle,
        initialState: TestState,
    ) = SavedStateStore(
        savedStateHandle = savedStateHandle,
        key = KEY,
        initialState = initialState,
    )

    private companion object {
        const val KEY = "test_state"
    }
}

@Parcelize
private data class TestState(
    val count: Int,
) : Parcelable
