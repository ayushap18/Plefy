package com.plefy.app.domain.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Wraps the pure-Kotlin output of [SqlQueryBuilder] into a [SupportSQLiteQuery] suitable for Room's
 * `@RawQuery` DAO methods. Kept separate from the builder so the builder stays free of Android
 * dependencies and remains JVM-unit-testable.
 */
object RawQueryFactory {

    /**
     * Creates a bound [SupportSQLiteQuery] from a `(sql, args)` pair produced by [SqlQueryBuilder].
     * Args are passed positionally to the `?` placeholders in [sql].
     */
    fun create(sql: String, args: List<Any?>): SupportSQLiteQuery =
        SimpleSQLiteQuery(sql, args.toTypedArray())
}
