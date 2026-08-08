package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreateSessionUseCaseTest {
    private val marker = MarkerId("marker-1")

    private fun useCase(
        repository: RecordingSessionRepository = RecordingSessionRepository(),
        ids: Iterator<String> = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator(),
        now: Instant = Instant.parse("2026-08-08T10:15:00Z"),
    ) = CreateSessionUseCase(
        repository = repository,
        newId = { ids.next() },
        clock = { now },
    )

    @Test
    fun `a created session is stored`() =
        runTest {
            val repository = RecordingSessionRepository()

            val created = useCase(repository)("Kitchen", marker)

            assertEquals(listOf(created), repository.stored)
        }

    @Test
    fun `the session carries the supplied name and marker`() =
        runTest {
            val created = useCase()("Kitchen", marker)

            assertEquals("Kitchen", created.name)
            assertEquals(marker, created.markerId)
        }

    @Test
    fun `creation time comes from the clock rather than the wall`() =
        runTest {
            // Injected so the ordering the repository promises can be tested at all. Reading
            // Instant.now here would make any test of newest-first ordering depend on how fast the
            // machine ran.
            val created = useCase(now = Instant.parse("2026-01-02T03:04:05Z"))("Kitchen", marker)

            assertEquals(Instant.parse("2026-01-02T03:04:05Z"), created.createdAt)
        }

    @Test
    fun `each session gets its own id`() =
        runTest {
            val useCase = useCase()

            val first = useCase("One", marker)
            val second = useCase("Two", marker)

            assertNotEquals(first.id, second.id)
        }

    @Test
    fun `a new session starts with nothing measured in it`() =
        runTest {
            val created = useCase()("Kitchen", marker)

            assertEquals(emptyList<Nothing>(), created.objects)
        }

    @Test
    fun `a blank name becomes a dated default rather than an empty title`() =
        runTest {
            // A session list full of untitled rows is unusable, and people skip the naming step. The
            // date is the one thing always known about a session at the moment it is created.
            val created =
                useCase(now = Instant.parse("2026-08-08T10:15:00Z"))("   ", marker)

            assertTrue("expected a dated default, got '${created.name}'", created.name.isNotBlank())
            assertTrue(
                "expected the creation date in the default name, got '${created.name}'",
                created.name.contains("2026-08-08"),
            )
        }

    @Test
    fun `a name is trimmed`() =
        runTest {
            val created = useCase()("  Kitchen  ", marker)

            assertEquals("Kitchen", created.name)
        }

    @Test
    fun `the created session is returned so the caller can navigate straight into it`() =
        runTest {
            val repository = RecordingSessionRepository()

            val created = useCase(repository)("Kitchen", marker)

            assertEquals(created.id, repository.stored.single().id)
            assertTrue(created.id.value.isNotBlank())
        }

    @Test
    fun `ids come from the generator rather than being invented`() =
        runTest {
            val created = useCase(ids = listOf("chosen").iterator())("Kitchen", marker)

            assertEquals(SessionId("chosen"), created.id)
        }
}

/** Captures what was stored, so the use case can be tested without a repository implementation. */
internal class RecordingSessionRepository : NoOpSessionRepository() {
    val stored = mutableListOf<Session>()

    override suspend fun putSession(session: Session) {
        stored += session
    }
}
