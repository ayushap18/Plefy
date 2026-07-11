package com.plefy.app.model

/**
 * The result of type inference for a single column of a sheet.
 *
 * @property index zero-based column position within the sheet.
 * @property name human-readable column name (typically taken from the header row).
 * @property type the [CellType] inferred for the column's values.
 * @property format optional format hint describing how values are rendered
 *   (for example a date pattern or currency symbol); `null` when not applicable or unknown.
 * @property confidence inference confidence in the range `0.0`..`1.0`, where `1.0` means certain.
 */
data class InferredColumn(
    val index: Int,
    val name: String,
    val type: CellType,
    val format: String?,
    val confidence: Double
)
