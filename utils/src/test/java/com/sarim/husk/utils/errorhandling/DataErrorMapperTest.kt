package com.sarim.husk.utils.errorhandling

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.datastore.core.CorruptionException
import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

internal class DataErrorMapperTest {
    @Test
    fun `DISK_FULL maps to error_disk_full resource`() {
        val error = DataError.Local.DISK_FULL
        val actual = error.toUiText()

        assertTrue(actual is UiText.StringResource)
        val stringRes = actual as UiText.StringResource
        assertEquals(R.string.error_disk_full, stringRes.resId)
    }

    @Test
    fun `DATABASE_ERROR maps to error_db_failure resource`() {
        val error = DataError.Local.DATABASE_ERROR
        val actual = error.toUiText()

        assertTrue(actual is UiText.StringResource)
        val stringRes = actual as UiText.StringResource
        assertEquals(R.string.error_db_failure, stringRes.resId)
    }

    @Test
    fun `UNKNOWN maps to error_unknown resource`() {
        val error = DataError.Local.UNKNOWN
        val actual = error.toUiText()

        assertTrue(actual is UiText.StringResource)
        val stringRes = actual as UiText.StringResource
        assertEquals(R.string.error_unknown, stringRes.resId)
    }

    @Test
    fun `new persistence errors map to their resources`() {
        assertEquals(
            R.string.error_corrupted_data,
            (DataError.Local.CORRUPTED_DATA.toUiText() as UiText.StringResource).resId,
        )
        assertEquals(
            R.string.error_io_failure,
            (DataError.Local.IO_ERROR.toUiText() as UiText.StringResource).resId,
        )
    }

    @Test
    fun `local storage exceptions map to expected data errors`() {
        val cases =
            listOf(
                SQLiteFullException("full") to DataError.Local.DISK_FULL,
                SQLiteException("database") to DataError.Local.DATABASE_ERROR,
                CorruptionException("corrupt") to DataError.Local.CORRUPTED_DATA,
                IOException("io") to DataError.Local.IO_ERROR,
                IllegalStateException("unexpected") to DataError.Local.UNKNOWN,
            )

        cases.forEach { (throwable, expectedError) ->
            assertEquals(expectedError, throwable.toLocalDataError())
        }
    }
}
