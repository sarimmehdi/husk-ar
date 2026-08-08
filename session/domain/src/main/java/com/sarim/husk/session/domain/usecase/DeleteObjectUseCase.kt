package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository

/** Removes one measured object, leaving the rest of the session alone. */
class DeleteObjectUseCase(
    private val repository: SessionRepository,
) {
    /** Deletes [objectId] from [sessionId]. Deleting something already gone is not an error. */
    suspend operator fun invoke(
        sessionId: SessionId,
        objectId: ObjectId,
    ) {
        repository.deleteObject(sessionId, objectId)
    }
}
