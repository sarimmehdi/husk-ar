package com.sarim.husk.session.domain.usecase

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.model.ShellAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AdjustShellUseCaseTest {
    private val sessionId = SessionId("s1")
    private val objectId = ObjectId("o1")

    private class Store(
        measured: MeasuredObject,
    ) : NoOpSessionRepository() {
        val sessions =
            MutableStateFlow(
                listOf(
                    Session(
                        id = SessionId("s1"),
                        name = "Kitchen",
                        markerId = MarkerId("m1"),
                        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                        objects = listOf(measured),
                    ),
                ),
            )

        override fun observeSession(id: SessionId): Flow<Session?> =
            sessions.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun putObject(
            sessionId: SessionId,
            measured: MeasuredObject,
        ) {
            sessions.value =
                sessions.value.map { session ->
                    session.copy(
                        objects = session.objects.map { if (it.id == measured.id) measured else it },
                    )
                }
        }

        val stored: MeasuredObject get() =
            sessions.value
                .single()
                .objects
                .single()
    }

    private fun measured(
        radii: Vector3 = Vector3(0.04, 0.05, 0.06),
        centre: Vector3 = Vector3(0.1, 0.2, 0.3),
    ) = MeasuredObject(
        id = objectId,
        label = "Mug",
        shell = Ellipsoid(centre, radii, Quaternion.IDENTITY),
        quality = MeasurementQuality(1.4, 0.001, 0.0009),
    )

    @Test
    fun `scaling an axis scales that radius`() =
        runTest {
            val store = Store(measured())

            AdjustShellUseCase(store)(
                sessionId,
                objectId,
                ShellAdjustment(extentScale = Vector3(2.0, 1.0, 1.0)),
            )

            assertEquals(0.08, store.stored.shell.radii.x, TOLERANCE)
            assertEquals(0.05, store.stored.shell.radii.y, TOLERANCE)
        }

    @Test
    fun `an offset shifts the centre`() =
        runTest {
            val store = Store(measured())

            AdjustShellUseCase(store)(
                sessionId,
                objectId,
                ShellAdjustment(centreOffsetMetres = Vector3(0.01, -0.02, 0.0)),
            )

            assertEquals(0.11, store.stored.shell.centre.x, TOLERANCE)
            assertEquals(0.18, store.stored.shell.centre.y, TOLERANCE)
            assertEquals(0.3, store.stored.shell.centre.z, TOLERANCE)
        }

    @Test
    fun `the orientation is left alone`() =
        runTest {
            // Sliders move and resize; nothing here turns the shell, and quietly renormalising the
            // rotation would drift it a little on every adjustment.
            val turned =
                measured().copy(
                    shell =
                        Ellipsoid(
                            Vector3.ZERO,
                            Vector3(0.04, 0.05, 0.06),
                            Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), 0.7),
                        ),
                )
            val store = Store(turned)

            AdjustShellUseCase(store)(sessionId, objectId, ShellAdjustment(extentScale = Vector3(2.0, 2.0, 2.0)))

            assertEquals(turned.shell.rotation, store.stored.shell.rotation)
        }

    @Test
    fun `an adjusted shell no longer claims the solver's confidence`() =
        runTest {
            // The number was earned by views that no longer describe what is on screen. Keeping it
            // would be the app vouching for a figure a person dragged into place.
            val store = Store(measured())

            AdjustShellUseCase(
                store,
            )(sessionId, objectId, ShellAdjustment(centreOffsetMetres = Vector3(0.01, 0.0, 0.0)))

            assertTrue(store.stored.isHandAdjusted)
        }

    @Test
    fun `an untouched shell is not marked as adjusted`() =
        runTest {
            val store = Store(measured())

            assertFalse(store.stored.isHandAdjusted)
        }

    @Test
    fun `the views it was fitted from are kept`() =
        runTest {
            // Adjusting is a correction, not a fresh start. Discarding the views would make the
            // shell unre-solvable and unreplayable.
            val store = Store(measured())

            AdjustShellUseCase(store)(sessionId, objectId, ShellAdjustment(extentScale = Vector3(1.1, 1.1, 1.1)))

            assertEquals(measured().observations, store.stored.observations)
            assertEquals(measured().quality, store.stored.quality)
        }

    @Test
    fun `a zero scale is ignored rather than collapsing the shell`() =
        runTest {
            // Zero would flatten it to a plane and a negative would turn it inside out. Neither is
            // anything a person dragging a slider means.
            val store = Store(measured())

            AdjustShellUseCase(store)(sessionId, objectId, ShellAdjustment(extentScale = Vector3(0.0, -1.0, 1.5)))

            assertEquals(0.04, store.stored.shell.radii.x, TOLERANCE)
            assertEquals(0.05, store.stored.shell.radii.y, TOLERANCE)
            assertEquals(0.09, store.stored.shell.radii.z, TOLERANCE)
        }

    @Test
    fun `an unknown object is left alone`() =
        runTest {
            val store = Store(measured())

            AdjustShellUseCase(
                store,
            )(sessionId, ObjectId("ghost"), ShellAdjustment(extentScale = Vector3(9.0, 9.0, 9.0)))

            assertEquals(0.04, store.stored.shell.radii.x, TOLERANCE)
            assertFalse(store.stored.isHandAdjusted)
        }

    @Test
    fun `an unknown session is left alone`() =
        runTest {
            val store = Store(measured())

            AdjustShellUseCase(
                store,
            )(SessionId("ghost"), objectId, ShellAdjustment(extentScale = Vector3(9.0, 9.0, 9.0)))

            assertEquals(0.04, store.stored.shell.radii.x, TOLERANCE)
        }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
