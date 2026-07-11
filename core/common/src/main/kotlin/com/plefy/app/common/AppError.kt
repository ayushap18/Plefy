package com.plefy.app.common

/**
 * Represents a recoverable, domain-level error produced anywhere in the Excel
 * backend core.
 *
 * [AppError] is a sealed hierarchy so that callers can exhaustively handle every
 * failure category with a `when` expression. Each subtype carries a
 * human-readable [message] describing what went wrong.
 *
 * This type is intentionally free of any platform (Android) dependency so it can
 * be used and unit-tested on a plain JVM.
 */
sealed class AppError(open val message: String) {

    /**
     * The input could not be parsed into the expected structure
     * (e.g. malformed cell contents or a corrupt workbook stream).
     */
    data class ParseError(override val message: String) : AppError(message)

    /**
     * The supplied file or data is in a format that is not supported
     * (e.g. an unexpected file extension or an unknown workbook version).
     */
    data class UnsupportedFormat(override val message: String) : AppError(message)

    /**
     * A sheet was expected to contain data but was empty.
     */
    data class EmptySheet(override val message: String) : AppError(message)

    /**
     * An I/O failure occurred while reading or writing data
     * (e.g. the underlying stream could not be accessed).
     */
    data class IoError(override val message: String) : AppError(message)
}
