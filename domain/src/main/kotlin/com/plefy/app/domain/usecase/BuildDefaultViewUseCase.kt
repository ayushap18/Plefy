package com.plefy.app.domain.usecase

import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.domain.query.GroupSpec
import com.plefy.app.domain.query.QuerySort
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.model.CellType
import com.plefy.app.model.InferredColumn
import com.plefy.app.model.SortDirection

/**
 * Binds an [InferredColumn] (whose [InferredColumn.index] is a sheet *position*) to the real
 * persisted [com.plefy.app.database.entity.ColumnDefEntity.id] it was stored as.
 *
 * [BuildDefaultViewUseCase] needs the persisted `columnId`s (not sheet positions) to build a
 * [QuerySpec], but it must stay a pure function; the caller resolves the ids and passes them in via
 * these bindings.
 */
data class ColumnBinding(val columnId: Long, val column: InferredColumn)

/**
 * Produces a sensible default [QuerySpec] to show immediately after a sheet is imported — a
 * "first view" the user can then refine. **Pure and deterministic** (no I/O), so it is trivially
 * unit-testable.
 *
 * ## Default sort
 * 1. the first `DATE`/`DATETIME` column, descending (most-recent first); else
 * 2. the first numeric column (`INTEGER`/`DECIMAL`/`CURRENCY`), ascending; else
 * 3. the first column of any type, ascending.
 *
 * ## Default grouping
 * The first `TEXT` column whose inference [confidence][InferredColumn.confidence] is at least
 * [HIGH_CONFIDENCE] is treated as a low-cardinality category and used as the group column. This is
 * a heuristic — high confidence on a text column typically indicates a constrained/enumerated
 * field rather than free text. When no such column exists, the view is ungrouped.
 *
 * With no columns at all, the result is an unsorted, ungrouped spec over [tableId].
 *
 * @param tableId the persisted table to query.
 * @param columns the table's columns bound to their persisted ids, in display order.
 */
class BuildDefaultViewUseCase {

    /**
     * Convenience overload that builds the default view directly from the persisted
     * [ColumnDefEntity] rows of a table — the shape the presentation layer actually holds.
     *
     * Each entity's [ColumnDefEntity.type] string is mapped back to its [CellType], and its
     * [ColumnDefEntity.id] becomes the binding's `columnId`, so the produced [QuerySpec] references
     * the real persisted column ids (resolving the id-vs-position caveat documented on
     * [ColumnBinding]). Columns are used in the order supplied.
     *
     * @param tableId the persisted table to query.
     * @param columns the table's persisted column definitions, in display order.
     */
    fun build(tableId: Long, columns: List<ColumnDefEntity>): QuerySpec =
        invoke(
            tableId,
            columns.map { column ->
                ColumnBinding(
                    columnId = column.id,
                    column = InferredColumn(
                        index = column.colIndex,
                        name = column.name,
                        type = CellType.valueOf(column.type),
                        format = column.format,
                        confidence = column.confidence
                    )
                )
            }
        )

    operator fun invoke(tableId: Long, columns: List<ColumnBinding>): QuerySpec {
        if (columns.isEmpty()) return QuerySpec(tableId = tableId)

        val sort = chooseSort(columns)
        val group = chooseGroup(columns)

        return QuerySpec(
            tableId = tableId,
            sorts = if (sort != null) listOf(sort) else emptyList(),
            group = group
        )
    }

    private fun chooseSort(columns: List<ColumnBinding>): QuerySort? {
        columns.firstOrNull { it.column.type == CellType.DATE || it.column.type == CellType.DATETIME }
            ?.let { return QuerySort(it.columnId, it.column.type, SortDirection.DESC) }

        columns.firstOrNull { it.column.type in NUMERIC_TYPES }
            ?.let { return QuerySort(it.columnId, it.column.type, SortDirection.ASC) }

        val first = columns.first()
        return QuerySort(first.columnId, first.column.type, SortDirection.ASC)
    }

    private fun chooseGroup(columns: List<ColumnBinding>): GroupSpec? =
        columns.firstOrNull {
            it.column.type == CellType.TEXT && it.column.confidence >= HIGH_CONFIDENCE
        }?.let { GroupSpec(it.columnId) }

    companion object {
        /** Confidence at/above which a TEXT column is assumed low-cardinality enough to group by. */
        const val HIGH_CONFIDENCE: Double = 0.8

        private val NUMERIC_TYPES = setOf(
            CellType.INTEGER,
            CellType.DECIMAL,
            CellType.CURRENCY
        )
    }
}
