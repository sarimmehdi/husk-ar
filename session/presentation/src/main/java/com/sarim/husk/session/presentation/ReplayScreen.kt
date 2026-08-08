package com.sarim.husk.session.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.ar.HuskArScene
import com.sarim.husk.ar.MarkerImage
import com.sarim.husk.ar.Nudge
import com.sarim.husk.ar.SceneContent
import com.sarim.husk.ar.SceneObserver
import com.sarim.husk.ar.ShellPalette
import com.sarim.husk.ui.theme.spacing

/**
 * Walking back through the views an object was measured from.
 *
 * Each step guides the camera to where a view was taken and draws the outline that was traced there.
 * Standing in the same place is how a measurement gets checked and how a bad view gets found.
 */
@Composable
fun ReplayScreen(
    viewModel: ReplayViewModel,
    marker: MarkerImage,
    palette: ShellPalette,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewModel.onEvent(ReplayScreenToViewModelEvents.PreviewResized(it.width, it.height))
                },
    ) {
        HuskArScene(
            marker = marker,
            content = SceneContent(ellipsoids = emptyList(), palette = palette),
            onSelect = {},
            observer =
                SceneObserver(
                    onFrame = { viewModel.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(it)) },
                ),
        )

        // Drawn only once the camera is close to where the view was taken. Shown from anywhere it
        // would sit over whatever happens to be in shot, which is worse than no hint at all.
        state.hint?.takeIf { state.isAligned }?.let { HintOutlineOverlay(it, palette.selected) }

        ReplayControls(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        )
    }
}

@Composable
private fun HintOutlineOverlay(
    hint: HintOutline,
    colour: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        rotateRad(hint.rotationRadians, pivot = Offset(hint.centreX, hint.centreY)) {
            drawOval(
                color = colour,
                topLeft = Offset(hint.centreX - hint.semiMajor, hint.centreY - hint.semiMinor),
                size = Size(hint.semiMajor * 2f, hint.semiMinor * 2f),
                style = Stroke(width = HINT_STROKE_PIXELS),
            )
        }
    }
}

@Composable
private fun ReplayControls(
    state: ReplayScreenState,
    onEvent: (ReplayScreenToViewModelEvents) -> Unit,
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
        Text(
            text = stringResource(R.string.replay_position, state.label, state.position, state.total),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(state.instructionId(), state.distanceCentimetres),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TextButton(onClick = { onEvent(ReplayScreenToViewModelEvents.Previous) }) {
                Text(stringResource(R.string.replay_previous))
            }
            TextButton(onClick = { onEvent(ReplayScreenToViewModelEvents.Next) }) {
                Text(stringResource(R.string.replay_next))
            }
        }
    }
}

/** The sentence for the current guidance, kept beside the states so none can go unworded. */
private fun ReplayScreenState.instructionId(): Int =
    when {
        !isMarkerTracked -> R.string.capture_find_marker
        isAligned -> R.string.replay_aligned
        else ->
            when (move) {
                Nudge.NONE -> R.string.replay_aligned
                Nudge.LEFT -> R.string.replay_move_left
                Nudge.RIGHT -> R.string.replay_move_right
                Nudge.UP -> R.string.replay_move_up
                Nudge.DOWN -> R.string.replay_move_down
                Nudge.FORWARD -> R.string.replay_move_forward
                Nudge.BACK -> R.string.replay_move_back
            }
    }

private const val CORNER_RADIUS_DP = 16
private const val SCRIM_ALPHA = 0.85f
private const val HINT_STROKE_PIXELS = 4f
