package com.plefy.app.domain.query

import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [SqlQueryBuilder]. They pin the exact SQL text and the bind-argument order so
 * any drift from the Phase-3 contract is caught without a database.
 */
class SqlQueryBuilderTest {

    private val builder = SqlQueryBuilder()
    private val base = "SELECT r.* FROM row_entity r WHERE r.tableId = ?"

    @Test
    fun `minimal spec selects table ordered by row index`() {
        val (sql, args) = builder.build(QuerySpec(tableId = 5))
        assertEquals("$base ORDER BY r.rowIndex ASC", sql)
        assertEquals(listOf<Any?>(5L), args)
    }

    @Test
    fun `numeric sort uses numericSort subquery with direction`() {
        val spec = QuerySpec(
            tableId = 1,
            sorts = listOf(QuerySort(columnId = 2, type = CellType.INTEGER, direction = SortDirection.DESC))
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base ORDER BY " +
                "(SELECT c.numericSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) DESC, " +
                "r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L), args)
    }

    @Test
    fun `text sort uses textSort subquery`() {
        val spec = QuerySpec(
            tableId = 1,
            sorts = listOf(QuerySort(columnId = 7, type = CellType.TEXT))
        )
        val (sql, _) = builder.build(spec)
        assertEquals(
            "$base ORDER BY " +
                "(SELECT c.textSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) ASC, " +
                "r.rowIndex ASC",
            sql
        )
    }

    @Test
    fun `text equals filter binds rawValue case-insensitively`() {
        val spec = QuerySpec(
            tableId = 3,
            filters = listOf(FilterSpec(columnId = 4, type = CellType.TEXT, op = FilterOp.EQUALS, value = "hi"))
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.rawValue = ? COLLATE NOCASE) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(3L, 4L, "hi"), args)
    }

    @Test
    fun `numeric equals filter binds double`() {
        val spec = QuerySpec(
            tableId = 3,
            filters = listOf(FilterSpec(columnId = 4, type = CellType.CURRENCY, op = FilterOp.EQUALS, value = "12.5"))
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.numericSort = ?) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(3L, 4L, 12.5), args)
    }

    @Test
    fun `contains and starts_with produce LIKE predicates`() {
        val (containsSql, containsArgs) = builder.build(
            QuerySpec(
                tableId = 1,
                filters = listOf(FilterSpec(1, CellType.TEXT, FilterOp.CONTAINS, "ab"))
            )
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.rawValue LIKE '%' || ? || '%') ORDER BY r.rowIndex ASC",
            containsSql
        )
        assertEquals(listOf<Any?>(1L, 1L, "ab"), containsArgs)

        val (startsSql, _) = builder.build(
            QuerySpec(
                tableId = 1,
                filters = listOf(FilterSpec(1, CellType.TEXT, FilterOp.STARTS_WITH, "ab"))
            )
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.rawValue LIKE ? || '%') ORDER BY r.rowIndex ASC",
            startsSql
        )
    }

    @Test
    fun `numeric greater and less use numericSort`() {
        val (gtSql, gtArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.INTEGER, FilterOp.GREATER_THAN, "5")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.numericSort > ?) ORDER BY r.rowIndex ASC",
            gtSql
        )
        assertEquals(listOf<Any?>(1L, 2L, 5.0), gtArgs)
    }

    @Test
    fun `text greater and less use textSort`() {
        val (ltSql, ltArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.LESS_THAN, "m")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.textSort < ?) ORDER BY r.rowIndex ASC",
            ltSql
        )
        assertEquals(listOf<Any?>(1L, 2L, "m"), ltArgs)
    }

    @Test
    fun `between numeric binds two doubles`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.INTEGER, FilterOp.BETWEEN, "10", "20")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.numericSort BETWEEN ? AND ?) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L, 10.0, 20.0), args)
    }

    @Test
    fun `is_empty and is_not_empty bind nothing`() {
        val (emptySql, emptyArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.IS_EMPTY)))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND (cf.rawValue IS NULL OR cf.rawValue = '')) ORDER BY r.rowIndex ASC",
            emptySql
        )
        assertEquals(listOf<Any?>(1L, 2L), emptyArgs)

        val (notEmptySql, _) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.IS_NOT_EMPTY)))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND (cf.rawValue IS NOT NULL AND cf.rawValue <> '')) ORDER BY r.rowIndex ASC",
            notEmptySql
        )
    }

    @Test
    fun `search adds cross-column LIKE existence`() {
        val (sql, args) = builder.build(QuerySpec(tableId = 9, search = SearchSpec("needle")))
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cs WHERE cs.rowId = r.id " +
                "AND cs.rawValue LIKE '%' || ? || '%') ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(9L, "needle"), args)
    }

    @Test
    fun `group column is ordered before sorts`() {
        val spec = QuerySpec(
            tableId = 1,
            sorts = listOf(QuerySort(columnId = 3, type = CellType.INTEGER, direction = SortDirection.ASC)),
            group = GroupSpec(columnId = 2)
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base ORDER BY " +
                "(SELECT c.rawValue FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) ASC, " +
                "(SELECT c.numericSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) ASC, " +
                "r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L, 3L), args)
    }

    @Test
    fun `full spec binds args in placeholder order`() {
        val spec = QuerySpec(
            tableId = 100,
            sorts = listOf(QuerySort(columnId = 5, type = CellType.DATE, direction = SortDirection.DESC)),
            filters = listOf(FilterSpec(columnId = 6, type = CellType.TEXT, op = FilterOp.CONTAINS, value = "x")),
            search = SearchSpec("q"),
            group = GroupSpec(columnId = 7)
        )
        val (_, args) = builder.build(spec)
        // tableId, then filter columnId + value, then search term, then group columnId, then sort columnId.
        assertEquals(listOf<Any?>(100L, 6L, "x", "q", 7L, 5L), args)
    }

    @Test
    fun `numeric sort ascending uses numericSort subquery`() {
        val spec = QuerySpec(
            tableId = 1,
            sorts = listOf(QuerySort(columnId = 2, type = CellType.DECIMAL, direction = SortDirection.ASC))
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base ORDER BY " +
                "(SELECT c.numericSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) ASC, " +
                "r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L), args)
    }

    @Test
    fun `multi-column sort emits each key in order`() {
        val spec = QuerySpec(
            tableId = 1,
            sorts = listOf(
                QuerySort(columnId = 2, type = CellType.INTEGER, direction = SortDirection.DESC),
                QuerySort(columnId = 3, type = CellType.TEXT, direction = SortDirection.ASC)
            )
        )
        val (sql, args) = builder.build(spec)
        assertEquals(
            "$base ORDER BY " +
                "(SELECT c.numericSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) DESC, " +
                "(SELECT c.textSort FROM cell_entity c WHERE c.rowId = r.id AND c.columnId = ?) ASC, " +
                "r.rowIndex ASC",
            sql
        )
        // Sort columnIds bind in ORDER BY order, after tableId.
        assertEquals(listOf<Any?>(1L, 2L, 3L), args)
    }

    @Test
    fun `not_equals numeric binds double, text binds rawValue`() {
        val (numSql, numArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.INTEGER, FilterOp.NOT_EQUALS, "5")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.numericSort <> ?) ORDER BY r.rowIndex ASC",
            numSql
        )
        assertEquals(listOf<Any?>(1L, 2L, 5.0), numArgs)

        val (txtSql, txtArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.NOT_EQUALS, "hi")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.rawValue <> ? COLLATE NOCASE) ORDER BY r.rowIndex ASC",
            txtSql
        )
        assertEquals(listOf<Any?>(1L, 2L, "hi"), txtArgs)
    }

    @Test
    fun `greater_than text uses textSort`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.GREATER_THAN, "m")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.textSort > ?) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L, "m"), args)
    }

    @Test
    fun `less_than numeric uses numericSort`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.CURRENCY, FilterOp.LESS_THAN, "9.5")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.numericSort < ?) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L, 9.5), args)
    }

    @Test
    fun `between text binds two strings on textSort`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.BETWEEN, "a", "m")))
        )
        assertEquals(
            "$base AND EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = r.id AND cf.columnId = ? " +
                "AND cf.textSort BETWEEN ? AND ?) ORDER BY r.rowIndex ASC",
            sql
        )
        assertEquals(listOf<Any?>(1L, 2L, "a", "m"), args)
    }

    @Test
    fun `tableId is always the first bound arg`() {
        val (_, args) = builder.build(
            QuerySpec(
                tableId = 77,
                sorts = listOf(QuerySort(1, CellType.TEXT)),
                filters = listOf(FilterSpec(2, CellType.TEXT, FilterOp.CONTAINS, "z"))
            )
        )
        assertEquals(77L, args.first())
    }

    @Test
    fun `every generated query ends with the rowIndex tiebreaker`() {
        val specs = listOf(
            QuerySpec(tableId = 1),
            QuerySpec(tableId = 1, sorts = listOf(QuerySort(2, CellType.INTEGER))),
            QuerySpec(tableId = 1, group = GroupSpec(3)),
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(4, CellType.TEXT, FilterOp.IS_EMPTY)))
        )
        for (spec in specs) {
            val (sql, _) = builder.build(spec)
            assertTrue("missing tiebreaker in: $sql", sql.trimEnd().endsWith("r.rowIndex ASC"))
        }
    }

    @Test
    fun `no user value is string-concatenated into the sql`() {
        // Distinctive operands that must NEVER appear literally in the SQL text; only '?' may.
        val spec = QuerySpec(
            tableId = 999,
            sorts = listOf(QuerySort(columnId = 5, type = CellType.DATE, direction = SortDirection.DESC)),
            filters = listOf(
                FilterSpec(6, CellType.TEXT, FilterOp.CONTAINS, "SECRET_CONTAINS"),
                FilterSpec(7, CellType.INTEGER, FilterOp.BETWEEN, "111333", "444555"),
                FilterSpec(8, CellType.TEXT, FilterOp.EQUALS, "SECRET_EQ")
            ),
            search = SearchSpec("SECRET_SEARCH"),
            group = GroupSpec(9)
        )
        val (sql, args) = builder.build(spec)

        // None of the user-supplied string operands leak into the SQL.
        for (leak in listOf("SECRET_CONTAINS", "SECRET_EQ", "SECRET_SEARCH", "111333", "444555")) {
            assertTrue("user value '$leak' was concatenated into SQL: $sql", !sql.contains(leak))
        }
        // The operands are instead carried as bound args, in placeholder order.
        assertTrue(args.contains("SECRET_CONTAINS"))
        assertTrue(args.contains("SECRET_EQ"))
        assertTrue(args.contains("SECRET_SEARCH"))
        assertTrue(args.contains(111333.0))
        assertTrue(args.contains(444555.0))
        // The number of '?' placeholders equals the number of bound args (nothing inlined).
        assertEquals(sql.count { it == '?' }, args.size)
    }

    @Test
    fun `group summary aggregates by raw value`() {
        val (sql, args) = builder.buildGroupSummary(tableId = 42, columnId = 8)
        assertEquals(
            "SELECT c.rawValue AS value, COUNT(*) AS count FROM cell_entity c " +
                "WHERE c.columnId = ? GROUP BY c.rawValue ORDER BY count DESC",
            sql
        )
        assertEquals(listOf<Any?>(8L), args)
    }

    // -- Robustness: filter operands must never throw (regression for the "filter not responding"
    //    bug where a formatted/date/boolean/empty operand crashed build() via "…".toDouble()).

    @Test
    fun `currency operand with formatting parses instead of throwing`() {
        val (_, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(
                FilterSpec(2, CellType.CURRENCY, FilterOp.GREATER_THAN, "$1,250.50")
            ))
        )
        // "$1,250.50" -> 1250.50, compared against numericSort.
        assertTrue(args.contains(1250.50))
    }

    @Test
    fun `accounting negative and percent operands parse`() {
        val (_, negArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.CURRENCY, FilterOp.LESS_THAN, "(500)")))
        )
        assertTrue(negArgs.contains(-500.0))
        val (_, pctArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.DECIMAL, FilterOp.EQUALS, "15%")))
        )
        assertTrue(pctArgs.contains(15.0))
    }

    @Test
    fun `boolean operand words parse to 0 and 1`() {
        val (_, tArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.BOOLEAN, FilterOp.EQUALS, "true")))
        )
        assertTrue(tArgs.contains(1.0))
        val (_, fArgs) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.BOOLEAN, FilterOp.EQUALS, "no")))
        )
        assertTrue(fArgs.contains(0.0))
    }

    @Test
    fun `date operand on greater_than degrades to rawValue instead of throwing`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(
                FilterSpec(2, CellType.DATE, FilterOp.GREATER_THAN, "2021-01-01")
            ))
        )
        // ISO date isn't a plain number -> compare rawValue lexicographically (orders ISO dates).
        assertTrue(sql.contains("cf.rawValue > ?"))
        assertTrue(args.contains("2021-01-01"))
    }

    @Test
    fun `numeric-looking date operand still compares rawValue not numericSort`() {
        // A DATE column's numericSort holds epoch-millis; a bare "2021" must NOT be compared as a
        // number against it (that would match all-or-nothing). It routes through rawValue instead.
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(
                FilterSpec(2, CellType.DATE, FilterOp.GREATER_THAN, "2021")
            ))
        )
        assertTrue(sql.contains("cf.rawValue > ?"))
        assertFalse(sql.contains("numericSort"))
        assertTrue(args.contains("2021"))
    }

    @Test
    fun `blank operand becomes a harmless no-op`() {
        val (sql, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.INTEGER, FilterOp.EQUALS, value = null)))
        )
        assertTrue(sql.contains("1 = 1"))
        // Only tableId + the filter's columnId are bound; no operand.
        assertEquals(listOf<Any?>(1L, 2L), args)
    }

    @Test
    fun `incomplete between range is a no-op`() {
        val (sql, _) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(
                FilterSpec(2, CellType.INTEGER, FilterOp.BETWEEN, value = "5", value2 = null)
            ))
        )
        assertTrue(sql.contains("1 = 1"))
    }

    @Test
    fun `non-numeric operand on a numeric column does not throw`() {
        // Previously "abc".toDouble() threw and killed the query. Now it degrades.
        val (_, args) = builder.build(
            QuerySpec(tableId = 1, filters = listOf(FilterSpec(2, CellType.INTEGER, FilterOp.EQUALS, "abc")))
        )
        assertTrue(args.contains("abc")) // fell back to a rawValue comparison
    }

    // -- Aggregate (chart calculation mode) --

    @Test
    fun `aggregate count needs no value column`() {
        val (sql, args) = builder.buildAggregate(
            AggregateSpec(tableId = 1, categoryColumnId = 5, op = AggregateOp.COUNT)
        )
        assertEquals(
            "SELECT c.rawValue AS label, COUNT(*) AS amount FROM cell_entity c " +
                "WHERE c.columnId = ? GROUP BY c.rawValue ORDER BY amount DESC",
            sql
        )
        assertEquals(listOf<Any?>(5L), args)
    }

    @Test
    fun `aggregate sum joins the value column, value id bound before category id`() {
        val (sql, args) = builder.buildAggregate(
            AggregateSpec(tableId = 1, categoryColumnId = 5, valueColumnId = 9, op = AggregateOp.SUM)
        )
        assertEquals(
            "SELECT c.rawValue AS label, SUM(v.numericSort) AS amount FROM cell_entity c " +
                "JOIN cell_entity v ON v.rowId = c.rowId AND v.columnId = ? " +
                "WHERE c.columnId = ? GROUP BY c.rawValue ORDER BY amount DESC",
            sql
        )
        assertEquals(listOf<Any?>(9L, 5L), args)
    }

    @Test
    fun `aggregate average with a filter appends an EXISTS on the category row`() {
        val (sql, args) = builder.buildAggregate(
            AggregateSpec(
                tableId = 1, categoryColumnId = 5, valueColumnId = 9, op = AggregateOp.AVERAGE,
                filters = listOf(FilterSpec(7, CellType.BOOLEAN, FilterOp.EQUALS, "true"))
            )
        )
        assertTrue(sql.contains("AVG(v.numericSort)"))
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM cell_entity cf WHERE cf.rowId = c.rowId AND cf.columnId = ?"))
        // args: valueId, categoryId, filter columnId, filter value(true->1.0)
        assertEquals(9L, args[0]); assertEquals(5L, args[1]); assertEquals(7L, args[2])
        assertTrue(args.contains(1.0))
    }

    @Test
    fun `aggregate non-count with null value column degrades to count`() {
        val (sql, args) = builder.buildAggregate(
            AggregateSpec(tableId = 1, categoryColumnId = 5, valueColumnId = null, op = AggregateOp.SUM)
        )
        assertTrue(sql.contains("COUNT(*)"))
        assertFalse(sql.contains("JOIN cell_entity v"))
        assertEquals(listOf<Any?>(5L), args)
    }
}
