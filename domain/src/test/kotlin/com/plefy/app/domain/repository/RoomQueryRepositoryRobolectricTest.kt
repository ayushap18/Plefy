package com.plefy.app.domain.repository

import androidx.test.core.app.ApplicationProvider
import com.plefy.app.database.AppDatabase
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import com.plefy.app.domain.query.FilterOp
import com.plefy.app.domain.query.FilterSpec
import com.plefy.app.domain.query.QuerySort
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.query.SearchSpec
import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end tests that run the SQL produced by [com.plefy.app.domain.query.SqlQueryBuilder]
 * against a real (Robolectric-hosted) in-memory SQLite [AppDatabase] via [RoomQueryRepository].
 *
 * Whereas [com.plefy.app.domain.query.SqlQueryBuilderTest] pins the SQL *text*, this class
 * proves the generated query actually returns the correct rows from SQLite: sort order, filter
 * selection, search matching, and group-summary counts.
 *
 * These require Robolectric (for `ApplicationProvider` + a native SQLite build) and are kept in
 * their own class, separate from the pure-JVM tests, so the pure tests still pass if Robolectric
 * cannot be resolved in the environment.
 */
@RunWith(RobolectricTestRunner::class)
class RoomQueryRepositoryRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomQueryRepository

    // Persisted ids resolved during dataset setup.
    private var tableId: Long = 0
    private var nameColId: Long = 0
    private var ageColId: Long = 0
    private var cityColId: Long = 0

    // rowIndex -> persisted rowId, so assertions can be written against known rows.
    private val rowIdByIndex = mutableMapOf<Int, Long>()

    @Before
    fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = RoomQueryRepository(db)
        seedDataset()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * A tiny known dataset: three typed columns (TEXT name, INTEGER age, TEXT city) over four rows.
     *
     * idx | name  | age | city
     *  0  | Bob   | 30  | Paris
     *  1  | Ann   | 25  | Paris
     *  2  | Cara  | 40  | Rome
     *  3  | Dan   | 25  | Paris
     */
    private suspend fun seedDataset() {
        tableId = db.sheetTableDao().insert(SheetTableEntity(name = "people", importedAt = 1L))
        val colIds = db.columnDao().insertAll(
            listOf(
                ColumnDefEntity(tableId = tableId, colIndex = 0, name = "name", type = CellType.TEXT.name),
                ColumnDefEntity(tableId = tableId, colIndex = 1, name = "age", type = CellType.INTEGER.name),
                ColumnDefEntity(tableId = tableId, colIndex = 2, name = "city", type = CellType.TEXT.name)
            )
        )
        nameColId = colIds[0]
        ageColId = colIds[1]
        cityColId = colIds[2]

        val rowIds = db.rowDao().insertAll(
            (0..3).map { RowEntity(tableId = tableId, rowIndex = it) }
        )
        rowIds.forEachIndexed { index, id -> rowIdByIndex[index] = id }

        fun text(rowIdx: Int, colId: Long, v: String) =
            CellEntity(
                rowId = rowIdByIndex.getValue(rowIdx),
                columnId = colId,
                rawValue = v,
                textSort = v.lowercase(),
                type = CellType.TEXT.name
            )

        fun int(rowIdx: Int, colId: Long, v: Int) =
            CellEntity(
                rowId = rowIdByIndex.getValue(rowIdx),
                columnId = colId,
                rawValue = v.toString(),
                numericSort = v.toDouble(),
                type = CellType.INTEGER.name
            )

        db.cellDao().insertAll(
            listOf(
                text(0, nameColId, "Bob"), int(0, ageColId, 30), text(0, cityColId, "Paris"),
                text(1, nameColId, "Ann"), int(1, ageColId, 25), text(1, cityColId, "Paris"),
                text(2, nameColId, "Cara"), int(2, ageColId, 40), text(2, cityColId, "Rome"),
                text(3, nameColId, "Dan"), int(3, ageColId, 25), text(3, cityColId, "Paris")
            )
        )
    }

    private suspend fun rowIndices(spec: QuerySpec): List<Int> =
        repo.rowsList(spec).map { it.rowIndex }

    @Test
    fun numericSortAscending_returnsRowsInAgeOrder() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            sorts = listOf(QuerySort(ageColId, CellType.INTEGER, SortDirection.ASC))
        )
        // ages: Ann 25, Dan 25, Bob 30, Cara 40. Ties (25) fall back to rowIndex ASC -> Ann(1) then Dan(3).
        assertEquals(listOf(1, 3, 0, 2), rowIndices(spec))
    }

    @Test
    fun numericSortDescending_returnsRowsInReverseAgeOrder() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            sorts = listOf(QuerySort(ageColId, CellType.INTEGER, SortDirection.DESC))
        )
        // Cara 40, Bob 30, then the two 25s ordered by rowIndex ASC tiebreak -> Ann(1), Dan(3).
        assertEquals(listOf(2, 0, 1, 3), rowIndices(spec))
    }

    @Test
    fun textSortAscending_returnsRowsInNameOrder() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            sorts = listOf(QuerySort(nameColId, CellType.TEXT, SortDirection.ASC))
        )
        // Ann, Bob, Cara, Dan -> rows 1, 0, 2, 3.
        assertEquals(listOf(1, 0, 2, 3), rowIndices(spec))
    }

    @Test
    fun numericGreaterThanFilter_selectsMatchingRows() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            filters = listOf(FilterSpec(ageColId, CellType.INTEGER, FilterOp.GREATER_THAN, "25")),
            sorts = listOf(QuerySort(ageColId, CellType.INTEGER, SortDirection.ASC))
        )
        // age > 25 -> Bob 30, Cara 40 -> rows 0, 2.
        assertEquals(listOf(0, 2), rowIndices(spec))
    }

    @Test
    fun textContainsFilter_selectsMatchingRows() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            filters = listOf(FilterSpec(nameColId, CellType.TEXT, FilterOp.CONTAINS, "a"))
        )
        // Names containing 'a': Ann(1), Cara(2), Dan(3) — Bob has none. Ordered by rowIndex tiebreak.
        assertEquals(listOf(1, 2, 3), rowIndices(spec))
    }

    @Test
    fun searchTerm_matchesAnyCellRawValue() = runTest {
        val spec = QuerySpec(tableId = tableId, search = SearchSpec("Rome"))
        // Only Cara's city is Rome.
        assertEquals(listOf(2), rowIndices(spec))
    }

    @Test
    fun searchTerm_matchesAcrossMultipleColumns() = runTest {
        // "Paris" appears in the city column for rows 0, 1, 3.
        val spec = QuerySpec(tableId = tableId, search = SearchSpec("Paris"))
        assertEquals(listOf(0, 1, 3), rowIndices(spec))
    }

    @Test
    fun filterAndSortCombine_selectThenOrder() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            filters = listOf(FilterSpec(cityColId, CellType.TEXT, FilterOp.EQUALS, "Paris")),
            sorts = listOf(QuerySort(ageColId, CellType.INTEGER, SortDirection.DESC))
        )
        // Paris rows: Bob(30), Ann(25), Dan(25) -> desc by age then rowIndex tiebreak -> 0, 1, 3.
        assertEquals(listOf(0, 1, 3), rowIndices(spec))
    }

    @Test
    fun groupSummary_countsDistinctValuesMostFrequentFirst() = runTest {
        val buckets = repo.groupSummary(tableId, cityColId)
        // Paris x3, Rome x1; ordered by count DESC.
        assertEquals("Paris", buckets.first().value)
        assertEquals(3, buckets.first().count)
        val byValue = buckets.associate { it.value to it.count }
        assertEquals(3, byValue["Paris"])
        assertEquals(1, byValue["Rome"])
        assertTrue("counts must be non-increasing", buckets.map { it.count } == buckets.map { it.count }.sortedDescending())
    }

    @Test
    fun emptyResult_whenFilterMatchesNothing() = runTest {
        val spec = QuerySpec(
            tableId = tableId,
            filters = listOf(FilterSpec(nameColId, CellType.TEXT, FilterOp.EQUALS, "Nobody"))
        )
        assertTrue(repo.rowsList(spec).isEmpty())
    }
}
