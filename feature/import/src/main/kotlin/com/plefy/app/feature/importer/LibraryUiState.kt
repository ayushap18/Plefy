package com.plefy.app.feature.importer

import com.plefy.app.database.entity.SheetTableEntity

/**
 * Immutable UI state for the Library screen.
 *
 * @property sheets the imported spreadsheets currently persisted, newest state pushed by the
 *   repository's observation flow.
 * @property importing `true` while a background [ImportWorker] is enqueued or running, so the UI
 *   can show progress.
 * @property progress the import completion fraction in `0f..1f`, or `null` when it is unknown and
 *   the progress bar should be indeterminate.
 * @property error a human-readable message describing the most recent import failure, or `null`
 *   when there is nothing to report. Consume it with [LibraryViewModel.onErrorShown].
 */
data class LibraryUiState(
    val sheets: List<SheetTableEntity> = emptyList(),
    val importing: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
)
