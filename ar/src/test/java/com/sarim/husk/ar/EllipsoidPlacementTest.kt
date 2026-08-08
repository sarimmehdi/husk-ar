package com.sarim.husk.ar

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * The transform that turns a solved ellipsoid into something Filament can draw.
 *
 * Nothing here needs a device. The renderer is not involved: this is the arithmetic between the
 * solver's output and a node's position, orientation and scale, which is where the mistakes that
 * would show up as a mispainted overlay actually live.
 */
class EllipsoidPlacementTest {
    private fun ellipsoid(
        centre: Vector3 = Vector3.ZERO,
        radii: Vector3 = Vector3(1.0, 1.0, 1.0),
        rotation: Quaternion = Quaternion.IDENTITY,
    ) = Ellipsoid(centre, radii, rotation)

    @Test
    fun `the centre becomes the node position unchanged`() {
        // The solver works in the marker anchor's frame, which is the frame SceneView renders in.
        // Any flip here would be a second conversion on top of the one already inside the projection
        // matrix, and would put the overlay somewhere the object is not.
        val placement = ellipsoid(centre = Vector3(0.4, 1.2, -0.3)).toPlacement()

        assertEquals(0.4f, placement.position.x, TOLERANCE)
        assertEquals(1.2f, placement.position.y, TOLERANCE)
        assertEquals(-0.3f, placement.position.z, TOLERANCE)
    }

    @Test
    fun `radii become the scale of a unit sphere`() {
        // Semi-axes, not full widths. Scaling by Ellipsoid.extent would draw the shell at twice the
        // size the solver measured, and it would look plausible enough to ship.
        val placement = ellipsoid(radii = Vector3(0.15, 0.09, 0.2)).toPlacement()

        assertEquals(0.15f, placement.scale.x, TOLERANCE)
        assertEquals(0.09f, placement.scale.y, TOLERANCE)
        assertEquals(0.2f, placement.scale.z, TOLERANCE)
    }

    @Test
    fun `the node is built from a sphere of unit radius`() {
        // The scale above is only the semi-axis length if the geometry it multiplies has radius one.
        assertEquals(1.0f, UNIT_SPHERE_RADIUS, TOLERANCE)
    }

    @Test
    fun `a sphere scales uniformly`() {
        val placement = ellipsoid(radii = Vector3(0.12, 0.12, 0.12)).toPlacement()

        assertEquals(placement.scale.x, placement.scale.y, TOLERANCE)
        assertEquals(placement.scale.y, placement.scale.z, TOLERANCE)
    }

    @Test
    fun `the rotation keeps its component order across the two quaternion types`() {
        // Both types read x, y, z, w, but they are different classes from different libraries and
        // nothing enforces that. A swapped w would tilt every overlay by an amount that varies with
        // the object's orientation.
        val quarterTurnAboutY = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 2.0)

        val placement = ellipsoid(rotation = quarterTurnAboutY).toPlacement()

        val halfRootTwo = (sqrt(2.0) / 2.0).toFloat()
        assertEquals(0.0f, placement.rotation.x, TOLERANCE)
        assertEquals(halfRootTwo, placement.rotation.y, TOLERANCE)
        assertEquals(0.0f, placement.rotation.z, TOLERANCE)
        assertEquals(halfRootTwo, placement.rotation.w, TOLERANCE)
    }

    @Test
    fun `the rotation is not conjugated on the way across`() {
        // A conjugated quaternion is still unit length and still a rotation, just the opposite one.
        // Checking a turn with a definite sign catches that; a symmetric one would not.
        val eighthTurnAboutX = Quaternion.fromAxisAngle(Vector3(1.0, 0.0, 0.0), PI / 4.0)

        val placement = ellipsoid(rotation = eighthTurnAboutX).toPlacement()

        assertEquals(kotlin.math.sin(PI / 8.0).toFloat(), placement.rotation.x, TOLERANCE)
        assertEquals(kotlin.math.cos(PI / 8.0).toFloat(), placement.rotation.w, TOLERANCE)
    }

    @Test
    fun `an unsolved ellipsoid collapses to nothing rather than to a unit sphere`() {
        // Ellipsoid.EMPTY is what a refused solve yields. Drawing it at scale one would put a metre
        // wide ball over the scene; at zero it is simply invisible.
        val placement = Ellipsoid.EMPTY.toPlacement()

        assertEquals(0.0f, placement.scale.x, TOLERANCE)
        assertEquals(0.0f, placement.scale.y, TOLERANCE)
        assertEquals(0.0f, placement.scale.z, TOLERANCE)
    }

    @Test
    fun `a negative radius cannot invert the geometry`() {
        // Nothing upstream should produce one, but a negative scale turns a surface inside out and
        // Filament renders it with the wrong faces culled, which reads as a hole rather than an
        // error.
        val placement = ellipsoid(radii = Vector3(-0.1, 0.2, 0.3)).toPlacement()

        assertEquals(0.0f, placement.scale.x, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
