package com.sarim.husk.session.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the hand-adjusted flag to measured objects.
 *
 * Written rather than skipped even though nothing has shipped, because a destructive fallback would
 * silently throw away every measurement on a phone that already had the app, and the only person
 * that could happen to is the one testing it.
 *
 * Existing rows default to false: whatever is already stored came from the solver.
 */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE measured_object ADD COLUMN isHandAdjusted INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
