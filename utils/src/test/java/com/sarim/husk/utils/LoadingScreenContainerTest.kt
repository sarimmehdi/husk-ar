package com.sarim.husk.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
internal class LoadingScreenContainerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content renders without overlay when loading is false`() {
        setContent(isLoading = false)

        composeTestRule.onNodeWithText("Screen content").assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(0)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `loading overlay displays progress and intercepts input`() {
        setContent(isLoading = true)

        composeTestRule.onNodeWithText("Screen content").assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(1)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeTestRule.onNode(hasClickAction()).performClick()
    }

    private fun setContent(isLoading: Boolean) {
        composeTestRule.setContent {
            MaterialTheme {
                LoadingScreenContainer(isLoading = isLoading) {
                    Text("Screen content")
                }
            }
        }
    }
}
