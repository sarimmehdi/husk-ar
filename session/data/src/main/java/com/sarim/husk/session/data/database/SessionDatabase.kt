package com.sarim.husk.session.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sarim.husk.session.data.dao.MeasurementDao
import com.sarim.husk.session.data.dao.SessionDao
import com.sarim.husk.session.data.entity.MeasuredObjectEntity
import com.sarim.husk.session.data.entity.ObservationEntity
import com.sarim.husk.session.data.entity.SessionEntity

/** The database holding measuring sessions. */
@Database(
    entities = [SessionEntity::class, MeasuredObjectEntity::class, ObservationEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SessionDatabase : RoomDatabase() {
    /** Access to session rows. */
    abstract fun sessionDao(): SessionDao

    /** Access to measured objects and their views. */
    abstract fun measurementDao(): MeasurementDao
}
