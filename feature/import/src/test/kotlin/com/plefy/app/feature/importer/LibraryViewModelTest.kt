package com.plefy.app.feature.importer

import android.net.Uri
import androidx.paging.PagingSource
import com.plefy.app.common.AppResult
import com.plefy.app.data.repository.SheetRepository
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

/**
 * Unit tests for [LibraryViewModel]'s state plumbing.
 *
 * The ViewModel is now framework-free: background import is delegated to an [ImportScheduler], so
 * these tests inject a scripted [FakeImportScheduler] and a fake [SheetRepository] and never touch
 * WorkManager. Robolectric is used only so `android.net.Uri` is real; there is no emulator and no
 * background executor to synchronise, so the tests are fast and deterministic.
 *
 * The Compose screen ([LibraryScreen]) is intentionally not instrumented here — its manual smoke
 * checklist lives in [ManualSmokeSteps] at the bottom of this file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSheetRepository
    private lateinit var scheduler: FakeImportScheduler
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSheetRepository()
        scheduler = FakeImportScheduler()
        viewModel = LibraryViewModel(sheetRepository = repository, importScheduler = scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_reflectsRepositorySheets() = runTest(dispatcher) {
        val states = mutableListOf<LibraryUiState>()
        val job = backgroundScope.launch { viewModel.uiState.toList(states) }

        repository.sheets.value = listOf(
            SheetTableEntity(id = 1, name = "People", rowCount = 3),
            SheetTableEntity(id = 2, name = "Sales", rowCount = 9),
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.sheets.size)
        assertEquals("People", state.sheets[0].name)
        assertEquals(9, state.sheets[1].rowCount)
        assertFalse(state.importing)
        assertNull(state.error)
        job.cancel()
    }

    @Test
    fun deleteSheet_removesFromRepositoryAndState() = runTest(dispatcher) {
        val states = mutableListOf<LibraryUiState>()
        val job = backgroundScope.launch { viewModel.uiState.toList(states) }
        repository.sheets.value = listOf(
            SheetTableEntity(id = 7, name = "Temp", rowCount = 1),
            SheetTableEntity(id = 8, name = "Keep", rowCount = 2),
        )

        viewModel.deleteSheet(7)

        assertTrue(repository.deletedIds.contains(7L))
        assertEquals(listOf(8L), viewModel.uiState.value.sheets.map { it.id })
        job.cancel()
    }

    @Test
    fun onErrorShown_clearsError() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        // Drive an error through a failing import first.
        scheduler.script = { flowOf(ImportStatus.Running(), ImportStatus.Failed("boom")) }
        viewModel.onFilePicked(Uri.parse("content://x/bad.csv"))
        assertEquals("boom", viewModel.uiState.value.error)

        viewModel.onErrorShown()

        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun onFilePicked_setsImportingWhileRunning() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val uri = Uri.parse("content://com.example.test/People.xlsx")
        // Hold in Running (never terminal) so the importing flag is observable, not conflated away.
        scheduler.script = { flow { emit(ImportStatus.Running()); awaitCancellation() } }

        viewModel.onFilePicked(uri)

        assertEquals("the picked uri is scheduled", listOf(uri), scheduler.enqueued)
        assertTrue("importing is true while running", viewModel.uiState.value.importing)
        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun onFilePicked_settlesImportingFalseOnSuccess() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        scheduler.script = { flowOf(ImportStatus.Running(), ImportStatus.Succeeded) }

        viewModel.onFilePicked(Uri.parse("content://com.example.test/People.xlsx"))

        assertFalse("importing settles false on success", viewModel.uiState.value.importing)
        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun onFilePicked_mapsRunningFractionIntoProgress() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        // Hold on a fractional Running (never terminal) so progress is observable, not conflated away.
        scheduler.script = { flow { emit(ImportStatus.Running(0.42f)); awaitCancellation() } }

        viewModel.onFilePicked(Uri.parse("content://com.example.test/People.xlsx"))

        val state = viewModel.uiState.value
        assertTrue("importing while running", state.importing)
        assertEquals("Running.fraction is surfaced as uiState.progress", 0.42f, state.progress)
        job.cancel()
    }

    @Test
    fun onFilePicked_indeterminateRunningLeavesProgressNull() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        // A null fraction means "unknown" — the bar should stay indeterminate.
        scheduler.script = { flow { emit(ImportStatus.Running(null)); awaitCancellation() } }

        viewModel.onFilePicked(Uri.parse("content://com.example.test/People.xlsx"))

        assertTrue(viewModel.uiState.value.importing)
        assertNull("indeterminate Running keeps progress null", viewModel.uiState.value.progress)
        job.cancel()
    }

    @Test
    fun onFilePicked_clearsProgressWhenImportSettles() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        scheduler.script = { flowOf(ImportStatus.Running(0.9f), ImportStatus.Succeeded) }

        viewModel.onFilePicked(Uri.parse("content://com.example.test/People.xlsx"))

        val state = viewModel.uiState.value
        assertFalse("importing settles false", state.importing)
        assertNull("progress resets once the import is done", state.progress)
        job.cancel()
    }

    @Test
    fun onFilePicked_surfacesErrorWhenImportFails() = runTest(dispatcher) {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        scheduler.script = { flowOf(ImportStatus.Running(), ImportStatus.Failed("Unsupported file.")) }

        viewModel.onFilePicked(Uri.parse("content://com.example.test/missing.zip"))

        val state = viewModel.uiState.value
        assertFalse(state.importing)
        assertNotNull(state.error)
        assertEquals("Unsupported file.", state.error)
        job.cancel()
    }

    /** Scriptable [ImportScheduler] fake: records enqueued URIs and returns a supplied status flow. */
    private class FakeImportScheduler : ImportScheduler {
        val enqueued = mutableListOf<Uri>()
        var script: () -> Flow<ImportStatus> = { flowOf(ImportStatus.Running(), ImportStatus.Succeeded) }

        override fun enqueue(uri: Uri): Flow<ImportStatus> {
            enqueued.add(uri)
            return script()
        }
    }

    /** In-memory [SheetRepository] fake exposing only what the ViewModel touches. */
    private class FakeSheetRepository : SheetRepository {
        val sheets = MutableStateFlow<List<SheetTableEntity>>(emptyList())
        val deletedIds = mutableListOf<Long>()

        override suspend fun importSpreadsheet(
            input: InputStream,
            fileName: String,
            displayName: String,
            onProgress: (suspend (Float) -> Unit)?,
        ): AppResult<Long> = AppResult.Success(1L)

        override fun observeSheets() = sheets

        override suspend fun getColumns(tableId: Long): List<ColumnDefEntity> = emptyList()

        override fun rowPagingSource(tableId: Long): PagingSource<Int, RowEntity> =
            throw UnsupportedOperationException()

        override suspend fun getCells(rowId: Long): List<CellEntity> = emptyList()

        override suspend fun deleteSheet(tableId: Long) {
            deletedIds.add(tableId)
            sheets.value = sheets.value.filterNot { it.id == tableId }
        }
    }
}

/**
 * Manual smoke steps for the Library screen ([LibraryScreen]) — run on a device/emulator, since
 * these paths are Compose + SAF + WorkManager UI that we intentionally do not instrument here.
 *
 * 1. Empty state: launch with no imported sheets — the "No spreadsheets yet" message and the
 *    "+" FloatingActionButton are visible.
 * 2. Import happy path: tap "+", pick a valid .csv/.xls/.xlsx in the SAF picker. A progress
 *    indicator appears while importing, then the new sheet's card (name + "<n> rows") appears and
 *    the progress indicator disappears.
 * 3. Import failure: pick an unsupported/corrupt file — a snackbar shows the failure message and
 *    the progress indicator clears; the snackbar does not reappear on rotation.
 * 4. Open: tap a sheet card — navigates to its viewer (onOpenSheet fires with the table id).
 * 5. Delete: tap a row's delete affordance — the row disappears from the list.
 * 6. Rotation: rotate mid-import — progress survives and the import completes without restarting.
 */
private object ManualSmokeSteps
