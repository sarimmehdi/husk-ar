package com.sarim.husk.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * A rotation as three human-editable angles.
 *
 * These exist for the one place a person adjusts a rotation by hand. Everything else stores a
 * [Quaternion], because a triple of angles suffers gimbal lock and depends on an axis order that is
 * easy to state and easy to get wrong.
 *
 * The order here is **intrinsic Y-X-Z**: yaw about the up axis, then pitch about the resulting right
 * axis, then roll about the resulting forward axis. Up is +Y, matching ARCore, which is why yaw is
 * the Y term rather than the Z one that aerospace conventions use.
 *
 * Several triples describe the same rotation, so a value that has been through [from] will not
 * always match the one that produced it. Hold these as the source of truth while a person is editing
 * and convert once on commit; converting back and forth every frame makes the angles jump.
 */
data class EulerAngles(
    /** Rotation about the right axis, applied second. */
    val pitchRadians: Double,
    /** Rotation about the up axis, applied first. */
    val yawRadians: Double,
    /** Rotation about the forward axis, applied last. */
    val rollRadians: Double,
) {
    /** The rotation these angles describe. */
    fun toQuaternion(): Quaternion =
        Quaternion.fromAxisAngle(UP, yawRadians) *
            Quaternion.fromAxisAngle(RIGHT, pitchRadians) *
            Quaternion.fromAxisAngle(FORWARD, rollRadians)

    /** Axis conventions and recovery from a quaternion. */
    companion object {
        private val RIGHT = Vector3(1.0, 0.0, 0.0)
        private val UP = Vector3(0.0, 1.0, 0.0)
        private val FORWARD = Vector3(0.0, 0.0, 1.0)

        /**
         * How close the pitch cosine may come to zero before yaw and roll stop being separable.
         *
         * Below this the two axes have collapsed onto each other and their split is arbitrary, so
         * dividing them out amplifies noise instead of recovering information.
         */
        private const val GIMBAL_LOCK_EPSILON = 1e-9

        /**
         * Recovers angles from [rotation].
         *
         * At gimbal lock — pitch at ±90°, where the yaw and roll axes coincide — roll is reported as
         * zero and the whole turn is attributed to yaw. That is one of infinitely many valid answers
         * and it keeps the result finite.
         */
        fun from(rotation: Quaternion): EulerAngles {
            val unit = rotation.normalized()
            // Columns of the rotation matrix, obtained by turning the basis vectors.
            val right = unit.rotate(RIGHT)
            val up = unit.rotate(UP)
            val forward = unit.rotate(FORWARD)

            val pitchSine = -forward.y
            val pitch = asin(pitchSine.coerceIn(-1.0, 1.0))

            return if (abs(pitchSine) >= 1.0 - GIMBAL_LOCK_EPSILON) {
                EulerAngles(
                    pitchRadians = if (pitchSine > 0.0) PI / 2 else -PI / 2,
                    yawRadians = atan2(-right.z, right.x),
                    rollRadians = 0.0,
                )
            } else {
                EulerAngles(
                    pitchRadians = pitch,
                    yawRadians = atan2(forward.x, forward.z),
                    rollRadians = atan2(right.y, up.y),
                )
            }
        }
    }
}
