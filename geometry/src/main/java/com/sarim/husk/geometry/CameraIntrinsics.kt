package com.sarim.husk.geometry

/**
 * A pinhole camera's internal parameters, in pixels.
 *
 * These come from ARCore's camera image intrinsics and describe how a point in front of the lens
 * lands on the sensor. Skew is not modelled: no phone camera has any worth the extra term.
 *
 * Pixels here are pixels of the **image the ellipse was traced on**. Tracing against a preview that
 * has been letterboxed or downscaled and then solving with the full sensor's intrinsics puts every
 * ellipse in the wrong place, so whoever captures a trace owns keeping the two in step.
 */
data class CameraIntrinsics(
    /** Focal length along the image x axis, in pixels. */
    val focalLengthX: Double,
    /** Focal length along the image y axis, in pixels. */
    val focalLengthY: Double,
    /** Principal point x, in pixels from the left edge. */
    val principalPointX: Double,
    /** Principal point y, in pixels from the top edge. */
    val principalPointY: Double,
) {
    /**
     * The 3x4 projection matrix `P = K [R | t]` that the solver consumes, row-major.
     *
     * [cameraInAnchor] is the camera's pose in the marker anchor's frame, as ARCore reports it —
     * y up, looking down its own negative z. The solver works the other way round, y down and
     * looking down positive z, so the pose is converted before being inverted into the world-to-
     * camera transform that `[R | t]` actually holds.
     *
     * Getting either step backwards still produces a plausible matrix. It just describes a camera
     * that is not the one that took the picture, and the ellipsoid comes back mirrored or behind
     * the viewer, which is why the conversion lives here rather than at each call site.
     */
    @Suppress("MagicNumber")
    fun projectionFrom(cameraInAnchor: Pose): Array<DoubleArray> {
        val worldToCamera = CameraConvention.glToCv(cameraInAnchor).inverse()

        // Columns of the rotation are where the basis vectors land, so rotating each in turn builds
        // the matrix without needing a separate quaternion-to-matrix conversion.
        val rotation = worldToCamera.rotation
        val axes =
            listOf(
                rotation.rotate(Vector3(1.0, 0.0, 0.0)),
                rotation.rotate(Vector3(0.0, 1.0, 0.0)),
                rotation.rotate(Vector3(0.0, 0.0, 1.0)),
            )
        val translation = worldToCamera.translation

        val extrinsic =
            arrayOf(
                doubleArrayOf(axes[0].x, axes[1].x, axes[2].x, translation.x),
                doubleArrayOf(axes[0].y, axes[1].y, axes[2].y, translation.y),
                doubleArrayOf(axes[0].z, axes[1].z, axes[2].z, translation.z),
            )

        // K [R | t], written out rather than multiplied, because K has only four free values and the
        // bottom row is the identity.
        return arrayOf(
            DoubleArray(4) { focalLengthX * extrinsic[0][it] + principalPointX * extrinsic[2][it] },
            DoubleArray(4) { focalLengthY * extrinsic[1][it] + principalPointY * extrinsic[2][it] },
            extrinsic[2].copyOf(),
        )
    }
}
