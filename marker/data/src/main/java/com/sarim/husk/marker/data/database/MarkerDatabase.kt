package com.sarim.husk.marker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sarim.husk.marker.data.dao.MarkerDao
import com.sarim.husk.marker.data.entity.MarkerEntity

/**
 * The marker library's database.
 *
 * Separate from the session database so this feature owns its storage and neither module has to
 * depend on the other's schema.
 */
@Database(entities = [MarkerEntity::class], version = 1, exportSchema = true)
abstract class MarkerDatabase : RoomDatabase() {
    /** Access to the marker library. */
    abstract fun markerDao(): MarkerDao
}
