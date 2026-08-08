package com.sarim.husk.session.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A session row. */
@Entity(tableName = "session")
data class SessionEntity(
    /** The session's id. */
    @PrimaryKey val id: String,
    /** Its title. */
    val name: String,
    /** The marker everything in it is measured against. */
    val markerId: String,
    /** When it was started. Stored as epoch milliseconds so it can be sorted in SQL. */
    val createdAtEpochMillis: Long,
)

/** An ellipsoid flattened into columns. */
data class ShellColumns(
    /** Centre x, metres. */
    val centreX: Double,
    /** Centre y, metres. */
    val centreY: Double,
    /** Centre z, metres. */
    val centreZ: Double,
    /** Semi-axis along local x, metres. */
    val radiusX: Double,
    /** Semi-axis along local y, metres. */
    val radiusY: Double,
    /** Semi-axis along local z, metres. */
    val radiusZ: Double,
    /** Orientation x. */
    val rotationX: Double,
    /** Orientation y. */
    val rotationY: Double,
    /** Orientation z. */
    val rotationZ: Double,
    /** Orientation w. */
    val rotationW: Double,
)

/**
 * A measured object row.
 *
 * [sortIndex] exists because SQLite has no inherent row order and the contract promises capture
 * order. It is assigned once, on first insert, and preserved when an object is replaced by a
 * re-solve — otherwise re-fitting a shell would shuffle the list someone is reading.
 */
@Entity(
    tableName = "measured_object",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            // Deleting a session takes its objects with it. Left to the repository this would be
            // two statements that a crash between them could leave half done.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class MeasuredObjectEntity(
    /** The object's id. */
    @PrimaryKey val id: String,
    /** Which session it belongs to. */
    val sessionId: String,
    /** What it is called. */
    val label: String,
    /** Where it came in the capture order. */
    val sortIndex: Int,
    /** The recovered shell. */
    @Embedded(prefix = "shell_") val shell: ShellColumns,
    /** Widest angle between lines of sight, radians. Null until a fit succeeds. */
    @ColumnInfo(name = "quality_viewSpreadRadians") val viewSpreadRadians: Double?,
    /** Reprojection disagreement. Null until a fit succeeds. */
    @ColumnInfo(name = "quality_conicResidual") val conicResidual: Double?,
    /** Null space margin. Null until a fit succeeds. */
    @ColumnInfo(name = "quality_nullSpaceMargin") val nullSpaceMargin: Double?,
    /** Whether the shell was adjusted by hand after being fitted. */
    val isHandAdjusted: Boolean,
)

/** A camera pose flattened into columns. */
data class PoseColumns(
    /** Position x, metres. */
    val translationX: Double,
    /** Position y, metres. */
    val translationY: Double,
    /** Position z, metres. */
    val translationZ: Double,
    /** Orientation x. */
    val rotationX: Double,
    /** Orientation y. */
    val rotationY: Double,
    /** Orientation z. */
    val rotationZ: Double,
    /** Orientation w. */
    val rotationW: Double,
)

/** Camera intrinsics flattened into columns. */
data class IntrinsicsColumns(
    /** Focal length along image x, pixels. */
    val focalLengthX: Double,
    /** Focal length along image y, pixels. */
    val focalLengthY: Double,
    /** Principal point x, pixels. */
    val principalPointX: Double,
    /** Principal point y, pixels. */
    val principalPointY: Double,
)

/** A traced outline flattened into columns. */
data class OutlineColumns(
    /** Row 0, column 0. */
    val m00: Double,
    /** Row 0, column 1. */
    val m01: Double,
    /** Row 0, column 2. */
    val m02: Double,
    /** Row 1, column 1. */
    val m11: Double,
    /** Row 1, column 2. */
    val m12: Double,
    /** Row 2, column 2. */
    val m22: Double,
)

/**
 * One traced view.
 *
 * Kept rather than discarded once a shell is fitted, because they are what makes a measurement
 * reviewable: re-solvable after a correction, and replayable.
 */
@Entity(
    tableName = "observation",
    foreignKeys = [
        ForeignKey(
            entity = MeasuredObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("objectId")],
)
data class ObservationEntity(
    /** The observation's id. */
    @PrimaryKey val id: String,
    /** Which object it is a view of. */
    val objectId: String,
    /** Where it came in the capture order. */
    val sortIndex: Int,
    /** Where the camera was, in the marker's frame. */
    @Embedded(prefix = "pose_") val pose: PoseColumns,
    /** The lens it was taken with. */
    @Embedded(prefix = "lens_") val intrinsics: IntrinsicsColumns,
    /** The outline traced on it. */
    @Embedded(prefix = "outline_") val outline: OutlineColumns,
    /** When it was captured, epoch milliseconds. */
    val capturedAtEpochMillis: Long,
)
