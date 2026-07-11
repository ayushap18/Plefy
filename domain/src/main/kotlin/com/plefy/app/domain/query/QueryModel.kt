package com.plefy.app.domain.query

import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection

/**
 * The domain-level query model for the sort / filter / search / group engine.
 *
 * These types are **pure Kotlin** (no Android or Room imports) so the [SqlQueryBuilder]
 * that consumes them can be exercised on the JVM. They describe *what* to query over the
 * EAV schema; [SqlQueryBuilder] translates them into parameterised SQLite.
 */

/** The comparison operators a [FilterSpec] can apply to a column. */
enum class FilterOp {
    /** Column value equals the supplied value. */
    EQUALS,

    /** Column value differs from the supplied value. */
    NOT_EQUALS,

    /** Raw text contains the supplied substring. */
    CONTAINS,

    /** Raw text starts with the supplied prefix. */
    STARTS_WITH,

    /** Value is strictly greater than the supplied value. */
    GREATER_THAN,

    /** Value is strictly less than the supplied value. */
    LESS_THAN,

    /** Value falls within the inclusive `[value, value2]` range. */
    BETWEEN,

    /** Cell has no value (null or empty string). */
    IS_EMPTY,

    /** Cell has a non-empty value. */
    IS_NOT_EMPTY
}

/**
 * A single filter predicate applied to one column.
 *
 * @property columnId the [com.plefy.app.database.entity.ColumnDefEntity] id to test.
 * @property type the column's [CellType]; decides whether numeric or text sort keys are compared.
 * @property op the [FilterOp] to apply.
 * @property value primary operand (unused for [FilterOp.IS_EMPTY]/[FilterOp.IS_NOT_EMPTY]).
 * @property value2 secondary operand for [FilterOp.BETWEEN].
 */
data class FilterSpec(
    val columnId: Long,
    val type: CellType,
    val op: FilterOp,
    val value: String? = null,
    val value2: String? = null
)

/**
 * A sort key over one column.
 *
 * @property columnId the column to sort by.
 * @property type the column's [CellType]; decides the sort key (numeric vs text).
 * @property direction ascending or descending.
 */
data class QuerySort(
    val columnId: Long,
    val type: CellType,
    val direction: SortDirection = SortDirection.ASC
)

/** A full-text search across every cell's raw value. */
data class SearchSpec(val term: String)

/** Groups rows so that rows sharing a value in [columnId] are ordered adjacently. */
data class GroupSpec(val columnId: Long)

/**
 * A complete query request over a single table.
 *
 * @property tableId the [com.plefy.app.database.entity.SheetTableEntity] id to query.
 * @property sorts ordered list of sort keys (first is primary).
 * @property filters conjunctive (AND-ed) filter predicates.
 * @property search optional cross-column text search.
 * @property group optional grouping column (emitted as the first ORDER BY key).
 */
data class QuerySpec(
    val tableId: Long,
    val sorts: List<QuerySort> = emptyList(),
    val filters: List<FilterSpec> = emptyList(),
    val search: SearchSpec? = null,
    val group: GroupSpec? = null
)

/** The calculation an [AggregateSpec] performs over each category group. */
enum class AggregateOp {
    /** Row count per category (no value column needed). */
    COUNT,

    /** Sum of the value column per category. */
    SUM,

    /** Mean of the value column per category. */
    AVERAGE,

    /** Smallest value-column value per category. */
    MIN,

    /** Largest value-column value per category. */
    MAX
}

/**
 * A grouped calculation: apply [op] to [valueColumnId] for every distinct value of
 * [categoryColumnId], over the rows of [tableId] that pass [filters]. Powers the chart's
 * "calculation" mode (e.g. average Salary by Department where Active = true).
 *
 * @property valueColumnId the numeric column to aggregate; ignored (may be null) for [AggregateOp.COUNT].
 * @property filters conjunctive predicates applied to the rows *before* aggregating.
 */
data class AggregateSpec(
    val tableId: Long,
    val categoryColumnId: Long,
    val valueColumnId: Long? = null,
    val op: AggregateOp = AggregateOp.COUNT,
    val filters: List<FilterSpec> = emptyList()
)
