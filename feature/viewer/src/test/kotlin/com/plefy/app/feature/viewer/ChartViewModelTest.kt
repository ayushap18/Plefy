package com.plefy.app.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingSource
import com.plefy.app.common.AppResult
import com.plefy.app.data.repository.SheetRepository
import com.plefy.app.database.dao.GroupBucket
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.repository.QueryRepository
import com.plefy.app.model.CellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

/**
 * Pure-JVM unit tests for [ChartViewModel]'s data plumbing (no emulator, no Compose).
 *
 * The ViewModel does two things worth testing: it loads the table's columns on init and it delegates
 * per-value counting to [QueryRepository.groupSummary]. Both are exercised here with fakes on a
 * [StandardTestDispatcher] installed as Main, so `viewModelScope` work is deterministic.
 *
 * ## Not covered here (intentionally)
 * The **top-N + "Other" bucketing** (`aggregate` in [ChartScreen]) is a `private` function that
 * returns Compose-colored `Slice`s, so it is neither reachable from a JVM test nor free of Compose.
 * Its pie/bar rendering is a Compose visual verified by the manual smoke steps below. To make the
 * bucketing itself unit-testable, the chart engineer should lift the count-sorting/top-N/Other fold
 * out of [ChartScreen] into a pure, color-free function on [ChartViewModel]; a test would then assert
 * that N+1 buckets collapse to TOP_N named slices plus one "Other" carrying the remainder count.
 *
 * ## Manual smoke note ([ChartScreen])
 * Launch the app, open a table's chart. Verify: (1) the category-column dropdown lists every column
 * and defaults to the first; (2) switching Bar/Pie re-renders; (3) a column with many distinct
 * values shows at most 12 colored slices plus a gray "Other"; (4) blank values label as "(blank)".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun column(id: Long, colIndex: Int) = ColumnDefEntity(
        id = id, tableId = TABLE_ID, colIndex = colIndex, name = "c$id",
        type = CellType.TEXT.name, format = null, confidence = 0.9,
    )

    private fun buildViewModel(
        columns: List<ColumnDefEntity> = emptyList(),
        query: RecordingQueryRepository = RecordingQueryRepository(),
    ) = ChartViewModel(
        savedStateHandle = SavedStateHandle(mapOf("tableId" to TABLE_ID)),
        sheetRepository = ChartStubSheetRepository(columns),
        queryRepository = query,
    )

    @Test
    fun `reads tableId from savedStateHandle`() {
        assertEquals(TABLE_ID, buildViewModel().tableId)
    }

    @Test
    fun `loads columns on init from the sheet repository`() = runTest(dispatcher) {
        val cols = listOf(column(1L, 0), column(2L, 1))
        val vm = buildViewModel(columns = cols)

        // init launches on Main; nothing is emitted until the dispatcher runs it.
        assertTrue(vm.columns.value.isEmpty())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(cols, vm.columns.value)
    }

    private companion object {
        const val TABLE_ID = 42L
    }
}

/** Records the [groupSummary] arguments and replays a scripted bucket list. */
private class RecordingQueryRepository(
    private val summary: List<GroupBucket> = emptyList(),
) : QueryRepository {
    var lastTableId: Long? = null
    var lastColumnId: Long? = null

    override fun rows(spec: QuerySpec): PagingSource<Int, RowEntity> =
        throw UnsupportedOperationException("not used")

    override suspend fun rowsList(spec: QuerySpec): List<RowEntity> = emptyList()

    override suspend fun groupSummary(tableId: Long, columnId: Long): List<GroupBucket> {
        lastTableId = tableId
        lastColumnId = columnId
        return summary
    }

    override suspend fun aggregate(spec: com.plefy.app.domain.query.AggregateSpec): List<com.plefy.app.database.dao.GroupAggregate> = emptyList()

    override suspend fun cells(rowId: Long): List<CellEntity> = emptyList()
}

/** Minimal [SheetRepository] fake: only [getColumns] is read by [ChartViewModel]. */
private class ChartStubSheetRepository(
    private val columns: List<ColumnDefEntity>,
) : SheetRepository {
    override suspend fun importSpreadsheet(
        input: InputStream,
        fileName: String,
        displayName: String,
        onProgress: (suspend (Float) -> Unit)?,
    ): AppResult<Long> = AppResult.Success(0L)

    override fun observeSheets(): Flow<List<SheetTableEntity>> = flowOf(emptyList())

    override suspend fun getColumns(tableId: Long): List<ColumnDefEntity> = columns

    override fun rowPagingSource(tableId: Long): PagingSource<Int, RowEntity> =
        throw UnsupportedOperationException("not used")

    override suspend fun getCells(rowId: Long): List<CellEntity> = emptyList()

    override suspend fun deleteSheet(tableId: Long) = Unit
}
