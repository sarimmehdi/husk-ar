package com.sarim.husk.utils.errorhandling

import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

internal class AppExceptionMapperTest {
    @Test
    fun `throwables map to their expected UI messages`() {
        val cases =
            listOf(
                IOException("offline") to R.string.error_connection,
                AppException.InvalidState("invalid") to R.string.error_invalid_state,
                AppException.Unknown("unknown") to R.string.error_unknown,
                IllegalStateException("unexpected") to R.string.error_unknown,
            )

        cases.forEach { (throwable, expectedResource) ->
            val actual = throwable.toUiText()

            assertTrue(actual is UiText.StringResource)
            assertEquals(expectedResource, (actual as UiText.StringResource).resId)
        }
    }
}
