package com.sarim.husk.utils.errorhandling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.utils.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

/**
 * Runs ViewModel work with shared loading and error handling.
 *
 * @param dispatcher Dispatcher on which the work runs.
 * @param onLoading Receives the loading state around the operation.
 * @param onError Receives the original failure for caller-owned state.
 * @param onEvent Receives a user-facing message for the failure.
 * @param block Work to run; cancellation is rethrown.
 */
fun ViewModel.handleTask(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    onLoading: (Boolean) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    onEvent: (UiText) -> Unit,
    block: suspend () -> Unit,
) {
    viewModelScope.launch(dispatcher) {
        try {
            onLoading(true)
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            handleTaskError(
                throwable = e,
                onEvent = onEvent,
                onError = onError,
            )
        } catch (e: AppException) {
            handleTaskError(
                throwable = e,
                onEvent = onEvent,
                onError = onError,
            )
        } finally {
            onLoading(false)
        }
    }
}

private fun handleTaskError(
    throwable: Throwable,
    onEvent: (UiText) -> Unit,
    onError: (Throwable) -> Unit,
) {
    Timber.e(throwable, "Standardized ViewModel Task Failure")
    onEvent(throwable.toUiText())
    onError(throwable)
}
