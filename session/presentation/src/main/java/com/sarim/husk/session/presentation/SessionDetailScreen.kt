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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.session.domain.model.MeasurementConfidence
import com.sarim.husk.ui.theme.spacing

/** Connects [SessionDetailViewModel] state and actions to the session UI. */
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onSessionMissing: () -> Unit,
    onMeasureObject: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A session can be deleted from another screen while this one is open. Leaving rather than
    // showing an empty shell, because there is nothing here to act on any more.
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onSessionMissing()
    }

    SessionDetailContent(
        state = state,
        onEvent = viewModel::onEvent,
        onMeasureObject = onMeasureObject,
        modifier = modifier,
    )
}

/** Renders the stateless, previewable session contents. */
@Composable
fun SessionDetailContent(
    state: SessionDetailScreenState,
    onEvent: (SessionDetailScreenToViewModelEvents) -> Unit,
    onMeasureObject: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held here rather than in the view model: which sheet is open is a property of this screen,
    // and routing it through state would survive rotation only to reopen over a deleted row.
    var adjusting by remember { mutableStateOf<MeasuredObjectRow?>(null) }

    adjusting?.let { row ->
        AdjustShellSheet(
            row = row,
            onApply = { adjustment ->
                onEvent(SessionDetailScreenToViewModelEvents.AdjustObject(row.id, adjustment))
                adjusting = null
            },
            onDismiss = { adjusting = null },
        )
    }

    SessionDetailScreenTemplate(
        modifier = modifier,
        onStartObject = { onEvent(SessionDetailScreenToViewModelEvents.StartObject("")) },
    ) { contentPadding ->
        when {
            state.isLoading -> CentredBox(contentPadding) { CircularProgressIndicator() }
            state.objects.isEmpty() ->
                CentredBox(contentPadding) {
                    Text(
                        text = stringResource(R.string.session_detail_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(MaterialTheme.spacing.screenPadding),
                    )
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    items(state.objects, key = { it.id }) { row ->
                        ObjectCard(
                            row = row,
                            onMeasure = { onMeasureObject(row.id, row.label) },
                            onAdjust = { adjusting = row },
                            onDelete = {
                                onEvent(SessionDetailScreenToViewModelEvents.DeleteObject(row.id))
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun ObjectCard(
    row: MeasuredObjectRow,
    onMeasure: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onMeasure,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.screenPadding),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(text = row.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = measurementText(row),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.session_view_count, row.viewCount, row.viewCount),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                TextButton(onClick = onAdjust) {
                    Text(stringResource(R.string.session_object_adjust))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.session_object_delete))
                }
            }
        }
    }
}

/**
 * The size line for a row.
 *
 * A rough measurement is shown with its caveat attached rather than as a bare number. Quoting
 * millimetres from views taken a tenth of a radian apart would be stating a precision the method
 * cannot deliver, and nothing on screen would say otherwise.
 */
@Composable
private fun measurementText(row: MeasuredObjectRow): String =
    when (row.confidence) {
        MeasurementConfidence.UNMEASURED -> stringResource(R.string.session_object_unmeasured)
        MeasurementConfidence.ROUGH ->
            stringResource(
                R.string.session_object_extent_rough,
                row.extentMillimetres[0],
                row.extentMillimetres[1],
                row.extentMillimetres[2],
            )
        MeasurementConfidence.ADJUSTED ->
            stringResource(
                R.string.session_object_extent_adjusted,
                row.extentMillimetres[0],
                row.extentMillimetres[1],
                row.extentMillimetres[2],
            )
        MeasurementConfidence.GOOD ->
            stringResource(
                R.string.session_object_extent,
                row.extentMillimetres[0],
                row.extentMillimetres[1],
                row.extentMillimetres[2],
            )
    }

@Composable
private fun CentredBox(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Provides the edge-aware scaffold shared by the session screen. */
@Composable
fun SessionDetailScreenTemplate(
    onStartObject: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            content = content,
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = onStartObject) {
                    Text(stringResource(R.string.session_detail_start_object))
                }
            },
        )
    }
}
