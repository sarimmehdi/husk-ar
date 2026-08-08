package com.sarim.husk.marker.data.repository

import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Everything a [MarkerRepository] must do, independent of how it stores anything.
 *
 * Written once and inherited by each implementation, as with sessions. The behaviours that differ
 * between a map and a database — ordering, replacement, absence — are the ones a separate suite
 * would forget to check.
 */
abstract class MarkerRepositoryContract {
    protected abstract fun createRepository(): MarkerRepository

    private fun marker(
        id: String,
        name: String = "Earth",
        width: Double = 200.0,
        origin: MarkerOrigin = MarkerOrigin.IMPORTED,
        addedAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = Marker(
        id = MarkerId(id),
        name = name,
        imagePath = "markers/$id.jpg",
        imagePixelWidth = 899,
        imagePixelHeight = 900,
        printedWidthMillimetres = width,
        origin = origin,
        addedAt = addedAt,
    )

    @Test
    fun `a stored marker can be observed by id`() =
        runTest {
            val repository = createRepository()
            val expected = marker("a")

            repository.put(expected)

            assertEquals(expected, repository.observeMarker(MarkerId("a")).first())
        }

    @Test
    fun `an unknown marker observes as null rather than failing`() =
        runTest {
            assertNull(createRepository().observeMarker(MarkerId("nobody")).first())
        }

    @Test
    fun `the bundled marker is listed first`() =
        runTest {
            // It is the one that always works and the one the app falls back to, so it belongs at
            // the top however recently anything else was added.
            val repository = createRepository()
            repository.put(marker("imported", addedAt = Instant.parse("2026-06-01T00:00:00Z")))
            repository.put(
                marker("bundled", origin = MarkerOrigin.BUNDLED, addedAt = Instant.parse("2026-01-01T00:00:00Z")),
            )

            val ids = repository.observeMarkers().first().map { it.id.value }

            assertEquals(listOf("bundled", "imported"), ids)
        }

    @Test
    fun `imported markers are listed newest first`() =
        runTest {
            val repository = createRepository()
            repository.put(marker("old", addedAt = Instant.parse("2026-01-01T00:00:00Z")))
            repository.put(marker("new", addedAt = Instant.parse("2026-06-01T00:00:00Z")))
            repository.put(marker("mid", addedAt = Instant.parse("2026-03-01T00:00:00Z")))

            val ids = repository.observeMarkers().first().map { it.id.value }

            assertEquals(listOf("new", "mid", "old"), ids)
        }

    @Test
    fun `storing a marker with an existing id replaces it`() =
        runTest {
            val repository = createRepository()
            repository.put(marker("a", name = "First"))

            repository.put(marker("a", name = "Second"))

            val markers = repository.observeMarkers().first()
            assertEquals(1, markers.size)
            assertEquals("Second", markers.single().name)
        }

    @Test
    fun `recording a measured width changes only that`() =
        runTest {
            // The correction someone makes after measuring the printed sheet. It has to leave the
            // name and image alone, or re-measuring would quietly rewrite the rest of the marker.
            val repository = createRepository()
            val original = marker("a", width = 200.0)
            repository.put(original)

            repository.setPrintedWidth(MarkerId("a"), 192.0)

            assertEquals(
                original.copy(printedWidthMillimetres = 192.0),
                repository.observeMarker(MarkerId("a")).first(),
            )
        }

    @Test
    fun `recording a width for an unknown marker creates nothing`() =
        runTest {
            val repository = createRepository()

            repository.setPrintedWidth(MarkerId("ghost"), 150.0)

            assertEquals(emptyList<Marker>(), repository.observeMarkers().first())
        }

    @Test
    fun `a deleted marker observes as null`() =
        runTest {
            val repository = createRepository()
            repository.put(marker("a"))

            repository.delete(MarkerId("a"))

            assertNull(repository.observeMarker(MarkerId("a")).first())
        }

    @Test
    fun `the bundled marker cannot be deleted`() =
        runTest {
            // It is what every session can fall back to. Removing it would leave sessions measured
            // against an image the app can no longer recognise, and nothing would bring it back.
            val repository = createRepository()
            repository.put(marker("bundled", origin = MarkerOrigin.BUNDLED))

            repository.delete(MarkerId("bundled"))

            assertNotNull(
                "the bundled marker must survive deletion",
                repository.observeMarker(MarkerId("bundled")).first(),
            )
        }

    @Test
    fun `deleting an unknown marker is not an error`() =
        runTest {
            val repository = createRepository()
            repository.put(marker("a"))

            repository.delete(MarkerId("ghost"))

            assertEquals(1, repository.observeMarkers().first().size)
        }

    @Test
    fun `observers see a marker appear after it is stored`() =
        runTest {
            val repository = createRepository()
            assertNull(repository.observeMarker(MarkerId("a")).first())

            repository.put(marker("a", name = "Earth"))

            assertEquals("Earth", repository.observeMarker(MarkerId("a")).first()?.name)
        }
}
