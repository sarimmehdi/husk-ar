package com.sarim.husk.geometry

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A rotation in three dimensions, stored as `x`, `y`, `z`, `w`.
 *
 * Rotations are held as quaternions rather than Euler angles throughout: Euler triples suffer
 * gimbal lock and depend on an axis convention that is easy to state and easy to get wrong. Euler
 * angles appear only where a person has to read or edit them.
 */
data class Quaternion(
    /** Vector part about the x axis. */
    val x: Double,
    /** Vector part about the y axis. */
    val y: Double,
    /** Vector part about the z axis. */
    val z: Double,
    /** Scalar part; the cosine of half the rotation angle. */
    val w: Double,
) {
    /** Squared length, in the quaternion's own four dimensions. */
    fun lengthSquared(): Double = x * x + y * y + z * z + w * w

    /**
     * This rotation scaled to unit length, or [IDENTITY] when it carries no rotation.
     *
     * Poses that arrive from tracking are not guaranteed to be unit length, and a zero quaternion
     * would otherwise normalise to NaN and corrupt everything derived from it.
     */
    fun normalized(): Quaternion {
        val lengthSquared = lengthSquared()
        if (lengthSquared == 0.0) return IDENTITY
        val inverseLength = 1.0 / sqrt(lengthSquared)
        return Quaternion(x * inverseLength, y * inverseLength, z * inverseLength, w * inverseLength)
    }

    /** The rotation that undoes this one. */
    fun inverse(): Quaternion = normalized().let { Quaternion(-it.x, -it.y, -it.z, it.w) }

    /**
     * The rotation that applies [other] first and this one second.
     *
     * Order matters: `a * b` is not `b * a`, and reversing it is a common source of poses that look
     * almost right.
     */
    operator fun times(other: Quaternion): Quaternion =
        Quaternion(
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w,
            w * other.w - x * other.x - y * other.y - z * other.z,
        )

    /**
     * Applies this rotation to [vector].
     *
     * The quaternion is normalised first so that a non-unit rotation turns the vector without also
     * scaling it.
     */
    fun rotate(vector: Vector3): Vector3 {
        val unit = normalized()
        val axis = Vector3(unit.x, unit.y, unit.z)
        val cross = axis cross vector
        return vector +
            (cross * (2.0 * unit.w)) +
            ((axis cross cross) * 2.0)
    }

    /** Ready-made rotations and the axis-angle constructor. */
    companion object {
        /** The rotation that changes nothing. */
        val IDENTITY = Quaternion(0.0, 0.0, 0.0, 1.0)

        /**
         * The rotation of [radians] about [axis], following the right-hand rule.
         *
         * An axis with no direction yields [IDENTITY], since there is nothing to rotate about.
         */
        fun fromAxisAngle(
            axis: Vector3,
            radians: Double,
        ): Quaternion {
            val unitAxis = axis.normalized()
            if (unitAxis == Vector3.ZERO) return IDENTITY
            val half = radians / 2.0
            val sine = sin(half)
            return Quaternion(unitAxis.x * sine, unitAxis.y * sine, unitAxis.z * sine, cos(half))
        }
    }
}
