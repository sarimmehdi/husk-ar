package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Every session, newest first.
 *
 * A pass-through today. It exists so the screen depends on the thing it needs rather than on the
 * whole repository, which is what keeps a list screen from quietly gaining the ability to delete.
 */
class ObserveSessionsUseCase(
    private val repository: SessionRepository,
) {
    /** The stream of sessions. */
    operator fun invoke(): Flow<List<Session>> = repository.observeSessions()
}
