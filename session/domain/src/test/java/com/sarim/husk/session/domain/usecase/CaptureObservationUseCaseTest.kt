package com.sarim.husk.session.domain.usecase

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.ObservationId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureObservationUseCaseTest {
    private val sessionId = SessionId("s1")
    private val objectId = ObjectId("o1")

    /** A store holding one session, enough to append observations to. */
    private class OneSessionStore(
        session: Session,
    ) : NoOpSessionRepository() {
        val sessions = MutableStateFlow(listOf(session))

        override fun observeSession(id: SessionId): Flow<Session?> =
            sessions.map { list ->
                list.firstOrNull { it.id == id }
            }

        override suspend fun putObject(
            sessionId: SessionId,
            measured: MeasuredObject,
        ) {
            sessions.value =
                sessions.value.map { session ->
                    if (session.id != sessionId) {
                        session
                    } else {
                        val index = session.objects.indexOfFirst { it.id == measured.id }
                        session.copy(
                            objects =
                                if (index >= 0) {
                                    session.objects.toMutableList().apply { set(index, measured) }
                                } else {
                                    session.objects + measured
                                },
                        )
                    }
                }
        }
    }

    private class StubFitter(
        private val result: ShellFit,
    ) : ShellFitter {
        var observationsSeen: List<Observation> = emptyList()
        var calls = 0

        override fun fit(observations: List<Observation>): ShellFit {
            calls++
            observationsSeen = observations
            return result
        }
    }

    private val fittedShell =
        Ellipsoid(
            centre = Vector3(0.0, 0.1, 0.0),
            radii = Vector3(0.05, 0.06, 0.07),
            rotation = com.sarim.husk.geometry.Quaternion.IDENTITY,
        )
    private val fittedQuality = MeasurementQuality(1.2, 0.001, 0.0009)

    private fun observation(id: String) =
        Observation(
            id = ObservationId(id),
            cameraInAnchor = Pose.IDENTITY,
            intrinsics = CameraIntrinsics(800.0, 800.0, 640.0, 360.0),
            outline = DualConic(4.0, 0.0, 0.0, 9.0, 0.0, -1.0),
            capturedAt = Instant.parse("2026-08-08T10:00:00Z"),
        )

    private fun store(objects: List<MeasuredObject> = listOf(emptyObject())) =
        OneSessionStore(
            Session(
                id = sessionId,
                name = "Kitchen",
                markerId = MarkerId("m1"),
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                objects = objects,
            ),
        )

    private fun emptyObject(observations: List<Observation> = emptyList()) =
        MeasuredObject(
            id = objectId,
            label = "Mug",
            shell = Ellipsoid.EMPTY,
            observations = observations,
        )

    private fun useCase(
        store: OneSessionStore,
        fitter: ShellFitter,
    ) = CaptureObservationUseCase(repository = store, fitter = fitter)

    @Test
    fun `the observation is kept`() =
        runTest {
            val store = store()
            val fitter = StubFitter(ShellFit.Refused(FitRefusal.TOO_FEW_VIEWS))

            useCase(store, fitter)(sessionId, objectId, observation("v1"))

            val stored =
                store.sessions.value
                    .single()
                    .objects
                    .single()
            assertEquals(listOf(ObservationId("v1")), stored.observations.map { it.id })
        }

    @Test
    fun `observations accumulate in capture order`() =
        runTest {
            // Order is not arithmetic -- the solver does not care -- but it is what replay walks
            // through, so losing it loses the ability to review a capture.
            val store = store()
            val fitter = StubFitter(ShellFit.Refused(FitRefusal.TOO_FEW_VIEWS))
            val capture = useCase(store, fitter)

            capture(sessionId, objectId, observation("v1"))
            capture(sessionId, objectId, observation("v2"))
            capture(sessionId, objectId, observation("v3"))

            val stored =
                store.sessions.value
                    .single()
                    .objects
                    .single()
            assertEquals(
                listOf("v1", "v2", "v3"),
                stored.observations.map { it.id.value },
            )
        }

    @Test
    fun `the fitter is given every observation including the new one`() =
        runTest {
            // Fitting only the new view, or only the old ones, are both easy mistakes that still
            // produce a plausible shell.
            val store = store(listOf(emptyObject(listOf(observation("v1"), observation("v2")))))
            val fitter = StubFitter(ShellFit.Fitted(fittedShell, fittedQuality))

            useCase(store, fitter)(sessionId, objectId, observation("v3"))

            assertEquals(listOf("v1", "v2", "v3"), fitter.observationsSeen.map { it.id.value })
        }

    @Test
    fun `a fitted shell is stored on the object`() =
        runTest {
            val store = store(listOf(emptyObject(listOf(observation("v1"), observation("v2")))))
            val fitter = StubFitter(ShellFit.Fitted(fittedShell, fittedQuality))

            useCase(store, fitter)(sessionId, objectId, observation("v3"))

            val stored =
                store.sessions.value
                    .single()
                    .objects
                    .single()
            assertEquals(fittedShell, stored.shell)
            assertEquals(fittedQuality, stored.quality)
        }

    @Test
    fun `the fit is returned so the screen can react at once`() =
        runTest {
            val store = store()
            val fitter = StubFitter(ShellFit.Fitted(fittedShell, fittedQuality))

            val fit = useCase(store, fitter)(sessionId, objectId, observation("v1"))

            assertEquals(ShellFit.Fitted(fittedShell, fittedQuality), fit)
        }

    @Test
    fun `a refused fit leaves the previous shell alone`() =
        runTest {
            // A fourth view that makes the fit worse must not wipe out a shell that was already
            // good. Storing the refusal would blank the overlay the person was looking at.
            val store =
                store(
                    listOf(
                        emptyObject(listOf(observation("v1"))).copy(
                            shell = fittedShell,
                            quality = fittedQuality,
                        ),
                    ),
                )
            val fitter = StubFitter(ShellFit.Refused(FitRefusal.VIEWS_TOO_CLOSE))

            useCase(store, fitter)(sessionId, objectId, observation("v2"))

            val stored =
                store.sessions.value
                    .single()
                    .objects
                    .single()
            assertEquals(fittedShell, stored.shell)
            assertEquals(fittedQuality, stored.quality)
        }

    @Test
    fun `a refused fit still keeps the observation`() =
        runTest {
            // The view is evidence whether or not it helped yet. Discarding it would mean a capture
            // that refuses at three views can never reach four.
            val store = store()
            val fitter = StubFitter(ShellFit.Refused(FitRefusal.TOO_FEW_VIEWS))

            useCase(store, fitter)(sessionId, objectId, observation("v1"))

            assertEquals(
                1,
                store.sessions.value
                    .single()
                    .objects
                    .single()
                    .observations.size,
            )
        }

    @Test
    fun `an unknown object is left alone rather than created`() =
        runTest {
            val store = store()
            val fitter = StubFitter(ShellFit.Fitted(fittedShell, fittedQuality))

            val fit = useCase(store, fitter)(sessionId, ObjectId("ghost"), observation("v1"))

            assertEquals(
                1,
                store.sessions.value
                    .single()
                    .objects.size,
            )
            assertTrue("expected a refusal for an unknown object", fit is ShellFit.Refused)
            assertEquals(0, fitter.calls)
        }

    @Test
    fun `an unknown session is left alone`() =
        runTest {
            val store = store()
            val fitter = StubFitter(ShellFit.Fitted(fittedShell, fittedQuality))

            val fit = useCase(store, fitter)(SessionId("ghost"), objectId, observation("v1"))

            assertTrue("expected a refusal for an unknown session", fit is ShellFit.Refused)
            assertEquals(0, fitter.calls)
            assertNull(store.sessions.value.firstOrNull { it.id == SessionId("ghost") })
        }
}
