package com.sarim.husk.session.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sarim.husk.ui.theme.HuskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionListContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: SessionListScreenState,
        onEvent: (SessionListScreenToViewModelEvents) -> Unit = {},
        onOpenSession: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            HuskTheme {
                SessionListContent(
                    state = state,
                    onEvent = onEvent,
                    onOpenSession = onOpenSession,
                    onOpenMarkers = {},
                )
            }
        }
    }

    private fun row(
        id: String = "a",
        name: String = "Kitchen",
        objectCount: Int = 0,
    ) = SessionRow(id = id, name = name, objectCount = objectCount, createdAtEpochMillis = 0L)

    @Test
    fun `sessions are listed by name`() {
        setContent(SessionListScreenState(sessions = listOf(row(name = "Kitchen")), isLoading = false))

        composeRule.onNodeWithText("Kitchen").assertIsDisplayed()
    }

    @Test
    fun `an empty list explains itself rather than showing nothing`() {
        // A blank screen reads as a bug. Saying there is nothing yet, and what to do about it, is
        // the difference between an empty state and a broken one.
        setContent(SessionListScreenState(sessions = emptyList(), isLoading = false))

        composeRule.onNodeWithText("No sessions yet. Start one to begin measuring.").assertIsDisplayed()
    }

    @Test
    fun `an empty list is not shown while the store has yet to answer`() {
        // Otherwise every launch flashes "no sessions" before the real ones arrive, which reads as
        // data loss.
        setContent(SessionListScreenState(sessions = emptyList(), isLoading = true))

        composeRule
            .onNodeWithText("No sessions yet. Start one to begin measuring.")
            .assertDoesNotExist()
    }

    @Test
    fun `the create action asks the view model to create`() {
        var event: SessionListScreenToViewModelEvents? = null
        setContent(
            state = SessionListScreenState(sessions = emptyList(), isLoading = false),
            onEvent = { event = it },
        )

        composeRule.onNodeWithText("New session").performClick()

        assertTrue("expected a create event, got $event", event is SessionListScreenToViewModelEvents.Create)
    }

    @Test
    fun `tapping a session opens it`() {
        var opened: String? = null
        setContent(
            state = SessionListScreenState(sessions = listOf(row(id = "a")), isLoading = false),
            onOpenSession = { opened = it },
        )

        composeRule.onNodeWithText("Kitchen").performClick()

        assertEquals("a", opened)
    }

    @Test
    fun `the object count is worded for one and for many`() {
        setContent(
            SessionListScreenState(
                sessions = listOf(row(id = "a", name = "One", objectCount = 1)),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("1 object measured").assertIsDisplayed()
    }
}
