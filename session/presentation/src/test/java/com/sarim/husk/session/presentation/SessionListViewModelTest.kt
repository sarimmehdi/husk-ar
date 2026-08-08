package com.sarim.husk.session.presentation

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import com.sarim.husk.session.domain.usecase.CreateSessionUseCase
import com.sarim.husk.session.domain.usecase.DeleteSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionsUseCase
import com.sarim.husk.session.domain.usecase.RenameSessionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val marker = MarkerId("marker-1")

    /** Enough of a repository to drive the view model, without the in-memory module's behaviour. */
    private class FakeRepository : SessionRepository {
        val sessions = MutableStateFlow<List<Session>>(emptyList())

        override fun observeSessions(): Flow<List<Session>> = sessions

        override fun observeSession(id: SessionId): Flow<Session?> =
            sessions.map { list ->
                list.firstOrNull { it.id == id }
            }

        override suspend fun putSession(session: Session) {
            sessions.value = listOf(session) + sessions.value
        }

        override suspend fun renameSession(
            id: SessionId,
            name: String,
        ) {
            sessions.value = sessions.value.map { if (it.id == id) it.copy(name = name) else it }
        }

        override suspend fun deleteSession(id: SessionId) {
            sessions.value = sessions.value.filterNot { it.id == id }
        }

        override suspend fun putObject(
            sessionId: SessionId,
            measured: MeasuredObject,
        ) = Unit

        override suspend fun deleteObject(
            sessionId: SessionId,
            objectId: ObjectId,
        ) = Unit
    }

    /**
     * A view model with its state already subscribed.
     *
     * The state is shared WhileSubscribed, so reading `value` with no collector returns the initial
     * placeholder rather than anything the store said. Compose subscribes through
     * collectAsStateWithLifecycle; a test that does not is reading a value the screen never sees.
     */
    private fun TestScope.viewModel(repository: SessionRepository): SessionListViewModel {
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val model =
            SessionListViewModel(
                useCases =
                    SessionListScreenUseCase(
                        observeSessionsUseCase = ObserveSessionsUseCase(repository),
                        createSessionUseCase =
                            CreateSessionUseCase(
                                repository = repository,
                                newId = { ids.next() },
                                clock = { Instant.parse("2026-08-08T10:15:00Z") },
                            ),
                        renameSessionUseCase = RenameSessionUseCase(repository),
                        deleteSessionUseCase = DeleteSessionUseCase(repository),
                    ),
                marker = marker,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        return model
    }

    private fun session(
        id: String,
        name: String = "Kitchen",
        objects: List<MeasuredObject> = emptyList(),
    ) = Session(
        id = SessionId(id),
        name = name,
        markerId = marker,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        objects = objects,
    )

    @Test
    fun `stored sessions become rows`() =
        runTest {
            val repository = FakeRepository()
            repository.sessions.value = listOf(session("a", name = "Kitchen"))

            val state = viewModel(repository).state.value

            assertEquals(listOf("Kitchen"), state.sessions.map { it.name })
            assertEquals(listOf("a"), state.sessions.map { it.id })
        }

    @Test
    fun `a row reports how much was measured in its session`() =
        runTest {
            // The count is the only thing on the list that says whether a session holds anything, so
            // it is worth pinning rather than trusting to a lambda.
            val repository = FakeRepository()
            repository.sessions.value =
                listOf(session("a", objects = listOf(measured("o1"), measured("o2"))))

            val state = viewModel(repository).state.value

            assertEquals(2, state.sessions.single().objectCount)
        }

    @Test
    fun `the list stops loading once the store has answered`() =
        runTest {
            val repository = FakeRepository()

            val state = viewModel(repository).state.value

            assertTrue("an answered empty store is not still loading", !state.isLoading)
            assertEquals(emptyList<SessionRow>(), state.sessions)
        }

    @Test
    fun `creating a session adds it to the list`() =
        runTest {
            val repository = FakeRepository()
            val model = viewModel(repository)

            model.onEvent(SessionListScreenToViewModelEvents.Create("Garage"))

            assertEquals(
                listOf("Garage"),
                model.state.value.sessions
                    .map { it.name },
            )
        }

    @Test
    fun `a created session is anchored to the marker the screen was opened with`() =
        runTest {
            // The frame every measurement is expressed in. A session created against the wrong
            // marker is not merely mislabelled, it is unreadable.
            val repository = FakeRepository()

            viewModel(repository).onEvent(SessionListScreenToViewModelEvents.Create("Garage"))

            assertEquals(
                marker,
                repository.sessions.value
                    .single()
                    .markerId,
            )
        }

    @Test
    fun `renaming a session updates its row`() =
        runTest {
            val repository = FakeRepository()
            repository.sessions.value = listOf(session("a", name = "Before"))
            val model = viewModel(repository)

            model.onEvent(SessionListScreenToViewModelEvents.Rename("a", "After"))

            assertEquals(
                listOf("After"),
                model.state.value.sessions
                    .map { it.name },
            )
        }

    @Test
    fun `a blank rename is ignored`() =
        runTest {
            val repository = FakeRepository()
            repository.sessions.value = listOf(session("a", name = "Before"))
            val model = viewModel(repository)

            model.onEvent(SessionListScreenToViewModelEvents.Rename("a", "   "))

            assertEquals(
                listOf("Before"),
                model.state.value.sessions
                    .map { it.name },
            )
        }

    @Test
    fun `deleting a session removes its row`() =
        runTest {
            val repository = FakeRepository()
            repository.sessions.value = listOf(session("a"), session("b", name = "Garage"))
            val model = viewModel(repository)

            model.onEvent(SessionListScreenToViewModelEvents.Delete("a"))

            assertEquals(
                listOf("Garage"),
                model.state.value.sessions
                    .map { it.name },
            )
        }

    private fun measured(id: String) =
        MeasuredObject(
            id = ObjectId(id),
            label = "Mug",
            shell = Ellipsoid.EMPTY,
        )
}
