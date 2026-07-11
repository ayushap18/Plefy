package com.plefy.app.model

/**
 * A single cell value after type inference, pairing the original raw string with a
 * canonical, sortable typed representation.
 *
 * The [typed] value is the canonical form used for comparison and sorting. Its runtime
 * type depends on [type]:
 *
 * - [CellType.INTEGER] -> [Long]
 * - [CellType.DECIMAL] / [CellType.CURRENCY] -> [java.math.BigDecimal]
 * - [CellType.DATE] / [CellType.DATETIME] -> [Long] epoch milliseconds
 * - [CellType.BOOLEAN] -> [Boolean]
 * - [CellType.TEXT] -> [String]
 * - [CellType.EMPTY] -> `null`
 *
 * @property raw the original string value as read, or `null` if the source cell was missing.
 * @property typed the canonical sortable value, or `null` when the cell is empty/unparseable.
 * @property type the [CellType] this cell was interpreted as.
 */
data class TypedCell(
    val raw: String?,
    val typed: Any?,
    val type: CellType
)
