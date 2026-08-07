package com.sarim.husk.utils.snackbar

import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Process-wide channel for transient user-facing messages. */
object SnackbarController {
    private val _events = Channel<SnackbarEvent>(capacity = Channel.BUFFERED)

    /** Messages awaiting display by the application snackbar host. */
    val events = _events.receiveAsFlow()

    /** Queues [event] for display. */
    suspend fun sendEvent(event: SnackbarEvent) {
        _events.send(event)
    }
}

/** Shows an error [message] with an optional retry action. */
fun CoroutineScope.showErrorSnackbar(
    message: UiText,
    retry: (suspend () -> Unit)? = null,
) {
    launch {
        SnackbarController.sendEvent(
            SnackbarEvent(
                message = message,
                action =
                    retry?.let {
                        SnackbarAction(
                            name = UiText.StringResource(R.string.retry),
                            action = it,
                        )
                    },
            ),
        )
    }
}

/** One queued snackbar message. */
data class SnackbarEvent(
    /** Text displayed by the snackbar. */
    val message: UiText,
    /** Optional snackbar action. */
    val action: SnackbarAction? = null,
)

/** An action button attached to a snackbar. */
data class SnackbarAction(
    /** Label displayed on the action button. */
    val name: UiText,
    /** Work performed when the action is selected. */
    val action: suspend () -> Unit,
)
