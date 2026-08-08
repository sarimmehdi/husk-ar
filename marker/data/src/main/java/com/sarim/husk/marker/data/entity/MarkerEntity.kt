package com.sarim.husk.marker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A marker row.
 *
 * The origin is stored as text rather than through a converter, so the table reads plainly and there
 * is one less piece of machinery between the column and its meaning.
 */
@Entity(tableName = "marker")
data class MarkerEntity(
    /** The marker's id. */
    @PrimaryKey val id: String,
    /** What it is called. */
    val name: String,
    /** Asset path for bundled markers, file name for imported ones. */
    val imagePath: String,
    /** Pixel width of the image. */
    val imagePixelWidth: Int,
    /** Pixel height of the image. */
    val imagePixelHeight: Int,
    /** How wide it is once printed, in millimetres. */
    val printedWidthMillimetres: Double,
    /** Either BUNDLED or IMPORTED. */
    val origin: String,
    /** When it was added, epoch milliseconds. */
    val addedAtEpochMillis: Long,
)
