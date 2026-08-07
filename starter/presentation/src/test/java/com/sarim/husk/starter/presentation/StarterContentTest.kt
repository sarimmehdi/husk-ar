package com.sarim.husk.starter.presentation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sarim.husk.ui.theme.HuskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StarterContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `name input and save action expose semantic user behavior`() {
        // Given
        var changedName: String? = null
        var saveCount = 0
        composeRule.setContent {
            HuskTheme {
                StarterContent(
                    uiState = StarterUiState(greeting = "Hello, Android!"),
                    onNameChanged = { changedName = it },
                    onSaveGreeting = { saveCount += 1 },
                )
            }
        }

        // When
        composeRule.onNodeWithText("Name").performTextInput("Ada")
        composeRule.onNodeWithText("Update greeting").performClick()

        // Then
        assertEquals("Ada", changedName)
        assertEquals(1, saveCount)
    }
}
