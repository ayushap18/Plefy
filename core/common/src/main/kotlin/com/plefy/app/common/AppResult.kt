package com.plefy.app.common

/**
 * A lightweight result type modelling the outcome of an operation that can either succeed with a
 * value of type [T] or fail with an [AppError].
 *
 * This is preferred over throwing exceptions for expected, recoverable failures because it makes
 * the failure path explicit in the type signature and forces callers to handle it (via a
 * `when (result) { is Success -> …; is Failure -> … }`).
 *
 * The type is pure JVM/Kotlin with no platform dependencies.
 */
sealed class AppResult<out T> {

    /** A successful outcome carrying the produced [value]. */
    data class Success<T>(val value: T) : AppResult<T>()

    /** A failed outcome carrying the [error] that describes what went wrong. */
    data class Failure(val error: AppError) : AppResult<Nothing>()
}
