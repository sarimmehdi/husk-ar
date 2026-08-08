package com.sarim.husk.marker.domain.repository

import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import kotlinx.coroutines.flow.Flow

/**
 * Storage-independent contract for the marker library.
 *
 * Every method is total: naming a marker that does not exist is not an error and never creates one.
 */
interface MarkerRepository {
    /** Every marker, the bundled one first and the rest newest first. */
    fun observeMarkers(): Flow<List<Marker>>

    /** One marker, emitting null while none has that id. */
    fun observeMarker(id: MarkerId): Flow<Marker?>

    /** Stores [marker], replacing any with the same id. */
    suspend fun put(marker: Marker)

    /** Records how wide a marker actually printed. Does nothing when [id] is unknown. */
    suspend fun setPrintedWidth(
        id: MarkerId,
        millimetres: Double,
    )

    /**
     * Removes a marker.
     *
     * A bundled marker is never removed. It is the one every session can fall back to, and deleting
     * it would leave sessions measured against something the app can no longer recognise.
     */
    suspend fun delete(id: MarkerId)
}
