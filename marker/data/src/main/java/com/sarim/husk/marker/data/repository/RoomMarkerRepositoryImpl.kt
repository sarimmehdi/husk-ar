package com.sarim.husk.marker.data.repository

import com.sarim.husk.marker.data.dao.MarkerDao
import com.sarim.husk.marker.data.entity.MarkerEntity
import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [MarkerRepository] backed by SQLite, passing the same contract the in-memory store does. */
class RoomMarkerRepositoryImpl(
    private val dao: MarkerDao,
) : MarkerRepository {
    override fun observeMarkers(): Flow<List<Marker>> =
        dao.observeMarkers().map { rows -> rows.map(MarkerEntity::toDomain) }

    override fun observeMarker(id: MarkerId): Flow<Marker?> = dao.observeMarker(id.value).map { it?.toDomain() }

    override suspend fun put(marker: Marker) {
        dao.upsert(marker.toEntity())
    }

    override suspend fun setPrintedWidth(
        id: MarkerId,
        millimetres: Double,
    ) {
        dao.setPrintedWidth(id.value, millimetres)
    }

    override suspend fun delete(id: MarkerId) {
        dao.delete(id.value)
    }
}

private fun Marker.toEntity() =
    MarkerEntity(
        id = id.value,
        name = name,
        imagePath = imagePath,
        imagePixelWidth = imagePixelWidth,
        imagePixelHeight = imagePixelHeight,
        printedWidthMillimetres = printedWidthMillimetres,
        origin = origin.name,
        addedAtEpochMillis = addedAt.toEpochMilli(),
    )

private fun MarkerEntity.toDomain() =
    Marker(
        id = MarkerId(id),
        name = name,
        imagePath = imagePath,
        imagePixelWidth = imagePixelWidth,
        imagePixelHeight = imagePixelHeight,
        printedWidthMillimetres = printedWidthMillimetres,
        // An unrecognised value means a newer build wrote something this one does not know. Treating
        // it as imported is the safe reading: the worst it allows is deleting a marker.
        origin = MarkerOrigin.entries.firstOrNull { it.name == origin } ?: MarkerOrigin.IMPORTED,
        addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
    )
