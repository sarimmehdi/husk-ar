package com.sarim.husk.session.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sarim.husk.session.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/** Room access for session rows themselves, without their contents. */
@Dao
interface SessionDao {
    /** Every session, newest first. Ordering is SQL's job, not the caller's. */
    @Query("SELECT * FROM session ORDER BY createdAtEpochMillis DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    /** One session, or null while none has that id. */
    @Query("SELECT * FROM session WHERE id = :id")
    fun observeSession(id: String): Flow<SessionEntity?>

    /** Inserts or replaces a session row, leaving its contents alone. */
    @Upsert
    suspend fun upsert(session: SessionEntity)

    /** Renames a session. Affects nothing when the id is unknown. */
    @Query("UPDATE session SET name = :name WHERE id = :id")
    suspend fun rename(
        id: String,
        name: String,
    )

    /** Removes a session. Its objects and their views follow by foreign key. */
    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: String)

    /** Whether a session exists, so callers can decline to create one by accident. */
    @Query("SELECT EXISTS(SELECT 1 FROM session WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
