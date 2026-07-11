package com.plefy.app.data.mapper

import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.model.InferredColumn

/**
 * Pure, Android-free mapping from an inferred column schema to its persisted EAV row.
 *
 * The mapper never sets a primary key: [ColumnDefEntity.id] is left at its `0` default so Room
 * auto-generates it on insert. The owning sheet is supplied by the caller as [tableId].
 */
object ColumnMapper {

    /**
     * Converts an [InferredColumn] into a [ColumnDefEntity] belonging to [tableId].
     *
     * The zero-based [InferredColumn.index] is stored as `colIndex` (the column `index` is a SQL
     * keyword, so the schema deliberately avoids it), and the [CellType] is serialised via
     * [toStorage].
     */
    fun toEntity(column: InferredColumn, tableId: Long): ColumnDefEntity =
        ColumnDefEntity(
            tableId = tableId,
            colIndex = column.index,
            name = column.name,
            type = column.type.toStorage(),
            format = column.format,
            confidence = column.confidence,
        )

    /** Maps an ordered list of columns for [tableId], preserving order. */
    fun toEntities(columns: List<InferredColumn>, tableId: Long): List<ColumnDefEntity> =
        columns.map { toEntity(it, tableId) }
}

/** Convenience extension mirroring [ColumnMapper.toEntity]. */
fun InferredColumn.toEntity(tableId: Long): ColumnDefEntity = ColumnMapper.toEntity(this, tableId)
