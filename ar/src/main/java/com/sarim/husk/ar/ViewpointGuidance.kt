package com.sarim.husk.ar

import com.sarim.husk.geometry.Pose
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Which way to move to get back to a recorded viewpoint. */
enum class Nudge {
    /** Already there. */
    NONE,

    /** Towards the holder's left. */
    LEFT,

    /** Towards the holder's right. */
    RIGHT,

    /** Higher. */
    UP,

    /** Lower. */
    DOWN,

    /** Closer to whatever the camera is pointing at. */
    FORWARD,

    /** Away from it. */
    BACK,
}

/** How far the camera is from a recorded viewpoint, and what to do about it. */
data class ViewpointGuidance(
    /** How far the camera has to travel, in metres. */
    val distanceMetres: Double,
    /** How far it has to turn, in degrees. */
    val angleDegrees: Double,
    /** Whether it is close enough to count as back on the spot. */
    val isAligned: Boolean,
    /** The one instruction to give. */
    val move: Nudge,
)

/** Close enough to stand in. Demanding better would make replay unusable. */
const val ALIGNED_WITHIN_METRES = 0.10

/** Close enough to be pointing the same way. */
const val ALIGNED_WITHIN_DEGREES = 12.0

/**
 * What to tell someone trying to get back to [target] from [current].
 *
 * Both poses are the camera in the marker's frame, as everything stored is.
 *
 * Position alone is not enough. Standing on the right spot facing the wrong way puts the object out
 * of shot entirely, so turning counts towards alignment as much as walking does.
 *
 * The instruction is given in the camera's own axes rather than the marker's, because "left" has to
 * mean the holder's left; told to move along the marker's x they would first have to work out which
 * way that is while holding a phone up at something. Only the largest error is named, since three
 * simultaneous instructions are not something anyone can act on.
 */
fun guidanceTo(
    target: Pose,
    current: Pose,
): ViewpointGuidance {
    val offset = target.translation - current.translation
    val distance = sqrt(offset.x * offset.x + offset.y * offset.y + offset.z * offset.z)

    // Half the angle of the relative rotation is in its w. The absolute value handles a quaternion
    // and its negation being the same turn, which otherwise reports a half turn as no turn at all.
    val relative = current.rotation.inverse() * target.rotation
    val angle = Math.toDegrees(2.0 * acos(abs(relative.w).coerceAtMost(1.0)))

    val aligned = distance <= ALIGNED_WITHIN_METRES && angle <= ALIGNED_WITHIN_DEGREES
    return ViewpointGuidance(
        distanceMetres = distance,
        angleDegrees = angle,
        isAligned = aligned,
        move = if (aligned) Nudge.NONE else nudgeFor(current, offset),
    )
}

/** The largest component of the offset, seen from behind the camera. */
private fun nudgeFor(
    current: Pose,
    offsetInMarker: com.sarim.husk.geometry.Vector3,
): Nudge {
    val inCamera = current.rotation.inverse().rotate(offsetInMarker)
    val sideways = abs(inCamera.x)
    val vertical = abs(inCamera.y)
    val forwards = abs(inCamera.z)

    return when {
        sideways >= vertical && sideways >= forwards ->
            if (inCamera.x > 0.0) Nudge.RIGHT else Nudge.LEFT

        vertical >= forwards ->
            if (inCamera.y > 0.0) Nudge.UP else Nudge.DOWN

        // A camera looks down its own negative z, so a target ahead has a negative z here. Reading
        // this the other way round would send the holder backwards away from the shot.
        else -> if (inCamera.z < 0.0) Nudge.FORWARD else Nudge.BACK
    }
}
