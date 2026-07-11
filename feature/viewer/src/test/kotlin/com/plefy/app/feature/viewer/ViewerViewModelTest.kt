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
import com.plefy.app.domain.query.FilterOp
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.repository.QueryRepository
import com.plefy.app.domain.usecase.BuildDefaultViewUseCase
import com.plefy.app.domain.usecase.GetRowCellsUseCase
import com.plefy.app.domain.usecase.QueryRowsUseCase
import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

/**
 * Pure-JVM unit tests for [ViewerViewModel] and the [BuildDefaultViewUseCase.build] overload it
 * relies on.
 *
 * These drive the spec/columns state machine with fakes (a fake [SheetRepository] plus real
 * use cases over a fake [QueryRepository]); the paged [ViewerViewModel.rows] flow is deliberately
 * **not** collected here. Asserting [androidx.paging.PagingData] contents needs a real pager
 * wired to a live [PagingSource], which is exercised in the domain/data layers instead — so the
 * fake query source is never actually queried. Coverage here focuses on spec-building + state:
 * the initial default view and the intent methods.
 *
 * Everything runs on the JVM (no emulator): [Dispatchers.setMain] with a [StandardTestDispatcher]
 * + [runTest], and a plain [SavedStateHandle]. No Compose/Robolectric instrumentation.
 *
 * ## Manual smoke note ([ViewerScreen])
 * The Compose [ViewerScreen] is not covered by an automated test (it needs an emulator/Compose
 * instrumentation). To smoke it manually: launch the app, import a spreadsheet, and open its
 * viewer. Verify: (1) rows render with the inferred default sort/grouping applied; (2) the Sort
 * dialog re-orders rows; (3) the Filter dialog narrows rows and "Clear all" restores them;
 * (4) the Group dialog (incl. "None") re-buckets rows; (5) the search field filters across
 * columns as you type and clears when emptied; (6) tapping a row opens the detail bottom sheet
 * with every column's value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val dateColumn = ColumnDefEntity(
        id = 10L, tableId = TABLE_ID, colIndex = 0, name = "Created",
        type = CellType.DATE.name, format = null, confidence = 0.9,
    )
    private val textColumn = ColumnDefEntity(
        id = 20L, tableId = TABLE_ID, colIndex = 1, name = "Category",
        type = CellType.TEXT.name, format = null, confidence = 0.95,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(
        columns: List<ColumnDefEntity> = listOf(dateColumn, textColumn),
    ): ViewerViewModel = ViewerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("tableId" to TABLE_ID)),
        sheetRepository = FakeSheetRepository(columns),
        queryRowsUseCase = QueryRowsUseCase(FakeQueryRepository),
        getRowCellsUseCase = GetRowCellsUseCase(FakeQueryRepository),
        buildDefaultViewUseCase = BuildDefaultViewUseCase(),
    )

    // ----------------------------------------------------------------------------------------
    // ViewModel: construction + initial default view
    // ----------------------------------------------------------------------------------------

    @Test
    fun `reads tableId from savedStateHandle`() {
        assertEquals(TABLE_ID, buildViewModel().tableId)
    }

    @Test
    fun `initial spec before columns load is a bare spec over the table`() {
        // No dispatcher advance: the init coroutine hasn't run, so the placeholder spec is visible.
        val vm = buildViewModel()
        val spec = vm.currentSpec.value
        assertEquals(TABLE_ID, spec.tableId)
        assertTrue(spec.sorts.isEmpty())
        assertTrue(spec.filters.isEmpty())
        assertNull(spec.search)
        assertNull(spec.group)
        assertTrue(vm.columns.value.isEmpty())
    }

    @Test
    fun `loads columns and builds default view spec on init`() = runTest(dispatcher) {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(dateColumn, textColumn), vm.columns.value)

        val spec = vm.currentSpec.value
        assertEquals(TABLE_ID, spec.tableId)
        // Default sort: first DATE column, descending.
        assertEquals(1, spec.sorts.size)
        assertEquals(dateColumn.id, spec.sorts.first().columnId)
        assertEquals(CellType.DATE, spec.sorts.first().type)
        assertEquals(SortDirection.DESC, spec.sorts.first().direction)
        // Default grouping: first high-confidence TEXT column.
        assertEquals(textColumn.id, spec.group?.columnId)
    }

    // ----------------------------------------------------------------------------------------
    // ViewModel: intent methods land their args in currentSpec
    // ----------------------------------------------------------------------------------------

    @Test
    fun `setSort replaces the sort key with the supplied column, type and direction`() =
        runTest(dispatcher) {
            val vm = buildViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.setSort(textColumn.id, CellType.TEXT, SortDirection.ASC)

            val sorts = vm.currentSpec.value.sorts
            assertEquals(1, sorts.size)
            assertEquals(textColumn.id, sorts.first().columnId)
            assertEquals(CellType.TEXT, sorts.first().type)
            assertEquals(SortDirection.ASC, sorts.first().direction)

            // A second setSort replaces rather than appends.
            vm.setSort(dateColumn.id, CellType.DATE, SortDirection.DESC)
            assertEquals(1, vm.currentSpec.value.sorts.size)
            assertEquals(dateColumn.id, vm.currentSpec.value.sorts.first().columnId)
        }

    @Test
    fun `addFilter appends filters preserving every arg and clearFilters removes them`() =
        runTest(dispatcher) {
            val vm = buildViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.addFilter(textColumn.id, CellType.TEXT, FilterOp.CONTAINS, "abc")
            vm.addFilter(dateColumn.id, CellType.DATE, FilterOp.BETWEEN, "2020", "2021")

            val filters = vm.currentSpec.value.filters
            assertEquals(2, filters.size)

            val first = filters[0]
            assertEquals(textColumn.id, first.columnId)
            assertEquals(CellType.TEXT, first.type)
            assertEquals(FilterOp.CONTAINS, first.op)
            assertEquals("abc", first.value)
            assertNull(first.value2)

            val second = filters[1]
            assertEquals(dateColumn.id, second.columnId)
            assertEquals(CellType.DATE, second.type)
            assertEquals(FilterOp.BETWEEN, second.op)
            assertEquals("2020", second.value)
            assertEquals("2021", second.value2)

            vm.clearFilters()
            assertTrue(vm.currentSpec.value.filters.isEmpty())
        }

    @Test
    fun `setSearch trims the term, and blank clears the search spec`() = runTest(dispatcher) {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.setSearch("  hello  ")
        assertEquals("hello", vm.currentSpec.value.search?.term)

        vm.setSearch("   ")
        assertNull(vm.currentSpec.value.search)

        vm.setSearch("world")
        assertEquals("world", vm.currentSpec.value.search?.term)

        vm.setSearch("")
        assertNull(vm.currentSpec.value.search)
    }

    @Test
    fun `setGroup sets the group column and null clears grouping`() = runTest(dispatcher) {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.setGroup(dateColumn.id)
        assertEquals(dateColumn.id, vm.currentSpec.value.group?.columnId)

        vm.setGroup(null)
        assertNull(vm.currentSpec.value.group)
    }

    @Test
    fun `intents preserve unrelated facets of the spec`() = runTest(dispatcher) {
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.setSearch("keep-me")
        vm.addFilter(textColumn.id, CellType.TEXT, FilterOp.EQUALS, "x")
        vm.setSort(textColumn.id, CellType.TEXT, SortDirection.ASC)
        vm.setGroup(dateColumn.id)

        val spec = vm.currentSpec.value
        assertEquals("keep-me", spec.search?.term)
        assertEquals(1, spec.filters.size)
        assertEquals(1, spec.sorts.size)
        assertEquals(dateColumn.id, spec.group?.columnId)
    }

    // ----------------------------------------------------------------------------------------
    // BuildDefaultViewUseCase.build(tableId, columns) overload, exercised directly
    // ----------------------------------------------------------------------------------------

    private val useCase = BuildDefaultViewUseCase()

    private fun column(
        id: Long,
        type: CellType,
        colIndex: Int = 0,
        confidence: Double = 0.9,
    ) = ColumnDefEntity(
        id = id, tableId = TABLE_ID, colIndex = colIndex, name = "c$id",
        type = type.name, format = null, confidence = confidence,
    )

    @Test
    fun `build prefers first date column sorted DESC`() {
        val spec = useCase.build(
            TABLE_ID,
            listOf(
                column(1L, CellType.INTEGER, colIndex = 0),
                column(2L, CellType.DATE, colIndex = 1),
                column(3L, CellType.DATETIME, colIndex = 2),
            ),
        )
        assertEquals(1, spec.sorts.size)
        // The DATE column wins over the earlier INTEGER and the later DATETIME.
        assertEquals(2L, spec.sorts.first().columnId)
        assertEquals(CellType.DATE, spec.sorts.first().type)
        assertEquals(SortDirection.DESC, spec.sorts.first().direction)
    }

    @Test
    fun `build falls back to first numeric column sorted ASC when no date`() {
        val spec = useCase.build(
            TABLE_ID,
            listOf(
                column(1L, CellType.BOOLEAN, colIndex = 0),
                column(2L, CellType.CURRENCY, colIndex = 1),
                column(3L, CellType.INTEGER, colIndex = 2),
            ),
        )
        assertEquals(1, spec.sorts.size)
        // First numeric type (CURRENCY at index 1) is chosen ascending.
        assertEquals(2L, spec.sorts.first().columnId)
        assertEquals(CellType.CURRENCY, spec.sorts.first().type)
        assertEquals(SortDirection.ASC, spec.sorts.first().direction)
    }

    @Test
    fun `build falls back to first column ASC when neither date nor numeric present`() {
        val spec = useCase.build(
            TABLE_ID,
            listOf(
                column(1L, CellType.BOOLEAN, colIndex = 0, confidence = 0.1),
                column(2L, CellType.TEXT, colIndex = 1, confidence = 0.1),
            ),
        )
        assertEquals(1, spec.sorts.size)
        assertEquals(1L, spec.sorts.first().columnId)
        assertEquals(CellType.BOOLEAN, spec.sorts.first().type)
        assertEquals(SortDirection.ASC, spec.sorts.first().direction)
    }

    @Test
    fun `build groups by first high-confidence text column`() {
        val spec = useCase.build(
            TABLE_ID,
            listOf(
                column(1L, CellType.DATE, colIndex = 0),
                column(2L, CellType.TEXT, colIndex = 1, confidence = 0.5), // too low
                column(3L, CellType.TEXT, colIndex = 2, confidence = 0.85), // low-cardinality
            ),
        )
        assertEquals(3L, spec.group?.columnId)
    }

    @Test
    fun `build does not group when text columns are low confidence`() {
        val spec = useCase.build(
            TABLE_ID,
            listOf(
                column(1L, CellType.DATE, colIndex = 0),
                column(2L, CellType.TEXT, colIndex = 1, confidence = 0.79), // just below threshold
            ),
        )
        assertNull(spec.group)
    }

    @Test
    fun `build over no columns yields an empty spec`() {
        val spec = useCase.build(TABLE_ID, emptyList())
        assertEquals(TABLE_ID, spec.tableId)
        assertTrue(spec.sorts.isEmpty())
        assertNull(spec.group)
        assertTrue(spec.filters.isEmpty())
        assertNull(spec.search)
    }

    private companion object {
        const val TABLE_ID = 42L
    }
}

/** Minimal [SheetRepository] fake: only [getColumns] is exercised by the view model. */
private class FakeSheetRepository(
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
        throw UnsupportedOperationException("not used in these tests")

    override suspend fun getCells(rowId: Long): List<CellEntity> = emptyList()

    override suspend fun deleteSheet(tableId: Long) = Unit
}

/** Minimal [QueryRepository] fake; [rows] is never invoked because the paged flow isn't collected. */
private object FakeQueryRepository : QueryRepository {
    override fun rows(spec: QuerySpec): PagingSource<Int, RowEntity> =
        throw UnsupportedOperationException("not used in these tests")

    override suspend fun rowsList(spec: QuerySpec): List<RowEntity> = emptyList()

    override suspend fun groupSummary(tableId: Long, columnId: Long): List<GroupBucket> = emptyList()
    override suspend fun aggregate(spec: com.plefy.app.domain.query.AggregateSpec): List<com.plefy.app.database.dao.GroupAggregate> = emptyList()

    override suspend fun cells(rowId: Long): List<CellEntity> = emptyList()
}
