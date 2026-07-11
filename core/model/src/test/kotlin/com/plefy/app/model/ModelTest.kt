package com.plefy.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Deterministic, offline unit tests for the :core:model value types.
 *
 * Covers enum membership/ordering and data-class equality/copy semantics.
 */
class ModelTest {

    // --- Enums -------------------------------------------------------------

    @Test
    fun cellType_hasExpectedValuesInOrder() {
        val expected = listOf(
            "EMPTY", "BOOLEAN", "INTEGER", "DECIMAL",
            "CURRENCY", "DATE", "DATETIME", "TEXT"
        )
        assertEquals(expected, CellType.values().map { it.name })
        assertEquals(expected.size, CellType.values().size)
    }

    @Test
    fun cellType_ordinalsAreStable() {
        assertEquals(0, CellType.EMPTY.ordinal)
        assertEquals(CellType.TEXT.ordinal, CellType.values().last().ordinal)
        assertEquals(CellType.INTEGER, CellType.valueOf("INTEGER"))
    }

    @Test
    fun sortDirection_hasExactlyAscAndDesc() {
        assertEquals(listOf("ASC", "DESC"), SortDirection.values().map { it.name })
        assertEquals(SortDirection.ASC, SortDirection.valueOf("ASC"))
        assertEquals(SortDirection.DESC, SortDirection.valueOf("DESC"))
    }


    // --- InferredColumn: equality & copy -----------------------------------

    @Test
    fun inferredColumn_equalityAndCopy() {
        val col = InferredColumn(
            index = 0,
            name = "Amount",
            type = CellType.CURRENCY,
            format = "$#,##0.00",
            confidence = 0.95
        )
        assertEquals(col, col.copy())
        assertEquals(col.hashCode(), col.copy().hashCode())

        val retyped = col.copy(type = CellType.DECIMAL, confidence = 1.0)
        assertEquals(CellType.DECIMAL, retyped.type)
        assertEquals(1.0, retyped.confidence, 0.0)
        assertEquals("Amount", retyped.name)
        assertNotEquals(col, retyped)
    }

    @Test
    fun inferredColumn_supportsNullFormat() {
        val col = InferredColumn(1, "Notes", CellType.TEXT, format = null, confidence = 0.5)
        assertNull(col.format)
        assertEquals(col, InferredColumn(1, "Notes", CellType.TEXT, null, 0.5))
    }

    // --- TypedCell: equality across typed payloads -------------------------

    @Test
    fun typedCell_equalityAndCopy() {
        val cell = TypedCell(raw = "42", typed = 42L, type = CellType.INTEGER)
        assertEquals(cell, TypedCell("42", 42L, CellType.INTEGER))

        val asDecimal = cell.copy(typed = BigDecimal("42"), type = CellType.DECIMAL)
        assertEquals(CellType.DECIMAL, asDecimal.type)
        assertEquals(BigDecimal("42"), asDecimal.typed)
        assertNotEquals(cell, asDecimal)
    }

    @Test
    fun typedCell_emptyHasNullPayload() {
        val empty = TypedCell(raw = null, typed = null, type = CellType.EMPTY)
        assertNull(empty.raw)
        assertNull(empty.typed)
        assertEquals(CellType.EMPTY, empty.type)
    }

    // --- RawRow: composite equality ----------------------------------------

    @Test
    fun rawRow_preservesNullCellsAndEquality() {
        val row = RawRow(index = 0, cells = listOf("a", null, "c"))
        assertEquals(row, RawRow(0, listOf("a", null, "c")))
        assertNotEquals(row, RawRow(0, listOf("a", "", "c")))
        assertNull(row.cells[1])
    }

    // --- toString sanity (non-brittle, just presence) ----------------------

    @Test
    fun dataClassToStringIncludesFieldValues() {
        val col = InferredColumn(7, "Amount", CellType.CURRENCY, null, 1.0)
        assertTrue(col.toString().contains("index=7"))
        assertTrue(col.toString().contains("CURRENCY"))
    }

    @Test
    fun sameInstanceIsReferenceEqual() {
        val col = InferredColumn(0, "A", CellType.TEXT, null, 1.0)
        assertSame(col, col)
    }
}
