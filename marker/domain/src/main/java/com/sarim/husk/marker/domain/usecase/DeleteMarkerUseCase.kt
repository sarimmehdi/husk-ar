package com.sarim.husk.marker.domain.usecase

import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.repository.MarkerRepository

/** Removes an imported marker. The bundled one is kept whatever is asked. */
class DeleteMarkerUseCase(
    private val repository: MarkerRepository,
) {
    /** Deletes [id]. Deleting something already gone is not an error. */
    suspend operator fun invoke(id: MarkerId) {
        repository.delete(id)
    }
}
