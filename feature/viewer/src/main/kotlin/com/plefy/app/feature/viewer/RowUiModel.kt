package com.plefy.app.feature.viewer

/**
 * A single table row prepared for display.
 *
 * The [cells] map is keyed by the persisted `columnId` (matching
 * [com.plefy.app.database.entity.ColumnDefEntity.id]) so the UI can look up each column's value
 * in header order regardless of the physical storage order. Values are nullable because an
 * empty/absent source cell is stored as `null`.
 *
 * @property rowId the persisted [com.plefy.app.database.entity.RowEntity.id].
 * @property cells map of `columnId` to the cell's raw display value (`null` when empty).
 */
data class RowUiModel(
    val rowId: Long,
    val cells: Map<Long, String?>
)
