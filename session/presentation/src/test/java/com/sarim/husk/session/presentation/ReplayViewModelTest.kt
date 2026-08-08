package com.sarim.husk.session.presentation

import com.sarim.husk.ar.CameraSnapshot
import com.sarim.husk.ar.Nudge
import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.ObservationId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionId = SessionId("s1")
    private val objectId = ObjectId("o1")

    private class Store(
        observations: List<Observation>,
    ) : SessionRepository {
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
                                MeasuredObject(
                                    id = ObjectId("o1"),
                                    label = "Mug",
                                    shell = Ellipsoid.EMPTY,
                                    observations = observations,
                                ),
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
        ) = Unit

        override suspend fun deleteObject(
            sessionId: SessionId,
            objectId: ObjectId,
        ) = Unit
    }

    private fun observation(
        id: String,
        at: Vector3,
    ) = Observation(
        id = ObservationId(id),
        cameraInAnchor = Pose(at, Quaternion.IDENTITY),
        intrinsics = CameraIntrinsics(800.0, 800.0, 320.0, 240.0),
        outline = DualConic.fromAxisAligned(320.0, 240.0, 60.0, 40.0),
        capturedAt = Instant.parse("2026-08-08T10:00:00Z"),
    )

    private fun snapshot(at: Vector3) =
        CameraSnapshot(
            cameraInMarker = Pose(at, Quaternion.IDENTITY),
            intrinsics = CameraIntrinsics(800.0, 800.0, 320.0, 240.0),
            imageWidth = 640,
            imageHeight = 480,
        )

    private fun viewModel(observations: List<Observation>): ReplayViewModel {
        val model =
            ReplayViewModel(
                useCases = ReplayScreenUseCase(ObserveSessionUseCase(Store(observations))),
                sessionId = sessionId,
                objectId = objectId,
            )
        model.onEvent(ReplayScreenToViewModelEvents.PreviewResized(1280, 960))
        return model
    }

    private val threeViews =
        listOf(
            observation("v1", Vector3(0.0, 0.0, 1.0)),
            observation("v2", Vector3(1.0, 0.0, 0.0)),
            observation("v3", Vector3(0.0, 0.0, -1.0)),
        )

    @Test
    fun `it opens on the first view`() =
        runTest {
            val state = viewModel(threeViews).state.value

            assertEquals(1, state.position)
            assertEquals(3, state.total)
            assertEquals("Mug", state.label)
        }

    @Test
    fun `stepping forward moves through the views`() =
        runTest {
            val model = viewModel(threeViews)

            model.onEvent(ReplayScreenToViewModelEvents.Next)

            assertEquals(2, model.state.value.position)
        }

    @Test
    fun `stepping past the last view wraps to the first`() =
        runTest {
            // Reviewing means going round more than once, and a dead button at the end turns that
            // into a chore.
            val model = viewModel(threeViews)

            repeat(3) { model.onEvent(ReplayScreenToViewModelEvents.Next) }

            assertEquals(1, model.state.value.position)
        }

    @Test
    fun `stepping back from the first wraps to the last`() =
        runTest {
            val model = viewModel(threeViews)

            model.onEvent(ReplayScreenToViewModelEvents.Previous)

            assertEquals(3, model.state.value.position)
        }

    @Test
    fun `an object with no views has nothing to replay`() =
        runTest {
            val state = viewModel(emptyList()).state.value

            assertEquals(0, state.position)
            assertEquals(0, state.total)
        }

    @Test
    fun `stepping through nothing does not crash or count`() =
        runTest {
            val model = viewModel(emptyList())

            model.onEvent(ReplayScreenToViewModelEvents.Next)

            assertEquals(0, model.state.value.position)
        }

    @Test
    fun `standing where the view was taken reads as aligned`() =
        runTest {
            val model = viewModel(threeViews)

            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(0.0, 0.0, 1.0))))

            assertTrue(model.state.value.isAligned)
            assertEquals(Nudge.NONE, model.state.value.move)
            assertEquals(0, model.state.value.distanceCentimetres)
        }

    @Test
    fun `standing elsewhere gives a direction and a distance`() =
        runTest {
            val model = viewModel(threeViews)

            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(0.5, 0.0, 1.0))))

            assertFalse(model.state.value.isAligned)
            assertEquals(Nudge.LEFT, model.state.value.move)
            assertEquals(50, model.state.value.distanceCentimetres)
        }

    @Test
    fun `guidance follows the view being replayed`() =
        runTest {
            // Stepping has to re-aim the guidance, or it walks the holder to the previous view.
            val model = viewModel(threeViews)
            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(1.0, 0.0, 0.0))))
            assertFalse("not yet on the second view's spot", model.state.value.isAligned)

            model.onEvent(ReplayScreenToViewModelEvents.Next)
            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(1.0, 0.0, 0.0))))

            assertTrue("the second view was taken from here", model.state.value.isAligned)
        }

    @Test
    fun `the recorded outline is offered as a hint`() =
        runTest {
            // Centred in a 640x480 image on a 1280x960 preview, so it belongs at the middle of the
            // preview at twice the size.
            val model = viewModel(threeViews)

            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(0.0, 0.0, 1.0))))

            val hint = assertNotNull(model.state.value.hint).let { model.state.value.hint!! }
            assertEquals(640f, hint.centreX, TOLERANCE)
            assertEquals(480f, hint.centreY, TOLERANCE)
            assertEquals(120f, hint.semiMajor, TOLERANCE)
        }

    @Test
    fun `losing the marker withdraws the hint rather than leaving it stranded`() =
        runTest {
            // Without the marker there is no frame to place it against, and a hint left where it
            // last happened to be would point at nothing.
            val model = viewModel(threeViews)
            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(snapshot(Vector3(0.0, 0.0, 1.0))))

            model.onEvent(ReplayScreenToViewModelEvents.FrameAvailable(null))

            assertNull(model.state.value.hint)
            assertFalse(model.state.value.isMarkerTracked)
        }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
