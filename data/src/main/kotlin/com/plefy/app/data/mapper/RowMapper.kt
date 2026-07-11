package com.plefy.app.data.mapper

import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.model.CellType
import com.plefy.app.model.InferredColumn
import com.plefy.app.model.TypedCell
import java.math.BigDecimal

/**
 * A function that normalises a raw cell string against a target [CellType] into a [TypedCell].
 *
 * This indirection keeps [RowMapper] a pure JVM unit (no `:backend:inference` type is referenced
 * directly), so it can be exhaustively unit-tested on the JVM. Production code passes
 * `CellNormalizer::normalize` from `:backend:inference`.
 */
typealias CellNormalizeFn = (raw: String?, type: CellType) -> TypedCell

/**
 * The pair of typed sort keys derived for a single cell.
 *
 * Exactly one of the two is non-null for a materialised value (or both are null for empty cells),
 * following the contract's sort-value rule. Persisting these alongside the raw display string is
 * what lets later queries `ORDER BY` a column in its natural, typed order.
 */
data class SortValues(val numericSort: Double?, val textSort: String?) {
    companion object {
        /** Both keys absent — used for empty/blank/null cells. */
        val EMPTY = SortValues(null, null)
    }
}

/**
 * The persisted materialisation of one data row: the [RowEntity] plus its [CellEntity] list.
 *
 * The cells carry their resolved `columnId` but a placeholder `rowId` of `0`; the repository fills
 * in the real row id (only known after the [RowEntity] is inserted) via [withRowId].
 */
data class MappedRow(val row: RowEntity, val cells: List<CellEntity>) {
    /** Returns a copy whose cells all point at [rowId]. */
    fun withRowId(rowId: Long): MappedRow =
        copy(row = row, cells = cells.map { it.copy(rowId = rowId) })
}

/**
 * Pure, Android-free mapping from a raw sheet row to its persisted EAV form.
 *
 * The heart of the mapper is [sortValues]: the single, well-tested function that turns a normalised
 * [TypedCell] into the `(numericSort, textSort)` pair the database is ordered by.
 */
object RowMapper {

    /**
     * Derives the typed sort keys for a normalised cell, per the schema contract:
     *
     * - `BOOLEAN`  -> `numericSort` = 1.0 (true) / 0.0 (false)
     * - `INTEGER`  -> `numericSort` = the [Long] as a [Double]
     * - `DECIMAL` / `CURRENCY` -> `numericSort` = the [BigDecimal] as a [Double]
     * - `DATE` / `DATETIME`    -> `numericSort` = epoch-millis [Long] as a [Double]
     * - `TEXT`     -> `textSort` = the value lower-cased
     * - `EMPTY` (or an unexpectedly null typed value) -> both null
     *
     * The switch keys off [TypedCell.type] (the *normalised* type), not the column's inferred type,
     * so a cell that failed coercion — which [com.plefy.app.backend.inference.CellNormalizer]
     * degrades to `TEXT`/`EMPTY` — is sorted as the text/empty it actually is.
     */
    fun sortValues(cell: TypedCell): SortValues = when (cell.type) {
        CellType.BOOLEAN -> when (val b = cell.typed) {
            is Boolean -> SortValues(numericSort = if (b) 1.0 else 0.0, textSort = null)
            else -> SortValues.EMPTY
        }

        CellType.INTEGER,
        CellType.DATE,
        CellType.DATETIME -> when (val n = cell.typed) {
            is Long -> SortValues(numericSort = n.toDouble(), textSort = null)
            is Number -> SortValues(numericSort = n.toDouble(), textSort = null)
            else -> SortValues.EMPTY
        }

        CellType.DECIMAL,
        CellType.CURRENCY -> when (val n = cell.typed) {
            is BigDecimal -> SortValues(numericSort = n.toDouble(), textSort = null)
            is Number -> SortValues(numericSort = n.toDouble(), textSort = null)
            else -> SortValues.EMPTY
        }

        CellType.TEXT -> when (val s = cell.typed) {
            is String -> SortValues(numericSort = null, textSort = s.lowercase())
            else -> SortValues.EMPTY
        }

        CellType.EMPTY -> SortValues.EMPTY
    }

    /**
     * Maps one raw row into a [RowEntity] and its [CellEntity] list.
     *
     * @param rawRow      the source row (its cells are addressed by column [InferredColumn.index])
     * @param tableId     the owning sheet id
     * @param rowIndex    the zero-based position of this row within the sheet's body
     * @param columns     the ordered inferred columns
     * @param columnIds   the persisted `ColumnDefEntity` ids, positionally aligned with [columns]
     * @param normalize   the cell normaliser (typically `CellNormalizer::normalize`)
     * @return a [MappedRow] whose cells carry a placeholder `rowId` of 0
     */
    fun mapRow(
        rawRow: com.plefy.app.model.RawRow,
        tableId: Long,
        rowIndex: Int,
        columns: List<InferredColumn>,
        columnIds: List<Long>,
        normalize: CellNormalizeFn,
    ): MappedRow {
        require(columns.size == columnIds.size) {
            "columns (${columns.size}) and columnIds (${columnIds.size}) must be aligned"
        }
        val row = RowEntity(tableId = tableId, rowIndex = rowIndex)
        val cells = columns.indices.map { i ->
            val column = columns[i]
            val rawValue = rawRow.cells.getOrNull(column.index)
            val typed = normalize(rawValue, column.type)
            val sort = sortValues(typed)
            CellEntity(
                rowId = 0L,
                columnId = columnIds[i],
                rawValue = rawValue,
                numericSort = sort.numericSort,
                textSort = sort.textSort,
                type = typed.type.toStorage(),
            )
        }
        return MappedRow(row, cells)
    }
}
