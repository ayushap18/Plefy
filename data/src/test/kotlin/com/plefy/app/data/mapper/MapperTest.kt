package com.plefy.app.data.mapper

import com.plefy.app.model.CellType
import com.plefy.app.model.InferredColumn
import com.plefy.app.model.RawRow
import com.plefy.app.model.TypedCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure JVM unit tests for the mappers. No Android or Room dependency, so these run on the plain
 * JVM test source set and pin down the sort-value contract that later typed `ORDER BY` relies on.
 */
class MapperTest {

    // ---- CellTypeMapping ----------------------------------------------------

    @Test
    fun cellType_roundTrips_throughStorage() {
        CellType.values().forEach { type ->
            assertEquals(type, CellTypeMapping.fromStorage(CellTypeMapping.toStorage(type)))
        }
    }

    @Test
    fun cellType_unknownToken_degradesToText() {
        assertEquals(CellType.TEXT, CellTypeMapping.fromStorage("NOT_A_TYPE"))
        assertEquals(CellType.TEXT, CellTypeMapping.fromStorage(null))
        assertEquals(CellType.TEXT, CellTypeMapping.fromStorage(""))
    }

    // ---- ColumnMapper -------------------------------------------------------

    @Test
    fun column_mapsToEntity_withColIndexAndTypeName() {
        val column = InferredColumn(index = 3, name = "Price", type = CellType.CURRENCY, format = "\$#", confidence = 0.9)
        val entity = ColumnMapper.toEntity(column, tableId = 42L)

        assertEquals(42L, entity.tableId)
        assertEquals(3, entity.colIndex)
        assertEquals("Price", entity.name)
        assertEquals("CURRENCY", entity.type)
        assertEquals("\$#", entity.format)
        assertEquals(0.9, entity.confidence, 0.0)
        assertEquals(0L, entity.id) // left for Room to auto-generate
    }

    // ---- RowMapper.sortValues (the contract) --------------------------------

    @Test
    fun sortValues_boolean_mapsToOneOrZero() {
        assertEquals(1.0, RowMapper.sortValues(TypedCell("yes", true, CellType.BOOLEAN)).numericSort)
        assertEquals(0.0, RowMapper.sortValues(TypedCell("no", false, CellType.BOOLEAN)).numericSort)
        assertNull(RowMapper.sortValues(TypedCell("yes", true, CellType.BOOLEAN)).textSort)
    }

    @Test
    fun sortValues_integer_mapsToDouble() {
        val sv = RowMapper.sortValues(TypedCell("17", 17L, CellType.INTEGER))
        assertEquals(17.0, sv.numericSort)
        assertNull(sv.textSort)
    }

    @Test
    fun sortValues_decimalAndCurrency_mapBigDecimalToDouble() {
        val dec = RowMapper.sortValues(TypedCell("3.5", BigDecimal("3.5"), CellType.DECIMAL))
        val cur = RowMapper.sortValues(TypedCell("\$3.50", BigDecimal("3.50"), CellType.CURRENCY))
        assertEquals(3.5, dec.numericSort)
        assertEquals(3.5, cur.numericSort)
        assertNull(dec.textSort)
    }

    @Test
    fun sortValues_dateAndDatetime_useEpochMillis() {
        val date = RowMapper.sortValues(TypedCell("2020-01-01", 1_577_836_800_000L, CellType.DATE))
        val dt = RowMapper.sortValues(TypedCell("2020-01-01T00:00", 1_577_836_800_000L, CellType.DATETIME))
        assertEquals(1_577_836_800_000.0, date.numericSort)
        assertEquals(1_577_836_800_000.0, dt.numericSort)
    }

    @Test
    fun sortValues_text_lowercasesForSort() {
        val sv = RowMapper.sortValues(TypedCell("HeLLo", "HeLLo", CellType.TEXT))
        assertEquals("hello", sv.textSort)
        assertNull(sv.numericSort)
    }

    @Test
    fun sortValues_empty_isBothNull() {
        val sv = RowMapper.sortValues(TypedCell(null, null, CellType.EMPTY))
        assertNull(sv.numericSort)
        assertNull(sv.textSort)
    }

    // ---- RowMapper.mapRow ---------------------------------------------------

    @Test
    fun mapRow_buildsCellsAlignedToColumns_withRawAndSortValues() {
        val columns = listOf(
            InferredColumn(0, "name", CellType.TEXT, null, 1.0),
            InferredColumn(1, "age", CellType.INTEGER, null, 1.0),
        )
        val columnIds = listOf(100L, 200L)
        val raw = RawRow(index = 5, cells = listOf("Ada", "36"))

        // A trivial normaliser mirroring the real one's canonical typed values.
        val normalize: CellNormalizeFn = { value, type ->
            when (type) {
                CellType.INTEGER -> TypedCell(value, value?.toLong(), CellType.INTEGER)
                else -> TypedCell(value, value, CellType.TEXT)
            }
        }

        val mapped = RowMapper.mapRow(raw, tableId = 7L, rowIndex = 2, columns, columnIds, normalize)

        assertEquals(7L, mapped.row.tableId)
        assertEquals(2, mapped.row.rowIndex)
        assertEquals(2, mapped.cells.size)

        val nameCell = mapped.cells[0]
        assertEquals(100L, nameCell.columnId)
        assertEquals("Ada", nameCell.rawValue)
        assertEquals("ada", nameCell.textSort)
        assertNull(nameCell.numericSort)
        assertEquals("TEXT", nameCell.type)

        val ageCell = mapped.cells[1]
        assertEquals(200L, ageCell.columnId)
        assertEquals("36", ageCell.rawValue)
        assertEquals(36.0, ageCell.numericSort)
        assertNull(ageCell.textSort)
        assertEquals(0L, ageCell.rowId) // placeholder until the row is inserted
    }

    @Test
    fun mappedRow_withRowId_stampsEveryCell() {
        val columns = listOf(InferredColumn(0, "c", CellType.TEXT, null, 1.0))
        val normalize: CellNormalizeFn = { v, _ -> TypedCell(v, v, CellType.TEXT) }
        val mapped = RowMapper.mapRow(RawRow(0, listOf("x")), 1L, 0, columns, listOf(9L), normalize)

        val stamped = mapped.withRowId(555L)
        assertEquals(555L, stamped.cells.single().rowId)
    }
}
