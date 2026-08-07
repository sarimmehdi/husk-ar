package com.sarim.husk.utils.errorui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText

/**
 * Wraps screen [content] with shared loading, blocking error, and banner handling.
 *
 * @param state Loading and error state adapted from the screen's own state.
 * @param onRetry Invoked when the user retries a failed operation.
 * @param content Screen content displayed when no blocking error exists.
 */
@Composable
fun LoadableScreenContainer(
    state: LoadableScreenUiState,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (state.loadError != null) {
        BlockingErrorComponent(state.loadError, onRetry)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val visibleMessage =
            state.refreshError?.let { message ->
                PersistentMessage(
                    message = message,
                    severity = MessageSeverity.ERROR,
                    actionLabel = UiText.StringResource(R.string.retry),
                )
            } ?: state.persistentMessage
        if (visibleMessage != null) {
            PersistentMessageBanner(
                state = visibleMessage,
                onAction = onRetry.takeIf { state.refreshError != null },
            )
        }

        Box(modifier = Modifier.weight(1F)) {
            content()
            if (state.isLoading) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun BlockingErrorComponent(
    message: UiText,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(R.string.error_icon_content_description),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.error_unavailable_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = message.asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Composable
private fun LoadingOverlay() {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = LOADING_SCRIM_ALPHA))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private const val LOADING_SCRIM_ALPHA = 0.32F
