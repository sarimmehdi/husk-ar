package com.sarim.husk.session.data.repository

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Everything a [SessionRepository] must do, independent of how it stores anything.
 *
 * Written once and inherited by each implementation. The in-memory store passes it today and the
 * database has to pass the same tests unchanged — which is what stops the two from quietly
 * disagreeing about ordering, replacement, or what happens when something is missing. Those are
 * exactly the behaviours that are obvious in a map and easy to get wrong in SQL.
 */
abstract class SessionRepositoryContract {
    protected abstract fun createRepository(): SessionRepository

    private fun session(
        id: String,
        name: String = "Kitchen",
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        objects: List<MeasuredObject> = emptyList(),
    ) = Session(
        id = SessionId(id),
        name = name,
        markerId = MarkerId("marker-1"),
        createdAt = createdAt,
        objects = objects,
    )

    private fun measured(
        id: String,
        label: String = "Mug",
        radius: Double = 0.05,
    ) = MeasuredObject(
        id = ObjectId(id),
        label = label,
        shell =
            Ellipsoid(
                centre = Vector3(0.0, 0.1, 0.0),
                radii = Vector3(radius, radius, radius),
                rotation = Quaternion.IDENTITY,
            ),
    )

    @Test
    fun `a stored session can be observed by id`() =
        runTest {
            val repository = createRepository()
            val expected = session("a")

            repository.putSession(expected)

            assertEquals(expected, repository.observeSession(SessionId("a")).first())
        }

    @Test
    fun `an unknown session observes as null rather than failing`() =
        runTest {
            val repository = createRepository()

            assertNull(repository.observeSession(SessionId("nobody")).first())
        }

    @Test
    fun `sessions are listed most recently created first`() =
        runTest {
            // The list screen shows the newest at the top. A map preserves insertion order and a
            // database preserves none, so the ordering has to be the repository's promise.
            val repository = createRepository()
            repository.putSession(session("old", createdAt = Instant.parse("2026-01-01T00:00:00Z")))
            repository.putSession(session("new", createdAt = Instant.parse("2026-06-01T00:00:00Z")))
            repository.putSession(session("mid", createdAt = Instant.parse("2026-03-01T00:00:00Z")))

            val ids = repository.observeSessions().first().map { it.id.value }

            assertEquals(listOf("new", "mid", "old"), ids)
        }

    @Test
    fun `storing a session with an existing id replaces it rather than duplicating`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a", name = "First"))

            repository.putSession(session("a", name = "Second"))

            val sessions = repository.observeSessions().first()
            assertEquals(1, sessions.size)
            assertEquals("Second", sessions.single().name)
        }

    @Test
    fun `renaming changes the name and leaves everything else alone`() =
        runTest {
            val repository = createRepository()
            val original = session("a", name = "Before", objects = listOf(measured("o1")))
            repository.putSession(original)

            repository.renameSession(SessionId("a"), "After")

            val stored = repository.observeSession(SessionId("a")).first()
            assertEquals(original.copy(name = "After"), stored)
        }

    @Test
    fun `renaming an unknown session does not create one`() =
        runTest {
            // A rename can land just after the session was deleted on another screen. Creating a
            // half-built session from that would be worse than doing nothing.
            val repository = createRepository()

            repository.renameSession(SessionId("ghost"), "Anything")

            assertEquals(emptyList<Session>(), repository.observeSessions().first())
        }

    @Test
    fun `a deleted session observes as null`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a"))

            repository.deleteSession(SessionId("a"))

            assertNull(repository.observeSession(SessionId("a")).first())
        }

    @Test
    fun `a deleted session leaves the list`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a"))
            repository.putSession(session("b"))

            repository.deleteSession(SessionId("a"))

            assertEquals(listOf("b"), repository.observeSessions().first().map { it.id.value })
        }

    @Test
    fun `deleting an unknown session is not an error`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a"))

            repository.deleteSession(SessionId("ghost"))

            assertEquals(1, repository.observeSessions().first().size)
        }

    @Test
    fun `a measured object is stored against its session`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a"))
            val mug = measured("o1")

            repository.putObject(SessionId("a"), mug)

            assertEquals(listOf(mug), repository.observeSession(SessionId("a")).first()?.objects)
        }

    @Test
    fun `storing an object with an existing id replaces it`() =
        runTest {
            // This is how a re-solve lands: same object, better shell. Appending instead would
            // leave two overlapping shells drawn on top of each other.
            val repository = createRepository()
            repository.putSession(session("a"))
            repository.putObject(SessionId("a"), measured("o1", radius = 0.05))

            repository.putObject(SessionId("a"), measured("o1", radius = 0.09))

            val objects =
                repository
                    .observeSession(SessionId("a"))
                    .first()
                    ?.objects
                    .orEmpty()
            assertEquals(1, objects.size)
            assertEquals(
                0.09,
                objects
                    .single()
                    .shell.radii.x,
                1e-9,
            )
        }

    @Test
    fun `storing an object against an unknown session creates nothing`() =
        runTest {
            val repository = createRepository()

            repository.putObject(SessionId("ghost"), measured("o1"))

            assertEquals(emptyList<Session>(), repository.observeSessions().first())
        }

    @Test
    fun `a deleted object leaves its session`() =
        runTest {
            val repository = createRepository()
            repository.putSession(session("a"))
            repository.putObject(SessionId("a"), measured("o1"))
            repository.putObject(SessionId("a"), measured("o2"))

            repository.deleteObject(SessionId("a"), ObjectId("o1"))

            val ids =
                repository
                    .observeSession(SessionId("a"))
                    .first()
                    ?.objects
                    ?.map { it.id.value }
            assertEquals(listOf("o2"), ids)
        }

    @Test
    fun `deleting a session takes its objects with it`() =
        runTest {
            // The failure this guards against is a store that keeps objects in their own table and
            // forgets to cascade. Recreating the session would then resurrect measurements that
            // were supposed to be gone.
            val repository = createRepository()
            repository.putSession(session("a"))
            repository.putObject(SessionId("a"), measured("o1"))
            repository.deleteSession(SessionId("a"))

            repository.putSession(session("a"))

            assertEquals(
                emptyList<MeasuredObject>(),
                repository.observeSession(SessionId("a")).first()?.objects,
            )
        }

    @Test
    fun `observers see a session appear after it is stored`() =
        runTest {
            // Reading once after writing proves storage. This proves the stream is live, which is
            // what every screen depends on.
            val repository = createRepository()
            assertNull(repository.observeSession(SessionId("a")).first())

            repository.putSession(session("a"))

            assertEquals("Kitchen", repository.observeSession(SessionId("a")).first()?.name)
        }
}
