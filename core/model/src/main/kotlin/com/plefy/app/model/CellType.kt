package com.plefy.app.model

/**
 * The inferred logical type of a spreadsheet cell or column.
 *
 * Ordering is intentionally coarse-to-specific: [EMPTY] represents the absence of a value,
 * while the remaining entries describe how a raw string value should be interpreted and
 * compared. This type is consumed from both Kotlin and Java modules, so it is kept as a
 * plain enum with no extra state.
 */
enum class CellType {
    /** No value present (null or blank cell). */
    EMPTY,

    /** A boolean value such as `true`/`false`, `yes`/`no`, `1`/`0`. */
    BOOLEAN,

    /** A whole number represented canonically as a [Long]. */
    INTEGER,

    /** A non-integer number represented canonically as a [java.math.BigDecimal]. */
    DECIMAL,

    /** A monetary amount represented canonically as a [java.math.BigDecimal]. */
    CURRENCY,

    /** A calendar date (no time component). */
    DATE,

    /** A date together with a time-of-day component. */
    DATETIME,

    /** Free-form text with no more specific type. */
    TEXT
}
