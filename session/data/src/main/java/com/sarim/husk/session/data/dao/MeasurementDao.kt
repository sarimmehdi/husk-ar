package com.sarim.husk.session.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sarim.husk.session.data.entity.MeasuredObjectEntity
import com.sarim.husk.session.data.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access for measured objects and the views they were fitted from.
 *
 * The two tables share one DAO because they are written together: an object and its observations
 * are replaced as a unit, and a Room transaction cannot span two DAOs.
 */
@Dao
interface MeasurementDao {
    /** Every object across the given sessions, in capture order. */
    @Query(
        "SELECT * FROM measured_object WHERE sessionId IN (:sessionIds) " +
            "ORDER BY sessionId ASC, sortIndex ASC",
    )
    fun observeObjectsIn(sessionIds: List<String>): Flow<List<MeasuredObjectEntity>>

    /** Every view of the given objects, in capture order. */
    @Query(
        "SELECT * FROM observation WHERE objectId IN (:objectIds) ORDER BY objectId ASC, sortIndex ASC",
    )
    fun observeObservationsOf(objectIds: List<String>): Flow<List<ObservationEntity>>

    /** The position an existing object holds, or null if it is new. */
    @Query("SELECT sortIndex FROM measured_object WHERE id = :id")
    suspend fun sortIndexOf(id: String): Int?

    /** The position after the last object in a session. */
    @Query("SELECT COALESCE(MAX(sortIndex) + 1, 0) FROM measured_object WHERE sessionId = :sessionId")
    suspend fun nextSortIndex(sessionId: String): Int

    /** Removes one object and, by foreign key, its views. */
    @Query("DELETE FROM measured_object WHERE id = :id AND sessionId = :sessionId")
    suspend fun deleteObject(
        sessionId: String,
        id: String,
    )

    /** Inserts or replaces one object row. */
    @Upsert
    suspend fun upsertObject(measured: MeasuredObjectEntity)

    /** Removes every view of an object, so a rewrite cannot leave stale ones behind. */
    @Query("DELETE FROM observation WHERE objectId = :objectId")
    suspend fun deleteObservationsOf(objectId: String)

    /** Inserts views. */
    @Upsert
    suspend fun upsertObservations(observations: List<ObservationEntity>)

    /**
     * Stores an object and its views as one unit.
     *
     * A transaction because the views are deleted and rewritten: a crash between the two would leave
     * an object whose entire capture history had vanished, which is the one thing that cannot be
     * reconstructed from anything else.
     */
    @Transaction
    suspend fun replaceObject(
        measured: MeasuredObjectEntity,
        observations: List<ObservationEntity>,
    ) {
        upsertObject(measured)
        deleteObservationsOf(measured.id)
        upsertObservations(observations)
    }
}
