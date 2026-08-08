package com.sarim.husk.session.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarim.husk.session.data.database.SessionDatabase
import com.sarim.husk.session.domain.repository.SessionRepository
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * The Room store against the same contract the in-memory one passes.
 *
 * Inherited rather than rewritten. Two stores described by two sets of tests would drift, and the
 * behaviours that differ between a map and a database — ordering, replacement, what happens when
 * something is missing — are exactly the ones a separate suite would forget to check.
 */
@RunWith(AndroidJUnit4::class)
class RoomSessionRepositoryImplTest : SessionRepositoryContract() {
    private lateinit var database: SessionDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SessionDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    override fun createRepository(): SessionRepository =
        RoomSessionRepositoryImpl(database.sessionDao(), database.measurementDao())
}
