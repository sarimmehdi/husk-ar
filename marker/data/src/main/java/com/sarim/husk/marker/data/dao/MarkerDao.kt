package com.sarim.husk.marker.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sarim.husk.marker.data.entity.MarkerEntity
import kotlinx.coroutines.flow.Flow

/** Room access for the marker library. */
@Dao
interface MarkerDao {
    /**
     * Every marker, bundled first and the rest newest first.
     *
     * Ordered in SQL rather than after reading, so the promise belongs to the query rather than to
     * whoever happens to call it.
     */
    @Query(
        "SELECT * FROM marker ORDER BY CASE origin WHEN 'BUNDLED' THEN 0 ELSE 1 END ASC, " +
            "addedAtEpochMillis DESC",
    )
    fun observeMarkers(): Flow<List<MarkerEntity>>

    /** One marker, or null while none has that id. */
    @Query("SELECT * FROM marker WHERE id = :id")
    fun observeMarker(id: String): Flow<MarkerEntity?>

    /** Inserts or replaces a marker. */
    @Upsert
    suspend fun upsert(marker: MarkerEntity)

    /** Records how wide a marker actually printed. Affects nothing when the id is unknown. */
    @Query("UPDATE marker SET printedWidthMillimetres = :millimetres WHERE id = :id")
    suspend fun setPrintedWidth(
        id: String,
        millimetres: Double,
    )

    /**
     * Removes a marker unless it shipped with the app.
     *
     * The condition is in the statement rather than checked first, so there is no window between
     * reading the origin and acting on it.
     */
    @Query("DELETE FROM marker WHERE id = :id AND origin <> 'BUNDLED'")
    suspend fun delete(id: String)
}
