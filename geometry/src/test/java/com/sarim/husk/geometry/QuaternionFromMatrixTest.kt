package com.sarim.husk.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The conversion the solver relies on to turn a recovered axis frame into an orientation.
 *
 * The standard derivation has four branches, chosen by which diagonal entry is largest, and a
 * mistake in any one of them produces a rotation that is wrong only for certain inputs. Each branch
 * is reached deliberately below.
 */
class QuaternionFromMatrixTest {
    private fun rotationAbout(
        axis: Vector3,
        radians: Double,
    ): Array<DoubleArray> {
        val q = Quaternion.fromAxisAngle(axis, radians)
        val columns =
            listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))
                .map { q.rotate(it) }
        return arrayOf(
            doubleArrayOf(columns[0].x, columns[1].x, columns[2].x),
            doubleArrayOf(columns[0].y, columns[1].y, columns[2].y),
            doubleArrayOf(columns[0].z, columns[1].z, columns[2].z),
        )
    }

    private fun assertRotatesLike(
        expected: Quaternion,
        actual: Quaternion,
    ) {
        // A quaternion and its negation are the same rotation, so compare what they do, not what
        // they hold.
        listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).forEach {
            val want = expected.rotate(it)
            val got = actual.rotate(it)
            assertEquals(want.x, got.x, TOLERANCE)
            assertEquals(want.y, got.y, TOLERANCE)
            assertEquals(want.z, got.z, TOLERANCE)
        }
    }

    @Test
    fun `the identity matrix is the identity rotation`() {
        val identity =
            arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
            )

        assertRotatesLike(Quaternion.IDENTITY, Quaternion.fromRotationMatrix(identity))
    }

    @Test
    fun `a small turn takes the positive trace branch`() {
        val axis = Vector3(0.0, 1.0, 0.0)

        assertRotatesLike(
            Quaternion.fromAxisAngle(axis, 0.4),
            Quaternion.fromRotationMatrix(rotationAbout(axis, 0.4)),
        )
    }

    @Test
    fun `a half turn about x takes the branch for the largest diagonal`() {
        // Trace is -1 here, so the positive-trace formula would divide by zero.
        val axis = Vector3(1.0, 0.0, 0.0)

        assertRotatesLike(
            Quaternion.fromAxisAngle(axis, PI),
            Quaternion.fromRotationMatrix(rotationAbout(axis, PI)),
        )
    }

    @Test
    fun `a half turn about y takes its own branch`() {
        val axis = Vector3(0.0, 1.0, 0.0)

        assertRotatesLike(
            Quaternion.fromAxisAngle(axis, PI),
            Quaternion.fromRotationMatrix(rotationAbout(axis, PI)),
        )
    }

    @Test
    fun `a half turn about z takes its own branch`() {
        val axis = Vector3(0.0, 0.0, 1.0)

        assertRotatesLike(
            Quaternion.fromAxisAngle(axis, PI),
            Quaternion.fromRotationMatrix(rotationAbout(axis, PI)),
        )
    }

    @Test
    fun `an arbitrary rotation survives the round trip`() {
        val axis = Vector3(0.3, -0.7, 0.5)

        assertRotatesLike(
            Quaternion.fromAxisAngle(axis, 2.1),
            Quaternion.fromRotationMatrix(rotationAbout(axis, 2.1)),
        )
    }

    @Test
    fun `columns are read as axes rather than rows`() {
        // A quarter turn about y is not symmetric, so reading it transposed turns the other way.
        val quarterTurn =
            arrayOf(
                doubleArrayOf(cos(PI / 2), 0.0, sin(PI / 2)),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(-sin(PI / 2), 0.0, cos(PI / 2)),
            )

        val turned = Quaternion.fromRotationMatrix(quarterTurn).rotate(Vector3(1.0, 0.0, 0.0))

        assertEquals(0.0, turned.x, TOLERANCE)
        assertEquals(-1.0, turned.z, TOLERANCE)
    }

    @Test
    fun `the result is a unit quaternion`() {
        val length = Quaternion.fromRotationMatrix(rotationAbout(Vector3(1.0, 2.0, 3.0), 1.3)).lengthSquared()

        assertEquals(1.0, length, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
