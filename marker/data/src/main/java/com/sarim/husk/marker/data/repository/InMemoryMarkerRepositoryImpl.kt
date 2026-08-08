package com.sarim.husk.marker.data.repository

import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Process-local [MarkerRepository], and what the layers above it are tested against. */
class InMemoryMarkerRepositoryImpl : MarkerRepository {
    private val markers = MutableStateFlow<Map<MarkerId, Marker>>(emptyMap())

    override fun observeMarkers(): Flow<List<Marker>> =
        markers
            .map { stored ->
                // Bundled first, then newest. Ordering applied on the way out keeps it a property
                // of the contract rather than of the order things happened to arrive.
                stored.values.sortedWith(
                    compareBy<Marker> { it.origin != MarkerOrigin.BUNDLED }
                        .thenByDescending { it.addedAt },
                )
            }.distinctUntilChanged()

    override fun observeMarker(id: MarkerId): Flow<Marker?> = markers.map { it[id] }.distinctUntilChanged()

    override suspend fun put(marker: Marker) {
        markers.update { it + (marker.id to marker) }
    }

    override suspend fun setPrintedWidth(
        id: MarkerId,
        millimetres: Double,
    ) {
        markers.update { stored ->
            val existing = stored[id] ?: return@update stored
            stored + (id to existing.copy(printedWidthMillimetres = millimetres))
        }
    }

    override suspend fun delete(id: MarkerId) {
        markers.update { stored ->
            // The bundled marker is what every session can fall back to, so it survives deletion.
            if (stored[id]?.origin == MarkerOrigin.BUNDLED) stored else stored - id
        }
    }
}
