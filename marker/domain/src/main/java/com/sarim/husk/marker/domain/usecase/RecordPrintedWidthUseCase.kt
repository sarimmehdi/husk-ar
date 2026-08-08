package com.sarim.husk.marker.domain.usecase

import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.repository.MarkerRepository

/**
 * Records how wide a marker actually printed.
 *
 * This number is the scale of every measurement taken against the marker, so a width of zero or less
 * is refused rather than stored. Honouring one would scale every future measurement to nothing and
 * the shells would vanish with no explanation — and this is the last place that can be caught before
 * it reaches storage.
 */
class RecordPrintedWidthUseCase(
    private val repository: MarkerRepository,
) {
    /** Stores [millimetres] as the marker's real printed width. */
    suspend operator fun invoke(
        id: MarkerId,
        millimetres: Double,
    ) {
        if (millimetres <= 0.0) return
        repository.setPrintedWidth(id, millimetres)
    }
}
