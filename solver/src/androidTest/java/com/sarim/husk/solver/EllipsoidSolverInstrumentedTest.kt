package com.sarim.husk.solver

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one test that needs a device: whether the JNI boundary actually binds and carries doubles
 * across intact.
 *
 * Everything else is covered without one. The arithmetic is pinned by the C++ host tests and the
 * conversion by the JVM tests; what neither can reach is `System.loadLibrary`, the symbol name the
 * shim exports, and whether array pinning hands back the values that went in.
 */
@RunWith(AndroidJUnit4::class)
class EllipsoidSolverInstrumentedTest {
    private val intrinsics = CameraIntrinsics(800.0, 800.0, 640.0, 360.0)

    /** Where a camera at [azimuth] around [target] sits, looking at it. */
    private fun cameraAt(
        target: Vector3,
        azimuth: Double,
        elevation: Double,
    ): Pose {
        val eye =
            Vector3(
                target.x + DISTANCE * cos(elevation) * cos(azimuth),
                target.y + DISTANCE * sin(elevation),
                target.z + DISTANCE * cos(elevation) * sin(azimuth),
            )
        // ARCore reports a camera looking down its own negative z, so the rotation that aims it at
        // the target is the one taking -z onto the direction of travel.
        val forward = (target - eye).normalized()
        val axis = Vector3(0.0, 0.0, -1.0) cross forward
        val angle = kotlin.math.acos((Vector3(0.0, 0.0, -1.0) dot forward).coerceIn(-1.0, 1.0))
        val rotation =
            if (axis.length() < EPSILON) Quaternion.IDENTITY else Quaternion.fromAxisAngle(axis, angle)
        return Pose(eye, rotation)
    }

    /** `C = P Q P'`, the exact outline the given ellipsoid casts in that view. */
    private fun outlineOf(
        ellipsoid: Ellipsoid,
        cameraInAnchor: Pose,
    ): DualConic {
        val p = intrinsics.projectionFrom(cameraInAnchor)
        val q = ellipsoid.toDualQuadric()
        val qp = Array(4) { row -> DoubleArray(3) { col -> (0..3).sumOf { q[row][it] * p[col][it] } } }
        val c = Array(3) { row -> DoubleArray(3) { col -> (0..3).sumOf { p[row][it] * qp[it][col] } } }
        return DualConic(c[0][0], c[0][1], c[0][2], c[1][1], c[1][2], c[2][2])
    }

    private fun observationsAround(ellipsoid: Ellipsoid): List<EllipseObservation> =
        listOf(0.0, 1.1, 2.3, 3.6, 4.9).mapIndexed { index, azimuth ->
            val pose = cameraAt(ellipsoid.centre, azimuth, 0.25 * sin(index.toDouble()))
            EllipseObservation(pose, intrinsics, outlineOf(ellipsoid, pose))
        }

    @Test
    fun recoversAKnownEllipsoidThroughTheNativeLibrary() {
        val original =
            Ellipsoid(
                centre = Vector3(0.4, 1.2, -0.3),
                radii = Vector3(0.15, 0.09, 0.2),
                rotation = Quaternion.IDENTITY,
            )

        val outcome = EllipsoidSolver().solve(observationsAround(original))

        val resolved = outcome as SolveOutcome.Resolved
        assertEquals(0.4, resolved.ellipsoid.centre.x, TOLERANCE)
        assertEquals(1.2, resolved.ellipsoid.centre.y, TOLERANCE)
        assertEquals(-0.3, resolved.ellipsoid.centre.z, TOLERANCE)

        val radii = listOf(resolved.ellipsoid.radii.x, resolved.ellipsoid.radii.y, resolved.ellipsoid.radii.z).sorted()
        listOf(0.09, 0.15, 0.2).forEachIndexed { index, expected ->
            assertEquals(expected, radii[index], TOLERANCE)
        }
    }

    @Test
    fun recoversARotatedEllipsoidThroughTheNativeLibrary() {
        val original =
            Ellipsoid(
                centre = Vector3(0.0, 0.5, 0.0),
                radii = Vector3(0.25, 0.08, 0.14),
                rotation = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 4.0),
            )

        val outcome = EllipsoidSolver().solve(observationsAround(original))

        val radii = (outcome as SolveOutcome.Resolved).ellipsoid.radii
        val sorted = listOf(radii.x, radii.y, radii.z).sorted()
        listOf(0.08, 0.14, 0.25).forEachIndexed { index, expected ->
            assertEquals(expected, sorted[index], TOLERANCE)
        }
    }

    @Test
    fun reportsDiagnosticsAcrossTheBoundary() {
        // Three doubles that are computed only in C++. Their arriving with sensible values is what
        // proves the result buffer came back rather than staying as Kotlin left it.
        val original =
            Ellipsoid(Vector3(0.0, 1.0, 0.0), Vector3(0.1, 0.1, 0.1), Quaternion.IDENTITY)

        val quality = EllipsoidSolver().solve(observationsAround(original)).quality

        assertTrue("margin should be healthy, was ${quality.nullSpaceMargin}", quality.nullSpaceMargin > 1e-4)
        assertTrue("exact outlines should reproject cleanly", quality.conicResidual < 1e-6)
        assertTrue("these views circle the object", quality.viewSpreadRadians > 1.0)
    }

    @Test
    fun refusesTooFewViewsThroughTheNativeLibrary() {
        // Confirms a refusal code survives the boundary as itself rather than arriving as zero.
        val original =
            Ellipsoid(Vector3(0.0, 1.0, 0.0), Vector3(0.1, 0.1, 0.1), Quaternion.IDENTITY)

        val outcome = EllipsoidSolver().solve(observationsAround(original).take(2))

        assertEquals(RefusalReason.TOO_FEW_VIEWS, (outcome as SolveOutcome.Refused).reason)
    }

    private companion object {
        const val DISTANCE = 2.0
        const val TOLERANCE = 1e-6
        const val EPSILON = 1e-9
    }
}
