package com.plefy.app.model

/**
 * A single row of raw, unparsed cell values exactly as read from the source sheet.
 *
 * Cells are kept as nullable strings so that empty/missing cells (`null`) can be
 * distinguished from cells that contain an explicit empty string. No type inference
 * or trimming is applied at this stage.
 *
 * @property index zero-based position of this row within the sheet.
 * @property cells the raw string value of each column, in column order; `null` denotes a missing cell.
 */
data class RawRow(
    val index: Int,
    val cells: List<String?>
)
