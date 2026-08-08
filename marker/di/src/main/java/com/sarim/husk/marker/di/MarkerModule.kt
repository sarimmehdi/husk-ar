package com.sarim.husk.marker.di

import androidx.room.Room
import com.sarim.husk.marker.data.database.MarkerDatabase
import com.sarim.husk.marker.data.repository.RoomMarkerRepositoryImpl
import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.repository.MarkerRepository
import com.sarim.husk.marker.domain.usecase.DeleteMarkerUseCase
import com.sarim.husk.marker.domain.usecase.ObserveMarkersUseCase
import com.sarim.husk.marker.domain.usecase.RecordPrintedWidthUseCase
import com.sarim.husk.marker.presentation.MarkerLibraryScreenUseCase
import com.sarim.husk.marker.presentation.MarkerLibraryViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.time.Instant

/** Dependency graph for the marker library. */
val markerModule =
    module {
        single {
            Room
                .databaseBuilder(
                    context = androidContext(),
                    klass = MarkerDatabase::class.java,
                    name = DATABASE_NAME,
                ).build()
        }
        single { get<MarkerDatabase>().markerDao() }
        single<MarkerRepository> { RoomMarkerRepositoryImpl(get()) }

        factory { ObserveMarkersUseCase(get()) }
        factory { RecordPrintedWidthUseCase(get()) }
        factory { DeleteMarkerUseCase(get()) }
        factory { MarkerLibraryScreenUseCase(get(), get(), get()) }
        viewModel { MarkerLibraryViewModel(get()) }
    }

/**
 * The marker that ships with the app.
 *
 * Written on every start rather than only when missing: it is the one marker guaranteed to work, and
 * a put of the same id replaces rather than duplicates. Its recorded width is deliberately left
 * alone by this, since someone may have measured their printed sheet and corrected it — see
 * [bundledMarker], which is only used when nothing is stored under that id.
 */
val BUNDLED_MARKER_ID = MarkerId("husk-default-marker")

/** The bundled marker as first stored, before anyone has measured a printed copy. */
fun bundledMarker(now: Instant): Marker =
    Marker(
        id = BUNDLED_MARKER_ID,
        name = "Earth",
        imagePath = BUNDLED_MARKER_ASSET,
        imagePixelWidth = BUNDLED_MARKER_PIXEL_WIDTH,
        imagePixelHeight = BUNDLED_MARKER_PIXEL_HEIGHT,
        printedWidthMillimetres = BUNDLED_MARKER_WIDTH_MILLIMETRES,
        origin = MarkerOrigin.BUNDLED,
        addedAt = now,
    )

/** From ARCore's own augmented images guide, where it scores 100 for trackability. */
const val BUNDLED_MARKER_ASSET = "markers/earth.jpg"

private const val BUNDLED_MARKER_PIXEL_WIDTH = 899
private const val BUNDLED_MARKER_PIXEL_HEIGHT = 900

/** Comfortable on A4 with a margin, and large enough to track from across a room. */
private const val BUNDLED_MARKER_WIDTH_MILLIMETRES = 200.0

private const val DATABASE_NAME = "husk-markers.db"
