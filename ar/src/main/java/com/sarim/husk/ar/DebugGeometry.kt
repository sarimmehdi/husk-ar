package com.sarim.husk.ar

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Vector3

/**
 * The direction a given image pixel looks along, in the camera's own frame.
 *
 * The inverse of projection. Undoing the principal point and focal length gives a direction on the
 * plane one unit ahead; the y is negated because image rows count downward while the camera's y
 * counts up, and z is negative because a camera looks down its own negative z.
 *
 * This is what makes a debug ray meaningful: drawn without the flips it points somewhere the camera
 * was never looking, which is worse than drawing nothing.
 */
fun rayDirection(
    intrinsics: CameraIntrinsics,
    imageX: Double,
    imageY: Double,
): Vector3 =
    Vector3(
        x = (imageX - intrinsics.principalPointX) / intrinsics.focalLengthX,
        y = -(imageY - intrinsics.principalPointY) / intrinsics.focalLengthY,
        z = -1.0,
    ).normalized()

/**
 * The eight corners of what a camera can see between two distances, in its own frame.
 *
 * Near corners first, then far, each starting at the image's top-left and going clockwise. Drawn as
 * a wireframe this is the volume every capture was taken through, which is the quickest way to see
 * whether views actually surrounded an object or merely lined up behind each other.
 */
fun frustumCorners(
    intrinsics: CameraIntrinsics,
    imageWidth: Int,
    imageHeight: Int,
    nearMetres: Double,
    farMetres: Double,
): List<Vector3> {
    val pixels =
        listOf(
            0.0 to 0.0,
            imageWidth.toDouble() to 0.0,
            imageWidth.toDouble() to imageHeight.toDouble(),
            0.0 to imageHeight.toDouble(),
        )
    return listOf(nearMetres, farMetres).flatMap { distance ->
        pixels.map { (x, y) ->
            // Scaled so the corner lands exactly on the plane at that distance rather than at that
            // distance along the ray, which would bow the near plane towards the camera at the
            // corners and draw a frustum nothing could have seen through.
            val direction = rayDirection(intrinsics, x, y)
            direction * (distance / -direction.z)
        }
    }
}

/** The ends of three unit axis arrows at a pose, expressed in the frame that pose is given in. */
fun axisEnds(
    pose: Pose,
    lengthMetres: Double,
): List<Vector3> =
    listOf(
        Vector3(lengthMetres, 0.0, 0.0),
        Vector3(0.0, lengthMetres, 0.0),
        Vector3(0.0, 0.0, lengthMetres),
    ).map(pose::transform)
