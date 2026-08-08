package com.sarim.husk.marker.presentation

import com.sarim.husk.ar.PaperSize
import com.sarim.husk.marker.data.repository.InMemoryMarkerRepositoryImpl
import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.repository.MarkerRepository
import com.sarim.husk.marker.domain.usecase.DeleteMarkerUseCase
import com.sarim.husk.marker.domain.usecase.ObserveMarkersUseCase
import com.sarim.husk.marker.domain.usecase.RecordPrintedWidthUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The printing instructions, as the screen shows them.
 *
 * Every number is derived from the marker and the chosen paper rather than written down, because a
 * stale instruction about the one thing that scales every measurement is worse than none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkerLibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun marker(
        id: String = "earth",
        width: Double = 200.0,
        origin: MarkerOrigin = MarkerOrigin.BUNDLED,
    ) = Marker(
        id = MarkerId(id),
        name = "Earth",
        imagePath = "markers/earth.jpg",
        imagePixelWidth = 899,
        imagePixelHeight = 900,
        printedWidthMillimetres = width,
        origin = origin,
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private suspend fun TestScope.viewModel(
        repository: MarkerRepository = InMemoryMarkerRepositoryImpl(),
        seed: Marker? = marker(),
    ): Pair<MarkerLibraryViewModel, MarkerRepository> {
        seed?.let { repository.put(it) }
        val model =
            MarkerLibraryViewModel(
                MarkerLibraryScreenUseCase(
                    observeMarkersUseCase = ObserveMarkersUseCase(repository),
                    recordPrintedWidthUseCase = RecordPrintedWidthUseCase(repository),
                    deleteMarkerUseCase = DeleteMarkerUseCase(repository),
                ),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        return model to repository
    }

    @Test
    fun `a marker says how wide to print it`() =
        runTest {
            val (model, _) = viewModel()

            assertEquals(
                200,
                model.state.value.markers
                    .single()
                    .printedWidthMillimetres,
            )
        }

    @Test
    fun `the side margins are worked out for the chosen paper`() =
        runTest {
            // A4 is 210 wide, so a 200mm marker leaves 5mm each side. Reading this off a help page
            // would be wrong the moment the width or the paper changed.
            val (model, _) = viewModel()

            assertEquals(
                5,
                model.state.value.markers
                    .single()
                    .horizontalPaddingMillimetres,
            )
        }

    @Test
    fun `the top and bottom margins are worked out too`() =
        runTest {
            val (model, _) = viewModel()

            val row =
                model.state.value.markers
                    .single()
            assertEquals(48, row.verticalPaddingMillimetres)
            assertEquals(200, row.printedHeightMillimetres)
        }

    @Test
    fun `choosing different paper recomputes the margins`() =
        runTest {
            val (model, _) = viewModel()

            model.onEvent(MarkerLibraryScreenToViewModelEvents.PaperChosen(PaperSize.LETTER))

            // Letter is 215.9 wide, so a 200mm marker leaves about 8mm a side rather than 5.
            assertEquals(
                8,
                model.state.value.markers
                    .single()
                    .horizontalPaddingMillimetres,
            )
            assertEquals(PaperSize.LETTER, model.state.value.paper)
        }

    @Test
    fun `the reference bar length is stated`() =
        runTest {
            val (model, _) = viewModel()

            assertEquals(
                100,
                model.state.value.markers
                    .single()
                    .referenceBarMillimetres,
            )
        }

    @Test
    fun `a marker too big for the paper says so`() =
        runTest {
            // Better than printing something silently cropped, which changes the marker's size and
            // the pattern ARCore matches on at the same time.
            val (model, _) = viewModel(seed = marker(width = 250.0))

            assertFalse(
                model.state.value.markers
                    .single()
                    .fitsOnPage,
            )
        }

    @Test
    fun `measuring the printed bar corrects the marker's width`() =
        runTest {
            // The step that makes the whole thing safe. A bar reading 96 instead of 100 means the
            // sheet printed at 96 percent, so the marker is 192mm and not 200mm.
            val (model, repository) = viewModel()

            model.onEvent(
                MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured("earth", 96.0),
            )

            assertEquals(
                192.0,
                repository.observeMarker(MarkerId("earth")).first()!!.printedWidthMillimetres,
                TOLERANCE,
            )
        }

    @Test
    fun `a correctly printed sheet leaves the width alone`() =
        runTest {
            val (model, repository) = viewModel()

            model.onEvent(
                MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured("earth", 100.0),
            )

            assertEquals(
                200.0,
                repository.observeMarker(MarkerId("earth")).first()!!.printedWidthMillimetres,
                TOLERANCE,
            )
        }

    @Test
    fun `a mistyped measurement does not scale everything to nothing`() =
        runTest {
            // A stray zero would otherwise make every future measurement zero, and the shells would
            // simply vanish with no explanation.
            val (model, repository) = viewModel()

            model.onEvent(MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured("earth", 0.0))

            assertEquals(
                200.0,
                repository.observeMarker(MarkerId("earth")).first()!!.printedWidthMillimetres,
                TOLERANCE,
            )
        }

    @Test
    fun `the bundled marker is marked as such`() =
        runTest {
            val (model, _) = viewModel()

            assertTrue(
                model.state.value.markers
                    .single()
                    .isBundled,
            )
        }

    @Test
    fun `an imported marker can be deleted`() =
        runTest {
            val (model, repository) = viewModel(seed = marker(id = "mine", origin = MarkerOrigin.IMPORTED))

            model.onEvent(MarkerLibraryScreenToViewModelEvents.DeleteMarker("mine"))

            assertEquals(emptyList<Marker>(), repository.observeMarkers().first())
        }

    @Test
    fun `the bundled marker survives a delete`() =
        runTest {
            val (model, repository) = viewModel()

            model.onEvent(MarkerLibraryScreenToViewModelEvents.DeleteMarker("earth"))

            assertEquals(1, repository.observeMarkers().first().size)
        }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
