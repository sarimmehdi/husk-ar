package com.sarim.husk.geometry

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Eigen decomposition of a real symmetric 3x3 matrix.
 *
 * Recovering an ellipsoid's axes from a quadric means decomposing its shape matrix, which is always
 * symmetric. The cyclic Jacobi method is used rather than a closed form: it is short, needs no
 * external dependency, and stays stable when two axes are nearly equal — which is exactly what a
 * near-spherical object produces.
 *
 * Magic-number reporting is suppressed for the whole object: the literals here are the fixed
 * dimensions of a 3x3 problem and the coefficients of the standard quaternion-from-matrix
 * derivation. Naming them would obscure the arithmetic rather than explain it.
 */
@Suppress("MagicNumber")
internal object SymmetricEigen {
    private const val MAX_SWEEPS = 32
    private const val CONVERGENCE_EPSILON = 1e-14

    /**
     * Returns eigenvalues and their matching eigenvectors, sorted by descending eigenvalue.
     *
     * Eigenvectors are the columns of the returned matrix.
     */
    fun decompose(matrix: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val a = Array(3) { row -> matrix[row].copyOf() }
        val vectors = Array(3) { row -> DoubleArray(3) { column -> if (row == column) 1.0 else 0.0 } }

        repeat(MAX_SWEEPS) {
            val offDiagonal = abs(a[0][1]) + abs(a[0][2]) + abs(a[1][2])
            if (offDiagonal < CONVERGENCE_EPSILON) return@repeat
            listOf(0 to 1, 0 to 2, 1 to 2).forEach { (p, q) -> rotate(a, vectors, p, q) }
        }

        val order = listOf(0, 1, 2).sortedByDescending { a[it][it] }
        val eigenvalues = DoubleArray(3) { a[order[it]][order[it]] }
        val eigenvectors = Array(3) { row -> DoubleArray(3) { column -> vectors[row][order[column]] } }
        return eigenvalues to eigenvectors
    }

    /**
     * Converts a matrix of eigenvector columns into a rotation.
     *
     * Eigenvectors carry no inherent handedness, so a decomposition can produce a reflection. The
     * third column is flipped when needed, because a reflection is not a rotation and would render
     * an object mirrored.
     */
    fun toRotation(eigenvectors: Array<DoubleArray>): Quaternion {
        val m = Array(3) { row -> eigenvectors[row].copyOf() }
        if (determinant(m) < 0.0) {
            for (row in 0..2) m[row][2] = -m[row][2]
        }

        val trace = m[0][0] + m[1][1] + m[2][2]
        return if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0
            Quaternion(
                x = (m[2][1] - m[1][2]) / s,
                y = (m[0][2] - m[2][0]) / s,
                z = (m[1][0] - m[0][1]) / s,
                w = 0.25 * s,
            )
        } else {
            largestDiagonalBranch(m)
        }.normalized()
    }

    private fun largestDiagonalBranch(m: Array<DoubleArray>): Quaternion =
        when {
            m[0][0] > m[1][1] && m[0][0] > m[2][2] -> {
                val s = sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0
                Quaternion(0.25 * s, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s, (m[2][1] - m[1][2]) / s)
            }
            m[1][1] > m[2][2] -> {
                val s = sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0
                Quaternion((m[0][1] + m[1][0]) / s, 0.25 * s, (m[1][2] + m[2][1]) / s, (m[0][2] - m[2][0]) / s)
            }
            else -> {
                val s = sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0
                Quaternion((m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, 0.25 * s, (m[1][0] - m[0][1]) / s)
            }
        }

    private fun determinant(m: Array<DoubleArray>): Double =
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

    private fun rotate(
        a: Array<DoubleArray>,
        vectors: Array<DoubleArray>,
        p: Int,
        q: Int,
    ) {
        if (abs(a[p][q]) < CONVERGENCE_EPSILON) return

        val theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q])
        val t = (if (theta >= 0.0) 1.0 else -1.0) / (abs(theta) + hypot(1.0, theta))
        val c = 1.0 / hypot(1.0, t)
        val s = t * c

        for (k in 0..2) {
            val akp = a[k][p]
            val akq = a[k][q]
            a[k][p] = c * akp - s * akq
            a[k][q] = s * akp + c * akq
        }
        for (k in 0..2) {
            val apk = a[p][k]
            val aqk = a[q][k]
            a[p][k] = c * apk - s * aqk
            a[q][k] = s * apk + c * aqk
        }
        for (k in 0..2) {
            val vkp = vectors[k][p]
            val vkq = vectors[k][q]
            vectors[k][p] = c * vkp - s * vkq
            vectors[k][q] = s * vkp + c * vkq
        }
    }
}
