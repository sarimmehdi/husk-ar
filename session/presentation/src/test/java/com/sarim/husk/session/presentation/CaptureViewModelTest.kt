package com.sarim.husk.session.presentation

import com.sarim.husk.ar.CameraSnapshot
import com.sarim.husk.ar.TracedEllipse
import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.fitting.ShellFitter
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementConfidence
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.CaptureObservationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.math.PI

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionId = SessionId("s1")
    private val objectId = ObjectId("o1")

    private class Store : SessionRepository {
        val sessions =
            MutableStateFlow(
                listOf(
                    Session(
                        id = SessionId("s1"),
                        name = "Kitchen",
                        markerId = MarkerId("m1"),
                        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                        objects =
                            listOf(
                                MeasuredObject(ObjectId("o1"), "Mug", Ellipsoid.EMPTY),
                            ),
                    ),
                ),
            )

        override fun observeSessions(): Flow<List<Session>> = sessions

        override fun observeSession(id: SessionId): Flow<Session?> =
            sessions.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun putSession(session: Session) = Unit

        override suspend fun renameSession(
            id: SessionId,
            name: String,
        ) = Unit

        override suspend fun deleteSession(id: SessionId) = Unit

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

        override suspend fun deleteObject(
            sessionId: SessionId,
            objectId: ObjectId,
        ) = Unit
    }

    private class StubFitter(
        var result: ShellFit,
    ) : ShellFitter {
        var seen: List<Observation> = emptyList()

        override fun fit(observations: List<Observation>): ShellFit {
            seen = observations
            return result
        }
    }

    private fun snapshot(
        translation: Vector3 = Vector3(0.0, 0.0, 1.0),
        rotation: Quaternion = Quaternion.IDENTITY,
    ) = CameraSnapshot(
        cameraInMarker = Pose(translation, rotation),
        intrinsics = CameraIntrinsics(800.0, 800.0, 320.0, 240.0),
        imageWidth = 640,
        imageHeight = 480,
    )

    private fun viewModel(
        store: Store = Store(),
        fitter: StubFitter = StubFitter(ShellFit.Refused(FitRefusal.TOO_FEW_VIEWS)),
    ): Pair<CaptureViewModel, StubFitter> {
        val ids = generateSequence(1) { it + 1 }.map { "v-$it" }.iterator()
        return CaptureViewModel(
            useCases = CaptureScreenUseCase(CaptureObservationUseCase(store, fitter)),
            sessionId = sessionId,
            objectId = objectId,
            label = "Mug",
            newId = { ids.next() },
            clock = { Instant.parse("2026-08-08T10:00:00Z") },
        ) to fitter
    }

    private val trace =
        TracedEllipse(
            centreX = 640f,
            centreY = 480f,
            semiMajor = 100f,
            semiMinor = 80f,
            rotationRadians = 0f,
        )

    private fun CaptureViewModel.capture(
        start: CameraSnapshot?,
        end: CameraSnapshot,
    ) {
        if (start != null) {
            onEvent(CaptureScreenToViewModelEvents.FrameAvailable(start))
            onEvent(CaptureScreenToViewModelEvents.TraceStarted)
        }
        onEvent(CaptureScreenToViewModelEvents.FrameAvailable(end))
        onEvent(CaptureScreenToViewModelEvents.TraceCommitted(trace, 1280, 960))
    }

    @Test
    fun `without the marker there is nothing to measure against`() =
        runTest {
            val (model, _) = viewModel()

            model.onEvent(CaptureScreenToViewModelEvents.FrameAvailable(null))

            assertEquals(CaptureMessage.FIND_THE_MARKER, model.state.value.message)
            assertTrue(!model.state.value.isMarkerTracked)
        }

    @Test
    fun `finding the marker invites an outline`() =
        runTest {
            val (model, _) = viewModel()

            model.onEvent(CaptureScreenToViewModelEvents.FrameAvailable(snapshot()))

            assertEquals(CaptureMessage.OUTLINE_THE_OBJECT, model.state.value.message)
            assertTrue(model.state.value.isMarkerTracked)
        }

    @Test
    fun `an outline records a view`() =
        runTest {
            val (model, _) = viewModel()

            model.capture(start = snapshot(), end = snapshot())

            assertEquals(1, model.state.value.viewCount)
        }

    @Test
    fun `the recorded view carries the pose and lens of the frame it was drawn on`() =
        runTest {
            // The reason a snapshot exists at all. Paired with another frame's pose the outline
            // describes an object that was never there, and nothing downstream can tell.
            val (model, fitter) = viewModel()
            val frame = snapshot(translation = Vector3(0.3, -0.2, 1.5))

            model.capture(start = frame, end = frame)

            val recorded = fitter.seen.single()
            assertEquals(frame.cameraInMarker, recorded.cameraInAnchor)
            assertEquals(frame.intrinsics, recorded.intrinsics)
        }

    @Test
    fun `the outline is converted into camera image coordinates`() =
        runTest {
            // Traced at the middle of a 1280x960 preview showing a 640x480 image, so it belongs at
            // the middle of the image. Recording preview pixels instead would misplace every shell
            // by the preview scale.
            val (model, fitter) = viewModel()

            model.capture(start = snapshot(), end = snapshot())

            val parameters =
                fitter.seen
                    .single()
                    .outline
                    .toParameters()
            assertEquals(320.0, parameters.centreX, 1e-6)
            assertEquals(240.0, parameters.centreY, 1e-6)
        }

    @Test
    fun `an outline drawn while the phone was shifting is refused`() =
        runTest {
            // The object slides across the preview under the finger, so the outline matches no
            // single frame. Recording it would pair a plausible ellipse with a pose it was never
            // drawn against.
            val (model, fitter) = viewModel()

            model.capture(
                start = snapshot(translation = Vector3(0.0, 0.0, 1.0)),
                end = snapshot(translation = Vector3(0.0, 0.0, 1.2)),
            )

            assertEquals(CaptureMessage.HOLD_STILL, model.state.value.message)
            assertEquals(0, model.state.value.viewCount)
            assertTrue("nothing should have been recorded", fitter.seen.isEmpty())
        }

    @Test
    fun `an outline drawn while the phone was turning is refused`() =
        runTest {
            val (model, fitter) = viewModel()

            model.capture(
                start = snapshot(rotation = Quaternion.IDENTITY),
                end = snapshot(rotation = Quaternion.fromAxisAngle(Vector3(0.0, 1.0, 0.0), PI / 12.0)),
            )

            assertEquals(CaptureMessage.HOLD_STILL, model.state.value.message)
            assertTrue(fitter.seen.isEmpty())
        }

    @Test
    fun `a steady hand is not refused`() =
        runTest {
            // Nobody holds a phone perfectly still. A millimetre of drift has to be tolerated or the
            // screen refuses every honest attempt.
            val (model, _) = viewModel()

            model.capture(
                start = snapshot(translation = Vector3(0.0, 0.0, 1.0)),
                end = snapshot(translation = Vector3(0.001, 0.0, 1.001)),
            )

            assertEquals(1, model.state.value.viewCount)
        }

    @Test
    fun `too few views asks for more`() =
        runTest {
            val (model, _) = viewModel(fitter = StubFitter(ShellFit.Refused(FitRefusal.TOO_FEW_VIEWS)))

            model.capture(start = snapshot(), end = snapshot())

            assertEquals(CaptureMessage.NEED_MORE_VIEWS, model.state.value.message)
        }

    @Test
    fun `bunched views ask the person to move around the object`() =
        runTest {
            val (model, _) =
                viewModel(fitter = StubFitter(ShellFit.Refused(FitRefusal.VIEWS_TOO_CLOSE)))

            model.capture(start = snapshot(), end = snapshot())

            assertEquals(CaptureMessage.MOVE_AROUND_THE_OBJECT, model.state.value.message)
        }

    @Test
    fun `a fit reports its confidence`() =
        runTest {
            val fitter =
                StubFitter(
                    ShellFit.Fitted(
                        Ellipsoid(Vector3.ZERO, Vector3(0.05, 0.05, 0.05), Quaternion.IDENTITY),
                        MeasurementQuality(1.4, 0.001, 0.0009),
                    ),
                )
            val (model, _) = viewModel(fitter = fitter)

            model.capture(start = snapshot(), end = snapshot())

            assertEquals(CaptureMessage.MEASURED, model.state.value.message)
            assertEquals(MeasurementConfidence.GOOD, model.state.value.confidence)
        }

    @Test
    fun `an outline drawn before the marker was found is refused`() =
        runTest {
            val (model, fitter) = viewModel()

            model.onEvent(CaptureScreenToViewModelEvents.FrameAvailable(null))
            model.onEvent(CaptureScreenToViewModelEvents.TraceCommitted(trace, 1280, 960))

            assertEquals(CaptureMessage.FIND_THE_MARKER, model.state.value.message)
            assertTrue(fitter.seen.isEmpty())
        }
}
