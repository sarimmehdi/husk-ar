package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository

/** Renames a session, refusing to leave it without a title. */
class RenameSessionUseCase(
    private val repository: SessionRepository,
) {
    /**
     * Renames [id] to [name], trimmed.
     *
     * A blank name is ignored rather than applied. Clearing the field and dismissing the dialog is a
     * far more likely accident than a deliberate wish for an untitled session, and the old name is
     * recoverable only if it was never overwritten.
     */
    suspend operator fun invoke(
        id: SessionId,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        repository.renameSession(id, trimmed)
    }
}
