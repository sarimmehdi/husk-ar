package com.sarim.husk.utils.errorhandling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ResultMapTest {
    @Test
    fun `map preserves error state and skips transform`() {
        val productionError = DataError.Local.UNKNOWN
        val result: Result<Int, Error> = Result.Error(productionError)

        var wasTransformCalled = false
        val mapped =
            result.map {
                wasTransformCalled = true
                it + 10
            }

        assertTrue(mapped is Result.Error)
        assertEquals(productionError, (mapped as Result.Error).error)

        assertEquals(false, wasTransformCalled)
    }

    @Test
    fun `map correctly transforms success data`() {
        val result: Result<Int, Error> = Result.Success(5)

        val mapped = result.map { it * 2 }

        assertTrue(mapped is Result.Success)
        assertEquals(10, (mapped as Result.Success).data)
    }

    @Test
    fun `asSuccess extension correctly wraps value`() {
        val value = "Test"
        val result = value.asSuccess<String, Error>()

        assertTrue(result is Result.Success)
        assertEquals("Test", (result as Result.Success).data)
    }
}
