package com.sarim.husk.session.domain.repository

import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * Storage-independent contract for sessions and what was measured in them.
 *
 * Mutation is granular rather than whole-aggregate. Rewriting an entire session to record one
 * capture would be wasteful against a database and, worse, would make two people editing different
 * objects clobber each other; each call here touches only what it names.
 *
 * Every method is total. Naming something that does not exist is not an error and never creates it —
 * a screen can act on a session the moment before it is deleted elsewhere, and a repository that
 * threw would turn that race into a crash.
 */
interface SessionRepository {
    /** Every session, most recently created first. */
    fun observeSessions(): Flow<List<Session>>

    /** One session, emitting null while no session with that id exists. */
    fun observeSession(id: SessionId): Flow<Session?>

    /** Stores [session], replacing any session that already has its id. */
    suspend fun putSession(session: Session)

    /** Renames a session. Does nothing when [id] is unknown. */
    suspend fun renameSession(
        id: SessionId,
        name: String,
    )

    /** Removes a session and everything measured in it. Does nothing when [id] is unknown. */
    suspend fun deleteSession(id: SessionId)

    /**
     * Stores [measured] in a session, replacing any object that already has its id.
     *
     * Does nothing when [sessionId] is unknown, rather than creating a session to hold it.
     */
    suspend fun putObject(
        sessionId: SessionId,
        measured: MeasuredObject,
    )

    /** Removes a measured object. Does nothing when either id is unknown. */
    suspend fun deleteObject(
        sessionId: SessionId,
        objectId: ObjectId,
    )
}
