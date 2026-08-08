package com.sarim.husk.marker.presentation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.ar.PaperSize
import com.sarim.husk.ui.theme.spacing

/** Connects [MarkerLibraryViewModel] to the marker library UI. */
@Composable
fun MarkerLibraryScreen(
    viewModel: MarkerLibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MarkerLibraryContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Renders the stateless, previewable marker library. */
@Composable
fun MarkerLibraryContent(
    state: MarkerLibraryScreenState,
    onEvent: (MarkerLibraryScreenToViewModelEvents) -> Unit,
    modifier: Modifier = Modifier,
) {
    MarkerLibraryTemplate(modifier = modifier) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            item { PaperChooser(state.paper, onEvent) }
            items(state.markers, key = { it.id }) { row -> MarkerCard(row, onEvent) }
        }
    }
}

@Composable
private fun PaperChooser(
    chosen: PaperSize,
    onEvent: (MarkerLibraryScreenToViewModelEvents) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        PaperSize.entries.forEach { paper ->
            FilterChip(
                selected = paper == chosen,
                onClick = { onEvent(MarkerLibraryScreenToViewModelEvents.PaperChosen(paper)) },
                label = { Text(paper.name) },
            )
        }
    }
}

/**
 * One marker and how to print it.
 *
 * The instructions are stated in full because the printed width scales every measurement taken
 * against this marker, and the reference bar is what turns a rescaled print from an invisible error
 * into one a ruler finds.
 */
@Composable
private fun MarkerCard(
    row: MarkerRow,
    onEvent: (MarkerLibraryScreenToViewModelEvents) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.screenPadding),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(text = row.name, style = MaterialTheme.typography.titleMedium)

            if (row.fitsOnPage) {
                PrintInstructions(row)
                MeasurementCorrection(row, onEvent)
            } else {
                Text(
                    text = stringResource(R.string.marker_too_big),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!row.isBundled) {
                TextButton(
                    onClick = { onEvent(MarkerLibraryScreenToViewModelEvents.DeleteMarker(row.id)) },
                ) {
                    Text(stringResource(R.string.marker_delete))
                }
            }
        }
    }
}

/** Exactly how to print this marker, worked out for the chosen sheet. */
@Composable
private fun PrintInstructions(row: MarkerRow) {
    Text(
        text =
            stringResource(
                R.string.marker_print_instructions,
                row.printedWidthMillimetres,
                row.printedHeightMillimetres,
                row.horizontalPaddingMillimetres,
                row.verticalPaddingMillimetres,
            ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.marker_print_scaling_warning),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Checking what the printer actually did.
 *
 * The instructions above are only as good as the print dialog obeyed them. Measuring the bar is what
 * turns a silently rescaled sheet into a number that can be corrected here.
 */
@Composable
private fun MeasurementCorrection(
    row: MarkerRow,
    onEvent: (MarkerLibraryScreenToViewModelEvents) -> Unit,
) {
    var measured by remember(row.id) { mutableStateOf("") }

    Text(
        text = stringResource(R.string.marker_reference_bar, row.referenceBarMillimetres),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = measured,
        onValueChange = { measured = it },
        label = { Text(stringResource(R.string.marker_measured_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = {
            // Anything unreadable is ignored rather than guessed at. A misread number here would
            // rescale every measurement ever taken against this marker.
            measured.toDoubleOrNull()?.let {
                onEvent(MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured(row.id, it))
            }
            measured = ""
        },
    ) {
        Text(stringResource(R.string.marker_apply_measurement))
    }
}

/** Provides the edge-aware scaffold shared by the marker library. */
@Composable
fun MarkerLibraryTemplate(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(content = content, contentWindowInsets = WindowInsets.safeDrawing)
    }
}
