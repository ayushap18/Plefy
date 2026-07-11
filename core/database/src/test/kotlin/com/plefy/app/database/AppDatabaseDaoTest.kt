package com.plefy.app.database

import androidx.paging.PagingSource
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import com.plefy.app.model.CellType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric-backed DAO tests running on the JVM against an in-memory [AppDatabase].
 *
 * These require Robolectric (for `ApplicationProvider` + a real SQLite build). They are kept
 * in their own class, separate from the pure-JVM [CellMapperTest], so the JVM tests still pass
 * if Robolectric cannot be resolved in the environment.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertSheetTable_getByIdReturnsIt() = runTest {
        val id = db.sheetTableDao().insert(
            SheetTableEntity(
                name = "budget",
                sourceUri = "content://sheet/1",
                sheetName = "Q1",
                rowCount = 10,
                importedAt = 123L
            )
        )

        val fetched = db.sheetTableDao().getById(id)
        assertNotNull(fetched)
        assertEquals(id, fetched!!.id)
        assertEquals("budget", fetched.name)
        assertEquals("content://sheet/1", fetched.sourceUri)
        assertEquals("Q1", fetched.sheetName)
        assertEquals(10, fetched.rowCount)
        assertEquals(123L, fetched.importedAt)
    }

    @Test
    fun getById_returnsNullForMissingTable() = runTest {
        assertNull(db.sheetTableDao().getById(999L))
    }

    @Test
    fun getAllFlow_emitsInsertedTable() = runTest {
        val id = db.sheetTableDao().insert(SheetTableEntity(name = "sales", importedAt = 1L))

        val tables = db.sheetTableDao().getAll().first()
        assertEquals(1, tables.size)
        assertEquals(id, tables.first().id)
        assertEquals("sales", tables.first().name)
    }

    @Test
    fun getAllFlow_ordersByImportedAtDescending() = runTest {
        db.sheetTableDao().insert(SheetTableEntity(name = "older", importedAt = 100L))
        db.sheetTableDao().insert(SheetTableEntity(name = "newer", importedAt = 200L))

        val names = db.sheetTableDao().getAll().first().map { it.name }
        assertEquals(listOf("newer", "older"), names)
    }

    @Test
    fun countForTable_reflectsInsertedRows() = runTest {
        val tableId = db.sheetTableDao().insert(SheetTableEntity(name = "t", importedAt = 1L))
        db.columnDao().insertAll(
            listOf(ColumnDefEntity(tableId = tableId, colIndex = 0, name = "c", type = CellType.TEXT.name))
        )
        db.rowDao().insertAll(
            listOf(
                RowEntity(tableId = tableId, rowIndex = 0),
                RowEntity(tableId = tableId, rowIndex = 1),
                RowEntity(tableId = tableId, rowIndex = 2)
            )
        )

        assertEquals(3, db.rowDao().countForTable(tableId))
        assertEquals(0, db.rowDao().countForTable(tableId + 1))
    }

    @Test
    fun pagingSource_returnsRowsInRowIndexOrder() = runTest {
        val tableId = db.sheetTableDao().insert(SheetTableEntity(name = "t", importedAt = 1L))
        // Insert deliberately out of rowIndex order to prove ORDER BY rowIndex ASC works.
        db.rowDao().insertAll(
            listOf(
                RowEntity(tableId = tableId, rowIndex = 2),
                RowEntity(tableId = tableId, rowIndex = 0),
                RowEntity(tableId = tableId, rowIndex = 1)
            )
        )

        val pagingSource = db.rowDao().pagingSource(tableId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(0, 1, 2), page.data.map { it.rowIndex })
    }

    @Test
    fun pagingSource_isScopedToRequestedTable() = runTest {
        val tableA = db.sheetTableDao().insert(SheetTableEntity(name = "a", importedAt = 1L))
        val tableB = db.sheetTableDao().insert(SheetTableEntity(name = "b", importedAt = 2L))
        db.rowDao().insertAll(listOf(RowEntity(tableId = tableA, rowIndex = 0)))
        db.rowDao().insertAll(
            listOf(
                RowEntity(tableId = tableB, rowIndex = 0),
                RowEntity(tableId = tableB, rowIndex = 1)
            )
        )

        val page = db.rowDao().pagingSource(tableA).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertEquals(1, page.data.size)
        assertTrue(page.data.all { it.tableId == tableA })
    }

    @Test
    fun deleteById_cascadesToColumnsRowsAndCells() = runTest {
        val tableId = db.sheetTableDao().insert(SheetTableEntity(name = "t", importedAt = 1L))
        val columnIds = db.columnDao().insertAll(
            listOf(
                ColumnDefEntity(tableId = tableId, colIndex = 0, name = "name", type = CellType.TEXT.name),
                ColumnDefEntity(tableId = tableId, colIndex = 1, name = "age", type = CellType.INTEGER.name)
            )
        )
        val rowIds = db.rowDao().insertAll(
            listOf(
                RowEntity(tableId = tableId, rowIndex = 0),
                RowEntity(tableId = tableId, rowIndex = 1)
            )
        )
        db.cellDao().insertAll(
            listOf(
                CellEntity(rowId = rowIds[0], columnId = columnIds[0], rawValue = "Bob", textSort = "bob", type = CellType.TEXT.name),
                CellEntity(rowId = rowIds[0], columnId = columnIds[1], rawValue = "30", numericSort = 30.0, type = CellType.INTEGER.name),
                CellEntity(rowId = rowIds[1], columnId = columnIds[0], rawValue = "Ann", textSort = "ann", type = CellType.TEXT.name),
                CellEntity(rowId = rowIds[1], columnId = columnIds[1], rawValue = "25", numericSort = 25.0, type = CellType.INTEGER.name)
            )
        )

        // Sanity: everything is present before the delete.
        assertEquals(2, db.rowDao().countForTable(tableId))
        assertEquals(2, db.columnDao().getForTableOnce(tableId).size)
        assertEquals(2, db.cellDao().getForRow(rowIds[0]).size)

        db.sheetTableDao().deleteById(tableId)

        assertNull(db.sheetTableDao().getById(tableId))
        assertEquals(0, db.rowDao().countForTable(tableId))
        assertTrue(db.columnDao().getForTableOnce(tableId).isEmpty())
        // Cells cascade transitively through their owning rows.
        assertTrue(db.cellDao().getForRow(rowIds[0]).isEmpty())
        assertTrue(db.cellDao().getForRow(rowIds[1]).isEmpty())
    }

    @Test
    fun rawQueries_queryRowsListAndGroupSummary_runAndMap() = runTest {
        // Smoke test that the @RawQuery methods execute against a real SQLite build and map their
        // results correctly. Exhaustive SQL coverage for the dynamic engine lives in :domain; here
        // we only confirm a hand-written SimpleSQLiteQuery binds, runs, and projects as expected.
        val tableId = db.sheetTableDao().insert(SheetTableEntity(name = "t", importedAt = 1L))
        val columnIds = db.columnDao().insertAll(
            listOf(
                ColumnDefEntity(tableId = tableId, colIndex = 0, name = "city", type = CellType.TEXT.name)
            )
        )
        val rowIds = db.rowDao().insertAll(
            listOf(
                RowEntity(tableId = tableId, rowIndex = 0),
                RowEntity(tableId = tableId, rowIndex = 1),
                RowEntity(tableId = tableId, rowIndex = 2)
            )
        )
        db.cellDao().insertAll(
            listOf(
                CellEntity(rowId = rowIds[0], columnId = columnIds[0], rawValue = "Paris", textSort = "paris", type = CellType.TEXT.name),
                CellEntity(rowId = rowIds[1], columnId = columnIds[0], rawValue = "Paris", textSort = "paris", type = CellType.TEXT.name),
                CellEntity(rowId = rowIds[2], columnId = columnIds[0], rawValue = "Rome", textSort = "rome", type = CellType.TEXT.name)
            )
        )

        // queryRowsList: project row_entity columns via a join+filter and map to RowEntity.
        val rowsQuery = SimpleSQLiteQuery(
            "SELECT r.* FROM row_entity r " +
                "JOIN cell_entity c ON c.rowId = r.id " +
                "WHERE c.columnId = ? AND c.textSort = ? " +
                "ORDER BY r.rowIndex ASC",
            arrayOf<Any>(columnIds[0], "paris")
        )
        val parisRows = db.rowDao().queryRowsList(rowsQuery)
        assertEquals(listOf(0, 1), parisRows.map { it.rowIndex })
        assertTrue(parisRows.all { it.tableId == tableId })

        // groupSummary: project value + count columns and map to GroupBucket, ordered by count.
        val summaryQuery = SimpleSQLiteQuery(
            "SELECT rawValue AS value, COUNT(*) AS count FROM cell_entity " +
                "WHERE columnId = ? GROUP BY rawValue ORDER BY count DESC, value ASC",
            arrayOf<Any>(columnIds[0])
        )
        val buckets = db.rowDao().groupSummary(summaryQuery)
        assertEquals(listOf("Paris", "Rome"), buckets.map { it.value })
        assertEquals(listOf(2, 1), buckets.map { it.count })
    }

    @Test
    fun emptyPagingSource_loadReturnsEmptyPage() = runTest {
        val tableId = db.sheetTableDao().insert(SheetTableEntity(name = "empty", importedAt = 1L))

        val page = db.rowDao().pagingSource(tableId).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())
    }
}
