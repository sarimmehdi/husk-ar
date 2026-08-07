package com.sarim.husk.utils

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiTextComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun asString_composable_resolvesDynamicString() {
        val uiText = UiText.DynamicString("Hello")
        var result = ""
        lateinit var recompose: () -> Unit

        composeTestRule.setContent {
            val recomposeSignal = remember { mutableIntStateOf(0) }
            recomposeSignal.intValue
            recompose = { recomposeSignal.intValue++ }
            result = uiText.asString()
        }

        composeTestRule.runOnIdle { recompose() }
        composeTestRule.waitForIdle()

        assertEquals("Hello", result)
    }

    @Test
    fun asString_composable_resolvesStringResource() {
        val uiText = UiText.StringResource(R.string.error_unknown)
        var result = ""

        composeTestRule.setContent {
            result = uiText.asString()
        }

        assertEquals("An unexpected error occurred. Please try again.", result)
    }
}
