package com.sarim.husk.utils.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Emits the first value for each selected key and suppresses that key for a window.
 *
 * @param windowMillis Duration for which an emitted key remains suppressed.
 * @param keySelector Extracts the independently throttled key from each value.
 */
fun <T, Key> Flow<T>.throttleFirstBy(
    windowMillis: Long,
    keySelector: (T) -> Key,
): Flow<T> =
    flow {
        require(windowMillis >= 0) {
            "windowMillis must not be negative."
        }

        val lastEmissions = mutableMapOf<Key, TimeMark>()

        collect { value ->
            val key = keySelector(value)
            val previousEmission = lastEmissions[key]

            if (
                previousEmission == null ||
                previousEmission.elapsedNow() >= windowMillis.milliseconds
            ) {
                lastEmissions[key] = TimeSource.Monotonic.markNow()
                emit(value)
            }
        }
    }
