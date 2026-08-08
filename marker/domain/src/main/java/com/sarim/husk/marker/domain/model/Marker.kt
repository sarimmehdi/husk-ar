package com.sarim.husk.marker.domain.model

import java.time.Instant

/** Identifies a marker image. */
data class MarkerId(
    /** The identifier itself. */
    val value: String,
)

/** Where a marker's image came from. */
enum class MarkerOrigin {
    /** Shipped with the app. Always present, and cannot be removed. */
    BUNDLED,

    /** Chosen by the person using it. */
    IMPORTED,
}

/**
 * A printed image that sessions are measured against.
 *
 * [printedWidthMillimetres] is the scale of every measurement taken against this marker. ARCore
 * reads the marker's apparent size in the camera and takes this number for its real one, so a marker
 * recorded as 200mm but printed at 192mm makes every measurement four percent large — with nothing
 * on screen to say so unless someone measures the printed sheet and corrects it here.
 */
data class Marker(
    /** Stable identity. */
    val id: MarkerId,
    /** What the person calls it. */
    val name: String,
    /** Where the image lives: an asset path for bundled markers, a file name for imported ones. */
    val imagePath: String,
    /** Pixel width of the image, for laying out a printed page. */
    val imagePixelWidth: Int,
    /** Pixel height of the image. */
    val imagePixelHeight: Int,
    /** How wide it is once printed. The scale of everything measured against it. */
    val printedWidthMillimetres: Double,
    /** Whether it shipped with the app. */
    val origin: MarkerOrigin,
    /** When it was added. */
    val addedAt: Instant,
)
