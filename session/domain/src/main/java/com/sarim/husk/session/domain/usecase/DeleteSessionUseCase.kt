package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository

/** Removes a session and everything measured in it. */
class DeleteSessionUseCase(
    private val repository: SessionRepository,
) {
    /** Deletes [id]. Deleting something already gone is not an error. */
    suspend operator fun invoke(id: SessionId) {
        repository.deleteSession(id)
    }
}
