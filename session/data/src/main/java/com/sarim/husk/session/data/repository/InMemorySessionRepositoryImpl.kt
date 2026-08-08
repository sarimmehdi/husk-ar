package com.sarim.husk.session.data.repository

import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Process-local [SessionRepository]: everything lives in one immutable snapshot behind a state flow.
 *
 * This is what the app runs on until the database arrives, and it stays afterwards as the store the
 * tests above the repository use — nothing here touches a file or a framework, so it needs no
 * instrumentation and no cleanup between tests.
 *
 * Sessions are held keyed rather than as a list, so replacing one by id cannot accidentally append.
 * Ordering is applied on the way out instead of on insert, which keeps it a property of the contract
 * rather than of the order things happened to arrive in.
 */
class InMemorySessionRepositoryImpl : SessionRepository {
    private val sessions = MutableStateFlow<Map<SessionId, Session>>(emptyMap())

    override fun observeSessions(): Flow<List<Session>> =
        sessions
            .map { stored -> stored.values.sortedByDescending { it.createdAt } }
            .distinctUntilChanged()

    override fun observeSession(id: SessionId): Flow<Session?> = sessions.map { it[id] }.distinctUntilChanged()

    override suspend fun putSession(session: Session) {
        sessions.update { it + (session.id to session) }
    }

    override suspend fun renameSession(
        id: SessionId,
        name: String,
    ) {
        mutate(id) { it.copy(name = name) }
    }

    override suspend fun deleteSession(id: SessionId) {
        // Objects hang off the session they belong to, so removing the entry takes them with it and
        // nothing can survive to reappear under a later session with the same id.
        sessions.update { it - id }
    }

    override suspend fun putObject(
        sessionId: SessionId,
        measured: MeasuredObject,
    ) {
        mutate(sessionId) { session ->
            val existing = session.objects.indexOfFirst { it.id == measured.id }
            val updated =
                if (existing >= 0) {
                    // Replaced in place rather than moved to the end, so a re-solve does not
                    // reshuffle the list a person is looking at.
                    session.objects.toMutableList().apply { set(existing, measured) }
                } else {
                    session.objects + measured
                }
            session.copy(objects = updated)
        }
    }

    override suspend fun deleteObject(
        sessionId: SessionId,
        objectId: ObjectId,
    ) {
        mutate(sessionId) { session ->
            session.copy(objects = session.objects.filterNot { it.id == objectId })
        }
    }

    /** Applies [change] to a stored session, doing nothing when [id] names no session. */
    private fun mutate(
        id: SessionId,
        change: (Session) -> Session,
    ) {
        sessions.update { stored ->
            val existing = stored[id] ?: return@update stored
            stored + (id to change(existing))
        }
    }
}
