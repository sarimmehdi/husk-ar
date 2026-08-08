package com.sarim.husk.session.domain.usecase

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository

/**
 * Begins measuring something new in a session.
 *
 * The object exists before any view of it does, so that the first capture has somewhere to go and so
 * that an abandoned attempt is visible rather than silently absent.
 */
class StartObjectUseCase(
    private val repository: SessionRepository,
    private val newId: () -> String,
) {
    /**
     * Creates an unmeasured object called [label] in [sessionId] and returns it.
     *
     * Its shell is [Ellipsoid.EMPTY] until three separated views exist, which draws as nothing
     * rather than as a unit sphere.
     */
    suspend operator fun invoke(
        sessionId: SessionId,
        label: String,
    ): MeasuredObject {
        val measured =
            MeasuredObject(
                id = ObjectId(newId()),
                label = label.trim().ifEmpty { DEFAULT_LABEL },
                shell = Ellipsoid.EMPTY,
            )
        repository.putObject(sessionId, measured)
        return measured
    }

    private companion object {
        /** Better than an empty row in a list of measurements. */
        const val DEFAULT_LABEL = "Object"
    }
}
