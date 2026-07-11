package com.plefy.app.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppResult]: the [AppResult.Success] / [AppResult.Failure] distinction and its
 * value-based equality. All tests are deterministic and offline.
 */
class AppResultTest {

    @Test
    fun success_carriesValueAndIsMatchable() {
        val result: AppResult<Int> = AppResult.Success(42)
        assertTrue(result is AppResult.Success)
        assertEquals(42, (result as AppResult.Success).value)
    }

    @Test
    fun failure_carriesErrorAndIsMatchable() {
        val error = AppError.ParseError("bad")
        val result: AppResult<Int> = AppResult.Failure(error)
        assertTrue(result is AppResult.Failure)
        assertSame(error, (result as AppResult.Failure).error)
    }

    @Test
    fun equalityIsValueBased() {
        assertEquals(AppResult.Success(1), AppResult.Success(1))
        assertTrue(AppResult.Success(1) != AppResult.Success(2))
        assertEquals(
            AppResult.Failure(AppError.EmptySheet("x")),
            AppResult.Failure(AppError.EmptySheet("x")),
        )
    }
}
