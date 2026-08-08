package com.sarim.husk.marker.domain.usecase

import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow

/** Every marker in the library, bundled first. */
class ObserveMarkersUseCase(
    private val repository: MarkerRepository,
) {
    /** The stream of markers. */
    operator fun invoke(): Flow<List<Marker>> = repository.observeMarkers()
}
