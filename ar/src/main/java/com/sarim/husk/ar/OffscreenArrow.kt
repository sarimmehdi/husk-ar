package com.sarim.husk.ar

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Vector3
import kotlin.math.atan2

/** Where something out of shot is, relative to the middle of the screen. */
data class OffscreenArrow(
    /**
     * Which way to point the arrow, in screen terms: zero is right, and it turns clockwise.
     *
     * Screen y counts downward, so this is measured against a downward y rather than the upward y
     * of the camera's own axes.
     */
    val angleRadians: Float,
    /**
     * Whether the thing is behind the camera rather than merely off to one side.
     *
     * Worth saying out loud: an arrow pointing right when the object is behind you is technically
     * the shortest way round and still reads as a lie.
     */
    val isBehind: Boolean,
)

/**
 * Where to point for something not currently in shot, or null when it is visible.
 *
 * The direction comes from the point's position in the camera's own axes rather than from its
 * projection, and that is the whole difficulty. Projecting divides by depth, and for anything behind
 * the camera the depth is negative, which mirrors the result: an object behind and to your right
 * projects to the left of the screen and an arrow built from it sends you turning the wrong way.
 * Taking the direction directly cannot make that mistake, and the projection is used only to decide
 * whether the thing is on screen at all.
 */
fun offscreenArrowTo(
    pointInMarker: Vector3,
    cameraInMarker: Pose,
    intrinsics: CameraIntrinsics,
    imageWidth: Int,
    imageHeight: Int,
): OffscreenArrow? {
    val inCamera = cameraInMarker.inverse().transform(pointInMarker)

    // A camera looks down its own negative z, so anything in front has a negative z here.
    val depth = -inCamera.z
    val isBehind = depth <= 0.0

    if (!isBehind) {
        val x = intrinsics.focalLengthX * inCamera.x / depth + intrinsics.principalPointX
        val y = -intrinsics.focalLengthY * inCamera.y / depth + intrinsics.principalPointY
        if (x in 0.0..imageWidth.toDouble() && y in 0.0..imageHeight.toDouble()) return null
    }

    // Screen y counts downward, so the camera's upward y is negated on the way in.
    return OffscreenArrow(
        angleRadians = atan2(-inCamera.y, inCamera.x).toFloat(),
        isBehind = isBehind,
    )
}
