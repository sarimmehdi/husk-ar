package com.sarim.husk.geometry

/**
 * A rigid transform: a rotation followed by a translation.
 *
 * A pose is meaningless without knowing which frame it is expressed in, so the frame belongs in the
 * name of whatever holds it — `cameraInWorld`, `cameraInAnchor`. Husk stores every persisted pose
 * relative to the marker anchor, in metres, which is what lets a session mean anything on a later
 * day.
 */
data class Pose(
    /** Position in the frame this pose is expressed in, in metres. */
    val translation: Vector3,
    /** Orientation relative to that same frame. */
    val rotation: Quaternion,
) {
    /**
     * Maps [point] out of this pose's frame and into the frame the pose is expressed in.
     *
     * Rotation is applied before translation. Doing it the other way round rotates the offset too
     * and puts the result somewhere else entirely.
     */
    fun transform(point: Vector3): Vector3 = rotation.rotate(point) + translation

    /** The transform that undoes this one. */
    fun inverse(): Pose {
        val inverseRotation = rotation.inverse()
        return Pose(
            translation = -inverseRotation.rotate(translation),
            rotation = inverseRotation,
        )
    }

    /**
     * The pose that applies [other] first and this one second.
     *
     * Reads left to right as frames compose: `anchorInWorld * cameraInAnchor` is `cameraInWorld`.
     */
    operator fun times(other: Pose): Pose =
        Pose(
            translation = transform(other.translation),
            rotation = rotation * other.rotation,
        )

    /** Ready-made poses. */
    companion object {
        /** The pose that changes nothing. */
        val IDENTITY = Pose(Vector3.ZERO, Quaternion.IDENTITY)
    }
}
