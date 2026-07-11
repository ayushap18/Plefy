package com.plefy.app.feature.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plefy.app.data.repository.SheetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing the Library screen.
 *
 * Folds three sources of truth into a single [LibraryUiState]:
 * - the persisted sheets, observed from [SheetRepository.observeSheets];
 * - an `importing` flag driven by the [ImportStatus] stream of the most recent import;
 * - a transient `error` message surfaced when an import fails.
 *
 * Background import is delegated to an [ImportScheduler]; the ViewModel holds no WorkManager or
 * `Context` reference, which keeps it a pure state machine that unit-tests on the JVM. DI lives
 * entirely in `:app`; this library module stays Hilt-module-free and only declares constructor
 * dependencies that `:app`'s `SingletonComponent` satisfies.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val sheetRepository: SheetRepository,
    private val importScheduler: ImportScheduler,
) : ViewModel() {

    /** `true` while an enqueued/running import is in flight. */
    private val _importing = MutableStateFlow(false)

    /** Latest import completion fraction (`0f..1f`), or `null` while indeterminate. */
    private val _progress = MutableStateFlow<Float?>(null)

    /** The latest import failure message, or `null` when there is nothing to report. */
    private val _error = MutableStateFlow<String?>(null)

    /**
     * The screen's observable state: the current sheets plus the import progress/error flags.
     *
     * `WhileSubscribed(5_000)` keeps the upstream Room flow warm across brief configuration
     * changes while releasing it when the screen is truly gone.
     */
    val uiState: StateFlow<LibraryUiState> =
        combine(
            sheetRepository.observeSheets(),
            _importing,
            _progress,
            _error,
        ) { sheets, importing, progress, error ->
            LibraryUiState(
                sheets = sheets,
                importing = importing,
                progress = progress,
                error = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    /**
     * Handles a spreadsheet picked from the system file picker: hands the URI to the
     * [ImportScheduler] and mirrors the resulting [ImportStatus] stream into `importing`/`error`.
     */
    fun onFilePicked(uri: Uri) {
        _error.value = null
        viewModelScope.launch {
            importScheduler.enqueue(uri).collect { status ->
                when (status) {
                    is ImportStatus.Running -> {
                        _importing.value = true
                        _progress.value = status.fraction
                    }
                    ImportStatus.Succeeded -> {
                        _importing.value = false
                        _progress.value = null
                    }
                    is ImportStatus.Failed -> {
                        _importing.value = false
                        _progress.value = null
                        _error.value = status.message
                    }
                }
            }
        }
    }

    /** Deletes a sheet (and, by cascade, its columns/rows/cells). */
    fun deleteSheet(tableId: Long) {
        viewModelScope.launch {
            sheetRepository.deleteSheet(tableId)
        }
    }

    /** Clears the current error after the UI has shown it (e.g. dismissed the snackbar). */
    fun onErrorShown() {
        _error.update { null }
    }
}
