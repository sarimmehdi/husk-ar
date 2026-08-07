package com.sarim.husk.utils.flow

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ThrottleFirstByTest {
    @Test
    fun `negative window is rejected when flow is collected`() =
        runTest {
            val exception =
                runCatching {
                    flowOf("value")
                        .throttleFirstBy(windowMillis = -1, keySelector = { it })
                        .toList()
                }.exceptionOrNull()

            assertTrue(exception is IllegalArgumentException)
            assertEquals("windowMillis must not be negative.", exception?.message)
        }

    @Test
    fun `zero window emits repeated values for the same key`() =
        runTest {
            val values =
                flowOf("first", "second")
                    .throttleFirstBy(windowMillis = 0, keySelector = { "shared" })
                    .toList()

            assertEquals(listOf("first", "second"), values)
        }

    @Test
    fun `positive window suppresses repeated keys without suppressing other keys`() =
        runTest {
            val values =
                flowOf(
                    KeyedValue(key = "a", value = 1),
                    KeyedValue(key = "a", value = 2),
                    KeyedValue(key = "b", value = 3),
                ).throttleFirstBy(
                    windowMillis = Long.MAX_VALUE,
                    keySelector = KeyedValue::key,
                ).toList()

            assertEquals(
                listOf(
                    KeyedValue(key = "a", value = 1),
                    KeyedValue(key = "b", value = 3),
                ),
                values,
            )
        }
}

private data class KeyedValue(
    val key: String,
    val value: Int,
)
