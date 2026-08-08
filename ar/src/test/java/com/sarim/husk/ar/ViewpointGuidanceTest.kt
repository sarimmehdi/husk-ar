package com.sarim.husk.ar

import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Guiding someone back to where a capture was taken from.
 *
 * Replay is only useful if it can be stood in. The nudge is expressed in the camera's own axes
 * rather than the marker's, because "left" has to mean the holder's left — told to move along the
 * marker's x they would have to work out which way that is while holding a phone up.
 */
class ViewpointGuidanceTest {
    private fun at(
        translation: Vector3,
        rotation: Quaternion = Quaternion.IDENTITY,
    ) = Pose(translation, rotation)

    private fun facingWorld(translation: Vector3) = at(translation, Quaternion.IDENTITY)

    @Test
    fun `standing on the spot is aligned`() {
        val spot = at(Vector3(0.3, 0.1, 1.2))

        val guidance = guidanceTo(target = spot, current = spot)

        assertTrue(guidance.isAligned)
        assertEquals(0.0, guidance.distanceMetres, TOLERANCE)
        assertEquals(0.0, guidance.angleDegrees, TOLERANCE)
        assertEquals(Nudge.NONE, guidance.move)
    }

    @Test
    fun `distance is how far the camera has to travel`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.0, 0.0, 0.0)),
                current = facingWorld(Vector3(0.3, 0.4, 0.0)),
            )

        assertEquals(0.5, guidance.distanceMetres, TOLERANCE)
    }

    @Test
    fun `a target to the camera's right says right`() {
        // ARCore's camera looks down its own negative z with x to the right, so a target at
        // positive x in camera axes is to the holder's right.
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.5, 0.0, 0.0)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.RIGHT, guidance.move)
    }

    @Test
    fun `a target to the camera's left says left`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(-0.5, 0.0, 0.0)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.LEFT, guidance.move)
    }

    @Test
    fun `a target ahead of the camera says forward`() {
        // Forward is negative z for a camera, which is the sign most likely to be written the wrong
        // way round and would send the holder backwards away from the shot.
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.0, 0.0, -0.5)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.FORWARD, guidance.move)
    }

    @Test
    fun `a target behind the camera says back`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.0, 0.0, 0.5)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.BACK, guidance.move)
    }

    @Test
    fun `a target above the camera says up`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.0, 0.5, 0.0)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.UP, guidance.move)
    }

    @Test
    fun `the nudge follows where the camera is pointing, not the marker`() {
        // The whole reason this is expressed in camera axes. Turning about the marker's up axis
        // takes the camera's forward with it, so the same target becomes forward or back depending
        // only on which way the holder happens to be facing. Told "right" in both cases, they would
        // walk away from the shot in one of them.
        val target = at(Vector3(0.5, 0.0, 0.0))
        val up = Vector3(0.0, 1.0, 0.0)

        // A quarter turn one way sends the camera's forward onto the marker's positive x, so the
        // target is straight ahead.
        val facingTarget = at(Vector3.ZERO, Quaternion.fromAxisAngle(up, -PI / 2.0))
        assertEquals(Nudge.FORWARD, guidanceTo(target, facingTarget).move)

        // A quarter turn the other way puts it squarely behind.
        val facingAway = at(Vector3.ZERO, Quaternion.fromAxisAngle(up, PI / 2.0))
        assertEquals(Nudge.BACK, guidanceTo(target, facingAway).move)
    }

    @Test
    fun `the largest error is the one reported`() {
        // One instruction at a time. Naming three axes at once is not something anyone can act on
        // while holding a phone up at an object.
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3(0.05, 0.5, 0.05)),
                current = facingWorld(Vector3.ZERO),
            )

        assertEquals(Nudge.UP, guidance.move)
    }

    @Test
    fun `turning without moving is still out of alignment`() {
        // Same spot, wrong direction: the object is not even in shot. Judging position alone would
        // call this aligned and replay would show nothing.
        val guidance =
            guidanceTo(
                target = at(Vector3.ZERO, Quaternion.IDENTITY),
                current = at(Vector3.ZERO, Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 4.0)),
            )

        assertFalse(guidance.isAligned)
        assertEquals(45.0, guidance.angleDegrees, ANGLE_TOLERANCE)
    }

    @Test
    fun `a half turn is reported as a half turn rather than as none`() {
        // A quaternion and its negation are the same rotation, so the naive angle of a half turn
        // can come out as zero if the sign is not handled.
        val guidance =
            guidanceTo(
                target = at(Vector3.ZERO, Quaternion.IDENTITY),
                current = at(Vector3.ZERO, Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI)),
            )

        assertEquals(180.0, guidance.angleDegrees, ANGLE_TOLERANCE)
    }

    @Test
    fun `close enough counts as aligned`() {
        // Nobody stands back on a spot exactly. Demanding that would make replay unusable.
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3.ZERO),
                current = facingWorld(Vector3(0.02, 0.0, 0.0)),
            )

        assertTrue("two centimetres should count as back on the spot", guidance.isAligned)
    }

    @Test
    fun `a step too far is not aligned`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3.ZERO),
                current = facingWorld(Vector3(0.4, 0.0, 0.0)),
            )

        assertFalse(guidance.isAligned)
    }

    @Test
    fun `an aligned viewpoint gives no instruction`() {
        val guidance =
            guidanceTo(
                target = facingWorld(Vector3.ZERO),
                current = facingWorld(Vector3(0.01, 0.0, 0.0)),
            )

        assertEquals(Nudge.NONE, guidance.move)
    }

    private companion object {
        const val TOLERANCE = 1e-9
        const val ANGLE_TOLERANCE = 1e-6
    }
}
