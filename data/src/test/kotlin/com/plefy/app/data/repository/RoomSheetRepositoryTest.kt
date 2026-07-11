package com.plefy.app.data.repository

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plefy.app.common.AppError
import com.plefy.app.common.AppResult
import com.plefy.app.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end test of the import pipeline against a real in-memory Room database (driven by
 * Robolectric). Exercises: reader selection -> schema inference -> sheet/column/row/cell
 * persistence -> paging read-back, plus the empty-sheet failure path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomSheetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SheetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Unconfined keeps the injected IO work on the test thread for deterministic ordering.
        repository = RoomSheetRepository(db, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importCsv_persistsSheetColumnsRowsAndTypedCells() = runTest {
        val csv = "name,age\nAda,36\nGrace,45\n"

        val result = repository.importSpreadsheet(csv.byteInputStream(), "people.csv", "People")

        assertTrue("expected success but was $result", result is AppResult.Success)
        val tableId = (result as AppResult.Success).value

        // Sheet metadata.
        val sheets = repository.observeSheets().first()
        assertEquals(1, sheets.size)
        assertEquals("People", sheets[0].name)
        assertEquals(2, sheets[0].rowCount)

        // Columns inferred from the header.
        val columns = repository.getColumns(tableId).sortedBy { it.colIndex }
        assertEquals(2, columns.size)
        assertEquals(listOf("name", "age"), columns.map { it.name })

        // Rows via paging.
        val page = repository.rowPagingSource(tableId).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )
        assertTrue(page is PagingSource.LoadResult.Page)
        val rows = (page as PagingSource.LoadResult.Page).data
        assertEquals(2, rows.size)

        // Cells of the first row carry raw values and typed sort keys.
        val firstRowCells = repository.getCells(rows.first().id).sortedBy { it.columnId }
        assertEquals(2, firstRowCells.size)
        assertEquals("Ada", firstRowCells[0].rawValue)
        assertEquals("ada", firstRowCells[0].textSort)
        // "age" is inferred INTEGER -> numeric sort key present.
        assertEquals(36.0, firstRowCells[1].numericSort)
    }

    @Test
    fun importEmptyInput_failsWithEmptySheet() = runTest {
        val result = repository.importSpreadsheet("".byteInputStream(), "empty.csv", "Empty")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.EmptySheet)
    }

    @Test
    fun importCsv_invokesOnProgressWithValidMonotonicFractions() = runTest {
        // 3 body rows + 1 header = 4 raw rows; a single chunk (< ROW_CHUNK) reports once.
        val csv = "name,age\nAda,36\nGrace,45\nAlan,41\n"
        val fractions = mutableListOf<Float>()

        val result = repository.importSpreadsheet(
            csv.byteInputStream(), "people.csv", "People",
        ) { fractions.add(it) }

        assertTrue(result is AppResult.Success)
        assertTrue("onProgress must be invoked at least once", fractions.isNotEmpty())
        // Every reported value is a valid 0..1 fraction and reports never go backwards.
        assertTrue("fractions within 0f..1f", fractions.all { it in 0f..1f })
        assertEquals("fractions are non-decreasing", fractions.sorted(), fractions)
        // Denominator is raw rows (header included), so a full sheet tops out at bodyRows/rawRows.
        assertEquals(0.75f, fractions.last(), 0.0001f)
    }

    @Test
    fun deleteSheet_removesItFromObservation() = runTest {
        val csv = "a,b\n1,2\n"
        val tableId = (repository.importSpreadsheet(csv.byteInputStream(), "t.csv", "T")
            as AppResult.Success).value

        repository.deleteSheet(tableId)

        assertTrue(repository.observeSheets().first().isEmpty())
    }
}
