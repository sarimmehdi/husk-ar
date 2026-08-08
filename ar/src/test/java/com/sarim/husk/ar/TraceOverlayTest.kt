package com.sarim.husk.ar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gesture that produces a trace.
 *
 * Runs without a device: a drag is a sequence of touch events, and what the overlay makes of them is
 * ordinary logic. Only what it looks like needs a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TraceOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var changed = mutableListOf<TracedEllipse>()
    private var committed: TracedEllipse? = null

    private fun setContent() {
        changed = mutableListOf()
        committed = null
        composeRule.setContent {
            TraceOverlay(
                onTraceChanged = { changed += it },
                onTraceCommitted = { committed = it },
                strokeColour = Color.Cyan,
                contentDescription = OVERLAY,
                modifier = Modifier.size(400.dp),
            )
        }
    }

    @Test
    fun `dragging a box commits a trace`() {
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(300f, 260f))
            up()
        }

        assertNotNull("a real drag should commit a trace", committed)
    }

    @Test
    fun `the committed trace is centred on the dragged box`() {
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(300f, 260f))
            up()
        }

        val trace = requireNotNull(committed)
        assertEquals(200f, trace.centreX, TOLERANCE)
        assertEquals(180f, trace.centreY, TOLERANCE)
    }

    @Test
    fun `the trace updates while the finger is still down`() {
        // The outline has to follow the finger. Reporting only on release would leave the person
        // dragging against a blank screen and guessing.
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(200f, 200f))
            moveTo(Offset(300f, 260f))
            up()
        }

        assertTrue("expected the trace to be reported during the drag", changed.size >= 2)
    }

    @Test
    fun `a tap without a drag commits nothing`() {
        // Tapping is a miss. Committing it would record a view of nothing and spend one of the
        // three the solver needs.
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(200f, 200f))
            moveTo(Offset(201f, 200f))
            up()
        }

        assertNull("a tap should not commit a trace", committed)
    }

    @Test
    fun `dragging up and to the left works as well as down and to the right`() {
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(300f, 260f))
            moveTo(Offset(100f, 100f))
            up()
        }

        val trace = requireNotNull(committed)
        assertEquals(200f, trace.centreX, TOLERANCE)
        assertEquals(180f, trace.centreY, TOLERANCE)
        assertTrue("axes must stay positive whichever way the drag went", trace.semiMinor > 0f)
    }

    @Test
    fun `a second drag replaces the first rather than adding to it`() {
        // Retracing is the normal way to correct a bad outline, so the previous box must not linger
        // as part of the new one.
        setContent()

        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(0f, 0f))
            moveTo(Offset(100f, 100f))
            up()
        }
        composeRule.onNodeWithContentDescription(OVERLAY).performTouchInput {
            down(Offset(200f, 200f))
            moveTo(Offset(400f, 360f))
            up()
        }

        val trace = requireNotNull(committed)
        assertEquals(300f, trace.centreX, TOLERANCE)
        assertEquals(280f, trace.centreY, TOLERANCE)
    }

    private companion object {
        const val OVERLAY = "Outline the object"
        const val TOLERANCE = 1e-3f
    }
}
