package com.sarim.husk.session.domain.model

import com.sarim.husk.geometry.Vector3

/**
 * A hand correction to a fitted shell.
 *
 * Scale is per axis rather than uniform, because a solve is often right in two directions and wrong
 * in the third — which is exactly the case a single slider cannot fix.
 */
data class ShellAdjustment(
    /** Multiplier applied to each semi-axis. */
    val extentScale: Vector3 = Vector3(1.0, 1.0, 1.0),
    /** Shift applied to the centre, in metres, in the marker's frame. */
    val centreOffsetMetres: Vector3 = Vector3.ZERO,
)
