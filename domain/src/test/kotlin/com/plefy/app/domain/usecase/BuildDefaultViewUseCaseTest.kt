package com.plefy.app.domain.usecase

import com.plefy.app.domain.query.GroupSpec
import com.plefy.app.domain.query.QuerySort
import com.plefy.app.model.CellType
import com.plefy.app.model.InferredColumn
import com.plefy.app.model.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the smart default-view heuristics. */
class BuildDefaultViewUseCaseTest {

    private val useCase = BuildDefaultViewUseCase()

    private fun binding(id: Long, type: CellType, confidence: Double = 0.9, index: Int = 0) =
        ColumnBinding(id, InferredColumn(index, "c$id", type, null, confidence))

    @Test
    fun `no columns yields unsorted ungrouped spec`() {
        val spec = useCase(tableId = 1, columns = emptyList())
        assertEquals(1L, spec.tableId)
        assertTrue(spec.sorts.isEmpty())
        assertNull(spec.group)
    }

    @Test
    fun `first date column sorts descending`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(
                binding(10, CellType.INTEGER),
                binding(11, CellType.DATETIME),
                binding(12, CellType.DATE)
            )
        )
        assertEquals(QuerySort(11, CellType.DATETIME, SortDirection.DESC), spec.sorts.single())
    }

    @Test
    fun `numeric column sorts ascending when no date column`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(
                binding(20, CellType.TEXT, confidence = 0.1),
                binding(21, CellType.CURRENCY)
            )
        )
        assertEquals(QuerySort(21, CellType.CURRENCY, SortDirection.ASC), spec.sorts.single())
    }

    @Test
    fun `falls back to first column ascending`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(binding(30, CellType.BOOLEAN, confidence = 0.1))
        )
        assertEquals(QuerySort(30, CellType.BOOLEAN, SortDirection.ASC), spec.sorts.single())
    }

    @Test
    fun `text-only columns fall back to sorting the first column ascending`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(
                binding(60, CellType.TEXT, confidence = 0.3),
                binding(61, CellType.TEXT, confidence = 0.2)
            )
        )
        assertEquals(QuerySort(60, CellType.TEXT, SortDirection.ASC), spec.sorts.single())
        // Low confidence on both, so no grouping.
        assertNull(spec.group)
    }

    @Test
    fun `high-confidence text column is grouped`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(
                binding(40, CellType.INTEGER),
                binding(41, CellType.TEXT, confidence = 0.95)
            )
        )
        assertEquals(GroupSpec(41), spec.group)
    }

    @Test
    fun `low-confidence text column is not grouped`() {
        val spec = useCase(
            tableId = 1,
            columns = listOf(binding(50, CellType.TEXT, confidence = 0.5))
        )
        assertNull(spec.group)
    }
}
