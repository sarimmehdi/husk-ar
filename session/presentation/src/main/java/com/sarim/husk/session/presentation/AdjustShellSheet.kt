package com.sarim.husk.session.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.ShellAdjustment
import com.sarim.husk.ui.theme.spacing

/**
 * Correcting a shell by hand.
 *
 * Size is per axis rather than one uniform slider, because a solve is commonly right in two
 * directions and wrong in the third — which is the case a single control cannot fix.
 *
 * Everything is relative: the sliders start neutral and describe a change, not an absolute size. A
 * sheet showing absolute millimetres would have to be rebuilt every time the shell was re-solved
 * underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustShellSheet(
    row: MeasuredObjectRow,
    onApply: (ShellAdjustment) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(1f) }
    var depth by remember { mutableFloatStateOf(1f) }
    var alongX by remember { mutableFloatStateOf(0f) }
    var alongY by remember { mutableFloatStateOf(0f) }
    var alongZ by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.session_adjust_title, row.label),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(stringResource(R.string.session_adjust_size), style = MaterialTheme.typography.labelLarge)
            ScaleSlider(R.string.session_adjust_axis_width, width) { width = it }
            ScaleSlider(R.string.session_adjust_axis_height, height) { height = it }
            ScaleSlider(R.string.session_adjust_axis_depth, depth) { depth = it }

            Text(stringResource(R.string.session_adjust_position), style = MaterialTheme.typography.labelLarge)
            OffsetSlider(R.string.session_adjust_axis_width, alongX) { alongX = it }
            OffsetSlider(R.string.session_adjust_axis_height, alongY) { alongY = it }
            OffsetSlider(R.string.session_adjust_axis_depth, alongZ) { alongZ = it }

            Button(
                onClick = {
                    onApply(
                        ShellAdjustment(
                            extentScale = Vector3(width.toDouble(), height.toDouble(), depth.toDouble()),
                            centreOffsetMetres =
                                Vector3(alongX.toDouble(), alongY.toDouble(), alongZ.toDouble()),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.session_adjust_apply))
            }
        }
    }
}

/** A multiplier from half to double, starting neutral. */
@Composable
private fun ScaleSlider(
    labelId: Int,
    value: Float,
    onChange: (Float) -> Unit,
) {
    val label = stringResource(labelId)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = MINIMUM_SCALE..MAXIMUM_SCALE,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** A shift of up to five centimetres either way, starting at none. */
@Composable
private fun OffsetSlider(
    labelId: Int,
    value: Float,
    onChange: (Float) -> Unit,
) {
    val label = stringResource(labelId)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = -MAXIMUM_OFFSET_METRES..MAXIMUM_OFFSET_METRES,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Halving is as far as a correction should go before the capture is simply wrong. */
private const val MINIMUM_SCALE = 0.5f

/** Doubling, likewise. */
private const val MAXIMUM_SCALE = 2.0f

/** Five centimetres. Further than this and the shell belongs to a different object. */
private const val MAXIMUM_OFFSET_METRES = 0.05f
