package com.sarim.husk.session.domain.model

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Pose
import java.time.Instant

/** Identifies a single capture. */
data class ObservationId(
    /** The identifier itself. */
    val value: String,
)

/**
 * One capture: where the camera was, what lens it had, and the outline traced on that frame.
 *
 * The three travel together and are meaningless apart. A pose without its intrinsics cannot say
 * where the outline projects from, and an outline traced on a frame the pose does not belong to
 * places the object somewhere it never was.
 */
data class Observation(
    /** Stable identity. */
    val id: ObservationId,
    /** Where the camera was, in the marker's frame, as ARCore reported it. */
    val cameraInAnchor: Pose,
    /** The lens, in the pixels the outline was traced in. */
    val intrinsics: CameraIntrinsics,
    /** The traced outline, in dual form. */
    val outline: DualConic,
    /** When the capture was taken. */
    val capturedAt: Instant,
)
