package com.sarim.husk.session.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.ar.HuskArScene
import com.sarim.husk.ar.MarkerImage
import com.sarim.husk.ar.SceneContent
import com.sarim.husk.ar.SceneObserver
import com.sarim.husk.ar.ShellPalette
import com.sarim.husk.ar.TraceOverlay
import com.sarim.husk.ui.theme.spacing

/**
 * Outlining an object through the camera.
 *
 * The overlay sits over the AR feed and reports outlines in preview pixels; the view model pairs
 * each with the frame it was drawn on. Preview size is measured here rather than assumed, because it
 * is what turns those pixels into camera image coordinates.
 */
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    marker: MarkerImage,
    palette: ShellPalette,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Measured rather than assumed: preview pixels only become camera image coordinates once the
    // size of the surface the outline was drawn on is known.
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { previewSize = it },
    ) {
        HuskArScene(
            marker = marker,
            content = SceneContent(ellipsoids = emptyList(), palette = palette),
            onSelect = {},
            observer =
                SceneObserver(
                    onFrame = {
                        viewModel.onEvent(CaptureScreenToViewModelEvents.FrameAvailable(it))
                    },
                ),
        )

        TraceOverlay(
            onTraceChanged = { viewModel.onEvent(CaptureScreenToViewModelEvents.TraceStarted) },
            onTraceCommitted = { trace ->
                viewModel.onEvent(
                    CaptureScreenToViewModelEvents.TraceCommitted(
                        trace = trace,
                        previewWidth = previewSize.width,
                        previewHeight = previewSize.height,
                    ),
                )
            },
            strokeColour = palette.selected,
            contentDescription = stringResource(R.string.capture_overlay_description),
        )

        CaptureStatus(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        )
    }
}

/** The instruction and progress shown over the camera. */
@Composable
private fun CaptureStatus(
    state: CaptureScreenState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.screenPadding)
                .clip(RoundedCornerShape(CORNER_RADIUS_DP.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA))
                .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(text = state.label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(state.message.textId()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = pluralStringResource(R.plurals.session_view_count, state.viewCount, state.viewCount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The sentence for each instruction, kept beside the enum so a new one cannot go unworded. */
private fun CaptureMessage.textId(): Int =
    when (this) {
        CaptureMessage.FIND_THE_MARKER -> R.string.capture_find_marker
        CaptureMessage.OUTLINE_THE_OBJECT -> R.string.capture_outline
        CaptureMessage.HOLD_STILL -> R.string.capture_hold_still
        CaptureMessage.NEED_MORE_VIEWS -> R.string.capture_need_more_views
        CaptureMessage.MOVE_AROUND_THE_OBJECT -> R.string.capture_move_around
        CaptureMessage.OUTLINE_UNUSABLE -> R.string.capture_outline_unusable
        CaptureMessage.MEASURED -> R.string.capture_measured
    }

private const val CORNER_RADIUS_DP = 16
private const val SCRIM_ALPHA = 0.85f
