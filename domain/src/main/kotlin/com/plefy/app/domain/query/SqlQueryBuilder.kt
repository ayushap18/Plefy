package com.plefy.app.domain.query

import com.plefy.app.model.CellType

/**
 * Translates a [QuerySpec] into parameterised SQLite over the EAV schema
 * (`row_entity` rows + `cell_entity` values).
 *
 * This class is **pure Kotlin** with no Android/Room dependencies so it can be unit-tested on
 * the JVM. [RawQueryFactory][com.plefy.app.domain.query.RawQueryFactory] wraps the output
 * into a `SupportSQLiteQuery` for Room's `@RawQuery` DAO methods.
 *
 * ## Safety
 * Every user-supplied operand (filter values, search terms) is emitted as a bound `?` placeholder
 * and returned in the args list in the exact left-to-right order the placeholders appear — never
 * concatenated into the SQL string. `tableId` and `columnId` are trusted [Long]s and are also
 * bound for uniformity.
 *
 * ## Sort keys
 * Numeric-ish types compare the pre-computed `numericSort` column; [CellType.TEXT] compares the
 * `textSort` / `rawValue` columns. See [isNumeric].
 */
class SqlQueryBuilder {

    /**
     * Builds the row-selection query.
     *
     * @return the SQL string paired with the bind arguments in placeholder order. The first arg is
     *   always `tableId` ([Long]); filter and search operands follow in clause order; finally the
     *   `columnId`s referenced by the `ORDER BY` correlated subqueries.
     */
    fun build(spec: QuerySpec): Pair<String, List<Any?>> {
        val sql = StringBuilder("SELECT r.* FROM row_entity r WHERE r.tableId = ?")
        val args = mutableListOf<Any?>()
        args.add(spec.tableId)

        // Filters -> conjunctive EXISTS sub-selects against the cell table.
        for (filter in spec.filters) {
            sql.append(
                " AND EXISTS (SELECT 1 FROM cell_entity cf" +
                    " WHERE cf.rowId = r.id AND cf.columnId = ? AND "
            )
            args.add(filter.columnId)
            appendPredicate(sql, args, filter)
            sql.append(")")
        }

        // Cross-column text search.
        spec.search?.let { search ->
            sql.append(
                " AND EXISTS (SELECT 1 FROM cell_entity cs" +
                    " WHERE cs.rowId = r.id AND cs.rawValue LIKE '%' || ? || '%')"
            )
            args.add(search.term)
        }

        // ORDER BY: group column first (if any), then each sort key, then a stable row-index tiebreak.
        val orderKeys = mutableListOf<String>()
        spec.group?.let { group ->
            // GroupSpec carries no type; ordering by rawValue clusters equal values regardless of type.
            orderKeys.add(
                "(SELECT c.rawValue FROM cell_entity c" +
                    " WHERE c.rowId = r.id AND c.columnId = ?) ASC"
            )
            args.add(group.columnId)
        }
        for (sort in spec.sorts) {
            val key = if (isNumeric(sort.type)) "numericSort" else "textSort"
            orderKeys.add(
                "(SELECT c.$key FROM cell_entity c" +
                    " WHERE c.rowId = r.id AND c.columnId = ?) ${sort.direction.name}"
            )
            args.add(sort.columnId)
        }
        orderKeys.add("r.rowIndex ASC")
        sql.append(" ORDER BY ").append(orderKeys.joinToString(", "))

        return sql.toString() to args
    }

    /**
     * Builds the group-summary aggregation for a single column: distinct raw values with their
     * row counts, most frequent first. Scoped by `columnId` (which itself belongs to one table),
     * so [tableId] is accepted for symmetry with the use case but is not part of the predicate.
     */
    fun buildGroupSummary(
        @Suppress("UNUSED_PARAMETER") tableId: Long,
        columnId: Long
    ): Pair<String, List<Any?>> {
        val sql = "SELECT c.rawValue AS value, COUNT(*) AS count FROM cell_entity c" +
            " WHERE c.columnId = ? GROUP BY c.rawValue ORDER BY count DESC"
        return sql to listOf<Any?>(columnId)
    }

    /**
     * Builds the grouped-calculation query: for each distinct value of the category column, apply
     * the aggregate op to the value column, over rows passing the filters. Returns
     * `(label TEXT, amount REAL)` rows ordered by amount desc.
     *
     * Numeric aggregates read `numericSort` (the canonical numeric key); COUNT needs no value
     * column. A non-COUNT op with a null value column safely degrades to COUNT. Filters reuse the
     * same EXISTS-per-predicate form as [build], correlated on the category cell's `rowId`.
     */
    fun buildAggregate(spec: AggregateSpec): Pair<String, List<Any?>> {
        val useValue = spec.op != AggregateOp.COUNT && spec.valueColumnId != null
        val agg = when {
            !useValue -> "COUNT(*)"
            spec.op == AggregateOp.SUM -> "SUM(v.numericSort)"
            spec.op == AggregateOp.AVERAGE -> "AVG(v.numericSort)"
            spec.op == AggregateOp.MIN -> "MIN(v.numericSort)"
            spec.op == AggregateOp.MAX -> "MAX(v.numericSort)"
            else -> "COUNT(*)"
        }
        val args = mutableListOf<Any?>()
        val sql = StringBuilder("SELECT c.rawValue AS label, ").append(agg).append(" AS amount FROM cell_entity c")
        if (useValue) {
            sql.append(" JOIN cell_entity v ON v.rowId = c.rowId AND v.columnId = ?")
            args.add(spec.valueColumnId)
        }
        sql.append(" WHERE c.columnId = ?")
        args.add(spec.categoryColumnId)
        for (filter in spec.filters) {
            sql.append(" AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = c.rowId AND cf.columnId = ? AND ")
            args.add(filter.columnId)
            appendPredicate(sql, args, filter)
            sql.append(")")
        }
        sql.append(" GROUP BY c.rawValue ORDER BY amount DESC")
        return sql.toString() to args
    }

    /**
     * Appends the per-operator predicate fragment and its bound args (already past `columnId = ?`).
     *
     * This method is deliberately **total** — it never throws on user input. Operators that need an
     * operand degrade to a harmless `1 = 1` (matches every cell of the column, i.e. a no-op filter)
     * when the operand is missing, so a half-filled filter dialog can't break the query. For a
     * numeric-ish column the operand is parsed tolerantly (see [parseOperand]); values that don't
     * look numeric (e.g. an ISO date typed into a DATE column, or free text) fall back to a
     * `rawValue`/`textSort` comparison instead of crashing on `"…".toDouble()`.
     */
    private fun appendPredicate(
        sql: StringBuilder,
        args: MutableList<Any?>,
        filter: FilterSpec
    ) {
        // Value-free operators first.
        when (filter.op) {
            FilterOp.IS_EMPTY -> {
                sql.append("(cf.rawValue IS NULL OR cf.rawValue = '')")
                return
            }
            FilterOp.IS_NOT_EMPTY -> {
                sql.append("(cf.rawValue IS NOT NULL AND cf.rawValue <> '')")
                return
            }
            else -> Unit
        }

        val raw = filter.value
        if (raw.isNullOrBlank()) {
            // Missing operand -> no-op rather than a crash or an accidental "match nothing".
            sql.append("1 = 1")
            return
        }

        val numeric = isNumeric(filter.type)
        // DATE/DATETIME store epoch-millis in numericSort, so a typed operand ("2021", "2021-01-01")
        // must NOT be compared as a plain number against it (2021 vs 1.6e12 would match all-or-nothing).
        // Route dates through the rawValue/textSort fallback, which orders ISO display strings correctly.
        val numericOperand = numeric && filter.type != CellType.DATE && filter.type != CellType.DATETIME
        val num = if (numericOperand) parseOperand(raw) else null

        when (filter.op) {
            FilterOp.CONTAINS -> {
                sql.append("cf.rawValue LIKE '%' || ? || '%'")
                args.add(raw)
            }
            FilterOp.STARTS_WITH -> {
                sql.append("cf.rawValue LIKE ? || '%'")
                args.add(raw)
            }
            FilterOp.EQUALS -> {
                if (num != null) {
                    sql.append("cf.numericSort = ?"); args.add(num)
                } else {
                    // Case-insensitive so "west" matches "West".
                    sql.append("cf.rawValue = ? COLLATE NOCASE"); args.add(raw)
                }
            }
            FilterOp.NOT_EQUALS -> {
                if (num != null) {
                    sql.append("cf.numericSort <> ?"); args.add(num)
                } else {
                    sql.append("cf.rawValue <> ? COLLATE NOCASE"); args.add(raw)
                }
            }
            FilterOp.GREATER_THAN -> appendComparison(sql, args, ">", filter.type, raw, num)
            FilterOp.LESS_THAN -> appendComparison(sql, args, "<", filter.type, raw, num)
            FilterOp.BETWEEN -> {
                val hi = if (numericOperand) parseOperand(filter.value2) else null
                when {
                    num != null && hi != null -> {
                        sql.append("cf.numericSort BETWEEN ? AND ?"); args.add(num); args.add(hi)
                    }
                    !filter.value2.isNullOrBlank() && filter.type == CellType.TEXT -> {
                        sql.append("cf.textSort BETWEEN ? AND ?")
                        args.add(raw.lowercase()); args.add(filter.value2.lowercase())
                    }
                    !filter.value2.isNullOrBlank() -> {
                        sql.append("cf.rawValue BETWEEN ? AND ?"); args.add(raw); args.add(filter.value2)
                    }
                    else -> sql.append("1 = 1") // incomplete range -> no-op
                }
            }
            // Value-free operators handled above; exhaustive branch keeps the compiler happy.
            FilterOp.IS_EMPTY, FilterOp.IS_NOT_EMPTY -> sql.append("1 = 1")
        }
    }

    /** Emits a `>`/`<` comparison, using the numeric key when parsed, else a text-key fallback. */
    private fun appendComparison(
        sql: StringBuilder,
        args: MutableList<Any?>,
        cmp: String,
        type: CellType,
        raw: String,
        num: Double?,
    ) {
        when {
            num != null -> { sql.append("cf.numericSort $cmp ?"); args.add(num) }
            type == CellType.TEXT -> { sql.append("cf.textSort $cmp ?"); args.add(raw.lowercase()) }
            // Numeric-ish column but the operand isn't a plain number (e.g. an ISO date): compare
            // the display value lexicographically, which orders ISO dates correctly.
            else -> { sql.append("cf.rawValue $cmp ?"); args.add(raw) }
        }
    }

    /**
     * Parses a user-typed operand for a numeric-ish column into a [Double], tolerating currency and
     * grouping formatting (`$`, `,`, `%`, spaces), accounting-style parentheses for negatives, and
     * boolean words. Returns `null` when the text isn't numeric (the caller then degrades to a text
     * comparison) — it never throws.
     */
    private fun parseOperand(value: String?): Double? {
        val s = value?.trim().orEmpty()
        if (s.isEmpty()) return null
        when (s.lowercase()) {
            "true", "yes", "y" -> return 1.0
            "false", "no", "n" -> return 0.0
        }
        var body = s
        var negative = false
        if (body.startsWith("(") && body.endsWith(")")) {
            negative = true
            body = body.substring(1, body.length - 1)
        }
        body = body.replace("$", "").replace(",", "").replace("%", "").replace(" ", "")
        if (body.startsWith("-")) { negative = true; body = body.substring(1) }
        val parsed = body.toDoubleOrNull() ?: return null
        return if (negative) -parsed else parsed
    }

    private companion object {
        /** Types that compare against the numeric sort key rather than the text sort key. */
        private val NUMERIC_TYPES = setOf(
            CellType.INTEGER,
            CellType.DECIMAL,
            CellType.CURRENCY,
            CellType.DATE,
            CellType.DATETIME,
            CellType.BOOLEAN
        )

        fun isNumeric(type: CellType): Boolean = type in NUMERIC_TYPES
    }
}
