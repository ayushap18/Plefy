package com.plefy.app.feature.importer

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * The lifecycle status of a background spreadsheet import.
 *
 * A scheduler emits [Running] while the work is enqueued/executing and exactly one terminal value
 * ([Succeeded] or [Failed]) before completing.
 */
sealed interface ImportStatus {
    /**
     * The import has been enqueued and is in flight.
     *
     * @property fraction progress in `0f..1f`, or `null` when it is unknown (still indeterminate).
     */
    data class Running(val fraction: Float? = null) : ImportStatus

    /** The import finished and the sheet is now persisted. */
    data object Succeeded : ImportStatus

    /** The import failed; [message] is a user-facing reason. */
    data class Failed(val message: String) : ImportStatus
}

/**
 * Enqueues background spreadsheet imports and reports their progress.
 *
 * This interface deliberately hides WorkManager (and the Android [Context] needed to resolve the
 * picked document) from [LibraryViewModel]. Keeping the framework out of the ViewModel makes the
 * ViewModel a pure, JVM-unit-testable state machine — a fake [ImportScheduler] can drive it with a
 * scripted [Flow] instead of a real, hard-to-synchronise WorkManager.
 */
interface ImportScheduler {
    /**
     * Enqueues an import of the document at [uri] and returns a cold [Flow] of its [ImportStatus].
     * The flow emits [ImportStatus.Running] first and completes after a terminal status.
     */
    fun enqueue(uri: Uri): Flow<ImportStatus>
}
