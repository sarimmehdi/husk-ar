package com.sarim.husk.utils.errorui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sarim.husk.utils.UiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
internal class LoadableScreenContainerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content is shown without an error`() {
        setContent()

        composeTestRule.onNodeWithText(CONTENT).assertIsDisplayed()
        composeTestRule.onNodeWithText(RETRY, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `load error replaces content and retries`() {
        var retryCount = 0
        setContent(
            loadError = UiText.DynamicString(LOAD_ERROR),
            onRetry = { retryCount++ },
        )

        composeTestRule.onNodeWithText(CONTENT).assertDoesNotExist()
        composeTestRule.onNodeWithText(LOAD_ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithText(RETRY).performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun `refresh error keeps content visible and retries`() {
        var retryCount = 0
        setContent(
            refreshError = UiText.DynamicString(REFRESH_ERROR),
            onRetry = { retryCount++ },
        )

        composeTestRule.onNodeWithText(CONTENT).assertIsDisplayed()
        composeTestRule.onNodeWithText(REFRESH_ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithText(RETRY).performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun `persistent message is shown below refresh priority`() {
        setContent(
            persistentMessage =
                PersistentMessage(
                    message = UiText.DynamicString(PERSISTENT_MESSAGE),
                    severity = MessageSeverity.INFO,
                ),
        )

        composeTestRule.onNodeWithText(CONTENT).assertIsDisplayed()
        composeTestRule.onNodeWithText(PERSISTENT_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(INFORMATION).assertIsDisplayed()
    }

    @Test
    fun `warning message exposes its severity`() {
        setContent(
            persistentMessage =
                PersistentMessage(
                    message = UiText.DynamicString(PERSISTENT_MESSAGE),
                    severity = MessageSeverity.WARNING,
                ),
        )

        composeTestRule.onNodeWithContentDescription(WARNING).assertIsDisplayed()
    }

    @Test
    fun `loading keeps content and shows progress`() {
        setContent(isLoading = true)

        composeTestRule.onNodeWithText(CONTENT).assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(1)
    }

    private fun setContent(
        isLoading: Boolean = false,
        loadError: UiText? = null,
        refreshError: UiText? = null,
        persistentMessage: PersistentMessage? = null,
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                LoadableScreenContainer(
                    state =
                        LoadableScreenUiState(
                            isLoading = isLoading,
                            loadError = loadError,
                            refreshError = refreshError,
                            persistentMessage = persistentMessage,
                        ),
                    onRetry = onRetry,
                ) {
                    Text(CONTENT)
                }
            }
        }
    }

    private companion object {
        const val CONTENT = "Screen content"
        const val ERROR = "Error"
        const val INFORMATION = "Information"
        const val LOAD_ERROR = "Could not load"
        const val PERSISTENT_MESSAGE = "Work profile monitoring information"
        const val REFRESH_ERROR = "Could not refresh"
        const val RETRY = "Retry"
        const val WARNING = "Warning"
    }
}
