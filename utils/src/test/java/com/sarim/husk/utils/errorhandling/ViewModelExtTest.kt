package com.sarim.husk.utils.errorhandling

import androidx.lifecycle.ViewModel
import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class ViewModelExtTest {
    private val testDispatcher = StandardTestDispatcher()
    private val viewModel = object : ViewModel() {}

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful block triggers loading and finally closes loading`() =
        runTest {
            val loadingStates = mutableListOf<Boolean>()
            var blockExecuted = false

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onLoading = { loadingStates.add(it) },
                onEvent = {},
            ) {
                blockExecuted = true
            }

            advanceUntilIdle()

            assertTrue(blockExecuted)
            assertEquals(listOf(true, false), loadingStates)
        }

    @Test
    fun `default dispatcher runs task on main dispatcher`() =
        runTest {
            var blockExecuted = false

            viewModel.handleTask(onEvent = {}) {
                blockExecuted = true
            }

            advanceUntilIdle()

            assertTrue(blockExecuted)
        }

    @Test
    fun `IOException triggers connection error event`() =
        runTest {
            var capturedErrorText: UiText? = null

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onEvent = { capturedErrorText = it },
            ) {
                throw IOException("No internet")
            }

            advanceUntilIdle()

            assertStringResource(
                actual = capturedErrorText,
                expectedResId = R.string.error_connection,
            )
        }

    @Test
    fun `InvalidState AppException triggers invalid state error event`() =
        runTest {
            var capturedErrorText: UiText? = null

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onEvent = { capturedErrorText = it },
            ) {
                throw AppException.InvalidState("Wrong screen")
            }

            advanceUntilIdle()

            assertStringResource(
                actual = capturedErrorText,
                expectedResId = R.string.error_invalid_state,
            )
        }

    @Test
    fun `Unknown AppException triggers unknown error event`() =
        runTest {
            var capturedErrorText: UiText? = null

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onEvent = { capturedErrorText = it },
            ) {
                throw AppException.Unknown("Something went wrong")
            }

            advanceUntilIdle()

            assertStringResource(
                actual = capturedErrorText,
                expectedResId = R.string.error_unknown,
            )
        }

    @Test
    fun `onError callback is triggered when IOException occurs`() =
        runTest {
            var capturedThrowable: Throwable? = null
            val throwable = IOException("Failure")

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onError = { capturedThrowable = it },
                onEvent = {},
            ) {
                throw throwable
            }

            advanceUntilIdle()

            assertSame(throwable, capturedThrowable)
        }

    @Test
    fun `onError callback is triggered when AppException occurs`() =
        runTest {
            var capturedThrowable: Throwable? = null
            val throwable = AppException.Unknown("Failure")

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onError = { capturedThrowable = it },
                onEvent = {},
            ) {
                throw throwable
            }

            advanceUntilIdle()

            assertSame(throwable, capturedThrowable)
        }

    @Test
    fun `loading closes after handled error`() =
        runTest {
            val loadingStates = mutableListOf<Boolean>()

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onLoading = { loadingStates.add(it) },
                onEvent = {},
            ) {
                throw AppException.Unknown("Failure")
            }

            advanceUntilIdle()

            assertEquals(listOf(true, false), loadingStates)
        }

    @Test
    fun `cancellation propagates while still closing loading`() =
        runTest {
            val loadingStates = mutableListOf<Boolean>()
            var eventCount = 0

            viewModel.handleTask(
                dispatcher = testDispatcher,
                onLoading = loadingStates::add,
                onEvent = { eventCount++ },
            ) {
                throw CancellationException("cancelled")
            }

            advanceUntilIdle()

            assertEquals(listOf(true, false), loadingStates)
            assertEquals(0, eventCount)
        }

    private fun assertStringResource(
        actual: UiText?,
        expectedResId: Int,
    ) {
        assertTrue(actual is UiText.StringResource)
        assertEquals(
            expectedResId,
            (actual as UiText.StringResource).resId,
        )
    }
}
