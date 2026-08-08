package com.sarim.husk.session.presentation

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementConfidence
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.AdjustShellUseCase
import com.sarim.husk.session.domain.usecase.DeleteObjectUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import com.sarim.husk.session.domain.usecase.StartObjectUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionId = SessionId("s1")

    private class FakeRepository(
        initial: List<Session>,
    ) : SessionRepository {
        val sessions = MutableStateFlow(initial)

        override fun observeSessions(): Flow<List<Session>> = sessions

        override fun observeSession(id: SessionId): Flow<Session?> =
            sessions.map { list ->
                list.firstOrNull { it.id == id }
            }

        override suspend fun putSession(session: Session) {
            sessions.value = sessions.value + session
        }

        override suspend fun renameSession(
            id: SessionId,
            name: String,
        ) = Unit

        override suspend fun deleteSession(id: SessionId) {
            sessions.value = sessions.value.filterNot { it.id == id }
        }

        override suspend fun putObject(
            sessionId: SessionId,
            measured: MeasuredObject,
        ) {
            sessions.value =
                sessions.value.map { session ->
                    if (session.id == sessionId) {
                        session.copy(objects = session.objects + measured)
                    } else {
                        session
                    }
                }
        }

        override suspend fun deleteObject(
            sessionId: SessionId,
            objectId: ObjectId,
        ) {
            sessions.value =
                sessions.value.map { session ->
                    if (session.id == sessionId) {
                        session.copy(objects = session.objects.filterNot { it.id == objectId })
                    } else {
                        session
                    }
                }
        }
    }

    private fun TestScope.viewModel(repository: SessionRepository): SessionDetailViewModel {
        val ids = generateSequence(1) { it + 1 }.map { "o-$it" }.iterator()
        val model =
            SessionDetailViewModel(
                useCases =
                    SessionDetailScreenUseCase(
                        observeSessionUseCase = ObserveSessionUseCase(repository),
                        startObjectUseCase = StartObjectUseCase(repository) { ids.next() },
                        deleteObjectUseCase = DeleteObjectUseCase(repository),
                        adjustShellUseCase = AdjustShellUseCase(repository),
                    ),
                sessionId = sessionId,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        return model
    }

    private fun session(objects: List<MeasuredObject> = emptyList()) =
        Session(
            id = sessionId,
            name = "Kitchen",
            markerId = MarkerId("m1"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            objects = objects,
        )

    private fun measured(
        id: String = "o1",
        label: String = "Mug",
        radii: Vector3 = Vector3(0.04, 0.045, 0.05),
        quality: MeasurementQuality? = MeasurementQuality(1.4, 0.001, 0.0009),
    ) = MeasuredObject(
        id = ObjectId(id),
        label = label,
        shell = Ellipsoid(Vector3.ZERO, radii, Quaternion.IDENTITY),
        quality = quality,
    )

    @Test
    fun `the session name is shown`() =
        runTest {
            val state = viewModel(FakeRepository(listOf(session()))).state.value

            assertEquals("Kitchen", state.name)
            assertFalse(state.isLoading)
        }

    @Test
    fun `measured objects become rows`() =
        runTest {
            val repository = FakeRepository(listOf(session(listOf(measured(label = "Mug")))))

            val state = viewModel(repository).state.value

            assertEquals(listOf("Mug"), state.objects.map { it.label })
        }

    @Test
    fun `extents are reported as millimetres across, largest first`() =
        runTest {
            // Radii are semi-axes; a person measuring a mug wants the width across it. Reporting
            // radii would halve every number on the screen and read entirely plausibly.
            val repository =
                FakeRepository(listOf(session(listOf(measured(radii = Vector3(0.04, 0.045, 0.05))))))

            val state = viewModel(repository).state.value

            assertEquals(listOf(100, 90, 80), state.objects.single().extentMillimetres)
        }

    @Test
    fun `an unsolved object reports no confidence rather than a false one`() =
        runTest {
            val repository =
                FakeRepository(listOf(session(listOf(measured(quality = null)))))

            val state = viewModel(repository).state.value

            assertEquals(
                MeasurementConfidence.UNMEASURED,
                state.objects.single().confidence,
            )
        }

    @Test
    fun `a well captured object reads as good`() =
        runTest {
            val repository =
                FakeRepository(
                    listOf(session(listOf(measured(quality = MeasurementQuality(1.4, 0.001, 0.0009))))),
                )

            val state = viewModel(repository).state.value

            assertEquals(MeasurementConfidence.GOOD, state.objects.single().confidence)
        }

    @Test
    fun `starting an object adds a row`() =
        runTest {
            val repository = FakeRepository(listOf(session()))
            val model = viewModel(repository)

            model.onEvent(SessionDetailScreenToViewModelEvents.StartObject("Kettle"))

            assertEquals(
                listOf("Kettle"),
                model.state.value.objects
                    .map { it.label },
            )
        }

    @Test
    fun `deleting an object removes its row`() =
        runTest {
            val repository =
                FakeRepository(listOf(session(listOf(measured("o1"), measured("o2", "Kettle")))))
            val model = viewModel(repository)

            model.onEvent(SessionDetailScreenToViewModelEvents.DeleteObject("o1"))

            assertEquals(
                listOf("Kettle"),
                model.state.value.objects
                    .map { it.label },
            )
        }

    @Test
    fun `a session deleted elsewhere is reported as missing rather than as empty`() =
        runTest {
            // The screen has to tell these apart: an empty session invites you to measure
            // something, a deleted one has to send you back. Both have no objects.
            val repository = FakeRepository(listOf(session()))
            val model = viewModel(repository)

            repository.deleteSession(sessionId)

            assertTrue("expected the session to read as missing", model.state.value.isMissing)
            assertFalse("a missing session is answered, not still loading", model.state.value.isLoading)
        }

    @Test
    fun `an empty session is not reported as missing`() =
        runTest {
            val state = viewModel(FakeRepository(listOf(session()))).state.value

            assertFalse(state.isMissing)
            assertEquals(emptyList<MeasuredObjectRow>(), state.objects)
        }
}
