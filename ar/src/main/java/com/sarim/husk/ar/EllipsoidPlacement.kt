package com.sarim.husk.ar

import com.sarim.husk.geometry.Ellipsoid
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion

/**
 * The radius of the sphere geometry an ellipsoid overlay is built from.
 *
 * One, so that scaling by the semi-axes produces exactly those semi-axes. Any other radius would
 * make [EllipsoidPlacement.scale] mean something other than a measurement in metres.
 */
const val UNIT_SPHERE_RADIUS = 1.0f

/**
 * Where to put a unit sphere, and how to stretch it, so that it becomes a given ellipsoid.
 *
 * Held as its own type rather than applied straight to a node so the arithmetic can be tested
 * without an engine, a surface, or a device.
 */
data class EllipsoidPlacement(
    /** Node position, in the marker anchor's frame. */
    val position: Float3,
    /** Node orientation, relative to that same frame. */
    val rotation: Quaternion,
    /** Per-axis scale applied to a sphere of [UNIT_SPHERE_RADIUS]. */
    val scale: Float3,
)

/**
 * This ellipsoid as a transform for a unit sphere node.
 *
 * No axis conversion happens here, and that is deliberate rather than an omission. The solver is
 * handed camera poses in the anchor's frame and returns the ellipsoid in that same frame; the
 * computer-vision convention it works in never escapes
 * [com.sarim.husk.geometry.CameraIntrinsics.projectionFrom]. Flipping anything here would be a
 * second conversion applied to coordinates that were never converted in the first place.
 *
 * Degenerate input yields a zero scale. A refused solve produces [Ellipsoid.EMPTY], and drawing that
 * at unit scale would drop a metre-wide ball into the scene; at zero it simply is not there.
 */
fun Ellipsoid.toPlacement(): EllipsoidPlacement =
    EllipsoidPlacement(
        position = Float3(centre.x.toFloat(), centre.y.toFloat(), centre.z.toFloat()),
        rotation =
            Quaternion(
                rotation.x.toFloat(),
                rotation.y.toFloat(),
                rotation.z.toFloat(),
                rotation.w.toFloat(),
            ),
        scale =
            Float3(
                drawableExtent(radii.x),
                drawableExtent(radii.y),
                drawableExtent(radii.z),
            ),
    )

/**
 * A semi-axis clamped to something safe to scale by.
 *
 * A negative scale turns the surface inside out, and Filament then culls the faces that should have
 * been visible, which reads on screen as a hole rather than as an error.
 */
private fun drawableExtent(radius: Double): Float = if (radius > 0.0) radius.toFloat() else 0.0f
