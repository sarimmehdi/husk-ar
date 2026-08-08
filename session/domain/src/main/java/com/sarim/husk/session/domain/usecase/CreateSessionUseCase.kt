package com.sarim.husk.session.domain.usecase

import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Starts a new session against a marker.
 *
 * Identity and time are supplied rather than taken, which is what makes this testable. Reading the
 * system clock here would leave the repository's newest-first ordering untestable except by
 * sleeping, and generating ids internally would make every assertion about them a guess.
 */
class CreateSessionUseCase(
    private val repository: SessionRepository,
    private val newId: () -> String,
    private val clock: () -> Instant,
) {
    /**
     * Creates and stores a session called [name], anchored to [markerId], and returns it.
     *
     * Returned rather than merely stored so the caller can open it immediately. Looking it back up
     * would mean waiting for the store to emit, and a person who just pressed New expects the
     * session to be there.
     *
     * A blank [name] becomes a dated default. Untitled rows make a session list useless, and naming
     * is the step people skip.
     */
    suspend operator fun invoke(
        name: String,
        markerId: MarkerId,
    ): Session {
        val createdAt = clock()
        val session =
            Session(
                id = SessionId(newId()),
                name = name.trim().ifEmpty { defaultNameFor(createdAt) },
                markerId = markerId,
                createdAt = createdAt,
            )
        repository.putSession(session)
        return session
    }

    private fun defaultNameFor(createdAt: Instant): String =
        DEFAULT_NAME_PREFIX + DATE_FORMAT.format(createdAt.atOffset(ZoneOffset.UTC))

    private companion object {
        const val DEFAULT_NAME_PREFIX = "Session "

        /** The date alone. A time would make the name harder to say and no easier to tell apart. */
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
