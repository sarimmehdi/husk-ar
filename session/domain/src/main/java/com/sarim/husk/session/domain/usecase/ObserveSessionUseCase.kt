package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

/** One session and everything measured in it, emitting null once it is gone. */
class ObserveSessionUseCase(
    private val repository: SessionRepository,
) {
    /** The stream for [id]. */
    operator fun invoke(id: SessionId): Flow<Session?> = repository.observeSession(id)
}
