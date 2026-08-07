package com.sarim.husk.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiTextContextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun asString_withContext_resolvesDynamicString() {
        val text = "Direct Value"
        val uiText = UiText.DynamicString(text)

        val result = uiText.asString(context)

        assertEquals(text, result)
    }

    @Test
    fun asString_withContext_resolvesStringResourceWithArgs() {
        val resId = R.string.error_unknown
        val args = arrayOf("Android")
        val uiText = UiText.StringResource(resId, args)

        val result = uiText.asString(context)

        assertEquals("An unexpected error occurred. Please try again.", result)
    }
}
