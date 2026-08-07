package com.sarim.husk.utils.errorhandling

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.datastore.core.CorruptionException
import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import java.io.IOException

/** Failures originating in application storage. */
sealed interface DataError : Error {
    /** On-device storage failures. */
    enum class Local : DataError {
        /** The device has no remaining space for the write. */
        DISK_FULL,

        /** The database rejected the requested operation. */
        DATABASE_ERROR,

        /** Stored content could not be decoded. */
        CORRUPTED_DATA,

        /** A filesystem read or write failed. */
        IO_ERROR,

        /** The failure did not match a known category. */
        UNKNOWN,
    }
}

/** Returns a user-facing message for this local storage failure. */
fun DataError.Local.toUiText(): UiText =
    when (this) {
        DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
        DataError.Local.DATABASE_ERROR -> UiText.StringResource(R.string.error_db_failure)
        DataError.Local.CORRUPTED_DATA -> UiText.StringResource(R.string.error_corrupted_data)
        DataError.Local.IO_ERROR -> UiText.StringResource(R.string.error_io_failure)
        DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
    }

/** Returns the typed local storage error corresponding to this exception. */
fun Throwable.toLocalDataError(): DataError.Local =
    when (this) {
        is SQLiteFullException -> DataError.Local.DISK_FULL
        is SQLiteException -> DataError.Local.DATABASE_ERROR
        is CorruptionException -> DataError.Local.CORRUPTED_DATA
        is IOException -> DataError.Local.IO_ERROR
        else -> DataError.Local.UNKNOWN
    }
