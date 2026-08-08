package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [SessionRepository] that does nothing, for tests to override one method of.
 *
 * Each use case touches one or two methods, and spelling out the other five every time buries the
 * behaviour under noise. Overriding what a test cares about leaves the rest visibly irrelevant.
 */
internal abstract class NoOpSessionRepository : SessionRepository {
    override fun observeSessions(): Flow<List<Session>> = flowOf(emptyList())

    override fun observeSession(id: SessionId): Flow<Session?> = flowOf(null)

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
