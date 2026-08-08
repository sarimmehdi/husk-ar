package com.sarim.husk.marker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarim.husk.marker.data.database.MarkerDatabase
import com.sarim.husk.marker.domain.repository.MarkerRepository
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/** The Room store against the same contract the in-memory one passes. */
@RunWith(AndroidJUnit4::class)
class RoomMarkerRepositoryImplTest : MarkerRepositoryContract() {
    private lateinit var database: MarkerDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MarkerDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    override fun createRepository(): MarkerRepository = RoomMarkerRepositoryImpl(database.markerDao())
}
