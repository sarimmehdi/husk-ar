package com.sarim.husk.geometry

import kotlin.math.sqrt

/**
 * A point or direction in three dimensions.
 *
 * Values are metres when the vector carries a position, and unitless when it carries a direction.
 * Doubles rather than floats: the solver accumulates products of camera parameters, where float
 * precision is visibly lossy.
 */
data class Vector3(
    /** Component along the x axis. */
    val x: Double,
    /** Component along the y axis. */
    val y: Double,
    /** Component along the z axis. */
    val z: Double,
) {
    /** Component-wise sum. */
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    /** Component-wise difference. */
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

    /** Uniform scale. */
    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)

    /** Negation. */
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    /** Dot product. */
    infix fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    /** Cross product, following the right-hand rule. */
    infix fun cross(other: Vector3): Vector3 =
        Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

    /** Euclidean length. */
    fun length(): Double = sqrt(this dot this)

    /**
     * This vector scaled to unit length, or [ZERO] when it has no direction to preserve.
     *
     * Returning zero rather than a NaN-filled vector keeps a degenerate input from silently
     * poisoning every value derived from it.
     */
    fun normalized(): Vector3 {
        val length = length()
        return if (length == 0.0) ZERO else this * (1.0 / length)
    }

    /** Ready-made vectors. */
    companion object {
        /** The origin, and the absence of a direction. */
        val ZERO = Vector3(0.0, 0.0, 0.0)
    }
}
