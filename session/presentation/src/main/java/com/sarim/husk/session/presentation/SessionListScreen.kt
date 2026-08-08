package com.sarim.husk.session.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.ui.theme.spacing

/** Connects [SessionListViewModel] state and actions to the session list UI. */
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (String) -> Unit,
    onOpenMarkers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSession = onOpenSession,
        onOpenMarkers = onOpenMarkers,
        modifier = modifier,
    )
}

/** Renders the stateless, previewable session list. */
@Composable
fun SessionListContent(
    state: SessionListScreenState,
    onEvent: (SessionListScreenToViewModelEvents) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenMarkers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SessionListScreenTemplate(
        modifier = modifier,
        onCreate = { onEvent(SessionListScreenToViewModelEvents.Create("")) },
        onOpenMarkers = onOpenMarkers,
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingIndicator(contentPadding)
            state.sessions.isEmpty() -> EmptyMessage(contentPadding)
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    items(state.sessions, key = { it.id }) { row ->
                        SessionCard(row = row, onOpen = { onOpenSession(row.id) })
                    }
                }
        }
    }
}

@Composable
private fun SessionCard(
    row: SessionRow,
    onOpen: () -> Unit,
) {
    val summary =
        pluralStringResource(R.plurals.session_object_count, row.objectCount, row.objectCount)
    Card(
        onClick = onOpen,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.screenPadding),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            // Card merges its descendants, so the title and count are already announced together
            // as one stop. Overriding semantics here would only strip the text a reader can find.
            Text(text = row.name, style = MaterialTheme.typography.titleMedium)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LoadingIndicator(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyMessage(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.session_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(MaterialTheme.spacing.screenPadding),
        )
    }
}

/** Provides the edge-aware scaffold shared by the session list. */
@Composable
fun SessionListScreenTemplate(
    onCreate: () -> Unit,
    onOpenMarkers: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            content = content,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.screenPadding),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onOpenMarkers) {
                        Text(stringResource(R.string.session_list_markers_action))
                    }
                }
            },
            floatingActionButton = {
                // The plain overload rather than the text/icon slots: with no icon to show, the
                // slotted form leaves its label outside the merged semantics node, so the button
                // has no accessible name.
                ExtendedFloatingActionButton(onClick = onCreate) {
                    Text(stringResource(R.string.session_list_create_action))
                }
            },
        )
    }
}
