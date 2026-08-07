package com.sarim.husk.geometry

/**
 * Converts camera poses between the two axis conventions Husk has to live with.
 *
 * ARCore reports poses in the OpenGL convention: the camera looks down **-Z**, **+Y is up**, +X is
 * right. The dual-quadric solver, like OpenCV, expects the camera to look down **+Z** with **+Y
 * down** and +X right, because its image rows run downward.
 *
 * The two differ by a 180° turn about X, which flips Y and Z and leaves X alone. That rotation is
 * its own inverse, so a single [glToCv] serves both directions — converting twice returns the
 * original. There is deliberately no `cvToGl`: a second name for the same operation invites callers
 * to believe they are undoing something when they are not.
 */
object CameraConvention {
    /**
     * The half turn about X that separates the two conventions.
     *
     * Expressed as a quaternion rather than applied component-wise so it composes with pose
     * rotations under the same multiplication rules as everything else.
     */
    private val HALF_TURN_ABOUT_X = Quaternion(1.0, 0.0, 0.0, 0.0)

    /**
     * Reinterprets a **camera-local** [direction] in the other convention.
     *
     * Only for vectors expressed in the camera's own axes, such as its forward or up direction. A
     * direction expressed in the anchor or world frame is unaffected by this conversion and must
     * not be passed here.
     */
    fun glToCv(direction: Vector3): Vector3 = Vector3(direction.x, -direction.y, -direction.z)

    /**
     * Reinterprets [pose] in the other convention.
     *
     * The translation is left alone. It states where the camera sits in the frame the pose is
     * expressed in, and reinterpreting the camera's own axes does not move the camera. Only the
     * rotation changes, gaining the half turn on the right so that the camera's axes are reoriented
     * while the frame it is expressed in is untouched.
     */
    fun glToCv(pose: Pose): Pose =
        Pose(
            translation = pose.translation,
            rotation = pose.rotation * HALF_TURN_ABOUT_X,
        )
}
