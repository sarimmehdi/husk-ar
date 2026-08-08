package com.sarim.husk.ar

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3

/**
 * Everything about the frame a trace was drawn on.
 *
 * A trace and the frame it was drawn on are one fact. Paired with a different frame's pose or lens
 * it describes an object that was never there, and nothing downstream can tell — which is why they
 * travel together from the moment of capture rather than being looked up separately later.
 */
data class CameraSnapshot(
    /** Where the camera was, in the marker's frame. */
    val cameraInMarker: Pose,
    /** The lens, in camera image pixels. */
    val intrinsics: CameraIntrinsics,
    /** Width of the camera image the intrinsics describe. */
    val imageWidth: Int,
    /** Height of the camera image the intrinsics describe. */
    val imageHeight: Int,
) {
    /** How this frame's image was laid onto a preview of the given size. */
    fun previewMapping(
        previewWidth: Int,
        previewHeight: Int,
    ): PreviewMapping =
        PreviewMapping(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
        )
}

/**
 * The camera's pose relative to the marker.
 *
 * ARCore reports both against a session origin that lands somewhere different every run. Husk stores
 * everything against the marker instead, which is the whole reason a session recorded today still
 * means something tomorrow, so the origin has to be divided out on the way in.
 *
 * Composed rather than subtracted: the offset is expressed in the marker's own axes, and subtracting
 * positions alone would be correct only while the marker happened to face the same way as the world.
 */
fun cameraInMarkerFrame(
    cameraInWorld: Pose,
    markerInWorld: Pose,
): Pose = markerInWorld.inverse() * cameraInWorld

/**
 * An ARCore pose as a geometry one.
 *
 * [rotationQuaternion] is ordered x, y, z, w, which is how ARCore hands it over. Reading it w-first
 * would still give a unit quaternion, describing an entirely different turn.
 */
fun poseFrom(
    translation: FloatArray,
    rotationQuaternion: FloatArray,
): Pose =
    Pose(
        translation =
            Vector3(
                translation[X].toDouble(),
                translation[Y].toDouble(),
                translation[Z].toDouble(),
            ),
        rotation =
            Quaternion(
                x = rotationQuaternion[X].toDouble(),
                y = rotationQuaternion[Y].toDouble(),
                z = rotationQuaternion[Z].toDouble(),
                w = rotationQuaternion[W].toDouble(),
            ),
    )

// ARCore's array order. Named so the quaternion's w cannot be read as its x by a slip of the index.
private const val X = 0
private const val Y = 1
private const val Z = 2
private const val W = 3
